package org.jetlinks.collector.subscribe;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import org.jetlinks.collector.LifecycleResult;

import java.util.Collection;

public interface PointSubscription extends Disposable {

    static PointSubscription unsupported() {
        return UnsupportedPointSubscription.INSTANCE;
    }

    boolean subscribed(String pointId);

    void subscribe(Collection<String> pointId);

    void unsubscribe(Collection<String> pointId);

    void reload();

    default Mono<LifecycleResult> awaitSubscribe(Collection<String> points) {
        return Mono.fromRunnable(() -> subscribe(points)).thenReturn(LifecycleResult.unknown);
    }

    default Mono<LifecycleResult> awaitUnsubscribe(Collection<String> points) {
        return Mono.fromRunnable(() -> unsubscribe(points)).thenReturn(LifecycleResult.unknown);
    }

    default Mono<LifecycleResult> closeAsync() {
        return Mono.fromRunnable(this::dispose).thenReturn(LifecycleResult.unknown);
    }

}
