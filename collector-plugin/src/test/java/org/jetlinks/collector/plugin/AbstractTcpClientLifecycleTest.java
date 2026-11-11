package org.jetlinks.collector.plugin;

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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
