package org.jetlinks.collector;

import org.junit.jupiter.api.Test;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import static org.jetlinks.collector.CollectorConstants.States.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedLifecycleTest {

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
