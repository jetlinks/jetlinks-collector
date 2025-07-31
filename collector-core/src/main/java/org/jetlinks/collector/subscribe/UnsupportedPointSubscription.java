package org.jetlinks.collector.subscribe;

import java.util.Collection;

class UnsupportedPointSubscription implements PointSubscription {

    static final UnsupportedPointSubscription INSTANCE = new UnsupportedPointSubscription();

    @Override
    public boolean subscribed(String pointId) {
        return false;
    }

    @Override
    public void subscribe(Collection<String> points) {

    }

    @Override
    public void unsubscribe(Collection<String> pointId) {

    }

    @Override
    public void reload() {

    }

    @Override
    public void dispose() {

    }
}
