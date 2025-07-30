package org.jetlinks.collector.serial;

import com.fazecast.jSerialComm.SerialPort;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.validator.ValidatorUtils;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * 串口配置信息.
 *
 * @author zhangji 2022/11/27
 */
@Getter
@Setter
public class SerialPortConfig {

    @Schema(title = "串口号")
    @NotBlank
    private String port;

    @Schema(title = "波特率")
    private int baudRate = 9600;

    @Schema(title = "数据位")
    private int dataBits = 8;

    @Schema(title = "停止位")
    private SerialPortStopBits stopBits = SerialPortStopBits.ONE;

    @Schema(title = "校验位")
    private SerialPortParity parity = SerialPortParity.NONE;

    @Schema(title = "RS485配置")
    private Rs485 rs485 = new Rs485();

    //Rtu通讯间隔时间（两个请求之间的间隔时间，以适应性能不佳的设备）
    private Duration communicationInterval = Duration.ZERO;

    public void validate(){
        ValidatorUtils.tryValidate(this);
    }

    public SerialPort create() {
        SerialPort serialPort = SerialPort.getCommPort(port);
        serialPort.setBaudRate(baudRate);
        serialPort.setNumDataBits(dataBits);
        serialPort.setNumStopBits(stopBits.getStopBits());
        serialPort.setParity(parity.getParity());
        rs485.accept(serialPort);
        return serialPort;
    }

    public static SerialPortConfig of(SerialPort port) {
        SerialPortConfig properties = new SerialPortConfig();
        properties.setPort(port.getSystemPortPath());
        properties.setBaudRate(port.getBaudRate());
        properties.setDataBits(port.getNumDataBits());
        properties.setStopBits(SerialPortStopBits.of(port.getNumStopBits()));
        properties.setParity(SerialPortParity.of(port.getParity()));
        return properties;
    }

    @Getter
    @Setter
    public static class Rs485 implements Consumer<SerialPort> {

        @Schema(title = "是否启用485模式")
        private boolean enabled = true;

        @Schema(title = "RTS 高电平发送",description = "设置 RTS（请求发送）信号为高电平时启用发送")
        private boolean rs485RtsActiveHigh = true;

        @Schema(title = "终端电阻")
        private boolean enableTermination = false;

        @Schema(title = "回波接收", description = "允许接收自己的回波")
        private boolean rxDuringTx = false;

        @Schema(title = "发送前延迟(微妙)", description = "在切换为发送模式后，延迟多少微秒再开始发数据")
        private int delayBeforeSendMicroseconds = 100;

        @Schema(title = "发送后延迟(微妙)", description = "发送完成后，延迟多少微秒再释放方向控制（切换为接收）")
        private int delayAfterSendMicroseconds = 100;

        @Override
        public void accept(SerialPort serialPort) {
            serialPort.setRs485ModeParameters(
                enabled,
                rs485RtsActiveHigh,
                enableTermination,
                rxDuringTx,
                delayBeforeSendMicroseconds,
                delayAfterSendMicroseconds
            );
        }
    }

}
