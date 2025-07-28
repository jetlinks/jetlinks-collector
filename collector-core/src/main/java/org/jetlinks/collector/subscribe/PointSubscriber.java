package org.jetlinks.collector.subscribe;

import org.jetlinks.collector.DataCollectorProvider;
import org.jetlinks.collector.PointData;
import reactor.core.publisher.Mono;

public interface PointSubscriber  {

    Mono<Void> next(PointData data);

    void setState(DataCollectorProvider.State state);

}
