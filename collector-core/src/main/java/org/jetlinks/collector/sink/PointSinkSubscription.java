package org.jetlinks.collector.sink;

import org.jetlinks.collector.address.PointAddress;
import reactor.core.Disposable;

import java.util.Collection;

public interface PointSinkSubscription extends Disposable {

    void subscribeAll();

    boolean subscribed(PointAddress descriptor);

    void subscribe(Collection<PointAddress> descriptors);

    void update(Collection<PointAddress> descriptors);

    void unsubscribe(Collection<PointAddress> descriptors);

    void reload();
}
