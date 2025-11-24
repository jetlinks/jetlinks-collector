package org.jetlinks.collector.sink;

import org.jetlinks.collector.Lifecycle;
import org.jetlinks.core.command.CommandSupport;

public interface PointDataSource extends Lifecycle, CommandSupport {

    // 获取数据操作接口
    PointDataOperations operations();

}
