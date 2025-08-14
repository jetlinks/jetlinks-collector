package org.jetlinks.collector;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.core.metadata.DataType;
import org.jetlinks.core.utils.SerializeUtils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

@Getter
@Setter
public class PointRuntimeInfo implements Externalizable {

    private String serverId;

    private String pointId;

    private String pointName;

    private DataType dataType;

    private PointData lastData;


    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(serverId);
        out.writeUTF(pointId);
        out.writeUTF(pointName);
        SerializeUtils.writeObject(dataType, out);
        SerializeUtils.writeObject(lastData, out);
    }

    @Override
    @SuppressWarnings("all")
    public void readExternal(ObjectInput in) throws IOException {
        this.serverId = in.readUTF();
        this.pointId = in.readUTF();
        this.pointName = in.readUTF();
        this.dataType = (DataType) SerializeUtils.readObject(in);
        this.lastData = (PointData) SerializeUtils.readObject(in);
    }
}