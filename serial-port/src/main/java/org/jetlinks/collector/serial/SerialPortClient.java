package org.jetlinks.collector.serial;

import com.fazecast.jSerialComm.SerialPort;
import io.netty.buffer.ByteBuf;
import org.jetlinks.core.monitor.Monitor;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Function;

/**
 * 串口客户端工具,使用完毕后调用{@link SerialPortClient#dispose()}释放资源.
 *
 * <pre>{@code
 * //modbus简单示例
 * SerialPortClient client = SerialPortClient
 *      .create(serialPort);
 * }</pre>
 *
 * @author zhouhao
 * @see SerialPortClient#create(SerialPort, PayloadParser)
 * @see PayloadParser
 * @since 1.0
 */
public interface SerialPortClient extends Disposable {

    /**
     * 获取串口地址
     * @return 串口地址
     */
    String getPath();

    /**
     * 发送数据并接收响应
     *
     * @param buf     数据
     * @param timeout 超时时间,数据发送到串口后等待响应的超时时间
     * @return 响应数据
     * @see RequestTimeoutException
     * @see Mono#onErrorResume(Function)
     */
    Mono<ByteBuf> sendAndReceive(ByteBuf buf, Duration timeout);

    /**
     * 发送并接收多个响应,直到超时时间到达.
     *
     * @param buf     请求数据
     * @param timeout 超时时间,超时未获取到数据时将返回empty.
     * @return 响应数据
     */
    Flux<ByteBuf> sendAndReceiveMulti(ByteBuf buf, Duration timeout);

    /**
     * 发送并接收多个响应,直到超时时间到达.
     *
     * @param buf     请求数据
     * @param timeout 超时时间,超时未获取到数据时将返回empty.
     * @param num     收取响应报文的数量限制，收完即停
     * @return 响应数据
     */
    Flux<ByteBuf> sendAndReceiveMulti(ByteBuf buf, Duration timeout, int num);

    /**
     * @return 是否已连接
     */
    default boolean isConnected(){
        return !isDisposed();
    }

    void doOnClosed(Disposable disposable);

    /**
     * 基于已经打开的串口创建客户端,在收到串口数据后,会根据{@link PayloadParser}的规则解析出完整报文后返回给发起方.
     *
     * @param port         串口
     * @param parser       完整报文解析器
     * @param maxQueueSize 最大队列长度
     * @return 串口客户端
     */
    static SerialPortClient create(SerialPortConfig port, PayloadParser parser, int maxQueueSize, Monitor monitor) {
        return new DefaultSerialPortClient(port, parser, maxQueueSize, monitor);
    }

    /**
     * 基于已经打开的串口创建客户端,在收到串口数据后,会根据{@link PayloadParser}的规则解析出完整报文后返回给发起方.
     *
     * @param port         串口
     * @param parser       完整报文解析器
     * @param maxQueueSize 最大队列长度
     * @return 串口客户端
     */
    static SerialPortClient create(SerialPort port, PayloadParser parser, int maxQueueSize) {
        return new DefaultSerialPortClient(port, parser, maxQueueSize, Monitor.noop());
    }

    /**
     * 基于已经打开的串口创建客户端,在收到串口数据后,会根据{@link PayloadParser}的规则解析出完整报文后返回给发起方.
     *
     * @param port   串口
     * @param parser 完整报文解析器
     * @return 串口客户端
     */
    static SerialPortClient create(SerialPort port, PayloadParser parser) {
        return create(port, parser, 1024);
    }
}
