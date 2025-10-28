package org.jetlinks.collector;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.core.GenericHeaderSupport;
import org.jetlinks.core.message.property.PropertyMessage;
import org.jetlinks.core.metadata.Jsonable;
import org.jetlinks.core.utils.SerializeUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
@Setter
public class BatchPointData extends GenericHeaderSupport<BatchPointData> implements PropertyMessage, Externalizable, Jsonable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 采集器提供商
     * @see DataCollectorProvider#getId()
     */
    private String provider;

    /**
     * 通道ID
     * @see DataCollectorProvider.ChannelRuntime#getId()
     */
    private String channelId;

    /**
     * 采集器ID
     * @see DataCollectorProvider.CollectorRuntime#getId()
     */
    private String collectorId;

    /**
     * 点位数据
     * @see DataCollectorProvider.PointRuntime#read()
     */
    private List<PointData> points;

    /**
     * 时间戳
     */
    private long timestamp = System.currentTimeMillis();

    /**
     * 点位源,表示点位数据从何而来.
     */
    private String source;

    @Setter(AccessLevel.PRIVATE)
    @Getter(AccessLevel.PRIVATE)
    private transient Map<String, PointData> propertiesMap;

    public Map<String, PointData> propertiesMap() {
        if (propertiesMap == null) {
            synchronized (this) {
                if (this.propertiesMap != null) {
                    return this.propertiesMap;
                }

                Map<String, PointData> properties = Maps.newLinkedHashMapWithExpectedSize(points.size());
                for (PointData point : points) {
                    properties.put(point.getPointId(), point);
                }
                this.propertiesMap = properties;
            }
        }
        return propertiesMap;
    }

    @Nonnull
    @Override
    @SuppressWarnings("all")
    public Map<String, Object> getProperties() {
        //转为SimplePointData,减少序列化时的空间占用
        return Maps.transformValues(propertiesMap(), PointData::toSimple);
    }

    public Map<String, Object> toParsedProperties() {
        return Maps.transformValues(propertiesMap(), PointData::getParsedData);
    }

    @Nullable
    @Override
    public Map<String, Long> getPropertySourceTimes() {
        Map<String, Long> properties = Maps.newLinkedHashMapWithExpectedSize(points.size());
        for (PointData point : points) {
            properties.put(point.getPointId(), point.getTimestamp());
        }
        return properties;
    }

    @Nullable
    @Override
    public Map<String, String> getPropertyStates() {
        Map<String, String> properties = Maps.newLinkedHashMapWithExpectedSize(points.size());
        for (PointData point : points) {
            if (point.getState() == null) {
                continue;
            }
            properties.put(point.getPointId(), point.getState());
        }
        return properties;
    }

    @Override
    public Optional<Long> getPropertySourceTime(@Nonnull String property) {
        PointData data = propertiesMap().get(property);
        if (null != data) {
            return Optional.of(data.getTimestamp());
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getPropertyState(@Nonnull String property) {
        PointData data = propertiesMap().get(property);
        if (null != data) {
            return Optional.ofNullable(data.getState());
        }
        return Optional.empty();
    }

    @Override
    public Optional<Object> getProperty(String property) {
        return property == null ? Optional.empty() : Optional
            .ofNullable(propertiesMap().get(property))
            .map(PointData::toSimple);
    }

    @Override
    public PropertyMessage properties(Map<String, Object> properties) {
        return this;
    }

    @Override
    public PropertyMessage propertySourceTimes(Map<String, Long> times) {
        return this;
    }

    @Override
    public PropertyMessage propertyStates(Map<String, String> states) {
        return this;
    }

    @Override
    public String toString() {
        return channelId + ":" + collectorId + "@" + provider + " " + points;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        SerializeUtils.writeKeyValue(getHeaders(), out);

        out.writeUTF(provider);
        out.writeUTF(channelId);
        out.writeUTF(collectorId);
        out.writeLong(timestamp);
        SerializeUtils.writeNullableUTF(source, out);
        if (CollectionUtils.isEmpty(points)) {
            out.writeInt(0);
        } else {
            out.write(points.size());
            for (PointData entry : points) {
                entry.writeExternal(out);
            }
        }

    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        SerializeUtils.readKeyValue(in, this::addHeader);

        provider = in.readUTF();
        channelId = in.readUTF();
        collectorId = in.readUTF();
        timestamp = in.readLong();
        source = SerializeUtils.readNullableUTF(in);

        int size = in.readInt();
        if (size == 0) {
            this.points = new ArrayList<>();
        } else {
            this.points = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                PointData value = new PointData();
                value.readExternal(in);
                this.points.add(value);
            }
        }
    }

    @Override
    public JSONObject toJson() {
        JSONObject data = FastBeanCopier.copy(this, JSONObject::new, "points");
        Map<String, Object> pointIdKeyData = Maps.transformValues(
            propertiesMap(),
            PointData::getParsedData);
        data.put("parseData", pointIdKeyData);
        if (CollectionUtils.isNotEmpty(points)) {
            List<JSONObject> pointsJson = points
                .stream()
                .map(p -> FastBeanCopier.copy(p, new JSONObject()))
                .collect(Collectors.toList());
            data.put("points", pointsJson);
        }
        return data;
    }
}
