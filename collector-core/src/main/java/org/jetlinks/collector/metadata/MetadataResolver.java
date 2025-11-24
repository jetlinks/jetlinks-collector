package org.jetlinks.collector.metadata;

import org.jetlinks.collector.PointMetadata;
import org.jetlinks.collector.PointProperties;
import reactor.core.publisher.Mono;

/**
 * 元数据解析器,用于根据配置解析出点位相关元数据.
 */
public interface MetadataResolver {

    /**
     * 将点位配置信息解析出点位元数据,用于描述点位信息.
     * <p>
     * 如果配置不全,则返回{@link Mono#empty()}
     *
     * @param properties 配置信息
     * @return 点位信息
     */
    Mono<PointMetadata> resolvePointMetadata(PointProperties properties);

}