package org.jetlinks.collector.plugin;

import jakarta.annotation.Nullable;
import org.jetlinks.collector.AbstractLifecycle;
import org.jetlinks.collector.CollectorConstants;
import org.jetlinks.core.enums.ErrorCode;
import org.jetlinks.core.exception.DeviceOperationException;
import org.jetlinks.core.monitor.logger.Logger;
import org.jetlinks.core.monitor.tracer.Tracer;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public abstract class AbstractTcpClientLifecycle extends AbstractLifecycle {

    static final AtomicReferenceFieldUpdater<AbstractTcpClientLifecycle, Sinks.One>
        CONNECTING = AtomicReferenceFieldUpdater.newUpdater(AbstractTcpClientLifecycle.class, Sinks.One.class, "connecting");

    private volatile Connection connected;
    // 握手尚未完成的连接。失败或停止时释放，成功后才转入 connected。
    private volatile Connection opening;
    private volatile Sinks.One<Connection> connecting;
    private volatile Disposable connectionDisposable;
    private int retryNumber;
    protected Throwable lastError;

    protected abstract Logger logger();

    protected abstract Tracer tracer();

    protected abstract TcpClient initClient();

    /**
     * 是否重连
     *
     * @param lastError 上次连接错误
     * @return
     */
    protected boolean isReconnect(@Nullable Throwable lastError) {
        return true;
    }


    /**
     * 最大连续重连次数,大于0生效
     */
    protected int getMaxRetryNumber() {
        return -1;
    }

    protected abstract Mono<Void> initConnection(Connection connection);


    @Override
    protected void start0() {
        tryReconnect();
    }


    @Override
    protected void stop0() {
        if (null != connected) {
            connected.dispose();
        }
        if (null != opening) {
            opening.dispose();
        }
        if (connectionDisposable != null) {
            connectionDisposable.dispose();
        }
        if (connecting != null) {
            connecting.tryEmitError(new DeviceOperationException(ErrorCode.CONNECTION_LOST));
        }
    }

    private void tryReconnect() {
        long duration = retryNumber * 2L;
        logger().info("tcp服务将在{}秒后重连", duration);
        // 握手在 connect() 内完成。这里只负责退避后重试，失败不再次重试，避免和外层错误回调叠成两次重连。
        Mono.delay(Duration.ofSeconds(duration))
            .then(Mono.defer(this::connect))
            .subscribe(null, error -> logger().warn("tcp服务初始化失败", error));
    }

    protected final Mono<Connection> connect() {
        if (connected != null && !connected.isDisposed()) {
            return Mono.just(connected);
        }
        //关闭循环
        if (CollectorConstants.States.stopped.equals(state())) {
            logger().debug("tcp服务已停止");
            return Mono.error(() -> new DeviceOperationException(ErrorCode.CONNECTION_LOST));
        }

        Sinks.One<Connection> connecting = Sinks.one();
        if (CONNECTING.compareAndSet(this, null, connecting)) {
            if (null != this.connectionDisposable) {
                this.connectionDisposable.dispose();
            }

            @SuppressWarnings("all")
            Disposable connectionDisposable = connection()
                // TCP 连通后先完成协议握手。Reactor Netty 先把连接交给下游，随后才回调 doOnConnected，
                // 所以握手失败必须释放这里的 socket，不能只依赖 opening。
                .flatMap(socket -> initConnection(socket)
                    .doOnError(error -> socket.dispose())
                    .thenReturn(socket))
                .as(tracer().traceMono("/tcp/connect"))
                .subscribe(
                    c -> {
                        logger().debug("tcp服务连接成功");
                        lastError = null;
                        retryNumber = 0;
                        publish(c);
                        connecting.tryEmitValue(c);
                        CONNECTING.compareAndSet(this, connecting, null);
                    },
                    (err) -> {
                        logger().warn("tcp服务连接失败", err);
                        discardOpening();
                        connecting.tryEmitError(lastError = err);
                        CONNECTING.compareAndSet(this, connecting, null);
                        if (!CollectorConstants.States.stopped.equals(state())) {
                            tryReconnect();
                        }
                    },
                    () -> {
                        if (CONNECTING.compareAndSet(this, connecting, null)) {
                            connecting.tryEmitEmpty();
                            logger().warn("tcp服务连接结束");
                            tryReconnect();
                        }
                    });
            this.connectionDisposable = connectionDisposable;
            return connecting.asMono();
        }
        return this.connecting.asMono();
    }

    private Mono<Connection> connection() {
        if (state() != CollectorConstants.States.starting && state() != CollectorConstants.States.running) {
            return Mono.empty();
        }
        if (this.isDisposed() || !isReconnect(lastError)) {
            return Mono.empty();
        }

        //超过重试次数
        if (this.getMaxRetryNumber() > 0
            && retryNumber >= this.getMaxRetryNumber()) {
            logger().warn("tcp服务连接重试次数超过最大值[{}]", this.getMaxRetryNumber());
            dispose();
            return Mono.empty();
        }
        logger().debug("开始第{}次连接tcp服务", retryNumber++);
        return initClient()
            .doOnConnected(this::connected)
            .connect()
            .cast(Connection.class);
    }

    private synchronized void connected(Connection socket) {
        // 此时只是 TCP 已连通。重试计数要等握手成功再清零，否则握手失败会以 0 秒间隔空转。
        if (CollectorConstants.States.stopped.equals(state())) {
            socket.dispose();
            return;
        }
        Connection previous = this.opening;
        this.opening = socket;
        if (previous != null && previous != socket) {
            previous.dispose();
        }
    }

    private synchronized void publish(Connection socket) {
        Connection previous = this.connected;
        this.connected = socket;
        this.opening = null;
        if (previous != null && previous != socket) {
            previous.dispose();
        }
    }

    private synchronized void discardOpening() {
        Connection opening = this.opening;
        this.opening = null;
        if (opening != null) {
            opening.dispose();
        }
    }

}
