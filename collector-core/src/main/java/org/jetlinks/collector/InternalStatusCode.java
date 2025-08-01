package org.jetlinks.collector;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.i18n.LocaleUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据采集状态码枚举
 * 基于OPC-UA状态码标准设计，兼容OPC-UA规范
 * <pre>{@code
 * 状态码结构（32位）：
 * ┌─────────────┬─────────────────────────────────────┐
 * │  分类2位     │         具体状态码30位                │
 * │ (30-31)     │         (0-29)                      │
 * │ 00=Good     │                                     │
 * │ 01=Uncertain│                                     │
 * │ 10=Bad      │                                     │
 * │ 11=Reserved │                                     │
 * └─────────────┴─────────────────────────────────────┘
 * }</pre>
 *
 * @author zhouhao
 * @author AI
 * @since 1.0
 */
@AllArgsConstructor
public enum InternalStatusCode implements StatusCode {

    // ======= Good 状态码 (0x00000000-0x3FFFFFFF) =======

    /**
     * 操作成功
     */
    Good(0x00000000L, "操作成功"),

    /**
     * 处理将异步完成
     */
    Good_CompletesAsynchronously(0x002E0000L, "处理将异步完成"),

    /**
     * 由于资源限制，采样速度已放缓
     */
    Good_Overload(0x002F0000L, "由于资源限制，采样速度已放缓"),

    /**
     * 写入的值被接受但被限制
     */
    Good_Clamped(0x00300000L, "写入的值被接受但被限制"),

    /**
     * 订阅已转移到另一个会话
     */
    Good_SubscriptionTransferred(0x002D0000L, "订阅已转移到另一个会话"),

    /**
     * 数据或事件已成功插入到历史数据库中
     */
    Good_EntryInserted(0x00A20000L, "数据或事件已成功插入到历史数据库中"),

    /**
     * 数据或事件字段已成功替换在历史数据库中
     */
    Good_EntryReplaced(0x00A30000L, "数据或事件字段已成功替换在历史数据库中"),

    /**
     * 请求的时间范围或事件过滤器没有数据存在
     */
    Good_NoData(0x00A50000L, "请求的时间范围或事件过滤器没有数据存在"),

    /**
     * 在请求的值数量之外，时间范围内还有更多数据可用
     */
    Good_MoreData(0x00A60000L, "在请求的值数量之外，时间范围内还有更多数据可用"),

    /**
     * 值已被覆盖
     */
    Good_LocalOverride(0x00960000L, "值已被覆盖"),

    // ======= Uncertain 状态码 (0x40000000-0x7FFFFFFF) =======

    /**
     * 值不确定但没有具体原因
     */
    Uncertain(0x40000000L, "值不确定但没有具体原因"),

    /**
     * 与数据源的通信失败，变量值是最后一个具有良好质量的值
     */
    Uncertain_NoCommunicationLastUsableValue(0x408F0000L, "与数据源的通信失败，变量值是最后一个具有良好质量的值"),

    /**
     * 更新此值的任何东西都已停止
     */
    Uncertain_LastUsableValue(0x40900000L, "更新此值的任何东西都已停止"),

    /**
     * 该值是手动覆盖的操作值
     */
    Uncertain_SubstituteValue(0x40910000L, "该值是手动覆盖的操作值"),

    /**
     * 对于通常从另一个变量接收其值的变量，该值是初始值
     */
    Uncertain_InitialValue(0x40920000L, "对于通常从另一个变量接收其值的变量，该值是初始值"),

    /**
     * 值处于传感器限制之一
     */
    Uncertain_SensorNotAccurate(0x40930000L, "值处于传感器限制之一"),

    /**
     * 值超出了为此参数定义的值范围
     */
    Uncertain_EngineeringUnitsExceeded(0x40940000L, "值超出了为此参数定义的值范围"),

    /**
     * 数据值来自多个源，但好源数量少于所需数量
     */
    Uncertain_SubNormal(0x40950000L, "数据值来自多个源，但好源数量少于所需数量"),

    // ======= Bad 状态码 (0x80000000-0xFFFFFFFF) =======

    /**
     * 值不好但没有具体原因
     */
    Bad(0x80000000L, "值不好但没有具体原因"),

