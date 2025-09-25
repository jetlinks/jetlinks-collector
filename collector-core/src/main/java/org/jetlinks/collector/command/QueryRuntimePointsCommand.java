package org.jetlinks.collector.command;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jetlinks.collector.PointRuntimeInfo;
import org.jetlinks.core.command.AbstractConvertCommand;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

/**
 * @deprecated {@link org.jetlinks.pro.collector.cmd.point.QueryRuntimePointsCommand}
 */
@Deprecated
@Schema(title = "获取指定采集器点位运行信息")
public class QueryRuntimePointsCommand extends AbstractConvertCommand<Flux<PointRuntimeInfo>, QueryRuntimePointsCommand> {

    public static final String collectorIdKey = "id";
    public static final String pointIdsKey = "pointIds";

    @Schema(name = "采集器id")
    public String getCollectorId() {
        return (String) readable().get(collectorIdKey);
    }

    @Schema(name = "点位id", description = "为空获取全部")
    public List<String> getPointIds() {
        String ids = (String) readable().get(pointIdsKey);
        if (ids == null) {
            return Collections.emptyList();
        }
        return List.of(ids.split(","));
    }

    public QueryRuntimePointsCommand withCollectorId(String id) {
        writable().put(collectorIdKey, id);
        return castSelf();
    }

    public QueryRuntimePointsCommand withPointIdList(List<String> ids) {
        return with(pointIdsKey, String.join(",", ids));
    }

    public QueryRuntimePointsCommand withPointId(String id) {
        return with(pointIdsKey, id);
    }

}
