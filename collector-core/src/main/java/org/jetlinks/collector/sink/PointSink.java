package org.jetlinks.collector.sink;

import org.jetlinks.collector.address.PointAddress;
import org.jetlinks.collector.PointData;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PointSink {

    Mono<Void> onDataReceived(PointAddress descriptor, PointData data);

    Mono<Void> onDataReceived(PointAddress descriptor, List<PointData> data);

}
