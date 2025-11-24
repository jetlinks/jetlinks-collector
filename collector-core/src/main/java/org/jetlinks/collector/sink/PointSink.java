package org.jetlinks.collector.sink;

import org.jetlinks.collector.StatusCode;
import org.jetlinks.collector.address.PointAddress;
import org.jetlinks.collector.PointData;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PointSink {

    Mono<Void> onDataReceived(PointAddress address, PointData data);

    Mono<Void> onDataReceived(PointAddress address, List<PointData> data);

   default void onSubscribeFailure(PointAddress address, StatusCode code){

   }
}
