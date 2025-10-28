package org.jetlinks.collector;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.codec.binary.Hex;
import org.jetlinks.core.utils.SerializeUtils;

import java.io.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class SimplePointData implements Externalizable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 原始数据,与{@link  PointData#getOriginData()}不能同时为null
     */
    private byte[] originData;

    /**
     * 解析后的数据,不同的点位格式不同.通常有基本数据类型和Map组成.
     */
    private Object parsedData;

    public String getHexString() {
        return originData == null ? null : Hex.encodeHexString(originData);
    }

    public static SimplePointData of(Object data) {
        if (data instanceof SimplePointData) {
            return (SimplePointData) data;
        }
        if (data instanceof byte[]) {
            return SimplePointData.of(((byte[]) data), null);
        }
        return SimplePointData.of(null, data);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        int size = originData == null ? 0 : originData.length;
        out.writeInt(size);
        if (originData != null) {
            out.write(originData);
        }
        SerializeUtils.writeObject(parsedData, out);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int size = in.readInt();
        if (size > 0) {
            originData = new byte[size];
            in.readFully(originData);
        }
        parsedData = SerializeUtils.readObject(in);
    }
}
