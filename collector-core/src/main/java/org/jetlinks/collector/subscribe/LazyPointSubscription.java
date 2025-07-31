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
            _sub.unsubscribe(points);
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
}
