package org.jetlinks.collector.plugin;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.ReferenceCountUtil;
import org.jetlinks.collector.AbstractLifecycle;
import org.jetlinks.collector.DataCollectorProvider;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.logger.Logger;
import org.jetlinks.core.monitor.metrics.MetricRegistration;
import org.jetlinks.core.monitor.metrics.Metrics;
import org.jetlinks.core.monitor.tracer.Tracer;
import org.jetlinks.core.server.ConnectionMetrics;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;
import reactor.netty.udp.UdpClient;
import reactor.netty.udp.UdpServer;

import java.net.InetSocketAddress;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TransportMetricsTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void countsRawBuffersAndDatagramsWithoutChangingOwnershipOrDoubleInstalling() {
        TransportMetrics metrics = new TransportMetrics(Monitor.noop());
        EmbeddedChannel first = new EmbeddedChannel(), second = new EmbeddedChannel();
        try {
            metrics.install(first);
            metrics.install(first);
            metrics.install(second);
            var bytes = Unpooled.wrappedBuffer(new byte[]{1, 2, 3});
            assertTrue(first.writeInbound(bytes));
            assertSame(bytes, first.readInbound());
            assertEquals(0, bytes.readerIndex());
            assertEquals(1, bytes.refCnt());
            bytes.release();
            var packet = new DatagramPacket(Unpooled.wrappedBuffer(new byte[]{4, 5}), new InetSocketAddress(10000));
            assertTrue(second.writeInbound(packet));
            assertSame(packet, second.readInbound());
            packet.release();
            assertEquals(5, metrics.getReadBytes());
            assertEquals(0, metrics.getTotalMessages(), "read chunks are not protocol messages");
            assertEquals(2, metrics.getActiveConnections());
            assertEquals(2, metrics.getConnectAttempts());
        } finally {
            first.finishAndReleaseAll();
            second.finishAndReleaseAll();
        }
        assertEquals(0, metrics.getActiveConnections());
        assertEquals(5, metrics.getReadBytes(), "closing a connection keeps channel totals");
    }

    @Test
    void observesEncodedBytesAndCountsOnlySuccessfulWrites() {
        TransportMetrics metrics = new TransportMetrics(Monitor.noop());
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            metrics.install(channel);
            Connection.from(channel).addHandlerFirst(new MessageToByteEncoder<Integer>() {
                @Override protected void encode(ChannelHandlerContext context, Integer value, io.netty.buffer.ByteBuf out) {
                    out.writeInt(value);
                }
            });
            assertTrue(channel.writeOutbound(123));
            ReferenceCountUtil.release(channel.readOutbound());
            assertEquals(4, metrics.getWrittenBytes(), "the metric handler stays before protocol encoders");
            channel.pipeline().addFirst("reject-write", new ChannelOutboundHandlerAdapter() {
                @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                    ReferenceCountUtil.release(message);
                    promise.setFailure(new IllegalStateException("write rejected"));
                }
            });
            var write = channel.writeAndFlush(456);
            assertFalse(write.isSuccess());
            assertEquals(4, metrics.getWrittenBytes());
            assertEquals(1, metrics.getTransportErrors());
        } finally { channel.finishAndReleaseAll(); }
    }

    @Test
    void tcpPropagatesChannelOwnerAndAccumulatesReconnectsWithoutCrossChannelLeakage() {
        var server = TcpServer.create().host("127.0.0.1").port(0)
            .handle((in, out) -> in.receive().asByteArray().next()
                .flatMap(bytes -> out.sendByteArray(Mono.just(bytes)).then()))
            .bindNow(TIMEOUT);
        Owner first = new Owner(Monitor.noop()), second = new Owner(Monitor.noop());
        try {
            exchange(server.port(), first, 3);
            exchange(server.port(), second, 5);
            exchange(server.port(), first, 7);
            assertEquals(10, first.metrics.getReadBytes());
            assertEquals(10, first.metrics.getWrittenBytes());
            assertEquals(5, second.metrics.getReadBytes());
            assertEquals(5, second.metrics.getWrittenBytes());
            assertEquals(2, first.metrics.getConnectAttempts());
            assertEquals(0, first.metrics.getActiveConnections());
        } finally { server.disposeNow(TIMEOUT); first.dispose(); second.dispose(); }
    }

    private static void exchange(int port, Owner owner, int size) {
        Connection connection = TransportMetrics.record(
            TransportMetrics.instrument(TcpClient.newConnection()).host("127.0.0.1").port(port).connect(), owner)
            .block(TIMEOUT);
        assertNotNull(connection);
        try {
            byte[] response = connection.outbound().sendByteArray(Mono.just(new byte[size])).then()
                .then(connection.inbound().receive().aggregate().asByteArray()).block(TIMEOUT);
            assertArrayEquals(new byte[size], response);
            connection.onDispose().block(TIMEOUT);
        } finally { connection.disposeNow(TIMEOUT); }
    }

    @Test
    void udpCountsWholeDatagramsThroughTheSameOwnerContext() {
        var server = UdpServer.create().host("127.0.0.1").port(0)
            .handle((in, out) -> out.sendObject(in.receiveObject().cast(DatagramPacket.class)
                .map(packet -> new DatagramPacket(packet.content().retain(), packet.sender()))))
            .bindNow(TIMEOUT);
        Owner owner = new Owner(Monitor.noop());
        var connection = TransportMetrics.record(
            TransportMetrics.instrument(UdpClient.create()).host("127.0.0.1").port(((InetSocketAddress) server.address()).getPort()).connect(), owner)
            .block(TIMEOUT);
        assertNotNull(connection);
        try {
            byte[] response = connection.outbound().sendByteArray(Mono.just(new byte[9])).then()
                .then(connection.inbound().receive().asByteArray().next()).block(TIMEOUT);
            assertArrayEquals(new byte[9], response);
            assertEquals(9, owner.metrics.getReadBytes());
            assertEquals(9, owner.metrics.getWrittenBytes());
        } finally { connection.disposeNow(TIMEOUT); server.disposeNow(TIMEOUT); owner.dispose(); }
        assertEquals(0, owner.metrics.getActiveConnections());
    }

    @Test
    void observationBelongsToTheChannelAndIsReleasedOnce() {
        Registry registry = new Registry();
        Monitor monitor = new Monitor() {
            @Override public Logger logger() { return Monitor.noop().logger(); }
            @Override public Tracer tracer() { return Monitor.noop().tracer(); }
            @Override public Metrics metrics() { return registry; }
        };
        Owner owner = new Owner(monitor);
        assertEquals("transport", registry.scope);
        assertSame(owner.metrics, registry.target);
        assertFalse(owner.metrics.isDisposed());
        owner.dispose(); owner.dispose();
        assertEquals(1, registry.disposed);
        assertTrue(owner.metrics.isDisposed());
    }

    @Test
    void resolvesTheCurrentOwnerAtSubscriptionAndDoesNotInheritAnotherChannelsOwner() {
        var first = new TransportMetrics(Monitor.noop());
        var second = new TransportMetrics(Monitor.noop());
        var current = new java.util.concurrent.atomic.AtomicReference<>(first);
        Owner owner = new Owner(Monitor.noop()) {
            @Override public TransportMetrics transportMetrics() { return current.get(); }
        };
        Mono<TransportMetrics> source = Mono.deferContextual(context -> Mono.justOrEmpty(context.<TransportMetrics>getOrEmpty(TransportMetrics.class)));
        var recorded = TransportMetrics.record(source, owner);
        assertSame(first, recorded.block(TIMEOUT));
        current.set(second);
        assertSame(second, recorded.block(TIMEOUT));
        assertNull(TransportMetrics.record(source, null)
            .contextWrite(context -> context.put(TransportMetrics.class, first)).block(TIMEOUT));
        owner.dispose(); first.dispose(); second.dispose();
    }

    static class Owner extends AbstractLifecycle implements DataCollectorProvider.ChannelRuntime, TransportMetrics.Owner {
        final TransportMetrics metrics;
        Owner(Monitor monitor) { metrics = new TransportMetrics(monitor); doOnStop(metrics); }
        @Override public String getId() { return "test"; }
        @Override public TransportMetrics transportMetrics() { return metrics; }
        @Override protected void start0() { }
        @Override protected void stop0() { }
    }

    private static class Registry implements Metrics {
        String scope; Object target; int disposed;
        @Override public void count(String operation, int inc) { }
        @Override public void value(String operation, double value) { }
        @Override public void error(String operation, Throwable error) { }
        @Override public <T> MetricRegistration observe(String scope, Class<T> contract, T target) {
            assertSame(ConnectionMetrics.class, contract);
            this.scope = scope; this.target = target;
            return new MetricRegistration() {
                @Override public boolean isSupported() { return true; }
                @Override public boolean isDisposed() { return disposed > 0; }
                @Override public void dispose() { if (disposed == 0) disposed++; }
            };
        }
    }
}
