package org.jetlinks.collector.serial;

import lombok.AllArgsConstructor;
import lombok.Generated;
import lombok.Getter;
import org.hswebframework.web.dict.Dict;
import org.hswebframework.web.dict.EnumDict;
import org.hswebframework.web.dict.I18nEnumDict;

/**
 * 串口奇偶校验
 */
@AllArgsConstructor
@Getter
@Dict("serial-port-parity")
@Generated
public enum SerialPortParity implements I18nEnumDict<Integer> {
    NONE("无校验", 0),
    ODD("奇校验", 1),
    EVEN("偶校验", 2),
    MARK("标志校验", 3),
    SPACE("空格校验", 4);


    private final String  text;
    private final int parity;

    @Override
    public Integer getValue() {
        return parity;
    }

    public static SerialPortParity of(int parity) {
        return EnumDict
            .findByValue(SerialPortParity.class, parity)
            .orElse(null);
    }
}
