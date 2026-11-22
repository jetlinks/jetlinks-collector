package org.jetlinks.collector;

import io.netty.buffer.ByteBufAllocator;
import lombok.AllArgsConstructor;
import org.jetlinks.collector.command.GetEditorResourceCommand;
import org.jetlinks.collector.metadata.MetadataResolver;
import org.jetlinks.core.annotation.command.CommandHandler;
import org.jetlinks.collector.command.GetChannelConfigMetadataCommand;
import org.jetlinks.collector.command.GetCollectorConfigMetadataCommand;
import org.jetlinks.collector.command.GetPointConfigMetadataCommand;
import org.jetlinks.core.command.CommandMetadataResolver;
import org.jetlinks.core.metadata.PropertyMetadata;
import org.jetlinks.supports.command.AnnotationCommandSupport;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@AllArgsConstructor
public abstract class AbstractDataCollectorProvider extends AnnotationCommandSupport implements DataCollectorProvider,
    MetadataResolver {
    protected final Class<?> channelConfigType;
    protected final Class<?> collectorConfigType;
    protected final Class<?> pointConfigType;


    @Override
    public MetadataResolver metadataResolver() {
        return this;
    }

    @Override
    public Mono<PointMetadata> resolvePointMetadata(PointProperties properties) {
        return Mono.empty();
    }

    protected Flux<DataBuffer> getEditorResource(String path, String provider, Class<?> clazz) {
        // 获取默认静态资源
        // /resources/{provider}/{path}
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        ClassPathResource resource = new ClassPathResource(provider + path, clazz.getClassLoader());
        // 归一化后的路径必须留在插件目录内；越界和缺失资源均交由接口返回404。
        if (!resource.getPath().startsWith(provider + "/") || !resource.exists()) {
            return Flux.empty();
        }
        return DataBufferUtils
            .read(resource,
                  new NettyDataBufferFactory(ByteBufAllocator.DEFAULT),
                  4096);
    }

    @CommandHandler
    public Flux<DataBuffer> getEditorResource(GetEditorResourceCommand command) {
        return getEditorResource(command.getPath(), getId(), this.getClass());
    }

    @CommandHandler
    public Mono<List<PropertyMetadata>> getChannelConfigProperties(GetChannelConfigMetadataCommand command) {
        List<PropertyMetadata> metadataList = CommandMetadataResolver.resolveInputs(ResolvableType.forType(channelConfigType));
        extendChannelConfigMetadata(metadataList);
        return Mono.just(metadataList);
    }

    @CommandHandler
    public Mono<List<PropertyMetadata>> getCollectorConfigProperties(GetCollectorConfigMetadataCommand command) {
        List<PropertyMetadata> metadataList = CommandMetadataResolver.resolveInputs(ResolvableType.forType(collectorConfigType));
        extendCollectorConfigMetadata(metadataList);
        return Mono.just(metadataList);
    }

    @CommandHandler
    public Mono<List<PropertyMetadata>> getPointConfigProperties(GetPointConfigMetadataCommand command) {
        List<PropertyMetadata> metadataList = CommandMetadataResolver.resolveInputs(ResolvableType.forType(pointConfigType));
        extendPointConfigMetadata(metadataList);
        return Mono.just(metadataList);
    }

    /**
     * 扩展通道配置元数据，子类可覆盖此方法为PropertyMetadata添加额外信息
     *
     * @param metadataList 配置元数据列表
     */
    protected void extendChannelConfigMetadata(List<PropertyMetadata> metadataList) {

    }

    /**
     * 扩展采集器配置元数据，子类可覆盖此方法为PropertyMetadata添加额外信息
     *
     * @param metadataList 配置元数据列表
     */
    protected void extendCollectorConfigMetadata(List<PropertyMetadata> metadataList) {

    }

    /**
     * 扩展点位配置元数据，子类可覆盖此方法为PropertyMetadata添加额外信息
     *
     * @param metadataList 配置元数据列表
     */
    protected void extendPointConfigMetadata(List<PropertyMetadata> metadataList) {

    }
}