    /**
     * 发生了意外错误
     */
    Bad_UnexpectedError(0x80010000L, "发生了意外错误"),

    /**
     * 由于编程或配置错误而发生内部错误
     */
    Bad_InternalError(0x80020000L, "由于编程或配置错误而发生内部错误"),

    /**
     * 内存不足，无法完成操作
     */
    Bad_OutOfMemory(0x80030000L, "内存不足，无法完成操作"),

    /**
     * 操作系统资源不可用
     */
    Bad_ResourceUnavailable(0x80040000L, "操作系统资源不可用"),

    /**
     * 发生了低级通信错误
     */
    Bad_CommunicationError(0x80050000L, "发生了低级通信错误"),

    /**
     * 由于正在序列化的对象中的无效数据而停止编码
     */
    Bad_EncodingError(0x80060000L, "由于正在序列化的对象中的无效数据而停止编码"),

    /**
     * 由于流中的无效数据而停止解码
     */
    Bad_DecodingError(0x80070000L, "由于流中的无效数据而停止解码"),

    /**
     * 消息编码/解码限制超出
     */
    Bad_EncodingLimitsExceeded(0x80080000L, "消息编码/解码限制超出"),

    /**
     * 操作超时
     */
    Bad_Timeout(0x800A0000L, "操作超时"),

    /**
     * 服务器不支持所请求的服务
     */
    Bad_ServiceUnsupported(0x800B0000L, "服务器不支持所请求的服务"),

    /**
     * 因为应用程序正在关闭而取消了操作
     */
    Bad_Shutdown(0x800C0000L, "因为应用程序正在关闭而取消了操作"),

    /**
     * 操作无法完成，因为客户端未连接到服务器
     */
    Bad_ServerNotConnected(0x800D0000L, "操作无法完成，因为客户端未连接到服务器"),

    /**
     * 服务器已停止，无法处理任何请求
     */
    Bad_ServerHalted(0x800E0000L, "服务器已停止，无法处理任何请求"),

    /**
     * 没有可处理的内容，因为没有要做的事情
     */
    Bad_NothingToDo(0x800F0000L, "没有可处理的内容，因为没有要做的事情"),

    /**
     * 无法处理请求，因为它指定了太多操作
     */
    Bad_TooManyOperations(0x80100000L, "无法处理请求，因为它指定了太多操作"),

    /**
     * 无法处理请求，因为订阅中有太多监视项
     */
    Bad_TooManyMonitoredItems(0x80DB0000L, "无法处理请求，因为订阅中有太多监视项"),

    /**
     * 因为无法识别数据类型ID，所以扩展对象无法（反）序列化
     */
    Bad_DataTypeIdUnknown(0x80110000L, "因为无法识别数据类型ID，所以扩展对象无法（反）序列化"),

    /**
     * 验证安全性时发生错误
     */
    Bad_SecurityChecksFailed(0x80130000L, "验证安全性时发生错误"),

    /**
     * 证书无效
     */
    Bad_CertificateInvalid(0x80120000L, "证书无效"),

    /**
     * 证书已过期或尚未生效
     */
    Bad_CertificateTimeInvalid(0x80140000L, "证书已过期或尚未生效"),

    /**
     * 用户没有执行所请求操作的权限
     */
    Bad_UserAccessDenied(0x801F0000L, "用户没有执行所请求操作的权限"),

    /**
     * 用户身份令牌无效
     */
    Bad_IdentityTokenInvalid(0x80200000L, "用户身份令牌无效"),

    /**
     * 用户身份令牌有效，但服务器已拒绝它
     */
    Bad_IdentityTokenRejected(0x80210000L, "用户身份令牌有效，但服务器已拒绝它"),

    /**
     * 指定的安全通道不再有效
     */
    Bad_SecureChannelIdInvalid(0x80220000L, "指定的安全通道不再有效"),

    /**
     * 时间戳超出服务器允许的范围
     */
    Bad_InvalidTimestamp(0x80230000L, "时间戳超出服务器允许的范围"),

    /**
     * 会话ID无效
     */
    Bad_SessionIdInvalid(0x80250000L, "会话ID无效"),

    /**
     * 会话被客户端关闭
     */
    Bad_SessionClosed(0x80260000L, "会话被客户端关闭"),

