package org.jetlinks.collector.sink;

import org.jetlinks.collector.address.PointAddress;
import org.jetlinks.collector.PointData;
import org.jetlinks.collector.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface PointDataOperations {

    /**
     * 获取历史数据
     *
     * @param address 地址
     * @param from    起始时间戳(毫秒)
     * @param to      截止时间戳(毫秒)
     * @return PublisherData
     */
    Flux<PointData> history(PointAddress address, long from, long to);

    Mono<PointData> read(PointAddress address);

    Flux<Result<MappedPointData>> read(Collection<PointAddress> addresses);

    Mono<Result<PointData>> write(PointAddress address, PointData data);

    Flux<Result<MappedPointData>> write(Collection<MappedPointData> data);

    // 创建数据订阅
    PointSinkSubscription createSubscription(String subscriptionId, PointSink sink);

}
