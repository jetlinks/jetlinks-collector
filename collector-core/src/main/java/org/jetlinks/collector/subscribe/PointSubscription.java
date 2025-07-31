package org.jetlinks.collector.subscribe;

import reactor.core.Disposable;

import java.util.Collection;

public interface PointSubscription extends Disposable {

    static PointSubscription unsupported() {
        return UnsupportedPointSubscription.INSTANCE;
    }

    boolean subscribed(String pointId);

    void subscribe(Collection<String> points);

    void unsubscribe(Collection<String> pointId);

    void reload();

}
