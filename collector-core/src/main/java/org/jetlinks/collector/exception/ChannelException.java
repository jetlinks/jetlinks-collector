package org.jetlinks.collector.exception;

import lombok.Getter;
import org.jetlinks.collector.InternalStatusCode;

/**
 * 通道级失败。插件知道通道不可用、未连接或通信中断时抛出，并带上平台状态码和原因键。
 *
 * @since 1.0
 */
@Getter
public class ChannelException extends CollectorFailure {

    private final String channelId;

    public ChannelException(String channelId,
                            String reason,
                            Object... args) {
        super(reason, 0L, null, args);
        this.channelId = channelId;
    }

    public ChannelException(String channelId,
                            String reason,
                            Throwable cause,
                            Object... args) {
        super(reason, 0L, cause, args);
        this.channelId = channelId;
    }

    public ChannelException(String channelId,
                            InternalStatusCode status,
                            String reason,
                            Object... args) {
        super(status, reason, args);
        this.channelId = channelId;
    }

    public ChannelException(String channelId,
                            InternalStatusCode status,
                            String reason,
                            Throwable cause,
                            Object... args) {
        super(status, reason, cause, args);
        this.channelId = channelId;
    }

}
