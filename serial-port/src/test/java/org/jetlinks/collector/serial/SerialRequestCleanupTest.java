package org.jetlinks.collector.serial;

import com.fazecast.jSerialComm.SerialPort;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jetlinks.core.monitor.Monitor;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SerialRequestCleanupTest {
    @Test
    void completedRequestsDoNotRetainTimeoutTasksAtOneHundredHz() throws Exception {
        try (Fixture fixture = new Fixture()) {
            AtomicInteger received = new AtomicInteger();
            fixture.reply = true;
            for (int i = 0; i < 400; i++) {
                fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(i), Duration.ofSeconds(5))
                              .subscribe(buffer -> { buffer.release(); received.incrementAndGet(); });
                fixture.scheduler.clock.advanceTimeBy(Duration.ofMillis(10));
            }
            assertEquals(400, received.get());
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
            System.out.println("serial 100 Hz: completed=400, retained timeout tasks=" + fixture.scheduler.liveTimeouts.get());
        }
    }

    @Test
    void cancellationTimeoutAndCloseShareTheSameTimerCleanup() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Disposable request = fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(1), Duration.ofSeconds(5))
                                               .subscribe(buffer -> buffer.release());
            fixture.scheduler.clock.advanceTime();
            assertEquals(1, fixture.scheduler.liveTimeouts.get());
            request.dispose();
            assertEquals(0, fixture.scheduler.liveTimeouts.get());

            StepVerifier.create(fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(2), Duration.ofSeconds(5)))
                        .then(() -> fixture.scheduler.clock.advanceTimeBy(Duration.ofSeconds(5)))
                        .expectError(RequestTimeoutException.class).verify();
            assertEquals(0, fixture.scheduler.liveTimeouts.get());

            StepVerifier.create(fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(3), Duration.ofSeconds(5)))
                        .then(() -> fixture.scheduler.clock.advanceTime())
                        .then(fixture.client::dispose)
                        .expectError(PortClosedException.class).verify();
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
        }
    }

    @Test
    void aReplyDuringTimeoutRegistrationCancelsTheNewTask() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.scheduler.duringTimeoutRegistration = () -> fixture.frames.tryEmitNext(Unpooled.buffer(1).writeByte(1));
            StepVerifier.create(fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(1), Duration.ofSeconds(5)))
                        .then(() -> fixture.scheduler.clock.advanceTime())
                        .assertNext(ByteBuf::release).verifyComplete();
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
        }
    }

    @Test
    void unmatchedFramesAndRequestsRejectedAfterCloseReleaseTheirBuffers() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ByteBuf unsolicited = Unpooled.buffer(1).writeByte(1);
            fixture.frames.tryEmitNext(unsolicited);
            assertEquals(0, unsolicited.refCnt());
            fixture.client.dispose();
            ByteBuf rejected = Unpooled.buffer(1).writeByte(1);
            StepVerifier.create(fixture.client.sendAndReceive(rejected, Duration.ofSeconds(5)))
                        .expectError(PortClosedException.class).verify();
            assertEquals(0, rejected.refCnt());
            verify(fixture.parser).close();
        }
    }

    @Test
    void aSlowMultiFrameSubscriberFailsWithoutBufferingCompleteFrames() throws Exception {
        try (Fixture fixture = new Fixture()) {
            List<ByteBuf> frames = new ArrayList<>();
            StepVerifier.create(fixture.client.sendAndReceiveMulti(Unpooled.buffer(1).writeByte(1), Duration.ofSeconds(5)), 0)
                        .then(() -> fixture.scheduler.clock.advanceTime())
                        .then(() -> {
                            for (int i = 0; i < 1_000; i++) {
                                ByteBuf response = Unpooled.buffer(1).writeByte(i);
                                frames.add(response);
                                fixture.frames.tryEmitNext(response);
                            }
                        })
                        .expectErrorMatches(Exceptions::isOverflow).verify(Duration.ofSeconds(2));
            frames.forEach(response -> assertEquals(0, response.refCnt()));
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
        }
    }

    @Test
    void cancellingQueuedRequestsImmediatelyMakesRoomForNewRequests() throws Exception {
        try (Fixture fixture = new Fixture(1)) {
            Disposable active = fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(1), Duration.ofSeconds(5))
                                              .subscribe(buffer -> buffer.release());
            fixture.scheduler.clock.advanceTime();
            AtomicInteger rejected = new AtomicInteger();
            for (int i = 0; i < 100; i++) {
                Disposable queued = fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(2), Duration.ofSeconds(5))
                                                  .subscribe(buffer -> buffer.release(), error -> rejected.incrementAndGet());
                queued.dispose();
            }
            assertEquals(0, rejected.get());
            active.dispose();
        }
    }

    @Test
    void cancellationIsIndependentOfOtherOwnersOfTheRequestBuffer() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.config.getCommunicationInterval()).thenReturn(Duration.ofMillis(1));
            ByteBuf request = Unpooled.buffer(1).writeByte(1).retain();
            Disposable pending = fixture.client.sendAndReceive(request, Duration.ofSeconds(5)).subscribe(ByteBuf::release);
            pending.dispose();
            assertEquals(1, request.refCnt());
            fixture.scheduler.clock.advanceTime();
            assertEquals(0, fixture.writes.get());
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
            request.release();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final TrackingScheduler scheduler = new TrackingScheduler();
        final Sinks.Many<ByteBuf> frames = Sinks.many().unicast().onBackpressureBuffer();
        final PayloadParser parser = mock(PayloadParser.class);
        final SerialPortConfig config = mock(SerialPortConfig.class);
        final AtomicInteger writes = new AtomicInteger();
        final DefaultSerialPortClient client;
        boolean reply;

        Fixture() throws Exception { this(32); }

        Fixture(int queueSize) throws Exception {
            SerialPort port = mock(SerialPort.class);
            when(port.isOpen()).thenReturn(true);
            when(port.addDataListener(any())).thenReturn(true);
            when(port.getSystemPortPath()).thenReturn("cleanup-test");
            when(config.getCommunicationInterval()).thenReturn(Duration.ZERO);
            when(parser.handlePayload()).thenReturn(frames.asFlux());
            when(port.getOutputStream()).thenReturn(new OutputStream() {
                @Override public void write(int value) { }
                @Override public void write(byte[] value) {
                    writes.incrementAndGet();
                    if (reply) scheduler.clock.schedule(() -> frames.tryEmitNext(Unpooled.buffer(1).writeByte(1)), 1, TimeUnit.MILLISECONDS);
                }
            });
            try (var factories = mockStatic(Schedulers.class, CALLS_REAL_METHODS)) {
                factories.when(() -> Schedulers.single(any(Scheduler.class))).thenReturn(scheduler);
                client = new DefaultSerialPortClient(config, port, parser, queueSize, Monitor.noop());
            }
        }

        @Override public void close() { client.dispose(); }
    }

    private static final class TrackingScheduler implements Scheduler {
        final VirtualTimeScheduler clock = VirtualTimeScheduler.create();
        final AtomicInteger liveTimeouts = new AtomicInteger();
        Runnable duringTimeoutRegistration;

        @Override public Disposable schedule(Runnable task) { return schedule(task, 0, TimeUnit.NANOSECONDS); }
        @Override public Disposable schedule(Runnable task, long delay, TimeUnit unit) {
            if (unit.toMillis(delay) < 1000) return clock.schedule(task, delay, unit);
            liveTimeouts.incrementAndGet();
            AtomicBoolean live = new AtomicBoolean(true);
            Runnable release = () -> { if (live.compareAndSet(true, false)) liveTimeouts.decrementAndGet(); };
            Disposable scheduled = clock.schedule(() -> { release.run(); task.run(); }, delay, unit);
            if (duringTimeoutRegistration != null) duringTimeoutRegistration.run();
            return new Disposable() {
                @Override public void dispose() { release.run(); scheduled.dispose(); }
                @Override public boolean isDisposed() { return !live.get(); }
            };
        }
        @Override public Worker createWorker() { return clock.createWorker(); }
        @Override public void dispose() { clock.dispose(); }
        @Override public boolean isDisposed() { return clock.isDisposed(); }
    }
}
