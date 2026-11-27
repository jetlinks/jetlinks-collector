package org.jetlinks.collector;

import org.junit.jupiter.api.Test;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.jetlinks.collector.CollectorConstants.States.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedLifecycleTest {

    @Test
    void unloadRetainsConfigurationAndPauseStateForTreeRebuild() {
        var old = mock(DataCollectorProvider.CollectorRuntime.class);
        var next = mock(DataCollectorProvider.CollectorRuntime.class);
        var source = new AtomicReference<Mono<DataCollectorProvider.CollectorRuntime>>(Mono.just(old));
        var lifecycle = new ManagedLifecycle<DataCollectorProvider.CollectorRuntime, String, String>() {
            @Override protected Mono<DataCollectorProvider.CollectorRuntime> reload0() { return source.get(); }
        };
        lifecycle.reload("runtime", "managed").block();
        lifecycle.pause();
        lifecycle.unload();
        verify(old).dispose();
        assertNull(lifecycle.loaded());
        assertEquals("runtime", lifecycle.runtimeConfig());
        assertTrue(lifecycle.isPaused());
        assertFalse(lifecycle.isDisposed());
        source.set(Mono.just(next));
        lifecycle.reload().block();
        verify(next).pause();
        verify(next, never()).start();
        lifecycle.dispose();
    }

    @Test
    void multipleWaitersShareThePendingLoadWithoutSpinning() throws Exception {
        var runtime = mock(DataCollectorProvider.CollectorRuntime.class);
        when(runtime.checkState()).thenReturn(Mono.just(running));
        var lifecycle = lifecycle(Mono.just(runtime));
        var first = lifecycle.checkState();
        var waiting = java.util.concurrent.CompletableFuture.supplyAsync(lifecycle::checkState);
        try {
            var second = waiting.get(1, java.util.concurrent.TimeUnit.SECONDS);
            lifecycle.reload("runtime", "managed").block();
            assertEquals(running, first.block());
            assertEquals(running, second.block());
        } finally {
            lifecycle.reload("runtime", "managed").block();
            lifecycle.dispose();
        }
    }

    @Test
    void loadingFailureMustReachCallerWithoutRetryingOrReplacingTheWorkingRuntime() {
        var working = mock(DataCollectorProvider.CollectorRuntime.class);
        var failure = new IllegalStateException("load failed");
        var source = new AtomicReference<Mono<DataCollectorProvider.CollectorRuntime>>(Mono.just(working));
        var attempts = new AtomicInteger();
        var lifecycle = new ManagedLifecycle<DataCollectorProvider.CollectorRuntime, String, String>() {
            @Override protected Mono<DataCollectorProvider.CollectorRuntime> reload0() {
                attempts.incrementAndGet();
                return source.get();
            }
        };
        lifecycle.reload("old", "old").block();
        source.set(Mono.error(failure));

        StepVerifier.create(lifecycle.reload("new", "new")).expectErrorMatches(error -> error == failure).verify();

        assertEquals(2, attempts.get());
        assertSame(working, lifecycle.loaded());
        assertEquals("old", lifecycle.runtimeConfig());
        verify(working, never()).dispose();

        when(working.isDisposed()).thenReturn(true);
        StepVerifier.create(lifecycle.reload("next", "next")).expectErrorMatches(error -> error == failure).verify();
        assertNull(lifecycle.loaded());
        lifecycle.dispose();
    }

    @Test
    void supersededLoadMustFailItsCallerAndDisposeLateRuntime() {
        var first = TestPublisher.<DataCollectorProvider.CollectorRuntime>createNoncompliant(
            TestPublisher.Violation.DEFER_CANCELLATION);
        var next = mock(DataCollectorProvider.CollectorRuntime.class);
        var late = mock(DataCollectorProvider.CollectorRuntime.class);
        var source = new AtomicReference<Mono<DataCollectorProvider.CollectorRuntime>>(first.mono());
        var lifecycle = new ManagedLifecycle<DataCollectorProvider.CollectorRuntime, String, String>() {
            @Override protected Mono<DataCollectorProvider.CollectorRuntime> reload0() { return source.get(); }
        };
        StepVerifier.create(lifecycle.reload("first", "first"))
            .then(() -> {
                source.set(Mono.just(next));
                lifecycle.reload("next", "next").block();
                first.emit(late);
            })
            .expectError(CancellationException.class)
            .verify();
        assertSame(next, lifecycle.loaded());
        assertEquals("next", lifecycle.runtimeConfig());
        verify(late).dispose();
        lifecycle.dispose();
    }

    @Test
    void emptyLoadIsAnErrorAndLoaderReceivesSubscriberContext() {
        var lifecycle = lifecycle(Mono.deferContextual(context -> {
            assertEquals("provider-generation", context.get("generation"));
            return Mono.empty();
        }));
        StepVerifier.create(lifecycle.reload("runtime", "managed")
                .contextWrite(context -> context.put("generation", "provider-generation")))
            .expectError(IllegalStateException.class)
            .verify();
        assertNull(lifecycle.loaded());
        lifecycle.dispose();
    }

    @Test
    void pauseRetainsRuntimeAndCanResumeWithoutReloading() {
        var runtime = mock(DataCollectorProvider.CollectorRuntime.class);
        when(runtime.state()).thenReturn(running);
        var lifecycle = lifecycle(Mono.just(runtime));
        lifecycle.reload("runtime", "managed").block();
        var resource = Disposables.single();
        lifecycle.pause.update(resource);

        lifecycle.pause();

        assertTrue(lifecycle.isPaused());
        assertEquals(paused, lifecycle.managedState());
        assertSame(runtime, lifecycle.loaded());
        assertTrue(resource.isDisposed());
        assertFalse(lifecycle.isDisposed());
        assertEquals("runtime", lifecycle.runtimeConfig());

        lifecycle.start();
        lifecycle.start();

        assertFalse(lifecycle.isPaused());
        assertEquals(running, lifecycle.managedState());
        assertSame(runtime, lifecycle.loaded());
        verify(runtime, times(2)).start();
        var resumedResource = Disposables.single();
        assertTrue(lifecycle.pause.update(resumedResource));
        assertFalse(resumedResource.isDisposed());
        lifecycle.dispose();
        assertTrue(resumedResource.isDisposed());
        lifecycle.start();
        verify(runtime, times(2)).start();
        assertEquals(stopped, lifecycle.managedState());
    }

    @Test
    void runtimeFinishingLoadWhilePausedMustNotStart() {
        var runtime = mock(DataCollectorProvider.CollectorRuntime.class);
        var pending = Sinks.<DataCollectorProvider.CollectorRuntime>one();
        var lifecycle = lifecycle(pending.asMono());
        var loading = lifecycle.reload("runtime", "managed").subscribe();

        lifecycle.pause();
        pending.tryEmitValue(runtime);

        assertTrue(loading.isDisposed());
        assertEquals(paused, lifecycle.managedState());
        verify(runtime, never()).start();
        verify(runtime).pause();
        lifecycle.start();
        verify(runtime).start();
        lifecycle.dispose();
    }

    private ManagedLifecycle<DataCollectorProvider.CollectorRuntime, String, String> lifecycle(
        Mono<DataCollectorProvider.CollectorRuntime> runtime) {
        return new ManagedLifecycle<>() {
            @Override
            protected Mono<DataCollectorProvider.CollectorRuntime> reload0() {
                return runtime;
            }
        };
    }
}
