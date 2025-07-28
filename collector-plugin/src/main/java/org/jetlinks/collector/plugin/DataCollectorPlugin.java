package org.jetlinks.collector.plugin;

import org.hswebframework.web.i18n.LocaleUtils;
import org.jetlinks.collector.DataCollectorProvider;
import org.jetlinks.plugin.core.AbstractPlugin;
import org.jetlinks.plugin.core.PluginContext;
import org.jetlinks.plugin.core.PluginType;

/**
 * 数据采集插件,继承此抽象类,平台将调用{@link DataCollectorPlugin#createProviders()}方法注册数采提供商到平台
 *
 * @author zhouhao
 * @since 1.0.0
 */
public abstract class DataCollectorPlugin extends AbstractPlugin {

    public static final PluginType TYPE = new PluginType() {
        @Override
        public String getId() {
            return "data_collector";
        }

        @Override
        public String getName() {
            return LocaleUtils.resolveMessage("message.plugin.collector.name","数据采集");
        }
    };

    public DataCollectorPlugin(String id, PluginContext context) {
        super(id, context);
    }

    @Override
    public PluginType getType() {
        return TYPE;
    }

    /**
     * 创建数采支持提供商
     *
     * @return DataCollectorProvider
     * @see DataCollectorProvider
     * @see org.jetlinks.collector.AbstractDataCollectorProvider
     */
    public abstract Iterable<DataCollectorProvider> createProviders();

}
