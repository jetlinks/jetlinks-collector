package org.jetlinks.collector.subscribe;

import org.jetlinks.collector.InternalStatusCode;
import org.jetlinks.collector.PointData;
import org.jetlinks.collector.Result;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

/**
 * 点位数据监听器接口
 * <p>
 * 用于监听点位数据的接收、错误和订阅失败事件。
 * 当点位数据发生变化或出现异常时，调用相应的回调方法。
 *
 * @see PointSubscription
 */
public interface PointListener {

    /**
     * 接收到单个点位数据时的回调方法
     *
     * @param data 点位数据
     * @see PointData
     */
    void onDataReceived(PointData data);

    /**
     * 接收到多个点位数据时的回调方法
     *
     * @param data 点位数据
     * @see PointData
     */
    default void onDataReceived(List<PointData> data) {
        for (PointData datum : data) {
            onDataReceived(datum);
        }
    }

    /**
     * 点位数据采集错误时的回调方法
     * <p>
     * 当某个点位在数据采集过程中出现错误时，调用此方法。
     * 错误可能包括：响应错误、数据格式错误等
     * </p>
     * 
     * @param pointId 发生错误的点位ID
     * @param result 错误结果，包含错误码、错误信息等详细信息
     * @see Result
     * @see InternalStatusCode
     */
    void onDataError(String pointId, Result<?> result);

    /**
     * 点位执行订阅失败时的回调方法
     * <p>
     * 当尝试订阅某个点位但订阅操作失败时，调用此方法。
     * 订阅失败通常是由于：点位不存在、权限不足、连接异常等原因。
     * </p>
     * 
     * @param pointId 订阅失败的点位ID
     * @param error 订阅失败的具体异常信息
     * @see PointSubscription#subscribe(Collection)
     */
    void onSubscribeFailed(String pointId, Throwable error);
}
