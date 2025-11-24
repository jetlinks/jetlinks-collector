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
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数采相关操作结果
 *
 * @param <T>
 */
@Getter
@Setter
public class Result<T> extends GenericHeaderSupport<Result<T>> implements Externalizable {

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
        result.addHeader("errorStack", ExceptionUtils.getStackTrace(error));
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

    public Result<T> with(Throwable error){
        this.setSuccess(false);
        this.addHeader("errorType", error.getClass().getCanonicalName());
        this.addHeader("errorStack", ExceptionUtils.getStackTrace(error));

        // 根据异常类型推断错误码
        long errorCode = CollectorUtils.inferErrorCode(error);
        this.setCode(errorCode);
        return this;
    }

    public <NEW> Result<NEW> copy(NEW data) {
        Result<NEW> result = new Result<>();
        result.setSuccess(this.isSuccess());
        result.setCode(this.getCode());
        if (this.getHeaders() != null) {
            result.setHeaders(new HashMap<>(this.getHeaders()));
        }
        result.setData(data);
        return result;
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