    /**
     * 无法使用会话，因为尚未调用ActivateSession
     */
    Bad_SessionNotActivated(0x80270000L, "无法使用会话，因为尚未调用ActivateSession"),

    /**
     * 订阅ID无效
     */
    Bad_SubscriptionIdInvalid(0x80280000L, "订阅ID无效"),

    /**
     * 请求的头部缺失或无效
     */
    Bad_RequestHeaderInvalid(0x802A0000L, "请求的头部缺失或无效"),

    /**
     * 要返回的时间戳参数无效
     */
    Bad_TimestampsToReturnInvalid(0x802B0000L, "要返回的时间戳参数无效"),

    /**
     * 请求被客户端取消
     */
    Bad_RequestCancelledByClient(0x802C0000L, "请求被客户端取消"),

    /**
     * 与数据源的通信已定义，但未建立，并且没有最后已知值可用
     */
    Bad_NoCommunication(0x80310000L, "与数据源的通信已定义，但未建立，并且没有最后已知值可用"),

    /**
     * 等待服务器从底层数据源获取值
     */
    Bad_WaitingForInitialData(0x80320000L, "等待服务器从底层数据源获取值"),

    /**
     * 节点ID的语法无效
     */
    Bad_NodeIdInvalid(0x80330000L, "节点ID的语法无效"),

    /**
     * 节点ID引用的节点在服务器地址空间中不存在
     */
    Bad_NodeIdUnknown(0x80340000L, "节点ID引用的节点在服务器地址空间中不存在"),

    /**
     * 指定节点不支持该属性
     */
    Bad_AttributeIdInvalid(0x80350000L, "指定节点不支持该属性"),

    /**
     * 索引范围参数的语法无效
     */
    Bad_IndexRangeInvalid(0x80360000L, "索引范围参数的语法无效"),

    /**
     * 指定的索引范围内不存在数据
     */
    Bad_IndexRangeNoData(0x80370000L, "指定的索引范围内不存在数据"),

    /**
     * 数据编码无效
     */
    Bad_DataEncodingInvalid(0x80380000L, "数据编码无效"),

    /**
     * 服务器不支持节点的请求数据编码
     */
    Bad_DataEncodingUnsupported(0x80390000L, "服务器不支持节点的请求数据编码"),

    /**
     * 访问级别不允许读取或订阅节点
     */
    Bad_NotReadable(0x803A0000L, "访问级别不允许读取或订阅节点"),

    /**
     * 访问级别不允许写入节点
     */
    Bad_NotWritable(0x803B0000L, "访问级别不允许写入节点"),

    /**
     * 值超出范围
     */
    Bad_OutOfRange(0x803C0000L, "值超出范围"),

    /**
     * 不支持所请求的操作
     */
    Bad_NotSupported(0x803D0000L, "不支持所请求的操作"),

    /**
     * 未找到请求的项目或搜索操作结束时没有成功
     */
    Bad_NotFound(0x803E0000L, "未找到请求的项目或搜索操作结束时没有成功"),

    /**
     * 无法使用对象，因为它已被删除
     */
    Bad_ObjectDeleted(0x803F0000L, "无法使用对象，因为它已被删除"),

    /**
     * 未实现请求的操作
     */
    Bad_NotImplemented(0x80400000L, "未实现请求的操作"),

    /**
     * 监视模式无效
     */
    Bad_MonitoringModeInvalid(0x80410000L, "监视模式无效"),

    /**
     * 监视项ID不引用有效的监视项
     */
    Bad_MonitoredItemIdInvalid(0x80420000L, "监视项ID不引用有效的监视项"),

    /**
     * 监视项过滤器参数无效
     */
    Bad_MonitoredItemFilterInvalid(0x80430000L, "监视项过滤器参数无效"),

    /**
     * 服务器不支持所请求的监视项过滤器
     */
    Bad_MonitoredItemFilterUnsupported(0x80440000L, "服务器不支持所请求的监视项过滤器"),

    /**
     * 监视过滤器不能与指定的属性结合使用
     */
    Bad_FilterNotAllowed(0x80450000L, "监视过滤器不能与指定的属性结合使用"),

    /**
     * 强制结构化参数缺失或为空
     */
    Bad_StructureMissing(0x80460000L, "强制结构化参数缺失或为空"),

