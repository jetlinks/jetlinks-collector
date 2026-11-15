package org.jetlinks.collector.serial;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;
import reactor.test.StepVerifier;
import reactor.util.concurrent.Queues;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectPayloadParserTest {
    @Test
    void overflowFailsTheFrameStreamAndReleasesQueuedBuffers() {
        DirectPayloadParser parser = new DirectPayloadParser();
        List<ByteBuf> frames = new ArrayList<>();
        for (int i = 0; i <= Queues.SMALL_BUFFER_SIZE; i++) {
            ByteBuf frame = Unpooled.buffer(1).writeByte(i);
            frames.add(frame);
            parser.handle(frame);
        }
        StepVerifier.create(parser.handlePayload())
                    .expectErrorMatches(Exceptions::isOverflow)
                    .verify();
        frames.forEach(frame -> assertEquals(0, frame.refCnt()));
    }

    @Test
    void closeReleasesBuffersWithoutWaitingForADownstreamRequest() {
        DirectPayloadParser parser = new DirectPayloadParser();
        ByteBuf frame = Unpooled.buffer(1).writeByte(1);
        parser.handle(frame);
        parser.close();
        assertEquals(0, frame.refCnt());
        StepVerifier.create(parser.handlePayload()).verifyComplete();
    }

    @Test
    void cancelledAndClosedParsersReleaseLateInput() {
        DirectPayloadParser parser = new DirectPayloadParser();
        StepVerifier.create(parser.handlePayload(), 0).thenCancel().verify();
        ByteBuf afterCancel = Unpooled.buffer(1).writeByte(1);
        parser.handle(afterCancel);
        assertEquals(0, afterCancel.refCnt());
        parser.close();
        ByteBuf afterClose = Unpooled.buffer(1).writeByte(1);
        parser.handle(afterClose);
        assertEquals(0, afterClose.refCnt());
    }
}
