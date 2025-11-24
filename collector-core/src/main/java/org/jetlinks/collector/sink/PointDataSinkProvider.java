package org.jetlinks.collector.sink;

import org.jetlinks.collector.Lifecycle;
import org.jetlinks.collector.PointDescriptor;
import org.jetlinks.core.command.Command;
import org.jetlinks.core.command.CommandSupport;
import org.jetlinks.core.monitor.Monitor;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;

/**
 * 数据发布提供商,用于将平台数据发布到其他地方,如: 将平台的设备、数采数据以modbus tcp方式发布.
 * <p>
 * 名词解释
 * <ul>
 *     <li>ChannelRuntime(通道): 通常用于管理数据发布的通信</li>
 *     <li>SinkRuntime(数据接收者): 用于接收平台的数据</li>
 * </ul>
 *
 * @author zhouhao
 * @since 1.0.1
 */
public interface PointDataSinkProvider extends CommandSupport {

    /**
     * ID
     *
     * @return ID
     */
    String getId();

    /**
     * 名称
     *
     * @return 名称
     */
    String getName();

    @Nonnull
    @Override
    <R> R execute(@Nonnull Command<R> command);

    /**
     * 创建一个通道,通常用于管理数据通信方式.
     *
     * @param configuration configuration
     * @return ChannelRuntime
     */
    Mono<ChannelRuntime> createChannel(ChannelConfiguration configuration);


    /**
     * 创建一个数据接收点运行时. 用于数据的读写订阅处理.
     *
     * @param configuration configuration
     * @return SinkRuntime
     */
    Mono<SinkRuntime> createSink(SinkConfiguration configuration);

    /**
     * 创建一个数据点描述.
     *
     * @param properties properties
     * @return PointDescriptor
     */
    Mono<PointDescriptor> createPoint(SinkPointDescriberProperties properties);


    interface SinkRuntime extends Lifecycle, CommandSupport {

    }

    interface ChannelRuntime extends CommandSupport, Lifecycle {

        @Nonnull
        @Override
        <R> R execute(@Nonnull Command<R> command);

    }


    interface ChannelConfiguration {

        ChannelProperties properties();

        // 监控接口
        Monitor monitor();

    }

    interface SinkConfiguration {

        SinkProperties properties();

        // 监控接口
        Monitor monitor();

        /**
         * 数据源操作接口
         *
         * @return 数据源操作接口
         */
        PointDataOperations opsForData();

        /**
         * 获取通道
         *
         * @return 通道
         */
        ChannelRuntime channel();
    }
}
