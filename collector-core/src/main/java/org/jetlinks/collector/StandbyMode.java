package org.jetlinks.collector;

/** 由插件声明，宿主决定是否准备备用；任何模式都不授予正式数据交付权。 */
public enum StandbyMode {
    none,
    connection
}
