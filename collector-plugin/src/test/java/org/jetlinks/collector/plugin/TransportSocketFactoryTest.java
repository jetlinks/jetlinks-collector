package org.jetlinks.collector.plugin;

import org.jetlinks.core.server.ConnectionMetricsRecorder;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TransportSocketFactoryTest {
    @Test
    void countsSingleBulkAndSkippedStreamBytesOnceAndClosesIdempotently() throws Exception {
        var metrics = new ConnectionMetricsRecorder();
        var factory = new TransportSocketFactory(metrics);
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var peer = CompletableFuture.runAsync(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6}, socket.getInputStream().readNBytes(6));
                    socket.getOutputStream().write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            Socket socket = factory.createSocket(InetAddress.getLoopbackAddress(), server.getLocalPort(), InetAddress.getLoopbackAddress(), 0);
            try (socket) {
                socket.setSoTimeout(5000);
                assertEquals(1, metrics.getActiveConnections());
                var out = socket.getOutputStream();
                out.write(1); out.write(new byte[]{2, 3, 4, 5, 6});
                var in = socket.getInputStream();
                assertEquals(1, in.read());
                assertArrayEquals(new byte[]{2, 3, 4}, in.readNBytes(3));
                assertEquals(2, in.skip(2));
                assertArrayEquals(new byte[]{7, 8}, in.readAllBytes());
                assertEquals(-1, in.read());
                assertEquals(8, metrics.getReadBytes());
                assertEquals(6, metrics.getWrittenBytes());
                assertEquals(0, metrics.getTotalMessages());
            }
            socket.close();
            assertEquals(0, metrics.getActiveConnections());
            assertEquals(1, metrics.getConnectAttempts());
            peer.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void failedConnectionsDoNotProduceBytesOrActiveConnections() throws Exception {
        var metrics = new ConnectionMetricsRecorder();
        var factory = new TransportSocketFactory(metrics);
        int port;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) { port = server.getLocalPort(); }
        try (Socket socket = factory.createSocket()) {
            assertThrows(java.io.IOException.class, () -> socket.connect(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1000));
            assertTrue(socket.isClosed());
        }
        assertEquals(1, metrics.getConnectFailures());
        assertEquals(0, metrics.getActiveConnections());
        assertEquals(0, metrics.getReadBytes());
        assertEquals(0, metrics.getWrittenBytes());
    }
}
