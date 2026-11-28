package org.jetlinks.collector.subscribe;

import reactor.core.Disposable;
import reactor.core.Disposables;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LazyPointSubscription implements PointSubscription {

    private final Set<String> points = ConcurrentHashMap.newKeySet();
    private final Disposable.Swap holder = Disposables.swap();

    public void reload(PointSubscription target) {
        if (holder.update(target)) {
            target.subscribe(points);
        }
    }

    @Override
    public boolean subscribed(String pointId) {
        Disposable sub = holder.get();
        if (sub instanceof PointSubscription _sub) {
            return _sub.subscribed(pointId);
        }
        return points.contains(pointId);
    }

    @Override
    public void subscribe(Collection<String> points) {
        this.points.addAll(points);
        Disposable sub = holder.get();
        if (sub instanceof PointSubscription _sub) {
            _sub.subscribe(points);
        }

    }

    @Override
    public void unsubscribe(Collection<String> pointId) {
        this.points.removeAll(pointId);
        Disposable sub = holder.get();
        if (sub instanceof PointSubscription _sub) {
            _sub.unsubscribe(pointId);
        }
    }

    @Override
    public void reload() {
        Disposable sub = holder.get();
        if (sub instanceof PointSubscription _sub) {
            _sub.reload();
        }
    }

    @Override
    public boolean isDisposed() {
        return holder.isDisposed();
    }

    @Override
    public void dispose() {
        holder.dispose();
    }

    @Override
    public reactor.core.publisher.Mono<org.jetlinks.collector.LifecycleResult> awaitSubscribe(Collection<String> requested) {
        return reactor.core.publisher.Mono.defer(() -> {
            points.addAll(requested);
            Disposable target = holder.get();
            return target instanceof PointSubscription subscription ? subscription.awaitSubscribe(requested)
                : reactor.core.publisher.Mono.just(org.jetlinks.collector.LifecycleResult.unknown);
        });
    }

    @Override
    public reactor.core.publisher.Mono<org.jetlinks.collector.LifecycleResult> awaitUnsubscribe(Collection<String> removing) {
        return reactor.core.publisher.Mono.defer(() -> {
            points.removeAll(removing);
            Disposable target = holder.get();
            return target instanceof PointSubscription subscription ? subscription.awaitUnsubscribe(removing)
                : reactor.core.publisher.Mono.just(org.jetlinks.collector.LifecycleResult.unknown);
        });
    }

    @Override
    public reactor.core.publisher.Mono<org.jetlinks.collector.LifecycleResult> closeAsync() {
        return reactor.core.publisher.Mono.defer(() -> {
            Disposable target = holder.get();
            return target instanceof PointSubscription subscription
                ? subscription.closeAsync().doFinally(ignore -> holder.dispose())
                : reactor.core.publisher.Mono.fromRunnable(holder::dispose)
                    .thenReturn(org.jetlinks.collector.LifecycleResult.unknown);
        }).cache();
    }
}
