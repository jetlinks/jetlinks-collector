package org.jetlinks.collector.exception;

import org.jetlinks.collector.InternalStatusCode;

/**
 * 编解码失败。插件知道字节无法按点位规则编码或解码时抛出，并带上平台状态码和原因键。
 *
 * @since 1.0
 */
public class CodecException extends CollectorFailure {

    public CodecException(String reason, Throwable cause, Object... args) {
        super(reason, 0L, cause, args);
    }

    public CodecException(InternalStatusCode status, String reason, Throwable cause, Object... args) {
        super(status, reason, cause, args);
    }

    public CodecException(InternalStatusCode status, String reason, Object... args) {
        super(status, reason, args);
    }

}