    /**
     * 事件过滤器无效
     */
    Bad_EventFilterInvalid(0x80470000L, "事件过滤器无效"),

    /**
     * 内容过滤器无效
     */
    Bad_ContentFilterInvalid(0x80480000L, "内容过滤器无效"),

    /**
     * 内容过滤器中使用的操作数无效
     */
    Bad_FilterOperandInvalid(0x80490000L, "内容过滤器中使用的操作数无效"),

    /**
     * 继续点提供的不再有效
     */
    Bad_ContinuationPointInvalid(0x804A0000L, "继续点提供的不再有效"),

    /**
     * 无法处理操作，因为所有继续点都已分配
     */
    Bad_NoContinuationPoints(0x804B0000L, "无法处理操作，因为所有继续点都已分配"),

    /**
     * 引用类型ID不引用有效的引用类型节点
     */
    Bad_ReferenceTypeIdInvalid(0x804C0000L, "引用类型ID不引用有效的引用类型节点"),

    /**
     * 浏览方向无效
     */
    Bad_BrowseDirectionInvalid(0x804D0000L, "浏览方向无效"),

    /**
     * 节点不是视图的一部分
     */
    Bad_NodeNotInView(0x804E0000L, "节点不是视图的一部分"),

    /**
     * 数据源出现故障
     */
    Bad_DeviceFailure(0x808B0000L, "数据源出现故障"),

    /**
     * 传感器出现故障
     */
    Bad_SensorFailure(0x808C0000L, "传感器出现故障"),

    /**
     * 数据源不可操作
     */
    Bad_OutOfService(0x808D0000L, "数据源不可操作"),

    /**
     * 存在影响值有用性的配置问题
     */
    Bad_ConfigurationError(0x80890000L, "存在影响值有用性的配置问题"),

    // ======= 数据采集专用状态码 (0x80F00000-0x80FFFFFF) =======

    /**
     * 采集器错误
     */
    Bad_CollectorError(0x80F00000L, "采集器错误"),

    /**
     * 采集器未运行
     */
    Bad_CollectorNotRunning(0x80F01000L, "采集器未运行"),

    /**
     * 采集器过载
     */
    Bad_CollectorOverload(0x80F02000L, "采集器过载"),

    /**
     * 通道错误
     */
    Bad_ChannelError(0x80F10000L, "通道错误"),

    /**
     * 通道未找到
     */
    Bad_ChannelNotFound(0x80F11000L, "通道未找到"),

    /**
     * 通道忙碌
     */
    Bad_ChannelBusy(0x80F12000L, "通道忙碌"),

    /**
     * 点位错误
     */
    Bad_PointError(0x80F20000L, "点位错误"),

    /**
     * 点位未找到
     */
    Bad_PointNotFound(0x80F21000L, "点位未找到"),

    /**
     * 点位不支持
     */
    Bad_PointNotSupported(0x80F22000L, "点位不支持"),

    /**
     * 设备错误
     */
    Bad_DeviceError(0x80F30000L, "设备错误"),

    /**
     * 设备未找到
     */
    Bad_DeviceNotFound(0x80F31000L, "设备未找到"),

    /**
     * 设备离线
     */
    Bad_DeviceOffline(0x80F32000L, "设备离线"),

    /**
     * 编解码器错误
     */
    Bad_CodecError(0x80F40000L, "编解码器错误"),

    /**
     * 编码器错误
     */
    Bad_EncoderError(0x80F41000L, "编码器错误"),

    /**
     * 解码器错误
     */
    Bad_DecoderError(0x80F42000L, "解码器错误"),

    /**
     * 协议错误
     */
    Bad_ProtocolError(0x80F50000L, "协议错误"),

    /**
     * 协议版本不支持
     */
    Bad_ProtocolVersionUnsupported(0x80F51000L, "协议版本不支持"),

    /**
     * 校验和错误
     */
    Bad_ChecksumError(0x80F52000L, "校验和错误"),

    /**
     * 连接错误
     */
    Bad_ConnectionError(0x80F60000L, "连接错误"),

    /**
     * 连接丢失
     */
    Bad_ConnectionLost(0x80F61000L, "连接丢失"),

