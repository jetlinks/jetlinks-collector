package org.jetlinks.collector.sink.source;

import org.jetlinks.collector.PointDescriptor;
import org.jetlinks.collector.sink.PointDataSource;
import org.jetlinks.core.monitor.Monitor;
import reactor.core.publisher.Mono;

public interface PointDataSourceProvider {

    String getId();

    String getName();

    Mono<PointDataSource> createDataSource(PointDataSourceConfiguration configuration);

    Mono<PointDescriptor> createPointDescriptor(SourcePointDescriberProperties configuration);




    interface PointDataSourceConfiguration {

        Monitor monitor();

        PointDataSourceProperties properties();


    }


}
