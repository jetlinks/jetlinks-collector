package org.jetlinks.collector.plugin;

import org.jetlinks.core.server.ConnectionMetricsRecorder;

import javax.net.SocketFactory;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/** 为使用阻塞 Socket 的协议 SDK 记录实际流字节，保留原有连接参数与关闭语义。 */
public final class TransportSocketFactory extends SocketFactory {
    private final ConnectionMetricsRecorder metrics;

    public TransportSocketFactory(ConnectionMetricsRecorder metrics) {
        this.metrics = metrics;
    }

    @Override
    public Socket createSocket() {
        return new Socket() {
            private boolean active;

            @Override
            public void connect(SocketAddress endpoint, int timeout) throws IOException {
                metrics.connecting();
                try {
                    super.connect(endpoint, timeout);
                    synchronized (this) {
                        if (!isClosed()) {
                            metrics.connected();
                            active = true;
                        }
                    }
                } catch (IOException error) {
                    metrics.connectFailed();
                    try { close(); } catch (IOException suppressed) { error.addSuppressed(suppressed); }
                    throw error;
                }
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new FilterInputStream(super.getInputStream()) {
                    @Override
                    public int read() throws IOException {
                        int value = in.read();
                        if (value >= 0) metrics.received(1);
                        return value;
                    }

                    @Override
                    public int read(byte[] bytes, int offset, int length) throws IOException {
                        int count = in.read(bytes, offset, length);
                        if (count > 0) metrics.received(count);
                        return count;
                    }

                    @Override
                    public long skip(long length) throws IOException {
                        long count = in.skip(length);
                        if (count > 0) metrics.received(count);
                        return count;
                    }
                };
            }

            @Override
            public OutputStream getOutputStream() throws IOException {
                return new FilterOutputStream(super.getOutputStream()) {
                    @Override
                    public void write(int value) throws IOException {
                        out.write(value);
                        metrics.sent(1);
                    }

                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        out.write(bytes, offset, length);
                        if (length > 0) metrics.sent(length);
                    }
                };
            }

            @Override
            public synchronized void close() throws IOException {
                try {
                    super.close();
                } finally {
                    if (active) {
                        active = false;
                        metrics.disconnected();
                    }
                }
            }
        };
    }

    private Socket connect(InetSocketAddress remote, InetSocketAddress local) throws IOException {
        Socket socket = createSocket();
        try {
            if (local != null) socket.bind(local);
            socket.connect(remote);
            return socket;
        } catch (IOException error) {
            try { socket.close(); } catch (IOException suppressed) { error.addSuppressed(suppressed); }
            throw error;
        }
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return connect(new InetSocketAddress(host, port), null);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return connect(new InetSocketAddress(host, port), null);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
        return connect(new InetSocketAddress(host, port), new InetSocketAddress(local, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
        return connect(new InetSocketAddress(host, port), new InetSocketAddress(local, localPort));
    }
}
