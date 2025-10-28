package org.jetlinks.collector;

import io.netty.buffer.ByteBuf;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.jetlinks.core.codec.Codec;
import org.jetlinks.core.codec.layout.ByteLayout;
import org.jetlinks.core.metadata.DataType;

@Getter
@Setter
public class PointMetadata {

    /**
     * 点位是否自动编解码,为true时,表示点位直接处理java类型,平台无需进行{@link Codec#encode(Object, ByteBuf)}.
     *
     * @see PointData#getParsedData()
     */
    @Schema(title = "是否自动编解码")
    private boolean autoCodec;

    /**
     * 当{@link PointMetadata#isAutoCodec()}为false时,此字段表示点位数据的字节长度,-1表示长度不确定.
     *
     * @see Codec#byteLength()
     * @see ByteLayout#byteLength()
     */
    @Schema(title = "字节长度")
    private int byteLength;

    /**
     * 当{@link PointMetadata#isAutoCodec()}为true时,此字段表示数据类型.
     *
     * @see Codec#byteLength()
     * @see ByteLayout#byteLength()
     */
    @Schema(title = "自动编解码器时的数据类型")
    private DataType dataType;
}
