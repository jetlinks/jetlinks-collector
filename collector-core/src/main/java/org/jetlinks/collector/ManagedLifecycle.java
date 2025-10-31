package org.jetlinks.collector;

import jakarta.annotation.Nonnull;
import org.jetlinks.core.command.AsyncProxyCommandSupport;
import org.jetlinks.core.command.CommandSupport;
import org.jetlinks.core.command.ProxyCommandSupport;
import org.jetlinks.core.utils.Reactors;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.*;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
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
        loaded.start();

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
                if (AWAIT.compareAndSet(this, null, loading = Sinks.one())) {
                    Sinks.One<T> finalLoading = loading;
                    return loading
                        .asMono()
                        .timeout(Duration.ofSeconds(10),
                                 Mono.fromSupplier(() -> this.loaded))
                        .doOnError(err -> AWAIT.compareAndSet(this, finalLoading, null));
                }
            }
        }
    }

    protected abstract Mono<T> reload0();

    public Mono<Void> reload() {
        return reload(runtimeConfig, managedConfig);
    }

    public final Mono<Void> reload(RC runtimeConfig,
                                   MC managedConfig) {

        if (isDisposed()) {
            return Mono.empty();
        }

        return Mono
            .create(sink -> {
                LoadSubscriber subscriber = new LoadSubscriber(runtimeConfig, managedConfig, sink);
                sink.onDispose(subscriber);
                subscriber.reload();
            });
    }

    protected void onLoadError(Throwable e) {

    }

    private class LoadSubscriber extends BaseSubscriber<T> implements ReloadDisposable {

        final RC newRC;
        final MC newMC;
        final T oldLoaded;
        final RC oldRC;
        final MC oldMC;
        MonoSink<Void> signal;

        T newLoaded;
        boolean rollback = true;
        boolean rollbackLoad = true;

        public LoadSubscriber(RC newRC, MC newMC, MonoSink<Void> signal) {
            this.newMC = newMC;
            this.newRC = newRC;
            this.signal = signal;
            ReloadDisposable.disposeNotReload(LOADING.getAndSet(ManagedLifecycle.this, this));
            //配置回滚后赋值
            oldLoaded = loaded;
            oldRC = runtimeConfig;
            oldMC = managedConfig;
            RUNTIME_CONFIG.compareAndSet(ManagedLifecycle.this, oldRC, newRC);
            MANAGED_CONFIG.compareAndSet(ManagedLifecycle.this, oldMC, newMC);
        }

        @Override
        protected void hookOnNext(@Nonnull T value) {
            if (LOADED.compareAndSet(ManagedLifecycle.this, oldLoaded, newLoaded = value)) {
                tryDispose(newLoaded);
                ManagedLifecycle.this.handleLoaded(value);
            }
        }

        @Override
        protected void hookOnComplete() {
            @SuppressWarnings("all")
            Sinks.One<T> await = AWAIT.getAndSet(ManagedLifecycle.this, null);
            if (await != null) {
                @SuppressWarnings("all")
                T loaded = (T) LOADED.get(ManagedLifecycle.this);
                if (loaded != null) {
                    await.emitValue(loaded, Reactors.emitFailureHandler());
                }
            }
        }

        @Override
        protected void hookOnError(@Nonnull Throwable throwable) {
            rollback();
            onLoadError(throwable);
        }

        @Override
        protected void hookOnCancel() {
            rollback();
        }

        private void rollback() {
            if (rollback) {
                // 加载失败 回退配置
                RUNTIME_CONFIG.compareAndSet(ManagedLifecycle.this, newRC, oldRC);
                MANAGED_CONFIG.compareAndSet(ManagedLifecycle.this, newMC, oldMC);
                LOADED.compareAndSet(ManagedLifecycle.this, newLoaded, oldLoaded);
                try {
                    tryDispose(newLoaded);
                    if (rollbackLoad) {
                        Lifecycle old = LOADED.get(ManagedLifecycle.this);
                        if (old != null && old == this.oldLoaded) {
                            MonoSink<Void> _signal = this.signal;
                            this.signal = null;
                            //使用回退后的配置重新加载，且不再回退
                            LoadSubscriber reloadSubscriber = new LoadSubscriber(runtimeConfig, managedConfig, _signal);
                            reloadSubscriber.rollback = false;
                            reloadSubscriber.reload();
                        }
                    }
                } catch (Throwable e) {
                    onLoadError(e);
                }
            }
        }

        void reload() {
            Mono<T> reload0;
            try {
                reload0 = reload0();
            } catch (Throwable e) {
                this.hookOnError(e);
                return;
            }
            reload0.subscribe(this);
        }

        @Override
        protected void hookFinally(@Nonnull SignalType type) {
            LOADING.compareAndSet(ManagedLifecycle.this, this, null);
            if (signal != null) {
                signal.success();
            }
        }

        @Override
        public void disposeNotReload() {
            rollbackLoad = false;
            this.dispose();
        }
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
        if (isPaused()) {
            return CollectorConstants.States.paused;
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

    }

    @Override
    public void pause() {
        T loaded = this.loaded;
        if (loaded != null) {
            loaded.pause();
        } else {
            tryLoad()
                .subscribe(Lifecycle::pause);
        }
        pause.update(Disposables.disposed());
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
        return pause.isDisposed();
    }

    @Override
    public boolean isDisposed() {
        return disposable.isDisposed();
    }

    @Override
    public void dispose() {
        disposable.dispose();
        ReloadDisposable.disposeNotReload(LOADING.getAndSet(this, null));
        tryDispose(LOADED.getAndSet(this, null));
    }

    private void tryDispose(Object value) {
        if (value instanceof Disposable) {
            ((Disposable) value).dispose();
            ;
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
