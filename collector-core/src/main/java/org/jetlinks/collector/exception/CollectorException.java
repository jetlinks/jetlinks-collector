package org.jetlinks.collector.exception;

import lombok.Getter;
import org.jetlinks.collector.InternalStatusCode;

/**
 * 采集器级失败。插件知道采集器已释放、未运行或设备返回了可识别的错误时抛出。
 *
 * @since 1.0
 */
@Getter
public class CollectorException extends CollectorFailure {

    private final String collectorId;

    public CollectorException(String collectorId,
                              String messageOrI18nCode,
                              Throwable cause,
                              long code,
                              Object... args) {
        super(messageOrI18nCode, code, cause, args);
        this.collectorId = collectorId;
    }

    public CollectorException(String collectorId,
                              InternalStatusCode status,
                              String reason,
                              Object... args) {
        super(status, reason, args);
        this.collectorId = collectorId;
    }

    public CollectorException(String collectorId,
                              InternalStatusCode status,
                              String reason,
                              Throwable cause,
                              Object... args) {
        super(status, reason, cause, args);
        this.collectorId = collectorId;
    }

    public long getCode() {
        return getStatusCode();
    }

}
