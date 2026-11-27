package org.jetlinks.collector;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ManagedLifecycleCloseTest {
    @Test
    void repeatingUnloadDoesNotTurnAnUnknownRemoteCloseIntoConfirmation() {
        ManagedLifecycle<AbstractLifecycle, String, String> managed = new ManagedLifecycle<>() {
            @Override protected Mono<AbstractLifecycle> reload0() {
                return Mono.just(new AbstractLifecycle() {
                    @Override protected void start0() { }
                    @Override protected void stop0() { }
                });
            }
        };
        try {
            managed.reload("runtime", "managed").block();
            assertEquals(LifecycleResult.unknown, managed.unloadAsync().block());
            assertEquals(LifecycleResult.unknown, managed.unloadAsync().block());
        } finally { managed.dispose(); }
    }
    @Test
    void replacementWaitsForTheSameCloseAfterItsFirstObserverCancels() {
        Sinks.Empty<Void> closed = Sinks.empty();
        AtomicInteger created = new AtomicInteger();
        ManagedLifecycle<AbstractLifecycle, String, String> managed = new ManagedLifecycle<>() {
            @Override protected Mono<AbstractLifecycle> reload0() {
                created.incrementAndGet();
                return Mono.just(new AbstractLifecycle() {
                    @Override protected void start0() { }
                    @Override protected void stop0() { }
                    @Override public Mono<LifecycleResult> closeAsync() {
                        return closed.asMono().then(Mono.fromRunnable(this::dispose)).thenReturn(LifecycleResult.confirmed);
                    }
                });
            }
        };
        try {
            managed.reload("first", "first").block();
            StepVerifier.create(managed.unloadAsync()).expectSubscription().thenCancel().verify();
            StepVerifier.create(managed.reload("second", "second"))
                .expectSubscription().then(() -> assertEquals(1, created.get()))
                .then(closed::tryEmitEmpty).verifyComplete();
            assertEquals(2, created.get());
        } finally {
            closed.tryEmitEmpty();
            managed.dispose();
        }
    }
}
