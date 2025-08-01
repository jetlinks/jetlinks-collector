package org.jetlinks.collector.subscribe;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetlinks.collector.DataCollectorProvider;
import org.jetlinks.core.monitor.Monitor;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractPointSubscription<P extends DataCollectorProvider.PointRuntime,
    S extends AbstractPointSubscription.SubscribingPoint<P>>
    extends ConcurrentHashMap<String, S> implements PointSubscription {

    protected final PointListener listener;
    private final Disposable.Swap disposable = Disposables.swap();

    public AbstractPointSubscription(PointListener listener) {
        this.listener = listener;
        this.disposable.update(this::doDispose);
    }

    public abstract Monitor monitor();

    public abstract Mono<P> getPointRuntime(String id);

    protected abstract S createSubscribing(P runtime);

    protected abstract Mono<Void> subscribe(List<S> subscribing);

    protected abstract Mono<Void> unsubscribe(List<S> subscribing);

    protected int getBufferSize() {
        return 50;
    }

    private Mono<Void> unsubscribe0(List<S> subscribing) {
        monitor()
            .logger()
            .debug("unsubscribe points : {}", subscribing);
        return this
            .unsubscribe(subscribing)
            .onErrorResume(err -> {
                monitor()
                    .logger()
                    .warn("subscribe points failed", err);
                return Mono.empty();
            })
            .doFinally(ignore -> {
                for (S s : subscribing) {
                    remove(s.point.getId(), s);
                }
            });
    }

    private Mono<Void> subscribe0(List<S> subscribing) {
        for (S s : subscribing) {
            put(s.point.getId(), s);
        }
        monitor()
            .logger()
            .debug("subscribe points : {}", subscribing);
        return this
            .subscribe(subscribing)
            .onErrorResume(err -> {
                monitor()
                    .logger()
                    .warn("subscribe points failed", err);
                for (S s : subscribing) {
                    listener.onSubscribeFailed(s.point.getId(), err);
                    remove(s.point.getId(), s);
                }
                return Mono.empty();
            });
    }

    protected final Mono<Void> subscribeAsync(Collection<String> points) {
        return Flux
            .fromIterable(points)
            .filter(s -> !containsKey(s))
            .flatMap(this::getPointRuntime)
            .map(this::createSubscribing)
            .buffer(getBufferSize())
            .concatMap(this::subscribe0)
            .then();
    }

    @Override
    public final void subscribe(Collection<String> points) {
        @SuppressWarnings("all")
        Disposable ignore = this
            .subscribeAsync(points)
            .subscribe(
                null,
                error -> {
                    monitor()
                        .logger()
                        .warn("subscribe points failed", error);
                    for (String id : points) {
                        listener.onSubscribeFailed(id, error);
                    }
                });
    }


    protected Mono<Void> unsubscribeAsync(Collection<String> pointId) {
        return Flux
            .fromIterable(pointId)
            .mapNotNull(this::remove)
            .buffer(getBufferSize())
            .concatMap(this::unsubscribe0)
            .then();
    }

    @Override
    public final void unsubscribe(Collection<String> pointId) {
        @SuppressWarnings("all")
        Disposable ignore = unsubscribeAsync(pointId)
            .subscribe(
                null,
                error -> {
                    monitor()
                        .logger()
                        .warn("unsubscribe points failed", error);
                });
    }

    @Override
    public final void reload() {
        Set<String> id = new HashSet<>(keySet());
        @SuppressWarnings("all")
        Disposable ignore = unsubscribeAsync(id)
            .then(subscribeAsync(id))
            .subscribe(
                null,
                error -> {
                    monitor()
                        .logger()
                        .warn("reload points failed", error);
                });

    }

    protected abstract void doDispose();

    @Override
    public final boolean subscribed(String pointId) {
        S s = get(pointId);
        return s != null && s.isSubscribed();
    }


    @Override
    public final boolean isDisposed() {
        return disposable.isDisposed();
    }

    @Override
    public final void dispose() {
        disposable.dispose();
    }

    @RequiredArgsConstructor
    @Getter
    public static class SubscribingPoint<P extends DataCollectorProvider.PointRuntime> {
        protected final P point;

        @Override
        public String toString() {
            return point.toString();
        }

        public boolean isSubscribed() {
            return true;
        }
    }
}
