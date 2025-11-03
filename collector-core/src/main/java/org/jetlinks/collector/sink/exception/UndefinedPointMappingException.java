package org.jetlinks.collector.sink.exception;

import lombok.Getter;
import org.hswebframework.web.exception.I18nSupportException;
import org.jetlinks.collector.address.PointAddress;

@Getter
public class UndefinedPointMappingException extends I18nSupportException.NoStackTrace {

    private final PointAddress address;

    public UndefinedPointMappingException(PointAddress address) {
        super("error.collector.sink.undefined_point_mapping", address);
        this.address = address;
    }
}
