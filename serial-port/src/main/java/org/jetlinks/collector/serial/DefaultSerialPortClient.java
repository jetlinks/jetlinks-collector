package org.jetlinks.collector.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListenerWithExceptions;
import com.fazecast.jSerialComm.SerialPortEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.server.ConnectionMetrics;
import org.jetlinks.core.server.ConnectionMetricsRecorder;
import org.slf4j.Logger;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * 单串口的有界请求队列与帧接收客户端，统一记录实际收发字节和请求耗时。
 * 实例持有观测对象；关闭时注销观测并释放请求、解析器及串口资源。
 */
@Slf4j
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

    private final int maxQueueSize;
    private final Monitor monitor;
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
        this.maxQueueSize = maxQueueSize;
        this.scheduler = Schedulers.single(Schedulers.boundedElastic());
        this.disposable.add(scheduler);
        this.disposable.add(
            parser
                .handlePayload()
                .subscribe(this::handleBuffer)
        );
        this.monitor = monitor;
        if (this.monitor != Monitor.noop()) {
            this.logger = this.monitor.logger().slf4j();
        } else {
            this.logger = log;
        }
        this.queue = new ConcurrentLinkedQueue<>();
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
    }

    @Override
    public String getPath() {
        return port.getSystemPortPath();
    }

    @Override
    public Mono<ByteBuf> sendAndReceive(ByteBuf buf, Duration timeout) {
        return metrics.recordRequest(this
            .sendAndReceiveMulti(buf, timeout, true)
            .singleOrEmpty(), error -> error instanceof RequestTimeoutException);
    }

    @Override
    public Flux<ByteBuf> sendAndReceiveMulti(ByteBuf buf, Duration timeout, int num) {
        return this
            .sendAndReceiveMulti(buf, timeout)
            .take(num);
    }

    @Override
    public boolean isConnected() {
        return port.isOpen();
    }

    private Flux<ByteBuf> sendAndReceiveMulti(ByteBuf buf, Duration timeout, boolean single) {
        return Flux
            .<ByteBuf>create(sink -> {
                if (!port.isOpen() || isDisposed()) {
                    sink.error(new PortClosedException(getPath()));
                    return;
                }
                PendingRequest request = new PendingRequest(buf, sink, timeout, single);
                sink.onDispose(request);

                if (queue.size() >= maxQueueSize || !queue.offer(request)) {
                    sink.error(new IllegalStateException("queue is full"));
                }
                drain();
            });
    }

    @Override
    public Flux<ByteBuf> sendAndReceiveMulti(ByteBuf buf, Duration timeout) {
        return sendAndReceiveMulti(buf, timeout, false);
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
                    scheduler.schedule(
                        request::sendRequest,
                        config.getCommunicationInterval().toNanos(),
                        TimeUnit.NANOSECONDS);
                } else {
                    queue.add(request);
                }
            }
            missed = WIP.addAndGet(this, -missed);
        } while (missed != 0);

    }

    @Override
    public void catchException(Exception e) {
        logger.error("handle serial port payload error", e);
        if (pending != null && !pending.isDisposed()) {
            pending.sink.error(new IllegalStateException("error.handle_serial_port_error", e));
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

            if (logger.isInfoEnabled()) {
                logger.info("received data: {}", ByteBufUtil.hexDump(event.getReceivedData()));
            }

            PendingRequest pending = PENDING.get(this);
            if (pending == null || pending.isDisposed()) {
                logger.warn("request is canceled! received data: {}", ByteBufUtil.hexDump(event.getReceivedData()));
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

        }

        drain();
    }

    @Override
    public void dispose() {
        disposable.dispose();
        try {
            port.closePort();
        } catch (Throwable ignore) {

        }
        PendingRequest pending = PENDING.getAndSet(this, null);
        if (pending != null && !pending.isCancelled()) {
            pending.sink.error(new PortClosedException(getPath()));
        }
        for (; ; ) {
            pending = queue.poll();
            if (pending == null) {
                break;
            }
            if (pending.isCancelled()) {
                continue;
            }
            pending.sink.error(new PortClosedException(getPath()));
        }

    }

    @Override
    public boolean isDisposed() {
        return disposable.isDisposed();
    }

    @AllArgsConstructor
    class PendingRequest implements Disposable {
        ByteBuf request;
        FluxSink<ByteBuf> sink;
        Duration requestTimeout;
        boolean single;

        public boolean isCancelled() {
            return isDisposed();
        }

        @Override
        public boolean isDisposed() {
            return ReferenceCountUtil.refCnt(request) == 0;
        }

        @Override
        public void dispose() {
            try {
                PENDING.compareAndSet(DefaultSerialPortClient.this, this, null);
                if (isDisposed()) {
                    return;
                }
                ReferenceCountUtil.safeRelease(request);
            } finally {
                drain();
            }
        }

        public void complete(ByteBuf buf) {
            sink.next(buf);
            if (single) {
                sink.complete();
            }
        }

        private void sendRequest() {
            try {
                byte[] payload = ByteBufUtil.getBytes(request);

                if (logger.isDebugEnabled()) {
                    logger.debug("request SerialPort [{}] data:{}",
                                 port.getSystemPortPath(),
                                 ByteBufUtil.hexDump(payload));
                }
                out.write(payload);
                metrics.sent(payload.length);
                if (logger.isInfoEnabled()) {
                    logger.info("write SerialPort [{}] data: {}",
                                port.getSystemPortPath(),
                                ByteBufUtil.hexDump(payload));
                }
                if (!isDisposed()) {
                    //请求超时处理
                    scheduler.schedule(
                        () -> {
                            //超时未获取到数据,重置粘拆包处理器.
                            if (!isDisposed()) {
                                parser.reset();
                            }
                            sink.error(new RequestTimeoutException(getPath()));
                        },
                        requestTimeout.toMillis(),
                        TimeUnit.MILLISECONDS);
                }
            } catch (RejectedExecutionException e) {
                logger.info("write SerialPort [{}] rejected {}", port.getSystemPortPath(), ByteBufUtil.hexDump(request), e);
                sink.complete();
            } catch (Throwable e) {
                metrics.transportError();
                logger.warn("write SerialPort [{}] failed {}", port.getSystemPortPath(), ByteBufUtil.hexDump(request), e);
                sink.error(new IllegalStateException("error.serial_port_error", e));
            }
        }
    }

    @Override
    public void doOnClosed(Disposable disposable) {
        this.disposable.add(disposable);
    }
}
