# jetlinks-collector
JetLinks 数据采集模块

## 版本维护

- `1.0.1` 分支维护 `1.0.1-SNAPSHOT`，保留既有依赖基线。
- `master` 开发 `1.0.2-SNAPSHOT`，新契约及其插件使用新的版本坐标。

使用执行录制、对象指标等新契约的插件，需要同步升级父工程及运行时的采集基础库；
不能仅替换插件包，继续搭配旧版基础库。数据配置兼容性需单独验证，版本号不代替迁移。
`jetlinks-sdk-api` 使用独立的 `jetlinks.sdk.version`，不随采集基础库版本自动升级。

## 受管生命周期

`ManagedLifecycle.pause()` 保留配置和已加载对象，释放暂停期资源并暂停插件；
`start()` 恢复已有对象，重复恢复不重复启动。暂停期间完成的加载保持暂停，
只有 `dispose()` 才永久释放实例。管理器负责同步暂停下级采集任务与协议订阅。

`reload()` 只替换一个受管节点，成功完成表示请求的新配置已经生效；加载失败向调用方
透传原错误，不隐式重新创建旧配置。失败前仍可用的旧实例可保留，已经关闭的实例不会恢复。
新请求替代旧请求时，旧请求以取消错误结束，迟到运行实例会被释放。加载过程继承调用方 Reactor Context。
`unload()` 关闭当前协议实例但保留包装对象、配置和独立暂停状态；父子树的暂停、子到父释放、
父到子加载及成功后恢复由宿主管理器统一编排，避免各节点递归重载与批量加载重复执行。

## 通道传输统计

通道使用 `TransportMetrics` 注册现有的 `transport`／`ConnectionMetrics` 观测，
实现 `TransportMetrics.Owner` 并通过 `doOnStop(metrics)` 注销；累计器归通道持有，
重连和多个采集器的连接共用该对象，不为每个连接覆盖同名观测。

Reactor TCP／UDP 工厂用 `TransportMetrics.instrument(client)` 安装原始字节统计，
连接订阅用 `TransportMetrics.record(source, configuration.channel())` 绑定通道。
内部自行订阅建连的 TCP 生命周期用 `instrument(client, channel)` 显式绑定。
协议客户端使用 `TcpClient.newConnection()`；HTTP 连接池归通道所有并随通道关闭，
避免跨通道复用带有其他通道累计器的物理连接。

第三方 Netty SDK 可在创建管线时调用 `metrics.install(channel)`；阻塞 Socket SDK
使用 `TransportSocketFactory` 保留原有 Socket 参数和流语义。统计只读取原始缓冲区长度，
不消费或释放协议缓冲区，成功写入后累计发送字节；接收块不计作协议消息。
这些字节包含传输边界的握手、控制报文等，不包含网卡重传和 IP／TCP 等下层开销，
不能使用业务对象的 JSON 大小替代。Socket 实际读写失败累计传输错误并保留原异常，正常 EOF、协议轮询读超时和本地主动关闭不计故障；同一层操作只记录一次。未接入的传输保持无指标，与真实零值区分。

此能力要求宿主和插件同时使用包含上述类的 `1.0.2-SNAPSHOT` 采集基础库，
应先合并并发布基础库，再构建和升级消费它的插件。

## 插件编辑器资源

公共资源入口仅提供插件 ID 对应目录中的 classpath 文件；路径归一化后越出目录或文件不存在时返回空结果，由 HTTP 入口响应404。
