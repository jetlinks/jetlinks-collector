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
        return DataBufferUtils
            .read(new ClassPathResource(provider + path, clazz.getClassLoader()),
                  new NettyDataBufferFactory(ByteBufAllocator.DEFAULT),
                  4096);
    }

    @CommandHandler
    public Flux<DataBuffer> getEditorResource(GetEditorResourceCommand command) {
        return getEditorResource(command.getPath(), getId(), this.getClass());
    }

    @CommandHandler
    public Mono<List<PropertyMetadata>> getChannelConfigProperties(GetChannelConfigMetadataCommand command) {
        return Mono.just(
            CommandMetadataResolver.resolveInputs(ResolvableType.forType(channelConfigType))
        );
    }

    @CommandHandler
    public Mono<List<PropertyMetadata>> getCollectorConfigProperties(GetCollectorConfigMetadataCommand command) {
        return Mono.just(
            CommandMetadataResolver.resolveInputs(ResolvableType.forType(collectorConfigType))
        );
    }

    @CommandHandler
    public Mono<List<PropertyMetadata>> getPointConfigProperties(GetPointConfigMetadataCommand command) {
        return Mono.just(
            CommandMetadataResolver.resolveInputs(ResolvableType.forType(pointConfigType))
        );
    }
}
