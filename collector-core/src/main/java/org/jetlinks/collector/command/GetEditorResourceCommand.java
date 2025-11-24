package org.jetlinks.collector.command;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.core.command.CommandHandler;
import org.jetlinks.core.command.CommandUtils;
import org.jetlinks.core.metadata.SimpleFunctionMetadata;
import org.jetlinks.core.metadata.SimplePropertyMetadata;
import org.jetlinks.core.metadata.types.StringType;
import org.jetlinks.core.utils.ConverterUtils;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.function.Function;

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

    @Deprecated
    public static CommandHandler<GetEditorResourceCommand, Flux<DataBuffer>> createHandler(
        Function<GetEditorResourceCommand, Flux<DataBuffer>> handler
    ) {
        return CommandHandler.of(
            () -> {
                SimpleFunctionMetadata metadata = new SimpleFunctionMetadata();
                metadata.setId(CommandUtils.getCommandIdByType(GetEditorResourceCommand.class));
                metadata.setName("获取对应的采集器的资源文件");
                metadata.setInputs(Collections.singletonList(
                    SimplePropertyMetadata.of("path", "资源文件名", StringType.GLOBAL)
                ));
                return metadata;
            },
            (cmd, ignore) -> handler.apply(cmd),
            GetEditorResourceCommand::new
        );
    }

    @Override
    public Object createResponseData(Object value) {
        return ConverterUtils.convertDataBuffer(value);
    }

}
