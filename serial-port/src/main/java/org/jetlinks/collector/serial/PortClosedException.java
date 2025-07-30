package org.jetlinks.collector.serial;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;

@AllArgsConstructor
@Getter
public class PortClosedException extends IOException {

    private final String port;

    @Override
    public String getMessage() {
        return "SerialPort [" + port + "] Closed";
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
