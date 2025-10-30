package org.jetlinks.collector.serial;

import reactor.core.publisher.Flux;

public interface SerialPortManager {

    Flux<SerialPortInfo> getAllAlivePort();

}
