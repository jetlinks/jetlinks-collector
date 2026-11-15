# jetlinks-collector
JetLinks 数据采集模块

## 版本维护

- `1.0.1` 分支维护 `1.0.1-SNAPSHOT`，保留既有依赖基线。
- `master` 开发 `1.0.2-SNAPSHOT`，新契约及其插件使用新的版本坐标。

使用执行录制、对象指标等新契约的插件，需要同步升级父工程及运行时的采集基础库；
不能仅替换插件包，继续搭配旧版基础库。数据配置兼容性需单独验证，版本号不代替迁移。
`jetlinks-sdk-api` 使用独立的 `jetlinks.sdk.version`，不随采集基础库版本自动升级。
