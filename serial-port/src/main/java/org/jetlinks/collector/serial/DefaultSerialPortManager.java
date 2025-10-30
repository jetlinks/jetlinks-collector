package org.jetlinks.collector.serial;

import com.fazecast.jSerialComm.SerialPort;
import reactor.core.publisher.Flux;

public class DefaultSerialPortManager implements SerialPortManager {
    static final SerialPortManager INSTANCE = new DefaultSerialPortManager();

    public static SerialPortManager instance() {
        return INSTANCE;
    }

    @Override
    public Flux<SerialPortInfo> getAllAlivePort() {
        return Flux
            .fromArray(SerialPort.getCommPorts())
            .map(port->{
                SerialPortInfo portInfo = new SerialPortInfo();
                portInfo.setId(port.getSystemPortPath());
                portInfo.setName(port.getSystemPortName());
                return portInfo;
            });
    }

}
