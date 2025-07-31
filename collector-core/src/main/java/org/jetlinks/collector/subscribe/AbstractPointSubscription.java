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
import java.util.List;
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
        return this
            .subscribe(subscribing)
            .<Void>then(Mono.fromRunnable(() -> {
                for (S s : subscribing) {
                    put(s.point.getId(), s);
                }
            }))
            .onErrorResume(err -> {
                monitor()
                    .logger()
                    .warn("subscribe points failed", err);
                for (S s : subscribing) {
                    listener.onSubscribeFailed(s.point.getId(), err);
                }
                return Mono.empty();
            });
    }

    @Override
    public final void subscribe(Collection<String> points) {

        @SuppressWarnings("all")
        Disposable subscribed = Flux
            .fromIterable(points)
            .flatMap(this::getPointRuntime)
            .map(this::createSubscribing)
            .buffer(getBufferSize())
            .concatMap(this::subscribe0)
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

    @Override
    public final void unsubscribe(Collection<String> pointId) {
        Flux.fromIterable(pointId)
            .mapNotNull(this::get)
            .buffer(getBufferSize())
            .concatMap(this::unsubscribe0)
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
          subscribe(keySet());
    }

    protected abstract void doDispose();

    @Override
    public final boolean subscribed(String pointId) {
        S s = get(pointId);
        return s != null && s.subscribed;
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
        boolean subscribed;
    }
}
