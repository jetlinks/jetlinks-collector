package org.jetlinks.collector;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class StatusCodes {

    private final static Map<Long, StatusCode> ALL_CODES = new ConcurrentHashMap<>();

    static {
        for (InternalStatusCode value : InternalStatusCode.values()) {
            ALL_CODES.put(value.getCode(), value);
        }
    }


    public static Optional<StatusCode> findCode(long code) {
        return Optional.of(ALL_CODES.get(code));
    }

    public static StatusCode of(long code) {
        StatusCode statusCode = ALL_CODES.get(code);
        if (statusCode != null) {
            return statusCode;
        }
        return new UndefinedStatusCode(code);
    }

}
