package org.jetlinks.collector;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.core.GenericHeaderSupport;
import org.jetlinks.core.utils.ExceptionUtils;
import org.jetlinks.core.utils.SerializeUtils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 数采相关操作结果
 *
 * @param <T>
 */
@Getter
@Setter
public class Result<T> extends GenericHeaderSupport<Result<T>> implements Externalizable {

    @Getter(lombok.AccessLevel.NONE)
    @Setter(lombok.AccessLevel.NONE)
    private volatile boolean headersExposed;

    /**
     * 数据
     */
    private T data;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 状态码，支持组合错误码
     *
     * @see InternalStatusCode
     */
    private long code;

    /**
     * 设置组合错误码
     *
     * @param code 错误码枚举数组，支持多个错误码组合
     * @return 当前Result实例
     */
    public Result<T> withCode(StatusCode code) {
        this.code = code.getCode();
        return this;
    }

    /**
     * 设置错误码
     *
     * @param code 错误码
     * @return 当前Result实例
     */
    public Result<T> withCode(long code) {
        this.code = code;
        return this;
    }

    public Result<T> withPointId(String pointId) {
        return addHeader(CollectorConstants.Headers.pointId, pointId);
    }

    public Result<T> withReason(String reason) {
        return addHeader(CollectorConstants.Headers.reason, reason);
    }

    public static Result<Void> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setData(data);
        result.setCode(InternalStatusCode.Good.getCode());
        return result;
    }

    public static <T> Result<T> error(long code) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(code);
        return result;
    }

    public static <T> Result<T> error(InternalStatusCode code, Throwable error) {
        return error(code.getCode(), error);
    }

    public static <T> Result<T> error(long code, Throwable error) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.addHeader("errorType", error.getClass().getCanonicalName());
        result.errorStack(error);
        result.setCode(code);
        return result;
    }

    public static <T> Result<T> error(StatusCode codes) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.withCode(codes);
        return result;
    }

    public static <T> Result<T> error(Throwable error) {
        return new Result<T>().with(error);
    }

    public Result<T> with(Throwable error) {
        this.setSuccess(false);
        if (error == null) {
            this.setCode(InternalStatusCode.Bad_InternalError.getCode());
            this.withReason("error.internal_error");
            return this;
        }
        this.addHeader("errorType", error.getClass().getCanonicalName());
        this.errorStack(error);
        long code = CollectorUtils.inferErrorCode(error);
        this.withReason(CollectorUtils.reasonOf(error, code));
        this.setCode(code);
        return this;
    }

    public <NEW> Result<NEW> copy(NEW data) {
        Result<NEW> result = new Result<>();
        result.setSuccess(this.isSuccess());
        result.setCode(this.getCode());
        Map<String, Object> headers = super.getHeaders();
        if (headers != null) {
            result.copyHeaders(headers);
        }
        result.setData(data);
        return result;
    }

    /** 单独读取原因/点位不展开堆栈；暴露整个头 Map 或序列化时保持 String 值契约。 */
    @Override
    public Map<String, Object> getHeaders() {
        if (super.getHeaders() == null) {
            return null;
        }
        if (!headersExposed) {
            synchronized (this) {
                if (!headersExposed) {
                    headerValue(super.getHeaders(), "errorStack");
                    headersExposed = true;
                }
            }
        }
        return super.getHeaders();
    }

    @Override
    public synchronized void setHeaders(Map<String, Object> headers) {
        // 外部可能继续持有同一个 ConcurrentHashMap，不能向已暴露的 Map 写入延迟值。
        headersExposed = true;
        super.setHeaders(headers);
    }

    private void copyHeaders(Map<String, Object> headers) {
        super.setHeaders(new ConcurrentHashMap<>(headers));
    }

    private synchronized void errorStack(Throwable error) {
        addHeader("errorStack", headersExposed ? ExceptionUtils.getStackTrace(error) : new ErrorStack(error));
    }

    @Override
    public Object getHeaderOrElse(String header, Supplier<Object> orElse) {
        Object value = header == null ? null : headerValue(super.getHeaders(), header);
        return value == null && orElse != null ? orElse.get() : value;
    }

    private static Object headerValue(Map<String, Object> headers, String key) {
        Object value = headers == null ? null : headers.get(key);
        if (value instanceof ErrorStack stack) {
            String text = stack.get();
            headers.replace(key, value, text);
            return text;
        }
        return value;
    }

    /** 结果副本共享一次计算，完成后释放 Throwable；不建立全局缓存。 */
    private static final class ErrorStack {
        private Throwable error;
        private volatile String text;

        ErrorStack(Throwable error) { this.error = error; }

        String get() {
            String value = text;
            if (value == null) {
                synchronized (this) {
                    value = text;
                    if (value == null) {
                        text = value = ExceptionUtils.getStackTrace(error);
                        error = null;
                    }
                }
            }
            return value;
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        SerializeUtils.writeKeyValue(getHeaders(), out);

        SerializeUtils.writeObject(data, out);
        out.writeBoolean(success);
        out.writeLong(code);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        setHeaders(SerializeUtils.readMap(in, ConcurrentHashMap::new));

        data = (T) SerializeUtils.readObject(in);

        success = in.readBoolean();
        code = in.readLong();
    }

    @Override
    public String toString() {
        if (success) {
            return "success," + data;
        }
        return "error,headers:" + Objects.toString(getHeaders(), "failed");
    }
}
