package org.jetlinks.collector;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.I18nEnumDict;
import org.jetlinks.core.lang.SeparatedCharSequence;
import org.jetlinks.core.lang.SharedPathString;
import org.jetlinks.core.message.HeaderKey;
import org.jetlinks.core.metadata.Feature;

public interface CollectorConstants {

    interface Metrics {
        String complete = "complete";
        String collect = "collect";
        String pending = "pending";

        // 接收流量
        String received = "received";
        // 发送流量
        String sent = "sent";
        // 错误
        String error = "error";
    }

    interface Headers {

        HeaderKey<String> pointId = HeaderKey.of("pointId", null, String.class);
        HeaderKey<String> reason = HeaderKey.of("reason", null, String.class);

    }

    @AllArgsConstructor
    @Getter
    enum CollectorFeatures implements Feature {
        subscribable("可订阅点位数据"),
        batchSupport("支持批量采集"),
        // 支持自动编解码,平台无需配置编解码规则.
        autoCodec("自动编解码");
        final String name;

        @Override
        public final String getId() {
            return name();
        }

        @Override
        public final String getType() {
            return "CollectorFeature";
        }
    }

    @AllArgsConstructor
    @Getter
    enum States implements State, I18nEnumDict<String> {

        initializing("初始化"),
        running("运行中"),
        paused("已暂停"),
        starting("启动中"),
        stopped("已停止"),
        shutdown("已关闭"),
        connectionClosed("连接已断开");

        private final String text;

        @Override
        public String getValue() {
            return name();
        }

    }

    interface Tracer {
        CharSequence decode = "decode";
        CharSequence encode = "encode";
        CharSequence convert = "convert";

    }

    interface Topics{

        /**
         * @see PointData
         */
        String ALL_POINT_DATA = "/collector/*/*/*/data";

        SharedPathString TOPIC_ALL_POINT_DATA_0 = SharedPathString.of(ALL_POINT_DATA);


        String ALL_POINT_STATE = "/collector/*/*/*/state";

        SharedPathString TOPIC_ALL_POINT_STATE_0 = SharedPathString.of(ALL_POINT_STATE);

        /**
         * @see BatchPointData
         */
        String ALL_POINT_BATCH_DATA = "/collector/*/*/data";

        SharedPathString TOPIC_ALL_POINT_BATCH_DATA_0 = SharedPathString.of(ALL_POINT_BATCH_DATA);

        static SeparatedCharSequence batchDataTopic(String channelId, String collectorId) {
            return TOPIC_ALL_POINT_BATCH_DATA_0.replace(2, channelId, 3, collectorId);
        }

        static SeparatedCharSequence pointDataTopic(String channelId, String collectorId, String pointId) {
            return TOPIC_ALL_POINT_DATA_0.replace(2, channelId, 3, collectorId,4,pointId);
        }

        static SeparatedCharSequence pointStateTopic(String channelId, String collectorId, String pointId) {
            return TOPIC_ALL_POINT_STATE_0.replace(2, channelId, 3, collectorId,4,pointId);
        }

    }

}
