package org.jetlinks.collector;

import org.jetlinks.supports.command.AnnotationCommandSupport;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;

public abstract class AbstractLifecycle extends AnnotationCommandSupport implements Lifecycle {

    private static final AtomicReferenceFieldUpdater<AbstractLifecycle, State>
        STATE = AtomicReferenceFieldUpdater.newUpdater(AbstractLifecycle.class, State.class, "state");

    private List<BiConsumer<State, State>> stateListener;

    private volatile State state = CollectorConstants.States.initializing;

    private final Composite disposable = Disposables.composite();

    protected abstract void start0();

    protected abstract void stop0();

    protected Mono<State> checkState0() {
        return Mono.just(state());
    }

    @Override
    public final Mono<State> checkState() {
        return checkState0();
    }

    @Override
    public final State state() {
        return STATE.get(this);
    }

    protected final boolean changeState(State expect,
                                        State state) {

        if (STATE.compareAndSet(this, expect, state)) {
            fireListener(expect, state);
            return true;
        }
        return false;
    }

    protected final boolean changeState(State state) {

        State before = STATE.getAndSet(this, state);

        if (before != state) {
            fireListener(before, state);
            return true;
        }
        return false;
    }

    private void fireListener(State before,
                              State after) {

        List<BiConsumer<State, State>> stateListener = this.stateListener;

        if (stateListener != null) {
            for (BiConsumer<State, State> consumer : stateListener) {
                consumer.accept(before, after);
            }
        }

    }

    @Override
    public final void start() {
        if (disposable.isDisposed()) {
            return;
        }
        if (changeState(CollectorConstants.States.starting)) {
            start0();
            changeState(CollectorConstants.States.starting,
                        CollectorConstants.States.running);
        }
    }

    @Override
    public void pause() {
        if (disposable.isDisposed()) {
            return;
        }
        changeState(CollectorConstants.States.paused);
    }

    protected final void doOnStop(Disposable listener) {
        this.disposable.add(listener);
    }

    @Override
    public final Disposable onStateChanged(BiConsumer<State, State> listener) {
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
    public final void dispose() {
        synchronized (this) {
            if (disposable.isDisposed()) {
                return;
            }
            disposable.dispose();
        }
        stop0();
        changeState(CollectorConstants.States.stopped);
    }
}
