package org.jetlinks.collector.plugin;

import io.netty.channel.Channel;
import io.netty.resolver.AddressResolverGroup;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.logger.Logger;
import org.jetlinks.core.monitor.tracer.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.ConnectionObserver;
import reactor.netty.DisposableServer;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;
import reactor.netty.transport.TransportConfig;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractTcpClientLifecycleTest {

    private DisposableServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.disposeNow();
        }
    }

    @Test
    void handshakeFailureDisposesSocketAndRetries() throws Exception {
        server = TcpServer.create()
                          .port(0)
                          .handle((in, out) -> in.receive().then())
                          .bindNow();
        Probe lifecycle = new Probe(server.port());
        lifecycle.start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (lifecycle.sockets.size() < 2 && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }

        assertTrue(lifecycle.sockets.size() >= 2, "握手失败后应重新建连");
        assertTrue(lifecycle.sockets.get(0).isDisposed(), "失败的握手连接必须释放");
        Connection ready = lifecycle.connect().block(Duration.ofSeconds(3));
        assertNotNull(ready);
        assertFalse(ready.isDisposed());
        assertNotSame(lifecycle.sockets.get(0), ready);
        lifecycle.dispose();
        assertTrue(ready.isDisposed());
    }

    @Test
    void reusesConnectionPublishedAfterTheInitialReadyCheck() throws Exception {
        server = TcpServer.create().port(0).bindNow();
        ReadyRaceProbe lifecycle = new ReadyRaceProbe(server.port());
        lifecycle.start();
        GatedConnection old = (GatedConnection) lifecycle.connect().block(Duration.ofSeconds(5));
        assertNotNull(old);
        old.disposeNow();
        CompletableFuture<Connection> delayed = CompletableFuture.supplyAsync(() -> {
            old.pauseThread = Thread.currentThread();
            return lifecycle.connect().block(Duration.ofSeconds(5));
        });
        try {
            assertTrue(old.checked.await(5, TimeUnit.SECONDS));
            Connection ready = lifecycle.connect().block(Duration.ofSeconds(5));
            assertNotNull(ready);
            old.resume.countDown();

            assertSame(ready, delayed.get(5, TimeUnit.SECONDS));
            assertEquals(2, lifecycle.connections.get(), "旧连接之后只应建立一个新连接");
            assertFalse(ready.isDisposed());
        } finally {
            old.resume.countDown();
            lifecycle.dispose();
        }
    }

    private static final class ReadyRaceProbe extends AbstractTcpClientLifecycle {
        private final int port;
        private final AtomicInteger connections = new AtomicInteger();

        private ReadyRaceProbe(int port) {
            this.port = port;
        }

        @Override protected Logger logger() { return Monitor.noop().logger(); }
        @Override protected Tracer tracer() { return Monitor.noop().tracer(); }
        @Override protected Mono<Void> initConnection(Connection connection) { return Mono.empty(); }
        @Override protected TcpClient initClient() {
            ConnectionProvider delegate = ConnectionProvider.newConnection();
            ConnectionProvider provider = new ConnectionProvider() {
                @Override
                public Mono<? extends Connection> acquire(TransportConfig config,
                                                          ConnectionObserver observer,
                                                          Supplier<? extends SocketAddress> address,
                                                          AddressResolverGroup<?> resolver) {
                    return delegate.acquire(config, observer, address, resolver)
                                   .map(connection -> {
                                       connections.incrementAndGet();
                                       return new GatedConnection(connection);
                                   });
                }
            };
            return TcpClient.create(provider).host("127.0.0.1").port(port);
        }
    }

    // 通过官方 Connection 适配保留旧连接的检查结果，精确重现另一个握手先完成的线程切换。
    private static final class GatedConnection implements Connection {
        private final Connection delegate;
        private final CountDownLatch checked = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);
        private volatile Thread pauseThread;

        private GatedConnection(Connection delegate) {
            this.delegate = delegate;
        }

        @Override public Channel channel() { return delegate.channel(); }
        @Override public boolean isDisposed() {
            boolean disposed = delegate.isDisposed();
            if (Thread.currentThread() == pauseThread) {
                pauseThread = null;
                checked.countDown();
                try {
                    if (!resume.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("ready check timed out");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
            }
            return disposed;
        }
    }

    static final class Probe extends AbstractTcpClientLifecycle {
        private final int port;
        private int handshakes;
        final List<Connection> sockets = new CopyOnWriteArrayList<>();

        Probe(int port) {
            this.port = port;
        }

        @Override
        protected Logger logger() {
            return Monitor.noop().logger();
        }

        @Override
        protected Tracer tracer() {
            return Monitor.noop().tracer();
        }

        @Override
        protected TcpClient initClient() {
            return TcpClient.create()
                            .host("127.0.0.1")
                            .port(port);
        }

        @Override
        protected Mono<Void> initConnection(Connection connection) {
            sockets.add(connection);
            if (handshakes++ == 0) {
                return Mono.error(new IllegalStateException("handshake"));
            }
            return Mono.empty();
        }
    }
}
