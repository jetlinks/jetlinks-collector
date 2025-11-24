package org.jetlinks.collector;

import org.jetlinks.core.Wrapper;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.function.BiConsumer;

/**
 * 生命周期,用于管理状态等逻辑.
 *
 * @since 1.2.3
 */
public interface Lifecycle extends Wrapper, Disposable {

    /**
     * 检查状态
     *
     * @return 检查状态
     */
    Mono<State> checkState();

    /**
     * 当前状态
     *
     * @return 状态
     */
    State state();

    /**
     * 启动
     */
    void start();

    /**
     * 暂停
     */
    void pause();

    /**
     * 停止
     */
    void dispose();

    /**
     * 监听状态变化
     *
     * @param listener 状态变化
     * @return Disposable
     */
    Disposable onStateChanged(BiConsumer<State, State> listener);
}
