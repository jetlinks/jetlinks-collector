package org.jetlinks.collector.serial;

import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Flux;

public interface PayloadParser {

    /**
     * 处理一个数据包
     *
     * @param buffer 数据包
     */
    void handle(ByteBuf buffer);

    /**
     * 订阅完整的数据包流,每一个元素为一个完整的数据包
     *
     * @return 完整数据包流
     */
    Flux<ByteBuf> handlePayload();

    /**
     * 关闭以释放相关资源
     */
    void close();

    /**
     * 重置规则
     */
   default void reset(){}
}
