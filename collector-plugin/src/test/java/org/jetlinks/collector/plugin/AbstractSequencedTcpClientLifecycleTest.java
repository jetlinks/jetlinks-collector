package org.jetlinks.collector.plugin;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import org.jetlinks.collector.plugin.tcp.SequencedPayload;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.logger.Logger;
import org.jetlinks.core.monitor.tracer.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AbstractSequencedTcpClientLifecycleTest {

    private DisposableServer server;
    private Probe probe;

    @AfterEach
    void stop() {
        if (probe != null) probe.dispose();
        if (server != null) server.disposeNow();
    }

    @Test
    void completesSequentialHandshakeBeforeAllocatingApplicationSequence() {
        start();
        Reply reply = probe.request(42).block(Duration.ofSeconds(5));

        assertNotNull(reply);
        assertEquals(42, reply.value());
        assertEquals(3, reply.getSequenceNumber());
        assertEquals(List.of(-1, -2, 42), probe.sent);
    }

    @Test
    void concurrentApplicationRequestsShareTheHandshakeAndKeepTheirOwnReplies() {
        start();
        var replies = Mono.zip(probe.request(20), probe.request(30)).block(Duration.ofSeconds(5));

        assertNotNull(replies);
        assertEquals(20, replies.getT1().value());
        assertEquals(30, replies.getT2().value());
        assertEquals(List.of(-1, -2, 20, 30), probe.sent);
    }

    private void start() {
        server = TcpServer.create().port(0)
                          .handle((in, out) -> out.send(in.receive().retain()))
                          .bindNow();
        probe = new Probe(server.port());
        probe.start();
    }

    private record Reply(Integer number, int value) implements SequencedPayload<Integer> {
        @Override
        public Integer getSequenceNumber() {
            return number;
        }
    }

    private static final class Probe extends AbstractSequencedTcpClientLifecycle<Integer, Reply, Integer> {
        private final int port;
        private final List<Integer> sent = new CopyOnWriteArrayList<>();

        private Probe(int port) {
            this.port = port;
        }

        @Override
        protected Logger logger() { return Monitor.noop().logger(); }

        @Override
        protected Tracer tracer() { return Monitor.noop().tracer(); }

        @Override
        protected TcpClient initClient() { return TcpClient.create().host("127.0.0.1").port(port); }

        @Override
        protected Mono<Void> beforeInitConnection(Connection connection) {
            connection.addHandlerLast(new FixedLengthFrameDecoder(8));
            return Mono.empty();
        }

        @Override
        protected Mono<Void> afterInitConnection(Connection connection) {
            return request(connection, -1).then(request(connection, -2)).then();
        }

        @Override
        protected Reply handleInbound(Object payload) {
            ByteBuf buffer = (ByteBuf) payload;
            return new Reply(buffer.readInt(), buffer.readInt());
        }

        @Override
        protected Object handleOutbound(Integer number, Integer value) {
            sent.add(value);
            return Unpooled.buffer(8).writeInt(number).writeInt(value);
        }

        @Override
        protected Integer nextSequenceNumber(Integer previous) { return previous == null ? 1 : previous + 1; }
    }
}