    /**
     * 连接被拒绝
     */
    Bad_ConnectionRefused(0x80F62000L, "连接被拒绝"),

    /**
     * 读取超时
     */
    Bad_ReadTimeout(0x80F70000L, "读取超时"),

    /**
     * 写入超时
     */
    Bad_WriteTimeout(0x80F71000L, "写入超时"),

    /**
     * 地址无效
     */
    Bad_InvalidAddress(0x80F80000L, "地址无效"),

    /**
     * 参数无效
     */
    Bad_InvalidParameter(0x80F81000L, "参数无效"),

    /**
     * 认证失败
     */
    Bad_AuthenticationFailed(0x80F90000L, "认证失败"),

    /**
     * 访问被拒绝
     */
    Bad_AccessDenied(0x80F91000L, "访问被拒绝"),

    /**
     * 数据格式错误
     */
    Bad_DataFormatError(0x80FA0000L, "数据格式错误");

    /**
     * 状态码值
     */
    @Getter
    private final long code;

    /**
     * 状态描述
     */
    private final String description;

    /**
     * 缓存的枚举值数组，避免重复调用values()
     */
    private static final InternalStatusCode[] VALUES = InternalStatusCode.values();

    /**
     * 获取状态码的严重程度
     *
     * @return 严重程度 (0=Good, 1=Uncertain, 2=Bad, 3=Reserved)
     */
    public int getSeverity() {
        return (int) ((code >>> 30) & 0x3);
    }

    /**
     * 检查是否为Good状态
     */
    public boolean isGood() {
        return getSeverity() == 0;
    }

    /**
     * 检查是否为Uncertain状态
     */
    public boolean isUncertain() {
        return getSeverity() == 1;
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public String getDescription() {
        return LocaleUtils.resolveMessage("message.collector.status_code." + name(), description);
    }

    /**
     * 检查是否为Bad状态
     */
    public boolean isBad() {
        return getSeverity() == 2;
    }


    /**
     * 根据状态码值查找对应的枚举
     *
     * @param code 状态码值
     * @return 对应的枚举，如果未找到返回null
     */
    public static InternalStatusCode fromCode(long code) {
        for (InternalStatusCode statusCode : VALUES) {
            if (statusCode.code == code) {
                return statusCode;
            }
        }
        return null;
    }

    /**
     * 根据状态码获取描述
     *
     * @param code 状态码值
     * @return 描述信息
     */
    public static String getDescription(long code) {
        InternalStatusCode statusCode = fromCode(code);
        if (statusCode != null) {
            return statusCode.getDescription();
        }
        return LocaleUtils.resolveMessage("message.collectors.unknown_status_code", Long.toHexString(code));
    }

    /**
     * 获取所有Good状态的状态码
     */
    public static List<InternalStatusCode> getGoodStatusCodes() {
        List<InternalStatusCode> result = new ArrayList<>();
        for (InternalStatusCode statusCode : VALUES) {
            if (statusCode.isGood()) {
                result.add(statusCode);
            }
        }
        return result;
    }

    /**
     * 获取所有Bad状态的状态码
     */
    public static List<InternalStatusCode> getBadStatusCodes() {
        List<InternalStatusCode> result = new ArrayList<>();
        for (InternalStatusCode statusCode : VALUES) {
            if (statusCode.isBad()) {
                result.add(statusCode);
            }
        }
        return result;
    }

    /**
     * 获取所有Uncertain状态的状态码
     */
    public static List<InternalStatusCode> getUncertainStatusCodes() {
        List<InternalStatusCode> result = new ArrayList<>();
        for (InternalStatusCode statusCode : VALUES) {
            if (statusCode.isUncertain()) {
                result.add(statusCode);
            }
        }
        return result;
    }

    /**
     * 检查状态码是否表示成功
     *
     * @param code 状态码值
     * @return 是否成功
     */
    public static boolean isSuccess(long code) {
        return (code >>> 30) == 0; // Good状态
    }

    /**
     * 检查状态码是否表示失败
     *
     * @param code 状态码值
     * @return 是否失败
     */
    public static boolean isFailure(long code) {
        return (code >>> 30) == 2; // Bad状态
    }

    @Override
    public String toString() {
        return String.format("%s(0x%08X): %s", name(), code, getDescription());
    }
}
