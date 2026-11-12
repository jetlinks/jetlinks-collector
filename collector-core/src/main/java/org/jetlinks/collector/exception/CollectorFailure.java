package org.jetlinks.collector.exception;

import lombok.Getter;
import org.hswebframework.web.exception.I18nSupportException;
import org.jetlinks.collector.InternalStatusCode;

/**
 * 插件已经明确的采集失败。
 *
 * <p>状态码使用 {@link InternalStatusCode}，原因使用 {@code error.*} 或协议已有的国际化键。
 * 调用方不知道原因时不要用这个类型猜测，留给 {@code Result.error(Throwable)} 按异常类型推断。
 *
 * @since 1.0
 */
@Getter
public class CollectorFailure extends I18nSupportException.NoStackTrace {

    private final long statusCode;

    public CollectorFailure(InternalStatusCode status, String reason, Object... args) {
        this(reason, status.getCode(), null, args);
    }

    public CollectorFailure(InternalStatusCode status, String reason, Throwable cause, Object... args) {
        this(reason, status.getCode(), cause, args);
    }

    /**
     * 老构造还没有状态码时传 {@code 0}，结果层再按原因键推断。
     */
    protected CollectorFailure(String reason, long statusCode, Throwable cause, Object... args) {
        super(reason, cause, args);
        this.statusCode = statusCode;
    }

    public InternalStatusCode status() {
        return InternalStatusCode.fromCode(statusCode);
    }
}
