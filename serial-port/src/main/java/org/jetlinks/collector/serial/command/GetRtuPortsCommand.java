package org.jetlinks.collector.serial.command;

import org.jetlinks.collector.serial.SerialPortInfo;
import org.jetlinks.core.command.AbstractCommand;
import reactor.core.publisher.Flux;

/**
 * 获取全部rtu端口.
 *
 * @author zhangji 2025/2/11
 * @since 2.3
 */
public class GetRtuPortsCommand extends AbstractCommand<Flux<SerialPortInfo>, GetRtuPortsCommand> {


}