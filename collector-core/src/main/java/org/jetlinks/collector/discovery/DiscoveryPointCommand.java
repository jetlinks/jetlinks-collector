package org.jetlinks.collector.discovery;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jetlinks.core.command.AbstractCommand;
import reactor.core.publisher.Flux;

@Schema(title = "发现点位信息")
public class DiscoveryPointCommand extends AbstractCommand<Flux<PointNode>, DiscoveryPointCommand> {

    @Schema(title = "父级地址")
    public String getAddress() {
        return getOrNull("address", String.class);
    }

    public DiscoveryPointCommand setAddress(String address) {
        return with("address", address);
    }

    @Schema(title = "遍历深度", description = "-1表示全部")
    public int getDepth() {
        return getOrNull("depth", int.class);
    }

    public DiscoveryPointCommand setDepth(int depth) {
        return with("depth", depth);
    }

}
