package org.jetlinks.collector.serial;

import lombok.AllArgsConstructor;
import lombok.Generated;
import lombok.Getter;
import org.hswebframework.web.dict.Dict;
import org.hswebframework.web.dict.EnumDict;
import org.hswebframework.web.dict.I18nEnumDict;

/**
 * 串口停止位
 */
@AllArgsConstructor
@Getter
@Dict("serial-port-stop-bits")
@Generated
public enum SerialPortStopBits implements I18nEnumDict<Integer> {
    ONE("1位", 1),
    ONE_POINT_FIVE("1.5位", 2),
    TWO("2位", 3);

    private final String  text;
    private final int stopBits;

    @Override
    public Integer getValue() {
        return stopBits;
    }

    public static SerialPortStopBits of(int stopBits) {
        return EnumDict
            .findByValue(SerialPortStopBits.class, stopBits)
            .orElse(null);
    }
}
