package org.jetlinks.collector.serial;

import com.fazecast.jSerialComm.SerialPort;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jetlinks.collector.CollectorUtils;
import org.jetlinks.collector.InternalStatusCode;
import org.jetlinks.collector.exception.CollectorFailure;
import org.jetlinks.core.monitor.Monitor;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    void cancellingAnActiveRequestClosesTheSessionBeforeAnyQueuedRequestCanBeSent() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ByteBuf first = Unpooled.buffer(1).writeByte(1);
            ByteBuf queued = Unpooled.buffer(1).writeByte(2);
            AtomicReference<Throwable> queuedError = new AtomicReference<>();
            AtomicInteger received = new AtomicInteger();
            Disposable active = fixture.client.sendAndReceive(first, Duration.ofSeconds(5))
                                               .subscribe(ByteBuf::release);
            fixture.scheduler.clock.advanceTime();
            fixture.client.sendAndReceive(queued, Duration.ofSeconds(5))
                          .subscribe(value -> { value.release(); received.incrementAndGet(); }, queuedError::set);
            active.dispose();
            fixture.emit(101);
            assertInstanceOf(PortClosedException.class, queuedError.get());
            assertTrue(fixture.client.isDisposed());
            assertFalse(fixture.client.isConnected());
            assertEquals(1, fixture.writes.get());
            assertEquals(0, received.get());
            assertEquals(0, first.refCnt());
            assertEquals(0, queued.refCnt());
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
        }
    }

    @Test
    void timeoutFailsTheOriginalRequestAndClosesTheSessionWithoutResettingIntoTheNextRequest() throws Exception {
        try (Fixture fixture = new Fixture()) {
            AtomicReference<Throwable> queuedError = new AtomicReference<>();
            ByteBuf queued = Unpooled.buffer(1).writeByte(2);
            StepVerifier.create(fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(1), Duration.ofSeconds(5)))
                        .then(() -> fixture.scheduler.clock.advanceTime())
                        .then(() -> fixture.client.sendAndReceive(queued, Duration.ofSeconds(5))
                                                 .subscribe(ByteBuf::release, queuedError::set))
                        .then(() -> fixture.scheduler.clock.advanceTimeBy(Duration.ofSeconds(5)))
                        .expectError(RequestTimeoutException.class).verify();
            fixture.emit(101);
            assertInstanceOf(PortClosedException.class, queuedError.get());
            assertEquals(1, fixture.writes.get());
            assertEquals(0, queued.refCnt());
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
            assertTrue(fixture.client.isDisposed());
            verify(fixture.parser, never()).reset();
        }
    }

    @Test
    void explicitCloseCleansTheActiveTimerAndRequest() throws Exception {
        try (Fixture fixture = new Fixture()) {
            StepVerifier.create(fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(3), Duration.ofSeconds(5)))
                        .then(() -> fixture.scheduler.clock.advanceTime())
                        .then(fixture.client::dispose)
                        .expectError(PortClosedException.class).verify();
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
        }
    }

    @Test
    void singleAndCountedResponsesKeepTheSessionReusable() throws Exception {
        try (Fixture fixture = new Fixture()) {
            StepVerifier.create(fixture.client.sendAndReceiveMulti(Unpooled.buffer(1).writeByte(1), Duration.ofSeconds(5), 2))
                        .then(() -> fixture.scheduler.clock.advanceTime())
                        .then(() -> fixture.emit(11))
                        .assertNext(value -> { assertEquals(11, value.readUnsignedByte()); value.release(); })
                        .then(() -> fixture.emit(12))
                        .assertNext(value -> { assertEquals(12, value.readUnsignedByte()); value.release(); })
                        .verifyComplete();
            assertFalse(fixture.client.isDisposed());
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
            StepVerifier.create(fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(2), Duration.ofSeconds(5)))
                        .then(() -> fixture.scheduler.clock.advanceTime())
                        .then(() -> fixture.emit(21))
                        .assertNext(ByteBuf::release).verifyComplete();
            assertFalse(fixture.client.isDisposed());
            assertEquals(2, fixture.writes.get());
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
            verify(fixture.parser, never()).close();
        }
    }

    @Test
    void cancellingBeforeSendKeepsTheSessionReusableAndDoesNotSendTheOldRequest() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.config.getCommunicationInterval()).thenReturn(Duration.ofMillis(20));
            ByteBuf first = Unpooled.buffer(1).writeByte(1);
            Disposable cancelled = fixture.client.sendAndReceive(first, Duration.ofSeconds(5)).subscribe(ByteBuf::release);
            cancelled.dispose();
            fixture.reply = true;
            StepVerifier.create(fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(2), Duration.ofSeconds(5)))
                        .then(() -> fixture.scheduler.clock.advanceTimeBy(Duration.ofMillis(30)))
                        .assertNext(ByteBuf::release).verifyComplete();
            assertFalse(fixture.client.isDisposed());
            assertEquals(List.of(2), fixture.writtenValues);
            assertEquals(0, first.refCnt());
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
        }
    }

    @Test
    void cancellingDuringBlockedWriteDoesNotBlockOrAllowQueuedWorkToStart() throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        try (Fixture fixture = new Fixture()) {
            when(fixture.config.getCommunicationInterval()).thenReturn(Duration.ofMillis(20));
            AtomicReference<Throwable> queuedError = new AtomicReference<>();
            Disposable active = fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(1), Duration.ofSeconds(5))
                                              .subscribe(ByteBuf::release);
            fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(2), Duration.ofSeconds(5))
                          .subscribe(ByteBuf::release, queuedError::set);
            CountDownLatch writing = new CountDownLatch(1);
            CountDownLatch releaseWrite = new CountDownLatch(1);
            fixture.duringWrite = () -> {
                writing.countDown();
                try {
                    assertTrue(releaseWrite.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
            };
            var sending = workers.submit(() -> fixture.scheduler.clock.advanceTimeBy(Duration.ofMillis(30)));
            try {
                assertTrue(writing.await(5, TimeUnit.SECONDS));
                // 取消无需等待阻塞写完成，且当前会话已失效，不可能放行排队请求。
                workers.submit(active::dispose).get(2, TimeUnit.SECONDS);
                assertTrue(fixture.client.isDisposed());
                assertEquals(List.of(1), fixture.writtenValues);
                assertInstanceOf(PortClosedException.class, queuedError.get());
            } finally {
                releaseWrite.countDown();
            }
            sending.get(5, TimeUnit.SECONDS);
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void cancellingAnUnboundedResponseStreamClosesTheIncompleteSession() throws Exception {
        try (Fixture fixture = new Fixture()) {
            StepVerifier.create(fixture.client.sendAndReceiveMulti(Unpooled.buffer(1).writeByte(1), Duration.ofSeconds(5)).take(1))
                        .then(() -> fixture.scheduler.clock.advanceTime())
                        .then(() -> fixture.emit(11))
                        .assertNext(ByteBuf::release).verifyComplete();
            assertTrue(fixture.client.isDisposed());
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
        }
    }

    @Test
    void closingTheClientFromTheResponseCallbackReleasesEachRequestOnlyOnce() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ByteBuf request = Unpooled.buffer(1).writeByte(1).retain();
            StepVerifier.create(fixture.client.sendAndReceiveMulti(request, Duration.ofSeconds(5))
                                            .doOnNext(value -> fixture.client.dispose()))
                        .then(() -> fixture.scheduler.clock.advanceTime())
                        .then(() -> fixture.emit(11))
                        .assertNext(ByteBuf::release).expectError(PortClosedException.class).verify();
            assertEquals(1, request.refCnt());
            request.release();
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
        }
    }

    @Test
    void aLateTimeoutCallbackCannotResetOrFailTheFollowingRequest() throws Exception {
        try (Fixture fixture = new Fixture()) {
            StepVerifier.create(fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(1), Duration.ofSeconds(5)))
                        .then(() -> fixture.scheduler.clock.advanceTime())
                        .then(() -> fixture.emit(11))
                        .assertNext(ByteBuf::release).verifyComplete();
            Runnable oldTimeout = fixture.scheduler.lastTimeout;
            assertNotNull(oldTimeout);
            StepVerifier.create(fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(2), Duration.ofSeconds(5)))
                        .then(() -> fixture.scheduler.clock.advanceTime())
                        .then(oldTimeout)
                        .then(() -> fixture.emit(21))
                        .assertNext(ByteBuf::release).verifyComplete();
            assertFalse(fixture.client.isDisposed());
            verify(fixture.parser, never()).reset();
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
        }
    }

    @Test
    void aFullRequestQueueReportsTheDeclaredBusyStatus() throws Exception {
        try (Fixture fixture = new Fixture(1)) {
            Disposable active = fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(1), Duration.ofSeconds(5))
                                              .subscribe(ByteBuf::release);
            fixture.scheduler.clock.advanceTime();
            Disposable queued = fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(2), Duration.ofSeconds(5))
                                              .subscribe(ByteBuf::release, error -> assertInstanceOf(PortClosedException.class, error));
            ByteBuf rejected = Unpooled.buffer(1).writeByte(3);
            StepVerifier.create(fixture.client.sendAndReceive(rejected, Duration.ofSeconds(5)))
                        .expectErrorSatisfies(SerialRequestCleanupTest::assertBusy).verify();
            assertEquals(0, rejected.refCnt());
            assertFalse(fixture.client.isDisposed());
            queued.dispose();
            active.dispose();
        }
    }

    @Test
    void aParserOverflowRetainsItsDeclaredStatusThroughTheTransportWrapper() throws Exception {
        try (Fixture fixture = new Fixture()) {
            StepVerifier.create(fixture.client.sendAndReceive(Unpooled.buffer(1).writeByte(1), Duration.ofSeconds(5)))
                        .then(() -> fixture.scheduler.clock.advanceTime())
                        .then(() -> fixture.frames.tryEmitError(new CollectorFailure(
                            InternalStatusCode.Bad_TooManyOperations, "error.channel_busy")))
                        .expectErrorSatisfies(SerialRequestCleanupTest::assertBusy).verify();
            assertTrue(fixture.client.isDisposed());
            assertEquals(0, fixture.scheduler.liveTimeouts.get());
        }
    }

    private static void assertBusy(Throwable error) {
        assertEquals(InternalStatusCode.Bad_TooManyOperations.getCode(), CollectorUtils.inferErrorCode(error));
        assertEquals("error.channel_busy", CollectorUtils.reasonOf(error));
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
                                fixture.emit(response);
                            }
                        })
                        .expectErrorSatisfies(SerialRequestCleanupTest::assertBusy).verify(Duration.ofSeconds(2));
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
        final List<Integer> writtenValues = new ArrayList<>();
        final DefaultSerialPortClient client;
        boolean reply;
        Runnable duringWrite;

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
                    writtenValues.add(Byte.toUnsignedInt(value[0]));
                    if (duringWrite != null) duringWrite.run();
                    if (reply) scheduler.clock.schedule(() -> emit(1), 1, TimeUnit.MILLISECONDS);
                }
            });
            try (var factories = mockStatic(Schedulers.class, CALLS_REAL_METHODS)) {
                factories.when(() -> Schedulers.single(any(Scheduler.class))).thenReturn(scheduler);
                client = new DefaultSerialPortClient(config, port, parser, queueSize, Monitor.noop());
            }
        }

        void emit(int value) { emit(Unpooled.buffer(1).writeByte(value)); }
        void emit(ByteBuf value) {
            if (frames.tryEmitNext(value).isFailure()) value.release();
        }

        @Override public void close() { client.dispose(); }
    }

    private static final class TrackingScheduler implements Scheduler {
        final VirtualTimeScheduler clock = VirtualTimeScheduler.create();
        final AtomicInteger liveTimeouts = new AtomicInteger();
        Runnable duringTimeoutRegistration;
        Runnable lastTimeout;

        @Override public Disposable schedule(Runnable task) { return schedule(task, 0, TimeUnit.NANOSECONDS); }
        @Override public Disposable schedule(Runnable task, long delay, TimeUnit unit) {
            if (unit.toMillis(delay) < 1000) return clock.schedule(task, delay, unit);
            lastTimeout = task;
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
