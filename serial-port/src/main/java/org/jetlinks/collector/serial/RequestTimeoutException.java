package org.jetlinks.collector.serial;

import java.util.concurrent.TimeoutException;

public class RequestTimeoutException extends TimeoutException {

    @Override
    public synchronized Throwable fillInStackTrace() {
        return  this;
    }
}
