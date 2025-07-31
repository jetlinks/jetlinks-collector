package org.jetlinks.collector.test;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.collector.ChannelProperties;
import org.jetlinks.collector.CollectorProperties;
import org.jetlinks.collector.DataCollectorProvider;
import org.jetlinks.collector.PointProperties;
import org.jetlinks.core.monitor.DefaultMonitor;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.logger.Slf4jLogger;
import org.jetlinks.core.monitor.metrics.Metrics;
import org.jetlinks.core.monitor.tracer.LogTracer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TestDataCollectorManager {

    private final DataCollectorProvider provider;

    private final Map<String, DataCollectorProvider.CollectorRuntime> collectors = new ConcurrentHashMap<>();
    @Getter
    private final DataCollectorProvider.ChannelRuntime channel;
    private final Map<DataCollectorProvider.CollectorRuntime, Map<String, DataCollectorProvider.PointRuntime>> points = new ConcurrentHashMap<>();


    public TestDataCollectorManager(DataCollectorProvider provider, Object channelProperties) {
        this.provider = provider;
        this.channel = createChannel("test", channelProperties);
    }


    public DataCollectorProvider.PointRuntime createPoint(DataCollectorProvider.CollectorRuntime collector,
                                                          String id,
                                                          Object pointProperties) {
        PointProperties properties = new PointProperties();
        properties.setId(id);
        properties.setConfiguration(FastBeanCopier.copy(pointProperties, new HashMap<>()));

        Monitor monitor = new DefaultMonitor(
            new Slf4jLogger(log),
            new LogTracer(log.getName()),
            Metrics.noop()
        );
        DataCollectorProvider.PointRuntime runtime = provider
            .createPoint(new DataCollectorProvider.PointConfiguration() {

                @Override
                public PointProperties getProperties() {
                    return properties;
                }

                @Override
                public Monitor monitor() {
                    return monitor;
                }

                @Override
                public DataCollectorProvider.ChannelRuntime channel() {
                    return channel;
                }

                @Override
                public DataCollectorProvider.CollectorRuntime collector() {
                    return collector;
                }
            })
            .block();
        points.computeIfAbsent(collector, ignore -> new ConcurrentHashMap<>())
              .put(id, runtime);
        return runtime;
    }

    public DataCollectorProvider.CollectorRuntime createCollector(String id, Object collectorProperties) {
        CollectorProperties properties = new CollectorProperties();
        properties.setId(id);
        properties.setConfiguration(FastBeanCopier.copy(collectorProperties, new HashMap<>()));

        Monitor monitor = new DefaultMonitor(
            new Slf4jLogger(log),
            new LogTracer(log.getName()),
            Metrics.noop()
        );
        DataCollectorProvider.CollectorRuntime[] collector = new DataCollectorProvider.CollectorRuntime[1];
        collector[0] =
            provider
                .createCollector(new DataCollectorProvider.CollectorConfiguration() {
                    @Override
                    public CollectorProperties getProperties() {
                        return properties;
                    }

                    @Override
                    public Monitor monitor() {
                        return monitor;
                    }

                    @Override
                    public DataCollectorProvider.ChannelRuntime channel() {
                        return channel;
                    }

                    @Override
                    public Flux<DataCollectorProvider.PointRuntime> points() {
                        return Mono
                            .justOrEmpty(points.get(collector[0]))
                            .flatMapIterable(Map::values);
                    }

                    @Override
                    public Mono<DataCollectorProvider.PointRuntime> point(String id) {
                        return Mono.justOrEmpty(points.get(collector[0]))
                                   .mapNotNull(map -> map.get(id));
                    }
                })
                .block();

        return collector[0];

    }

    public DataCollectorProvider.ChannelRuntime createChannel(String id, Object channelProperties) {
        ChannelProperties properties = new ChannelProperties();
        properties.setId(id);
        properties.setConfiguration(FastBeanCopier.copy(channelProperties, new HashMap<>()));

        Monitor monitor = new DefaultMonitor(
            new Slf4jLogger(log),
            new LogTracer(log.getName()),
            Metrics.noop()
        );
        return provider
            .createChannel(new DataCollectorProvider.ChannelConfiguration() {
                @Override
                public ChannelProperties getProperties() {
                    return properties;
                }

                @Override
                public Monitor monitor() {
                    return monitor;
                }

                @Override
                public Flux<DataCollectorProvider.CollectorRuntime> collectors() {
                    return Flux.fromIterable(collectors.values());
                }
            })
            .block();

    }
}
