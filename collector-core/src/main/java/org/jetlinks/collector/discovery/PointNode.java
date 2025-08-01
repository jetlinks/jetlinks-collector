package org.jetlinks.collector.discovery;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.collector.AccessMode;
import org.jetlinks.core.metadata.DataType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
@Setter
public class PointNode {

    /**
     * 点位地址
     */
    private String address;

    /**
     * 点位名称
     */
    private String name;

    /**
     * 支持的访问模式
     */
    private AccessMode[] accessModes;

    /**
     * 点位类型
     */
    private Type nodeType;

    /**
     * 上级地址
     */
    private String parentAddress;

    /**
     * 数据类型
     */
    private DataType dataType;

    /**
     * 说明
     */
    private String description;

    /**
     * 其他配置信息
     */
    private Map<String, Object> others;


    private List<PointNode> children;

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(nodeType).append(": ").append(address);
        if (name != null && !Objects.equals(address, name)) {
            builder.append(" (").append(name).append(")");
        }
        if (accessModes != null && accessModes.length > 0) {
            builder.append(" ").append(Arrays.toString(accessModes));
        }
        if (dataType != null) {
            builder.append(" ").append(dataType.getId());
        }
        if (others != null) {
            builder.append(" ").append(others);
        }
        if (description != null) {
            builder.append(description);
        }

        return builder.toString();
    }

    public enum Type {
        // 点位
        point,
        // 目录
        directory
    }
}
