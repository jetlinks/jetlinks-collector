package org.jetlinks.collector.subscribe;

import org.jetlinks.collector.PointData;
import org.jetlinks.collector.Result;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PointListener {

    void onDataReceived(PointData data);

    void onDataReceived(List<PointData> data);

    void onDataError(String pointId, Result<?> result);

    void onSubscribeFailed(String pointId,Throwable error);
}
