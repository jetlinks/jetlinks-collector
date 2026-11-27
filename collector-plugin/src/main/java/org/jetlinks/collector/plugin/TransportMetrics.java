package org.jetlinks.collector.plugin;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.FileRegion;
import org.jetlinks.collector.DataCollectorProvider;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.metrics.MetricRegistration;
import org.jetlinks.core.server.ConnectionMetrics;
import org.jetlinks.core.server.ConnectionMetricsRecorder;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.ConnectionObserver;
import reactor.netty.NettyPipeline;
import reactor.netty.tcp.TcpClient;
import reactor.netty.udp.UdpClient;

import java.net.SocketAddress;

/**
 * 通道持有的实际传输累计器，重连及多个采集器连接共用同一观测。
 * Reactor 连接在订阅 Context 中取得 owner；第三方 Netty 客户端可直接安装到原始字节边界。
 * 不改变缓冲区索引、引用计数、报文或连接所有权，不把接收块当成协议消息。
 */
public final class TransportMetrics extends ConnectionMetricsRecorder implements Disposable {
    private static final String HANDLER = NettyPipeline.LEFT + "collectorTransport";
    private final MetricRegistration registration;

    public TransportMetrics(Monitor monitor) {
        registration = monitor.metrics().observe("transport", ConnectionMetrics.class, this);
    }

    public interface Owner {
        TransportMetrics transportMetrics();
    }

    /** 只给本次连接订阅绑定通道，不修改配置或使用线程全局状态。 */
    public static <T> Mono<T> record(Mono<T> source, DataCollectorProvider.ChannelRuntime channel) {
        return source.contextWrite(context -> {
            if (channel == null || !channel.isWrapperFor(Owner.class)) {
                return context.delete(TransportMetrics.class);
            }
            return context.put(TransportMetrics.class, channel.unwrap(Owner.class).transportMetrics());
        });
    }

    public static TcpClient instrument(TcpClient client) {
        return client.doOnChannelInit(TransportMetrics::install);
    }

    /** 用于在内部自行订阅建连的客户端，在建连之前显式指定 owner。 */
    public static TcpClient instrument(TcpClient client, DataCollectorProvider.ChannelRuntime channel) {
        if (channel == null || !channel.isWrapperFor(Owner.class)) return client;
        TransportMetrics metrics = channel.unwrap(Owner.class).transportMetrics();
        return client.doOnChannelInit((observer, socket, address) -> metrics.install(socket));
    }

    public static UdpClient instrument(UdpClient client) {
        return client.doOnChannelInit(TransportMetrics::install);
    }

    private static void install(ConnectionObserver observer, Channel channel, SocketAddress address) {
        observer.currentContext().<TransportMetrics>getOrEmpty(TransportMetrics.class)
            .ifPresent(metrics -> metrics.install(channel));
    }

    /** 同一个物理连接只安装一次；置于 Reactor 左侧，协议编解码器始终在统计之后。 */
    public void install(Channel channel) {
        if (channel.pipeline().get(HANDLER) != null) {
            return;
        }
        connecting();
        channel.pipeline().addFirst(HANDLER, new ChannelDuplexHandler() {
            private boolean active;

            @Override
            public void handlerAdded(ChannelHandlerContext context) {
                if (context.channel().isActive()) {
                    activate();
                }
                context.channel().closeFuture().addListener(ignored -> {
                    if (active) disconnected();
                    else connectFailed();
                });
            }

            private void activate() {
                if (!active) {
                    active = true;
                    connected();
                }
            }

            @Override
            public void channelActive(ChannelHandlerContext context) throws Exception {
                activate();
                super.channelActive(context);
            }

            @Override
            public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                long size = bytes(message);
                if (size > 0) received(size);
                super.channelRead(context, message);
            }

            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                long size = bytes(message);
                ChannelPromise completion = promise.unvoid();
                completion.addListener(result -> {
                    if (result.isSuccess()) {
                        if (size > 0) sent(size);
                    } else {
                        transportError();
                    }
                });
                context.write(message, completion);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext context, Throwable error) throws Exception {
                transportError();
                super.exceptionCaught(context, error);
            }
        });
    }

    private static long bytes(Object message) {
        if (message instanceof ByteBuf buffer) return buffer.readableBytes();
        if (message instanceof ByteBufHolder holder) return holder.content().readableBytes();
        if (message instanceof FileRegion region) return region.count();
        return 0;
    }

    @Override
    public void dispose() {
        registration.dispose();
    }

    @Override
    public boolean isDisposed() {
        return registration.isDisposed();
    }
}
