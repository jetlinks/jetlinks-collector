package org.jetlinks.collector;

/**
 * 状态码接口
 * <p>
 * 定义了通用的状态码标准接口，用于表示各种操作和过程的状态。
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>数据采集过程中的状态反馈</li>
 *   <li>设备连接状态的表示</li>
 *   <li>错误码的统一管理和国际化</li>
 *   <li>系统间状态码的标准化</li>
 *   <li>日志记录和监控</li>
 * </ul>
 *
 * <h3>实现要求：</h3>
 * <ul>
 *   <li>必须提供国际化支持</li>
 *   <li>状态码值应具有唯一性</li>
 *   <li>应提供便捷的状态检查方法</li>
 *   <li>支持扩展和自定义状态码</li>
 * </ul>
 *
 * @author zhouhao
 * @author AI
 * @see InternalStatusCode
 * @since 1.0
 */
public interface StatusCode {

    /**
     * 获取状态码的数值
     *
     * @return 32位状态码值，遵循标准状态码结构规范
     */
    long getCode();

    /**
     * 检查是否为Good状态
     * <p>
     * Good状态表示操作成功或状态良好
     * 通常表示：
     * </p>
     * <ul>
     *   <li>操作执行成功</li>
     *   <li>系统状态良好</li>
     *   <li>数据获取正常</li>
     *   <li>连接建立成功</li>
     * </ul>
     *
     * @return true表示Good状态，false表示其他状态
     */
    boolean isGood();

    /**
     * 检查是否为Bad状态
     * <p>
     * Bad状态表示操作失败或状态错误
     * 通常表示：
     * </p>
     * <ul>
     *   <li>操作执行失败</li>
     *   <li>系统错误</li>
     *   <li>连接建立失败</li>
     *   <li>数据获取异常</li>
     * </ul>
     *
     * @return true表示Bad状态，false表示其他状态
     */
    boolean isBad();

    /**
     * 检查是否为Uncertain状态
     * <p>
     * Uncertain状态表示状态不确定
     * 通常表示：
     * </p>
     * <ul>
     *   <li>数据质量不确定</li>
     *   <li>系统状态不稳定</li>
     *   <li>部分操作成功</li>
     *   <li>需要进一步验证</li>
     * </ul>
     *
     * @return true表示Uncertain状态，false表示其他状态
     */
    boolean isUncertain();

    /**
     * 获取状态码的名称
     * <p>
     * 状态码名称通常采用驼峰命名法，如：
     * </p>
     * <ul>
     *   <li>Good - 表示成功状态</li>
     *   <li>Bad_Timeout - 表示超时错误</li>
     *   <li>Uncertain_LastUsableValue - 表示不确定但可使用最后值</li>
     *   <li>Bad_ConnectionError - 表示连接错误</li>
     * </ul>
     *
     * @return 状态码的名称，用于标识和调试
     */
    String getName();

    /**
     * 获取状态码的描述信息
     * <p>
     * 描述信息支持国际化，会根据当前语言环境返回相应的描述。
     * 描述信息应该：
     * </p>
     * <ul>
     *   <li>清晰表达状态的含义</li>
     *   <li>提供足够的上下文信息</li>
     *   <li>支持多语言</li>
     *   <li>便于用户理解</li>
     * </ul>
     *
     * @return 状态码的本地化描述信息
     */
    String getDescription();

}
