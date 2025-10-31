package org.jetlinks.collector;

import org.jetlinks.collector.command.GetChannelConfigMetadataCommand;
import org.jetlinks.collector.command.GetCollectorConfigMetadataCommand;
import org.jetlinks.collector.command.GetPointConfigMetadataCommand;
import org.jetlinks.collector.discovery.DiscoveryPointCommand;
import org.jetlinks.collector.metadata.MetadataResolver;
import org.jetlinks.collector.subscribe.PointListener;
import org.jetlinks.collector.subscribe.PointSubscription;
import org.jetlinks.core.Wrapper;
import org.jetlinks.core.command.Command;
import org.jetlinks.core.command.CommandSupport;
import org.jetlinks.core.metadata.Feature;
import org.jetlinks.core.monitor.Monitor;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 针对数据采集的支持,用于平台主动采集的场景,如: 定时采集modbus数据等.
 * <p>
 * 数采集由通道 、采集器 、点位组成.
 * <p>
 * 通道 {@link ChannelRuntime} 用于实现一个具体的数据通道，如: 一个modbus主站，一个TCP客户端，一个数据库连接等.
 * <p>
 * 采集器 {@link CollectorRuntime} 用于实现对于一个通道的采集逻辑，负责对点位进行相关操作.如: 一个SQL查询语句，一个http请求.
 * <p>
 * 点位 {@link PointRuntime} 用于针对一个具体的数据点进行操作,如: 读取数据，写入数据等.
 *
 * @author zhouhao
 * @see GetChannelConfigMetadataCommand
 * @see GetCollectorConfigMetadataCommand
 * @see GetPointConfigMetadataCommand
 * @since 1.2.3
 */
public interface DataCollectorProvider extends CommandSupport {

    /**
     * 数据采集提供商标识
     *
     * @return 标识
     */
    String getId();

    /**
     * 数据采集提供商名称
     *
     * @return 名称
     */
    String getName();

    /**
     * 创建通道
     *
     * @param configuration 通道配置
     * @return 通道运行时
     */
    Mono<ChannelRuntime> createChannel(ChannelConfiguration configuration);

    /**
     * 创建采集器
     *
     * @param configuration 采集器配置
     * @return 采集器运行时
     */
    Mono<CollectorRuntime> createCollector(CollectorConfiguration configuration);

    /**
     * 创建点位运行时
     *
     * @param configuration 点位配置
     * @return 点位运行时
     */
    Mono<PointRuntime> createPoint(PointConfiguration configuration);

    /**
     * 元数据解析器
     * @return MetadataResolver
     */
    MetadataResolver metadataResolver();

    /**
     * 获取采集器的特性信息
     *
     * @return 特性信息
     * @see org.jetlinks.collector.CollectorConstants.CollectorFeatures
     */
    default Set<? extends Feature> getFeatures() {
        return Collections.emptySet();
    }



    interface ChannelConfiguration {

        /**
         * 获取通道配置
         *
         * @return 通道配置
         */
        ChannelProperties getProperties();

        /**
         * 监控器
         *
         * @return 监控器
         * @see Monitor#logger()
         * @see Monitor#tracer()
         */
        Monitor monitor();

        /**
         * 获取通道下的所有采集器
         *
         * @return 采集器运行时
         */
        Flux<CollectorRuntime> collectors();
    }

    interface CollectorConfiguration {

        /**
         * 获取通道配置
         *
         * @return 通道配置
         */
        CollectorProperties getProperties();

        /**
         * 创建通道监控器
         *
         * @return 监控器
         */
        Monitor monitor();

        /**
         * 通道运行时
         *
         * @return 通道运行时
         */
        ChannelRuntime channel();

        /**
         * 获取采集器下所有的点位运行时
         *
         * @return 点位
         */
        Flux<PointRuntime> points();

        /**
         * 获取采集器下的点位运行时
         *
         * @param id 点位ID
         * @return 点位
         * @see PointRuntime#getId()
         */
        Mono<PointRuntime> point(String id);
    }

