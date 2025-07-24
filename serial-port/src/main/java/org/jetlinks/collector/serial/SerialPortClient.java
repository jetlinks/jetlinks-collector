package org.jetlinks.collector.serial;

import com.fazecast.jSerialComm.SerialPort;
import io.netty.buffer.ByteBuf;
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
 *      .create(serialPort, new PipePayloadParser()
 *      .fixed(3)
 *      .handler((buffer, parser) -> {
 *         //地址
 *        byte addr = buffer.getByte(0);
 *        //功能码
 *        int code = buffer.getUnsignedByte(1);
 *        //长度
 *        int len = buffer.getUnsignedByte(2) + 2;
 *        System.out.printf("addr:%x,code:%x,len:%s \n", addr, code, len);
 *        //读取下一个长度报文
 *        parser.fixed(len).result(buffer);
 *        })
 *       .handler((buffer, parser) -> parser.result(buffer).complete()));
 * }</pre>
 *
 * @author zhouhao
 * @see SerialPortClient#create(SerialPort, PayloadParser)
 * @see PayloadParser
 * @since 1.0
 */
public interface SerialPortClient extends Disposable {

    /**
     * 发送数据并接收响应
     *
     * @param buf     数据
     * @param timeout 超时时间,超时未获取到数据时将返回empty.
     * @return 响应数据
     * @see java.util.concurrent.TimeoutException
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
     * 基于已经打开的串口创建客户端,在收到串口数据后,会根据{@link PayloadParser}的规则解析出完整报文后返回给发起方.
     *
     * @param port         串口
     * @param parser       完整报文解析器
     * @param maxQueueSize 最大队列长度
     * @return 串口客户端
     */
    static SerialPortClient create(SerialPort port, PayloadParser parser, int maxQueueSize) {
        return new DefaultSerialPortClient(port, parser, maxQueueSize);
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
