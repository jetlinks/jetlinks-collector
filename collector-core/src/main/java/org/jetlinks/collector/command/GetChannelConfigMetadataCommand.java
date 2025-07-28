package org.jetlinks.collector.command;

import org.jetlinks.collector.DataCollectorProvider;
import org.jetlinks.core.command.Command;

/**
 * 获取通道配置的元数据信息,用于动态渲染编辑,导入导出等场景
 *
 * @author zhouhao
 * @since 1.2.4
 * @see DataCollectorProvider#execute(Command)
 */
public class GetChannelConfigMetadataCommand extends GetConfigMetadataCommand<GetChannelConfigMetadataCommand> {
}
