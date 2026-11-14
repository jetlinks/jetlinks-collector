package org.jetlinks.collector.address;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
class StringPointAddress extends PointAddress {
    final String address;

    @Override
    @Nonnull
    // 字符串地址按其原始值返回，保持其他协议地址的结构化表达不变。
    @JsonValue
    public String toString() {
        return address;
    }
}
