package org.jetlinks.collector.sink;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.collector.address.PointAddress;
import org.jetlinks.collector.PointData;

@Getter
@Setter
public class MappedPointData {

    private PointAddress address;

    private PointData data;

    public MappedPointData with(PointAddress address) {
        MappedPointData _data = new MappedPointData();
        _data.data = data;
        _data.address = address;
        return _data;
    }
}
