package org.jetlinks.collector.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListenerWithExceptions;
import com.fazecast.jSerialComm.SerialPortEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import org.jetlinks.collector.InternalStatusCode;
import org.jetlinks.collector.exception.CollectorFailure;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.logger.Logger;
import org.jetlinks.core.server.ConnectionMetrics;
import org.jetlinks.core.server.ConnectionMetricsRecorder;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.OutputStream;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * 单串口的有界请求队列与帧接收客户端，统一记录实际收发字节和请求耗时。
 * 实例持有观测对象；关闭时注销观测并释放请求、解析器及串口资源。
 */
public class DefaultSerialPortClient implements
    SerialPortClient, SerialPortDataListenerWithExceptions {
    private static final AtomicReferenceFieldUpdater<DefaultSerialPortClient, PendingRequest>
        PENDING = AtomicReferenceFieldUpdater.newUpdater(DefaultSerialPortClient.class, PendingRequest.class, "pending");
    private static final AtomicIntegerFieldUpdater<DefaultSerialPortClient>
        WIP = AtomicIntegerFieldUpdater.newUpdater(DefaultSerialPortClient.class, "wip");

    private final SerialPort port;
    private final SerialPortConfig config;

    private final PayloadParser parser;

    private final OutputStream out;

    private volatile PendingRequest pending;

    private volatile int wip;

    private final Queue<PendingRequest> queue;

    private final Disposable.Composite disposable = Disposables.composite();

    private final Scheduler scheduler;

    private final Logger logger;
    // 串口层统一记录实际帧字节，协议插件无需再次估算 Modbus／其他协议的流量。
    private final ConnectionMetricsRecorder metrics = new ConnectionMetricsRecorder() {
        @Override public long getPendingMessages() {
            return queue.size() + (pending == null ? 0 : 1);
        }
    };

    public DefaultSerialPortClient(SerialPortConfig config,
                                   PayloadParser parser,
                                   int maxQueueSize,
                                   Monitor monitor) {
        this(config, config.create(), parser, maxQueueSize, monitor);
    }

    public DefaultSerialPortClient(SerialPort port,
                                   PayloadParser parser,
                                   int maxQueueSize,
                                   Monitor monitor) {
        this(SerialPortConfig.of(port), port, parser, maxQueueSize, monitor);
    }

    public DefaultSerialPortClient(SerialPortConfig config,
                                   SerialPort port,
                                   PayloadParser parser,
                                   int maxQueueSize,
                                   Monitor monitor) {
        this.config = config;
        this.port = port;
        this.parser = parser;
        this.out = port.getOutputStream();
        this.logger = monitor.logger();
        this.queue = new ArrayBlockingQueue<>(maxQueueSize);
        this.scheduler = Schedulers.single(Schedulers.boundedElastic());
        this.disposable.add(scheduler);
        if (!port.isOpen()) {
            port.openPort();
        }
        if (!this.port.addDataListener(this)) {
            throw new IllegalStateException("do not set listener to port");
        }
        metrics.connecting();
        if (port.isOpen()) {
            metrics.connected();
            disposable.add(metrics::disconnected);
        } else {
            metrics.connectFailed();
        }
        disposable.add(monitor.metrics().observe("transport", ConnectionMetrics.class, metrics));
        this.disposable.add(parser.handlePayload().subscribe(this::handleBuffer, error -> {
            catchException(error instanceof Exception exception
                ? exception : new IllegalStateException("error.handle_serial_port_error", error));
            dispose();
        }));
    }

    @Override
    public String getPath() {
        return port.getSystemPortPath();
    }

    @Override
    public Mono<ByteBuf> sendAndReceive(ByteBuf buf, Duration timeout) {
        return metrics.recordRequest(this
            .receive(buf, timeout, 1)
            .singleOrEmpty(), error -> error instanceof RequestTimeoutException);
    }

    @Override
    public Flux<ByteBuf> sendAndReceiveMulti(ByteBuf buf, Duration timeout, int num) {
        return this
            .receive(buf, timeout, num)
            .take(num);
    }

    @Override
    public boolean isConnected() {
        return !isDisposed() && port.isOpen();
    }

    private Flux<ByteBuf> receive(ByteBuf buf, Duration timeout, int responses) {
        return Flux
            .<ByteBuf>create(sink -> {
                if (!port.isOpen() || isDisposed()) {
                    ReferenceCountUtil.safeRelease(buf);
                    sink.error(new PortClosedException(getPath()));
                    return;
                }
                PendingRequest request = new PendingRequest(buf, sink, timeout, responses);
                sink.onDispose(request);

                if (!queue.offer(request)) {
                    request.fail(channelBusy());
                } else if (isDisposed()) {
                    sink.error(new PortClosedException(getPath()));
                } else if (request.isDisposed()) {
                    queue.remove(request);
                }
                drain();
            }, FluxSink.OverflowStrategy.ERROR)
            .doOnDiscard(ByteBuf.class, ReferenceCountUtil::safeRelease);
    }

    @Override
    public Flux<ByteBuf> sendAndReceiveMulti(ByteBuf buf, Duration timeout) {
        return receive(buf, timeout, -1);
    }

    private void drain() {
        if (isDisposed()) {
            return;
        }

        if (WIP.getAndIncrement(this) != 0) {
            return;
        }

        int missed = 1;
        do {
            for (; ; ) {
                PendingRequest request = PENDING.get(this);
                if (request != null) {
                    break;
                }
                if (isDisposed()) {
                    break;
                }
                request = queue.poll();
                if (request == null) {
                    break;
                }
                // 任务已经取消,重新拉取.
                if (request.isCancelled()) {
                    continue;
                }
                if (PENDING.compareAndSet(this, null, request)) {
                    //独立线程发起请求,防止阻塞请求线程
                    try {
                        scheduler.schedule(request::sendRequest,
                                           config.getCommunicationInterval().toNanos(),
                                           TimeUnit.NANOSECONDS);
                    } catch (RejectedExecutionException error) {
                        request.fail(new PortClosedException(getPath()));
                    }
                } else {
                    if (!queue.offer(request)) {
                        request.fail(channelBusy());
                    }
                }
            }
            missed = WIP.addAndGet(this, -missed);
        } while (missed != 0);

    }

    @Override
    public void catchException(Exception e) {
        logger.error("handle serial port payload error", e);
        PendingRequest request = PENDING.get(this);
        if (request != null) {
            request.fail(new IllegalStateException("error.handle_serial_port_error", e));
        }
    }

    @Override
    public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_RECEIVED |
            SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
            dispose();
            return;
        }
        if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_RECEIVED) {
            metrics.received(event.getReceivedData().length);

            if (logger.isDebugEnabled()) {
                logger.debug("received data: {}", ByteBufUtil.hexDump(event.getReceivedData()));
            }

            PendingRequest pending = PENDING.get(this);
            if (pending == null || pending.isDisposed()) {
                logger.debug("SerialPort [{}] received data without an active request", getPath());
                return;
            }
            parser.handle(Unpooled.wrappedBuffer(event.getReceivedData()));
        }

    }

    private void handleBuffer(ByteBuf buffer) {
        metrics.messageReceived();
        PendingRequest pending = PENDING.get(this);
        if (logger.isDebugEnabled()) {
            logger.debug("received SerialPort [{}] data :{}",
                         port.getSystemPortPath(),
                         ByteBufUtil.hexDump(buffer));
        }
        if (pending != null) {

            pending.complete(buffer);

        } else {
            ReferenceCountUtil.safeRelease(buffer);
        }

        drain();
    }

    @Override
    public void dispose() {
        if (isDisposed()) {
            return;
        }
        disposable.dispose();
        parser.close();
        try {
            port.closePort();
        } catch (Throwable ignore) {

        }
        PendingRequest pending = PENDING.getAndSet(this, null);
        if (pending != null && !pending.isCancelled()) {
            pending.fail(new PortClosedException(getPath()));
        }
        for (; ; ) {
            pending = queue.poll();
            if (pending == null) {
                break;
            }
            if (pending.isCancelled()) {
                continue;
            }
            pending.fail(new PortClosedException(getPath()));
        }

    }

    @Override
    public boolean isDisposed() {
        return disposable.isDisposed();
    }

    private static CollectorFailure channelBusy() {
        return new CollectorFailure(InternalStatusCode.Bad_TooManyOperations, "error.channel_busy");
    }

    class PendingRequest implements Disposable {
        final ByteBuf request;
        final FluxSink<ByteBuf> sink;
        final Duration requestTimeout;
        private int remaining;
        private boolean sent;
        private boolean completed;
        private boolean emitting;
        private volatile boolean terminated;
        private volatile Disposable timeout;

        PendingRequest(ByteBuf request, FluxSink<ByteBuf> sink, Duration requestTimeout, int responses) {
            this.request = request;
            this.sink = sink;
            this.requestTimeout = requestTimeout;
            this.remaining = responses;
        }

        public boolean isCancelled() {
            return isDisposed();
        }

        @Override
        public boolean isDisposed() {
            return terminated;
        }

        @Override
        public void dispose() {
            synchronized (this) {
                if (terminated) {
                    return;
                }
                terminated = true;
                // take/single 可在 next 中同步取消，等本次通知退出后再清理资源。
                if (emitting) {
                    return;
                }
            }
            cleanup();
        }

        private void fail(Throwable error) {
            boolean deferCleanup;
            synchronized (this) {
                if (terminated) {
                    return;
                }
                terminated = true;
                deferCleanup = emitting;
            }
            if (!deferCleanup) {
                cleanup();
            }
            sink.error(error);
        }

        private void cleanup() {
            queue.remove(this);
            Disposable timer = timeout;
            if (timer != null) {
                timer.dispose();
            }
            ReferenceCountUtil.safeRelease(request);
            if (sent && !completed) {
                // 串口响应没有通用关联号。可能已发出的请求被中断后，不能让迟到帧匹配下一请求。
                // 保留 pending 到客户端失效，由上层既有连接恢复流程重新建立会话。
                DefaultSerialPortClient.this.dispose();
            } else {
                PENDING.compareAndSet(DefaultSerialPortClient.this, this, null);
                drain();
            }
        }

        public void complete(ByteBuf buf) {
            boolean overflow;
            boolean cleanup;
            synchronized (this) {
                if (terminated || !sent) {
                    ReferenceCountUtil.safeRelease(buf);
                    return;
                }
                overflow = sink.requestedFromDownstream() == 0;
                if (overflow) {
                    cleanup = false;
                } else {
                    // 收齐标记必须先于 next，避免下游 take 的同步取消被当成未完成请求。
                    completed = remaining > 0 && --remaining == 0;
                    emitting = true;
                    try {
                        sink.next(buf);
                        if (completed) {
                            sink.complete();
                        }
                    } finally {
                        emitting = false;
                    }
                    cleanup = terminated;
                }
            }
            if (overflow) {
                ReferenceCountUtil.safeRelease(buf);
                fail(channelBusy());
            } else if (cleanup) {
                cleanup();
            }
        }

        private void sendRequest() {
            try {
                byte[] payload;
                synchronized (this) {
                    if (terminated || DefaultSerialPortClient.this.isDisposed()) {
                        return;
                    }
                    payload = ByteBufUtil.getBytes(request);
                    // 提交发送后取消按“可能已上线路”处理；不持锁执行阻塞串口 IO。
                    sent = true;
                }

                if (logger.isDebugEnabled()) {
                    logger.debug("request SerialPort [{}] data:{}",
                                 port.getSystemPortPath(),
                                 ByteBufUtil.hexDump(payload));
                }
                if (isDisposed()) {
                    return;
                }
                out.write(payload);
                metrics.sent(payload.length);
                if (!isDisposed()) {
                    Disposable timer = scheduler.schedule(
                        () -> fail(new RequestTimeoutException(getPath())),
                        requestTimeout.toMillis(),
                        TimeUnit.MILLISECONDS);
                    timeout = timer;
                    // 响应/取消可能先于 schedule 返回，不能把已结束请求重新留在定时队列里。
                    if (isDisposed()) {
                        timer.dispose();
                    }
                }
            } catch (RejectedExecutionException e) {
                logger.debug("write SerialPort [{}] rejected", port.getSystemPortPath(), e);
                fail(new PortClosedException(getPath()));
            } catch (Throwable e) {
                metrics.transportError();
                logger.warn("write SerialPort [{}] failed", port.getSystemPortPath(), e);
                fail(new IllegalStateException("error.serial_port_error", e));
            }
        }
    }

    @Override
    public void doOnClosed(Disposable disposable) {
        this.disposable.add(disposable);
    }
}
