package org.jetlinks.collector.serial;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 串口信息.
 *
 * @author zhangji 2025/2/11
 * @since 2.3
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SerialPortInfo {
    private String id;
    private String name;
}