    interface PointConfiguration {
        /**
         * 获取通道配置
         *
         * @return 通道配置
         */
        PointProperties getProperties();

        /**
         * 监控器
         *
         * @return 监控器
         * @see Monitor#logger()
         * @see Monitor#tracer()
         */
        Monitor monitor();

        /**
         * 获取通道运行时
         *
         * @return ChannelRuntime
         */
        ChannelRuntime channel();

        /**
         * 获取采集器运行时
         *
         * @return CollectorRuntime
         */
        CollectorRuntime collector();
    }

    /**
     * 通道运行时,用于执行通信等操作.
     *
     * @author zhouhao
     * @since 1.2.3
     */
    interface ChannelRuntime extends Lifecycle, CommandSupport {

        /**
         * 获取通道ID
         *
         * @return 通道ID
         */
        String getId();

        /**
         * 测试,返回健康度.
         *
         * @return 测试结果
         * @see Result#getCode()
         */
        default Mono<Result<Health>> test() {
            return Mono.just(Result.success(Health.ok()));
        }

    }

    /**
     * 数据采集器运行时，用于执行采集逻辑.
     *
     * @author zhouhao
     * @see DiscoveryPointCommand
     * @since 1.2.3
     */
    interface CollectorRuntime extends Lifecycle, CommandSupport {

        /**
         * 执行采集器的命令
         *
         * @param command 命令
         * @param <R>     结果类型
         * @return 执行结果
         */
        @Nonnull
        @Override
        <R> R execute(@Nonnull Command<R> command);

        /**
         * 创建订阅,用于订阅点位数据等.
         *
         * @param listener 点位监听器
         * @return 订阅
         * @see AccessMode#subscribe
         * @see PointSubscription#subscribe(Collection)
         * @see PointSubscription#unsubscribe(Collection)
         * @see PointSubscription#subscribed(String)
         */
        default PointSubscription createSubscription(PointListener listener) {
            return PointSubscription.unsupported();
        }

        /**
         * 采集指定的点位数据. 用于主动获取点位数据，如定时获取等.
         *
         * @return 点位数据
         * @see PointRuntime#read()
         * @see AccessMode#read
         */
        Flux<Result<PointData>> collect(List<? extends PointRuntime> points);

        /**
         * 获取采集器支持的特性
         *
         * @return 特性
         * @see CollectorConstants.CollectorFeatures
         */
        Set<? extends Feature> getFeatures();

        /**
         * 将点位配置信息解析出点位元数据,用于描述点位信息.
         * <p>
         * 如果配置不全,则返回{@link Mono#empty()}
         *
         * @param properties 配置信息
         * @return 点位信息
         */
        Mono<PointMetadata> resolvePointMetadata(PointProperties properties);

        /**
         * 判断是否支持特性
         *
         * @param feature 特性
         * @return 支持：true，不支持：false。
         */
        default boolean hasFeature(Feature feature) {
            return getFeatures().contains(feature);
        }

        /**
         * 测试,返回健康度.
         *
         * @return 测试结果
         * @see Result#getCode()
         */
        default Mono<Result<Health>> test() {
            return Mono.just(Result.success(Health.ok()));
        }
    }

    /**
     * 点位运行时
     *
     * @since 1.2.3
     */
    interface PointRuntime extends CommandSupport, Lifecycle {

        /**
         * 点位ID
         *
         * @return 点位ID
         */
        String getId();

        /**
         * 测试点位,返回点位健康度.
         *
         * @return 测试结果
         * @see Result#getCode()
         */
        Mono<Result<Health>> test();

        /**
         * 读取点位数据
         *
         * @return 点位数据
         */
        Mono<Result<PointData>> read();

        /**
         * 写入点位数据
         *
         * @param data 点位数据
         * @return 点位数据
         */
        Mono<Result<PointData>> write(PointData data);

        /**
         * 获取排序序号,值越小,越在前. 影响{@link CollectorRuntime#collect(List)}参数的顺序.
         *
         * @return 序号
         * @see CollectorRuntime#collect(List)
         */
        default int getOrder() {
            return Integer.MAX_VALUE;
        }
    }

}
