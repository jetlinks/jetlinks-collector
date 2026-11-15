package org.jetlinks.collector.subscribe;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetlinks.collector.DataCollectorProvider;
import org.jetlinks.collector.InternalStatusCode;
import org.jetlinks.collector.exception.CollectorFailure;
import org.jetlinks.core.monitor.Monitor;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按点位管理订阅，合并已加载点位的协议订阅操作。
 * 加载中的订阅意图仅由当前操作持有，退订/关闭会取消查找；协议资源仍由实现类注销。
 *
 * @see PointSubscription
 * @since 1.0
 */
public abstract class AbstractPointSubscription<P extends DataCollectorProvider.PointRuntime,
    S extends AbstractPointSubscription.SubscribingPoint<P>>
    extends ConcurrentHashMap<String, S> implements PointSubscription {

    protected final PointListener listener;
    private final Disposable.Swap disposable = Disposables.swap();
    private final Object lifecycleLock = new Object();
    // 仅保留加载/提交中的意图，完成后移除；不为每个已订阅点位增加常驻历史。
    private final Map<String, PendingSubscription> pending = new HashMap<>();
    private final Sinks.Empty<Void> stopped = Sinks.empty();

    public AbstractPointSubscription(PointListener listener) {
        this.listener = listener;
        this.disposable.update(() -> {
            stopped.tryEmitEmpty();
            synchronized (lifecycleLock) {
                doDispose();
                clear();
            }
        });
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

    private Mono<Void> subscribe0(List<PendingSubscription> requested) {
        List<S> subscribing = new ArrayList<>(requested.size());
        return Mono.defer(() -> {
                synchronized (lifecycleLock) {
                    for (PendingSubscription request : requested) {
                        // lookup 完成后可能仍在 buffer 中；只提交当前仍有效的那一代意图。
                        if (!isDisposed() && pending.get(request.id) == request) {
                            put(request.id, request.subscribing);
                            subscribing.add(request.subscribing);
                        }
                    }
                    if (subscribing.isEmpty()) {
                        return Mono.empty();
                    }
                    monitor().logger().debug("subscribe points : {}", subscribing);
                    // 部分实现会在工厂方法内同步注册，和撤销意图共用同一短锁；异步 IO 不在锁内等待。
                    return subscribe(subscribing);
                }
            })
            .onErrorResume(err -> {
                monitor().logger().warn("subscribe points failed", err);
                for (S s : subscribing) {
                    if (remove(s.point.getId(), s)) {
                        listener.onSubscribeFailed(s.point.getId(), err);
                    }
                }
                return Mono.empty();
            })
            .doFinally(ignore -> requested.forEach(PendingSubscription::dispose));
    }

    private Mono<PendingSubscription> lookup(String id, Disposable.Composite loading) {
        return Mono.defer(() -> {
            PendingSubscription request;
            synchronized (lifecycleLock) {
                if (isDisposed() || containsKey(id) || pending.containsKey(id)) {
                    return Mono.empty();
                }
                request = new PendingSubscription(id, loading);
                pending.put(id, request);
                if (!loading.add(request)) {
                    return Mono.empty();
                }
            }
            return Mono.defer(() -> getPointRuntime(id))
                       .takeUntilOther(request.cancelled.asMono())
                       .mapNotNull(request::prepare)
                       .doFinally(ignore -> {
                           if (request.subscribing == null) {
                               request.dispose();
                           }
                       });
        });
    }

    protected final Mono<Void> subscribeAsync(Collection<String> points) {
        return Mono.defer(() -> {
            if (isDisposed()) {
                CollectorFailure error = new CollectorFailure(InternalStatusCode.Bad_ChannelError, "error.channel_error");
                for (String id : points) {
                    listener.onSubscribeFailed(id, error);
                }
                return Mono.empty();
            }
            Disposable.Composite loading = Disposables.composite();
            return Flux.fromIterable(points)
                       .flatMap(id -> lookup(id, loading))
                       .buffer(getBufferSize())
                       .concatMap(this::subscribe0)
                       .takeUntilOther(stopped.asMono())
                       .doFinally(ignore -> loading.dispose())
                       .then();
        });
    }

    @Override
    public final void subscribe(Collection<String> points) {
        this.subscribeAsync(points)
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
            .mapNotNull(id -> {
                PendingSubscription request;
                S subscribing;
                synchronized (lifecycleLock) {
                    request = pending.remove(id);
                    subscribing = remove(id);
                }
                if (request != null) {
                    request.dispose();
                }
                return subscribing;
            })
            .buffer(getBufferSize())
            .concatMap(this::unsubscribe0)
            .then();
    }

    @Override
    public final void unsubscribe(Collection<String> pointId) {
        unsubscribeAsync(pointId)
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
        Set<String> id;
        synchronized (lifecycleLock) {
            id = new HashSet<>(keySet());
            id.addAll(pending.keySet());
        }
        unsubscribeAsync(id)
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

    /** 当前操作的取消令牌；点位进入协议订阅批次后释放，不随正常数据回调常驻。 */
    private final class PendingSubscription implements Disposable {
        final String id;
        final Disposable.Composite loading;
        final Sinks.Empty<Void> cancelled = Sinks.empty();
        S subscribing;
        private volatile boolean disposed;

        PendingSubscription(String id, Disposable.Composite loading) {
            this.id = id;
            this.loading = loading;
        }

        PendingSubscription prepare(P runtime) {
            synchronized (lifecycleLock) {
                if (isDisposed() || pending.get(id) != this || AbstractPointSubscription.this.isDisposed()) {
                    return null;
                }
                subscribing = createSubscribing(runtime);
                return this;
            }
        }

        @Override
        public void dispose() {
            synchronized (lifecycleLock) {
                if (disposed) {
                    return;
                }
                disposed = true;
                pending.remove(id, this);
            }
            loading.remove(this);
            cancelled.tryEmitEmpty();
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
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
