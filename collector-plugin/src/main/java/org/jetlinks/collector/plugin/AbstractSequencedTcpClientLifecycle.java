package org.jetlinks.collector.plugin;

import io.netty.util.ReferenceCountUtil;
import jakarta.annotation.Nullable;
import org.hswebframework.ezorm.core.CastUtil;
import org.jetlinks.collector.plugin.tcp.SequencedPayload;
import org.jetlinks.core.enums.ErrorCode;
import org.jetlinks.core.exception.DeviceOperationException;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.netty.Connection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * 基于序列号的收发tcp客户端实现
 *
 * @param <REQ> 请求体类型
 * @param <RES> 响应体类型
 * @param <ID>  序列号类型
 */
public abstract class AbstractSequencedTcpClientLifecycle<REQ, RES extends SequencedPayload<ID>, ID> extends AbstractTcpClientLifecycle {

    @SuppressWarnings("all")
    static final AtomicReferenceFieldUpdater<AbstractSequencedTcpClientLifecycle, Object> SEQUENCE_GENERATOR
        = AtomicReferenceFieldUpdater.newUpdater(AbstractSequencedTcpClientLifecycle.class, Object.class, "sequenceGenerator");

    private volatile ID sequenceGenerator;

    private final Map<ID, MonoSink<RES>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * 初始化连接前，用于添加粘拆包策略、编解码器等
     */
    protected Mono<Void> beforeInitConnection(Connection connection) {
        return Mono.empty();
    }

    /**
     * 初始化连接后，用于发送连接认证报文等
     */
    protected Mono<Void> afterInitConnection(Connection connection) {
        return Mono.empty();
    }

    /**
     * 处理接收数据
     *
     * @param payload 接收数据
     * @return 响应数据
     */
    protected abstract @Nullable RES handleInbound(Object payload);

    /**
     * 处理发送数据
     *
     * @param payload 请求数据
     * @return 发送数据
     */
    protected abstract Object handleOutbound(ID number, REQ payload);

    /**
     * 获取下一个序列号
     *
     * @param prev 前一个
     * @return 下一个
     */
    protected abstract ID nextSequenceNumber(@Nullable ID prev);


    private ID nextRequestSequenceNumber() {
        Object next = SEQUENCE_GENERATOR.updateAndGet(this, i -> nextSequenceNumber(CastUtil.cast(i)));
        return CastUtil.cast(next);
    }

    @Override
    protected Mono<Void> initConnection(Connection connection) {
        return beforeInitConnection(connection)
            .then(Mono.defer(() -> {
                //接收消息
                Disposable inbound = connection
                    .inbound()
                    .receiveObject()
                    .doOnNext(v -> {
                        RES payload = handleInbound(v);
                        if (payload != null) {
                            MonoSink<RES> sink = pendingRequests.remove(payload.getSequenceNumber());
                            if (null != sink) {
                                sink.success(payload);
                                return;
                            }
                        }
                        ReferenceCountUtil.safeRelease(payload);
                    })
                    .subscribe();
                connection.onDispose(inbound);
                return afterInitConnection(connection);
            }));
    }

    @Override
    protected void stop0() {
        super.stop0();
        for (MonoSink<RES> sink : pendingRequests.values()) {
            sink.error(new DeviceOperationException(ErrorCode.CONNECTION_LOST));
        }
    }


    public Mono<RES> request(REQ request) {
        return Mono
            .<RES>create(sink -> {
                ID number = nextRequestSequenceNumber();
                pendingRequests.put(number, sink);
                @SuppressWarnings("all")
                Disposable outbound = connect()
                    .flatMap(c -> c
                        .outbound()
                        .sendObject(handleOutbound(number, request))
                        .then())
                    .subscribe(ignore -> {
                    }, sink::error);

                sink.onDispose(() -> {
                    pendingRequests.remove(number, sink);
                    outbound.dispose();
                });

            })
            .as(tracer().traceMono("/tcp/request/"));
    }
}
