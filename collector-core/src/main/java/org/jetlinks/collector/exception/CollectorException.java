package org.jetlinks.collector.exception;

import lombok.Getter;
import org.hswebframework.web.exception.I18nSupportException;

@Getter
public class CollectorException extends I18nSupportException.NoStackTrace {

    private final String collectorId;
    private final long code;

    public CollectorException(String collectorId,
                              String messageOrI18nCode,
                              Throwable cause,
                              long code,
                              Object... args) {
        super(messageOrI18nCode, cause, args);
        this.collectorId = collectorId;
        this.code = code;
    }

}
