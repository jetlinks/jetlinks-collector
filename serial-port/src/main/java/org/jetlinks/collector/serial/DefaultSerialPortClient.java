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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

@Slf4j
public class DefaultSerialPortClient implements
    SerialPortClient, SerialPortDataListenerWithExceptions {
    private static final AtomicReferenceFieldUpdater<DefaultSerialPortClient, PendingRequest>
        PENDING = AtomicReferenceFieldUpdater.newUpdater(DefaultSerialPortClient.class, PendingRequest.class, "pending");
    private static final AtomicIntegerFieldUpdater<DefaultSerialPortClient>
        WIP = AtomicIntegerFieldUpdater.newUpdater(DefaultSerialPortClient.class, "wip");

    private final SerialPort port;

    private final PayloadParser parser;

    private final OutputStream out;

    private volatile PendingRequest pending;

    private volatile int wip;

    private final Queue<PendingRequest> queue;

    private final Disposable.Composite disposable = Disposables.composite();

    private final Scheduler scheduler;

    private final int maxQueueSize;

    private Logger logger = log;

    public DefaultSerialPortClient(SerialPort port, PayloadParser parser, int maxQueueSize) {
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
        queue = new ConcurrentLinkedQueue<>();

        if (!this.port.addDataListener(this)) {
            throw new IllegalStateException("do not set listener to port");
        }
    }

    @Override
    public Mono<ByteBuf> sendAndReceive(ByteBuf buf, Duration timeout) {
        return this
            .sendAndReceiveMulti(buf, timeout)
            .take(1)
            .singleOrEmpty();
    }

    @Override
    public Flux<ByteBuf> sendAndReceiveMulti(ByteBuf buf, Duration timeout, int num) {
        return this
            .sendAndReceiveMulti(buf, timeout)
            .take(num);
    }

    @Override
    public Flux<ByteBuf> sendAndReceiveMulti(ByteBuf buf, Duration timeout) {
        return Flux
            .<ByteBuf>create(sink -> {
                if (!port.isOpen() || isDisposed()) {
                    sink.error(new IllegalStateException());
                    return;
                }
                PendingRequest request = new PendingRequest(buf, sink, timeout);
                sink.onDispose(request);

                if (queue.size() >= maxQueueSize || !queue.offer(request)) {
                    sink.error(new IllegalStateException("queue is full"));
                }
                drain();
            });
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
                    scheduler.schedule(request::sendRequest);
                } else {
                    queue.add(request);
                }
            }
            missed = WIP.addAndGet(this, -missed);
        } while (missed != 0);

    }

    @Override
    public void catchException(Exception e) {
        log.error("handle serial port payload error", e);
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
        if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_RECEIVED) {
            return;
        }

        PendingRequest pending = PENDING.get(this);
        if (pending == null || pending.isDisposed()) {
            return;
        }
        parser.handle(Unpooled.wrappedBuffer(event.getReceivedData()));
    }

    private void handleBuffer(ByteBuf buffer) {
        PendingRequest pending = PENDING.get(this);
        if (log.isDebugEnabled()) {
            log.debug("received SerialPort [{}] data :{}",
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
        port.closePort();

        if (pending != null) {
            pending.sink.error(new IllegalStateException("port closed"));
        }
        PendingRequest pending = this.pending;
        while (pending != null) {
            if (!pending.isCancelled()) {
                pending.sink.error(new IllegalStateException("port closed"));
            }
            pending = queue.poll();
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
                if (logger.isInfoEnabled()) {
                    logger.info("write SerialPort [{}] data: {}",
                                port.getSystemPortPath(),
                                ByteBufUtil.hexDump(payload));
                }
                //请求超时处理
                scheduler.schedule(
                    () -> {
                        //超时未获取到数据,重置粘拆包处理器.
                        if (!isDisposed()) {
                            parser.reset();
                        }
                        sink.complete();
                    },
                    requestTimeout.toMillis(),
                    TimeUnit.MILLISECONDS);
            } catch (Throwable e) {
                logger.error("write SerialPort [{}] failed {}",  port.getSystemPortPath(), ByteBufUtil.hexDump(request), e);
                sink.error(new IllegalStateException("error.serial_port_error", e));
            }
        }
    }
}
