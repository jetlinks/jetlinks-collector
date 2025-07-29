package org.jetlinks.collector.serial;

import io.netty.buffer.ByteBuf;
import org.jetlinks.core.utils.Reactors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

public class DirectPayloadParser implements PayloadParser {

    private final Sinks.Many<ByteBuf> sinks =
        Sinks.unsafe()
             .many()
             .unicast()
             .onBackpressureBuffer(Queues.<ByteBuf>unboundedMultiproducer().get());

    @Override
    public void handle(ByteBuf buffer) {
        sinks.emitNext(buffer, Reactors.emitFailureHandler());
    }

    @Override
    public Flux<ByteBuf> handlePayload() {
        return sinks.asFlux();
    }

    @Override
    public void close() {
        sinks.tryEmitComplete();
    }
}
