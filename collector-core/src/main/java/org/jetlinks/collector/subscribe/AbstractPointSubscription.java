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
 * 加载/提交中的意图仅由当前操作持有；同 ID 的新订阅等待旧提交及注销完成。
 * 实现类负责协议句柄和不可取消远端请求的释放，公共层只通过既有退订 SPI 管理撤销。
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

    /**
     * 完成信号表示整批注册已完成；取消时实现类须释放部分提交及不可取消远端操作的后续资源。
     * 公共层会调用退订 SPI 补偿已撤销成员，但不能代替实现类管理协议句柄或迟到应答。
     */
    protected abstract Mono<Void> subscribe(List<S> subscribing);

    /** 注销指定代次的协议资源，完成后才允许同 ID 的下一代注册。 */
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
                    .warn("unsubscribe points failed", err);
                return Mono.empty();
            });
    }

    private Mono<Void> subscribe0(List<PendingSubscription> requested) {
        // usingWhen 使外部 cancel 也拥有异步注销链，不能用 doFinally 启动无人等待的退订任务。
        return Mono.usingWhen(Mono.just(new SubscribingBatch(requested)),
                              SubscribingBatch::subscribe,
                              batch -> batch.release(false, null),
                              (batch, error) -> batch.release(true, error),
                              batch -> batch.release(true, null))
                   // 失败已由 release 按有效点位回调，保持原订阅入口的错误处理语义。
                   .onErrorResume(error -> Mono.empty());
    }

    private Mono<PendingSubscription> lookup(String id, Disposable.Composite loading) {
        return Mono.defer(() -> {
            PendingSubscription request;
            synchronized (lifecycleLock) {
                if (isDisposed() || containsKey(id)) {
                    return Mono.empty();
                }
                PendingSubscription previous = pending.get(id);
                if (previous != null) {
                    // 旧批次注销前不能开始同 ID 的新一代，避免协议按 ID 删除时误删新订阅。
                    return previous.isDisposed()
                        ? previous.finished.asMono().then(lookup(id, loading)) : Mono.empty();
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
                               request.finish();
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
        return Mono.defer(() -> {
            List<PendingSubscription> removing = new ArrayList<>();
            return Flux.fromIterable(pointId)
                .flatMap(id -> {
                    PendingSubscription request;
                    synchronized (lifecycleLock) {
                        request = pending.get(id);
                        S subscribing = remove(id);
                        if (request != null) {
                            request.cancel();
                        } else if (subscribing != null) {
                            // 正常订阅也可能异步注销，同 ID 在注销结束前继续共用临时意图屏障。
                            request = new PendingSubscription(id, null);
                            request.subscribing = subscribing;
                            request.disposed = true;
                            pending.put(id, request);
                            removing.add(request);
                            return Mono.just(request);
                        } else {
                            return Mono.<PendingSubscription>empty();
                        }
                    }
                    request.signalCancellation();
                    return request.finished.asMono().then(Mono.<PendingSubscription>empty());
                })
                .buffer(getBufferSize())
                .concatMap(requests -> unsubscribe0(requests.stream().map(request -> request.subscribing).toList())
                    .doFinally(ignore -> requests.forEach(PendingSubscription::finish)))
                .takeUntilOther(stopped.asMono())
                .doFinally(ignore -> {
                    synchronized (lifecycleLock) {
                        removing.forEach(PendingSubscription::finish);
                    }
                })
                .then();
        });
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
    public Mono<org.jetlinks.collector.LifecycleResult> awaitSubscribe(Collection<String> points) {
        return subscribeAsync(points).thenMany(Flux.fromIterable(points).concatMap(id -> Mono.defer(() -> {
            synchronized (lifecycleLock) {
                PendingSubscription request = pending.get(id);
                return request == null ? Mono.empty() : request.finished.asMono();
            }
        }))).then().thenReturn(org.jetlinks.collector.LifecycleResult.unknown);
    }

    @Override
    public Mono<org.jetlinks.collector.LifecycleResult> awaitUnsubscribe(Collection<String> points) {
        return unsubscribeAsync(points).thenReturn(org.jetlinks.collector.LifecycleResult.unknown);
    }

    @Override
    public Mono<org.jetlinks.collector.LifecycleResult> closeAsync() {
        return Mono.defer(() -> {
            Set<String> ids;
            synchronized (lifecycleLock) {
                ids = new HashSet<>(keySet());
                ids.addAll(pending.keySet());
            }
            return unsubscribeAsync(ids)
                .then(Mono.fromRunnable(this::dispose))
                .thenReturn(org.jetlinks.collector.LifecycleResult.unknown);
        });
    }

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

    /** 只在一次提交期间持有批次；撤销部分成员不能取消仍含有效点位的协议 RPC。 */
    private final class SubscribingBatch {
        final List<PendingSubscription> requested;
        final List<PendingSubscription> submitting = new ArrayList<>();
        final List<S> points = new ArrayList<>();
        final Sinks.Empty<Void> cancelled = Sinks.empty();

        SubscribingBatch(List<PendingSubscription> requested) {
            this.requested = requested;
        }

        Mono<Void> subscribe() {
            return Mono.defer(() -> {
                synchronized (lifecycleLock) {
                    for (PendingSubscription request : requested) {
                        // lookup 后可能还在 buffer 中，只提交未被撤销的当前意图。
                        if (!isDisposed() && pending.get(request.id) == request && !request.isDisposed()) {
                            request.batch = this;
                            put(request.id, request.subscribing);
                            submitting.add(request);
                            points.add(request.subscribing);
                        }
                    }
                    if (points.isEmpty()) {
                        return Mono.empty();
                    }
                    monitor().logger().debug("subscribe points : {}", points);
                    // SPI 工厂可能同步注册；仅工厂调用与撤销共用短锁，异步 IO 不在锁内等待。
                    return AbstractPointSubscription.this.subscribe(points);
                }
            }).takeUntilOther(cancelled.asMono());
        }

        void cancelIfEmpty() {
            synchronized (lifecycleLock) {
                if (submitting.stream().anyMatch(request -> !request.isDisposed())) {
                    return;
                }
            }
            cancelled.tryEmitEmpty();
        }

        Mono<Void> release(boolean revokeAll, Throwable failure) {
            return Mono.defer(() -> {
                List<PendingSubscription> revoked = new ArrayList<>();
                List<String> failed = new ArrayList<>();
                synchronized (lifecycleLock) {
                    for (PendingSubscription request : submitting) {
                        if (failure != null && remove(request.id, request.subscribing)) {
                            failed.add(request.id);
                        }
                        if (revokeAll) {
                            request.cancel();
                        }
                        if (request.isDisposed()) {
                            revoked.add(request);
                        } else {
                            request.finish();
                        }
                    }
                }
                if (failure != null) {
                    monitor().logger().warn("subscribe points failed", failure);
                    failed.forEach(id -> listener.onSubscribeFailed(id, failure));
                }
                // 整体关闭时 doDispose 拥有协议资源；其余撤销经原 SPI 注销，不猜测协议句柄。
                Mono<Void> cleanup = revoked.isEmpty() || isDisposed() ? Mono.empty()
                    : Mono.defer(() -> AbstractPointSubscription.this.unsubscribe(
                        revoked.stream().map(request -> request.subscribing).toList()));
                return cleanup
                    .takeUntilOther(stopped.asMono())
                    .doOnError(error -> {
                        monitor().logger().warn("unsubscribe points failed", error);
                        revoked.forEach(request -> listener.onSubscribeFailed(request.id, error));
                    })
                    .onErrorResume(error -> Mono.empty())
                    .doFinally(ignore -> revoked.forEach(PendingSubscription::finish));
            });
        }
    }

    /** 当前操作的取消令牌；异步提交及补偿结束后释放，不随正常数据回调常驻。 */
    private final class PendingSubscription implements Disposable {
        final String id;
        final Disposable.Composite loading;
        final Sinks.Empty<Void> cancelled = Sinks.empty();
        final Sinks.Empty<Void> finished = Sinks.empty();
        S subscribing;
        SubscribingBatch batch;
        private volatile boolean disposed;
        private boolean complete;

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
                cancel();
            }
            signalCancellation();
        }

        // 调用方持有 lifecycleLock，先撤销意图，再在锁外向异步源发信号。
        void cancel() {
            if (disposed || complete) {
                return;
            }
            disposed = true;
            if (subscribing != null) {
                remove(id, subscribing);
            }
            if (batch == null) {
                finish();
            }
        }

        void signalCancellation() {
            cancelled.tryEmitEmpty();
            if (batch != null) {
                batch.cancelIfEmpty();
            }
        }

        void finish() {
            synchronized (lifecycleLock) {
                if (complete) {
                    return;
                }
                complete = true;
                pending.remove(id, this);
                if (loading != null) {
                    loading.remove(this);
                }
            }
            finished.tryEmitEmpty();
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
