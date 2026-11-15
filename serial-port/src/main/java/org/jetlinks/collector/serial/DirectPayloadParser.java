package org.jetlinks.collector.serial;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.jetlinks.collector.InternalStatusCode;
import org.jetlinks.collector.exception.CollectorFailure;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * 透传完整串口帧。接收调用串行化并限制待消费帧数，溢出时明确失败而不丢帧继续解析。
 * handle 接管输入引用；消费方接管输出引用，关闭、取消及拒绝的输入由解析器释放。
 * @see PayloadParser
 */
public class DirectPayloadParser implements PayloadParser {

    private final Queue<ByteBuf> queue = new ArrayBlockingQueue<>(Queues.SMALL_BUFFER_SIZE);
    private final Sinks.Many<ByteBuf> sinks =
        Sinks.unsafe()
             .many()
             .unicast()
             .onBackpressureBuffer(queue);

    @Override
    public synchronized void handle(ByteBuf buffer) {
        Sinks.EmitResult emitted = sinks.tryEmitNext(buffer);
        if (emitted.isFailure()) {
            ReferenceCountUtil.safeRelease(buffer);
            if (emitted == Sinks.EmitResult.FAIL_OVERFLOW || emitted == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                // 丢失一帧后不能假装后续帧仍构成完整响应，清空并终止本解析器。
                releaseQueued();
                sinks.tryEmitError(new CollectorFailure(InternalStatusCode.Bad_TooManyOperations, "error.channel_busy"));
            }
        }
    }

    @Override
    public Flux<ByteBuf> handlePayload() {
        return sinks.asFlux().doOnDiscard(ByteBuf.class, ReferenceCountUtil::safeRelease);
    }

    @Override
    public synchronized void close() {
        releaseQueued();
        sinks.tryEmitComplete();
    }

    private void releaseQueued() {
        ByteBuf buffer;
        while ((buffer = queue.poll()) != null) {
            ReferenceCountUtil.safeRelease(buffer);
        }
    }
}
