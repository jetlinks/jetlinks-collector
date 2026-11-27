package org.jetlinks.collector;

import jakarta.annotation.Nonnull;
import org.jetlinks.core.command.AsyncProxyCommandSupport;
import org.jetlinks.core.command.CommandSupport;
import org.jetlinks.core.command.ProxyCommandSupport;
import org.jetlinks.core.utils.Reactors;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.*;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;

public abstract class ManagedLifecycle<
    T extends Lifecycle & CommandSupport,
    RC,
    MC>
    implements Lifecycle, ProxyCommandSupport,
    BiConsumer<State, State> {

    // 加载中等待
    @SuppressWarnings("all")
    static final AtomicReferenceFieldUpdater<ManagedLifecycle, Sinks.One>
        AWAIT = AtomicReferenceFieldUpdater.newUpdater(ManagedLifecycle.class, Sinks.One.class, "await");
    private volatile Sinks.One<T> await;

    @SuppressWarnings("all")
    static final AtomicReferenceFieldUpdater<ManagedLifecycle, Object>
        RUNTIME_CONFIG = AtomicReferenceFieldUpdater.newUpdater(ManagedLifecycle.class, Object.class, "runtimeConfig"),
        MANAGED_CONFIG = AtomicReferenceFieldUpdater.newUpdater(ManagedLifecycle.class, Object.class, "managedConfig");

    protected volatile RC runtimeConfig;
    protected volatile MC managedConfig;

    protected volatile T loaded;

    @SuppressWarnings("all")
    public RC runtimeConfig() {
        return (RC) RUNTIME_CONFIG.get(this);
    }

    @SuppressWarnings("all")
    public MC managedConfig() {
        return (MC) MANAGED_CONFIG.get(this);
    }

    @SuppressWarnings("all")
    public T loaded() {
        return (T) LOADED.get(this);
    }

    @SuppressWarnings("all")
    static final AtomicReferenceFieldUpdater<ManagedLifecycle, Lifecycle>
        LOADED = AtomicReferenceFieldUpdater.newUpdater(ManagedLifecycle.class, Lifecycle.class, "loaded");

    @SuppressWarnings("all")
    static final AtomicReferenceFieldUpdater<ManagedLifecycle, ReloadDisposable>
        LOADING = AtomicReferenceFieldUpdater.newUpdater(ManagedLifecycle.class, ReloadDisposable.class, "loading");
    private volatile ReloadDisposable loading;
    private long reloadSequence;
    private volatile Mono<LifecycleResult> closing;
    private volatile LifecycleResult previousCloseResult = LifecycleResult.confirmed;

    @SuppressWarnings("all")
    private static final AtomicReferenceFieldUpdater<ManagedLifecycle, State>
        MANAGED_STATE = AtomicReferenceFieldUpdater.newUpdater(ManagedLifecycle.class, State.class, "managedState");
    private volatile State managedState;


    private List<BiConsumer<State, State>> stateListener;

    /**
     * 暂停时释放
     */
    protected final Disposable.Swap pause = Disposables.swap();

    /**
     * 停止时释放
     */
    protected final Disposable.Composite disposable = Disposables.composite(pause);


    @Override
    public CommandSupport getProxyTarget() {
        T loaded = this.loaded;
        if (loaded != null) {
            return loaded;
        }
        return new AsyncProxyCommandSupport(Mono.defer(this::tryLoad));
    }

    protected void handleLoaded(T loaded) {

        loaded.onStateChanged(this);
        if (isPaused()) {
            loaded.pause();
        } else {
            loaded.start();
        }

    }

    protected Mono<T> tryLoad() {
        for (; ; ) {
            @SuppressWarnings("all")
            T loaded = (T) LOADED.get(this);
            if (loaded != null) {
                return Mono.just(loaded);
            }
            @SuppressWarnings("all")
            Sinks.One<T> loading = AWAIT.get(this);
            if (loading == null) {
                if (!AWAIT.compareAndSet(this, null, loading = Sinks.one())) continue;
            }
            Sinks.One<T> finalLoading = loading;
            return loading.asMono()
                .timeout(Duration.ofSeconds(10), Mono.fromSupplier(() -> this.loaded))
                .doOnError(err -> AWAIT.compareAndSet(this, finalLoading, null));
        }
    }

    protected abstract Mono<T> reload0();

    public Mono<Void> reload() {
        return reload(runtimeConfig, managedConfig);
    }

    /**
     * 替换一个运行实例；成功仅表示本次新配置生效。失败恢复可用的旧实例和配置并透传原错误，
     * 不隐式重新加载旧配置。被新请求替代的加载以取消错误结束，迟到实例会被释放。
     */
    public final Mono<Void> reload(RC runtimeConfig, MC managedConfig) {
        return Mono.create(sink -> {
            LoadSubscriber subscriber;
            synchronized (this) {
                long sequence = ++reloadSequence;
                ReloadDisposable.disposeNotReload(LOADING.get(this));
                if (isDisposed() || sequence != reloadSequence) {
                    sink.error(new CancellationException("reload was superseded or disposed"));
                    return;
                }
                subscriber = new LoadSubscriber(runtimeConfig, managedConfig, sink);
                LOADING.set(this, subscriber);
                RUNTIME_CONFIG.set(this, runtimeConfig);
                MANAGED_CONFIG.set(this, managedConfig);
            }
            sink.onCancel(subscriber::cancelByCaller);
            subscriber.reload();
        });
    }

    protected void onLoadError(Throwable e) {

    }

    /** 释放当前协议实例，保留配置、管理状态和包装对象，供父子树按顺序重新加载。 */
    public final void unload() {
        synchronized (this) {
            ++reloadSequence;
            ReloadDisposable.disposeNotReload(LOADING.get(this));
            tryDispose(LOADED.getAndSet(this, null));
        }
    }

    /** 先撤销本地实例，再等待插件清理；取消时由调用方持有清理作业。 */
    public final Mono<LifecycleResult> unloadAsync() {
        return Mono.defer(() -> {
            synchronized (this) {
                ++reloadSequence;
                ReloadDisposable.disposeNotReload(LOADING.get(this));
                if (closing != null) return closing;
                T previous = (T) LOADED.getAndSet(this, null);
                if (previous == null) return Mono.just(previousCloseResult);
                closing = Mono.defer(previous::closeAsync)
                    .map(this::finishClosing)
                    .doOnError(error -> finishClosing(LifecycleResult.unknown)).cache();
                return closing;
            }
        });
    }

    private synchronized LifecycleResult finishClosing(LifecycleResult result) {
        previousCloseResult = previousCloseResult == LifecycleResult.confirmed && result == LifecycleResult.confirmed
            ? LifecycleResult.confirmed : LifecycleResult.unknown;
        closing = null;
        return previousCloseResult;
    }

    private class LoadSubscriber extends BaseSubscriber<T> implements ReloadDisposable {

        final RC newRC;
        final MC newMC;
        final T oldLoaded;
        final RC oldRC;
        final MC oldMC;
        final MonoSink<Void> signal;
        T newLoaded;
        volatile boolean callerCancelled;

        public LoadSubscriber(RC newRC, MC newMC, MonoSink<Void> signal) {
            this.newMC = newMC;
            this.newRC = newRC;
            this.signal = signal;
            oldLoaded = loaded;
            oldRC = runtimeConfig;
            oldMC = managedConfig;
        }

        @Override
        public Context currentContext() { return Context.of(signal.contextView()); }

        @Override
        protected void hookOnNext(@Nonnull T value) {
            synchronized (ManagedLifecycle.this) {
                if (LOADING.get(ManagedLifecycle.this) != this || ManagedLifecycle.this.isDisposed()) {
                    tryDispose(value);
                    return;
                }
                newLoaded = value;
                LOADED.set(ManagedLifecycle.this, value);
                tryDispose(oldLoaded);
                ManagedLifecycle.this.handleLoaded(value);
            }
        }

        @Override
        protected void hookOnComplete() {
            Sinks.One<T> await;
            synchronized (ManagedLifecycle.this) {
                if (!LOADING.compareAndSet(ManagedLifecycle.this, this, null)) return;
                await = AWAIT.getAndSet(ManagedLifecycle.this, null);
            }
            if (await != null) {
                await.emitValue(newLoaded, Reactors.emitFailureHandler());
            }
            signal.success();
        }

        @Override
        protected void hookOnError(@Nonnull Throwable throwable) {
            failed(throwable, true);
        }

        @Override
        protected void hookOnCancel() {
            failed(new CancellationException("reload was cancelled"), false);
        }

        private void failed(Throwable error, boolean report) {
            Sinks.One<T> await = null;
            synchronized (ManagedLifecycle.this) {
                if (LOADING.compareAndSet(ManagedLifecycle.this, this, null)) {
                    RUNTIME_CONFIG.compareAndSet(ManagedLifecycle.this, newRC, oldRC);
                    MANAGED_CONFIG.compareAndSet(ManagedLifecycle.this, newMC, oldMC);
                    LOADED.set(ManagedLifecycle.this,
                        oldLoaded != null && !oldLoaded.isDisposed() ? oldLoaded : null);
                    tryDispose(newLoaded);
                    await = AWAIT.getAndSet(ManagedLifecycle.this, null);
                    if (report) onLoadError(error);
                }
            }
            if (await != null) await.tryEmitError(error);
            if (!callerCancelled) signal.error(error);
        }

        void reload() {
            Mono<T> reload0;
            synchronized (ManagedLifecycle.this) {
                if (LOADING.get(ManagedLifecycle.this) != this) return;
                try {
                    Mono<LifecycleResult> previousClose = closing;
                    reload0 = previousClose == null ? Objects.requireNonNull(reload0(), "reload publisher")
                        : previousClose.then(Mono.defer(() -> {
                            synchronized (ManagedLifecycle.this) {
                                if (LOADING.get(ManagedLifecycle.this) != this || ManagedLifecycle.this.isDisposed()) {
                                    return Mono.error(new CancellationException("reload was superseded or disposed"));
                                }
                                return Objects.requireNonNull(reload0(), "reload publisher");
                            }
                        }));
                } catch (Throwable e) {
                    this.onError(e);
                    return;
                }
            }
            reload0.switchIfEmpty(Mono.error(new IllegalStateException("reload returned no runtime")))
                .doOnDiscard(Lifecycle.class, ManagedLifecycle.this::tryDispose)
                .subscribe(this);
        }

        @Override
        public void disposeNotReload() { dispose(); }

        void cancelByCaller() { callerCancelled = true; dispose(); }
    }

    public interface ReloadDisposable extends Disposable {
        void disposeNotReload();

        static void disposeNotReload(Object disposableMaybe) {
            if (disposableMaybe instanceof ManagedLifecycle.ReloadDisposable) {
                ((ReloadDisposable) disposableMaybe).disposeNotReload();
            }
        }
    }

    @Override
    public Mono<State> checkState() {
        return tryLoad().flatMap(Lifecycle::checkState);
    }

    public State managedState() {
        if (isDisposed()) {
            return CollectorConstants.States.stopped;
        }
        State managedState = MANAGED_STATE.get(this);
        return managedState == null ? state() : managedState;
    }

    @Override
    public State state() {
        if (isDisposed()) {
            return CollectorConstants.States.stopped;
        }
        T loaded = this.loaded;
        return loaded == null ? CollectorConstants.States.starting : loaded.state();
    }

    public void start() {
        if (!isDisposed() && MANAGED_STATE.getAndSet(this, null) == CollectorConstants.States.paused) {
            T loaded = this.loaded;
            if (loaded != null) {
                loaded.start();
            }
        }
    }

    @Override
    public void pause() {
        MANAGED_STATE.set(this, CollectorConstants.States.paused);
        T loaded = this.loaded;
        if (loaded != null) {
            loaded.pause();
        }
        pause.update(null);
    }

    @Override
    public <X> X unwrap(Class<X> type) {
        return loaded == null ? type.cast(this) : loaded.unwrap(type);
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return loaded == null ? type.isInstance(this) : loaded.isWrapperFor(type);
    }

    public boolean isPaused() {
        return MANAGED_STATE.get(this) == CollectorConstants.States.paused;
    }

    @Override
    public boolean isDisposed() {
        return disposable.isDisposed();
    }

    @Override
    public void dispose() {
        synchronized (this) {
            disposable.dispose();
            ReloadDisposable.disposeNotReload(LOADING.get(this));
            tryDispose(LOADED.getAndSet(this, null));
        }
    }

    private void tryDispose(Object value) {
        if (value instanceof Disposable) {
            previousCloseResult = LifecycleResult.unknown;
            ((Disposable) value).dispose();
        }
    }

    @Override
    public Disposable onStateChanged(BiConsumer<State, State> listener) {
        synchronized (this) {
            List<BiConsumer<State, State>> listeners = this.stateListener;
            if (listeners == null) {
                listeners = new LinkedList<>();
                this.stateListener = listeners;
            }
            listeners.add(listener);

            return () -> {
                synchronized (this) {
                    List<BiConsumer<State, State>> _listeners = this.stateListener;
                    _listeners.remove(listener);
                    if (_listeners.isEmpty()) {
                        this.stateListener = null;
                    }
                }
            };
        }
    }


    @Override
    public void accept(State before, State after) {
        List<BiConsumer<State, State>> listeners = this.stateListener;
        if (listeners != null) {
            for (BiConsumer<State, State> listener : listeners) {
                listener.accept(before, after);
            }
        }
    }
}
