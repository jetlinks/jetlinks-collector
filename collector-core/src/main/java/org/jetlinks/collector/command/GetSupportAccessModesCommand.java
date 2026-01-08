package org.jetlinks.collector.command;

import org.jetlinks.collector.AccessMode;
import org.jetlinks.core.command.AbstractCommand;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 获取支持的访问方式
 */
public class GetSupportAccessModesCommand extends AbstractCommand<Mono<List<AccessMode>>, GetSupportAccessModesCommand> {
}
