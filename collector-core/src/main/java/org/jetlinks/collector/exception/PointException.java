package org.jetlinks.collector.exception;

import lombok.Getter;
import org.jetlinks.collector.InternalStatusCode;

/**
 * 点位级失败。插件知道点位已释放、不可读或不可写时抛出，并带上平台状态码和原因键。
 *
 * @since 1.0
 */
@Getter
public class PointException extends CollectorFailure {

    private final String pointId;

    public PointException(String pointId,
                          String reason,
                          Object... args) {
        super(reason, 0L, null, args);
        this.pointId = pointId;
    }

    public PointException(String pointId,
                          String reason,
                          Throwable cause,
                          Object... args) {
        super(reason, 0L, cause, args);
        this.pointId = pointId;
    }

    public PointException(String pointId,
                          InternalStatusCode status,
                          String reason,
                          Object... args) {
        super(status, reason, args);
        this.pointId = pointId;
    }

    public PointException(String pointId,
                          InternalStatusCode status,
                          String reason,
                          Throwable cause,
                          Object... args) {
        super(status, reason, cause, args);
        this.pointId = pointId;
    }

}
