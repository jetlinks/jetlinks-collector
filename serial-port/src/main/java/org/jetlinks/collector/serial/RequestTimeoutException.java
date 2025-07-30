package org.jetlinks.collector.serial;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.concurrent.TimeoutException;

@Getter
@AllArgsConstructor
public class RequestTimeoutException extends TimeoutException {

    private final String port;

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
