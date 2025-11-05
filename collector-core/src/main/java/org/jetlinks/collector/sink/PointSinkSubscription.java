package org.jetlinks.collector.sink;

import org.jetlinks.collector.address.PointAddress;
import reactor.core.Disposable;

import java.util.Collection;

public interface PointSinkSubscription extends Disposable {

    void subscribeAll();

    boolean subscribed(PointAddress address);

    void subscribe(Collection<PointAddress> address);

    void update(Collection<PointAddress> address);

    void unsubscribe(Collection<PointAddress> address);

    void reload();
}
