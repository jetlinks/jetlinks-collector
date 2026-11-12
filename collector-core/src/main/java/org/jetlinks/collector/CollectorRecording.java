package org.jetlinks.collector;

import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.recorder.ActionRecorder;
import org.jetlinks.core.monitor.recorder.Recorder;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

/**
 * 采集阶段共用的记录名称、结果摘要与本地父子作用域。
 * 只使用 Monitor 的 Recorder，不持有记录、历史或全局接收端；作用域随一次执行的 Context 释放。
 * 阶段完成与业务结果分别记录，过滤和跳过不伪装为通信异常。
 *
 * @since 1.0.1
 * @see ActionRecorder
 */
public final class CollectorRecording {
    public static final String COLLECT = "collect";
    public static final String POINT = "point";
    public static final String DECODE = "decode";
    public static final String CONVERT = "convert";
    public static final String OUTLIER = "outlier";
    public static final String DEADBAND = "deadband";
    public static final String CHANGED_ONLY = "changedOnly";
    public static final String OUTPUT = "output";
    public static final String SCHEDULE = "schedule";
    public static final String REQUEST = "request";

    private CollectorRecording() { }

    /** 每次实际执行创建未共享的 action；本地父作用域按原接收端身份关联。 */
    public static <T> ActionRecorder<T> start(Monitor monitor, String name, ContextView context) {
        Recorder recorder = monitor.recorder();
        if (recorder == Recorder.noop()) return ActionRecorder.noop();
        ActionRecorder<?> parent = context.getOrDefault(CollectorRecording.class, null);
        if (parent != null) recorder = Recorder.withParent(recorder, parent);
        return recorder.<T>action(name).start(context);
    }

    /** 只在本地 Context 中传递父操作；不能将活动 action 写入消息或集群载体。 */
    public static Context context(ContextView context, ActionRecorder<?> action) {
        Context result = Context.of(context);
        return action == ActionRecorder.noop() ? result : result.put(CollectorRecording.class, action);
    }

    /** 保留公共状态码与成功标志；不展开堆栈、不格式化国际化文案。 */
    public static void result(ActionRecorder<?> action, Result<?> result) {
        action.attribute("code", result.getCode()).attribute("success", result.isSuccess());
        Object reason = result.getHeaderOrDefault(CollectorConstants.Headers.reason);
        if (reason != null) action.attribute("reason", reason);
    }

    /** 仅保存简单值的有限摘要，不调用任意业务对象的序列化或 toString。 */
    public static void value(ActionRecorder<?> action, String name, Object value) {
        if (action == ActionRecorder.noop() || value == null) return;
        if (value instanceof String) {
            String text = (String) value;
            action.attribute(name, text.length() <= 160 ? text : text.substring(0, 160));
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Long || value instanceof Float || value instanceof Double || value instanceof Boolean) {
            action.attribute(name, value);
        } else {
            action.attribute(name + "Type", value.getClass().getName());
        }
    }
}
