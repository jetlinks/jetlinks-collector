package org.jetlinks.collector.command;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.core.utils.ConverterUtils;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

/**
 * 获取静态资源命令.
 *
 * @author zhangji 2024/9/11
 * @since 2.3
 */
@Schema(title = "获取编辑器资源文件")
public class GetEditorResourceCommand extends AbstractCommand<Flux<DataBuffer>, GetEditorResourceCommand> {

    @Schema(title = "文件相对路径")
    public String getPath() {
        return getOrNull("path", String.class);
    }

    public GetEditorResourceCommand setPath(String path) {
        return with("path", path);
    }

    @Override
    public Object createResponseData(Object value) {
        return ConverterUtils.convertDataBuffer(value);
    }

}
