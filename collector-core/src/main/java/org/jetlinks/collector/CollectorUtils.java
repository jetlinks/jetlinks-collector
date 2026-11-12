package org.jetlinks.collector;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.ChannelException;
import jakarta.validation.ValidationException;
import org.hswebframework.web.exception.I18nSupportException;
import org.jetlinks.collector.exception.CollectorException;
import org.jetlinks.collector.exception.CollectorFailure;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.channels.ClosedChannelException;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import javax.naming.AuthenticationException;

/**
 * 数据收集器工具类
 * 提供错误码编码、解码等工具方法
 */
public class CollectorUtils {

    /**
     * 根据异常类型推断对应的错误状态码
     *
     * @param error 异常对象
     * @return 对应的状态码
     */
    /**
     * 异常上已经写明的国际化键。平台异常取 {@code i18nCode}，普通异常取形如 {@code error.xxx} 或 {@code modbus.功能.异常码} 的消息。
     */
    public static String reasonKey(Throwable error) {
        if (error == null) {
            return null;
        }
        if (error instanceof I18nSupportException i18n && isReasonKey(i18n.getI18nCode())) {
            return i18n.getI18nCode();
        }
        String message = error.getMessage();
        if (message == null) {
            return null;
        }
        String token = message.trim();
        int space = token.indexOf(' ');
        if (space > 0) {
            token = token.substring(0, space);
        }
        return isReasonKey(token) ? token : null;
    }

    /**
     * 结果原因：优先用异常自带的键，否则按推断出的状态码给一个平台 {@code error.*} 键。
     */
    public static String reasonOf(Throwable error) {
        String key = reasonKey(error);
        if (key != null) {
            return key;
        }
        return genericReason(inferErrorCode(error));
    }

    public static long inferErrorCode(Throwable error) {
        if (error == null) {
            return InternalStatusCode.Bad_InternalError.getCode();
        }
        // 插件已经选好的状态码优先于按消息猜。
        if (error instanceof CollectorFailure failure && failure.getStatusCode() != 0) {
            return failure.getStatusCode();
        }
        if (error instanceof CollectorException collectorException && collectorException.getCode() != 0) {
            return collectorException.getCode();
        }
        Long fromKey = codeFromKey(reasonKey(error));
        if (fromKey != null) {
            return fromKey;
        }

        // === 网络连接相关异常 ===
        if (error instanceof ConnectException || error instanceof NoRouteToHostException) {
            return InternalStatusCode.Bad_ConnectionRefused.getCode();
        }
        if (error instanceof UnknownHostException) {
            return InternalStatusCode.Bad_ServerNotConnected.getCode();
        }
        if (error instanceof SocketTimeoutException) {
            return InternalStatusCode.Bad_Timeout.getCode();
        }
        if (error instanceof BindException) {
            return InternalStatusCode.Bad_ResourceUnavailable.getCode();
        }
        if (error instanceof ClosedChannelException) {
            return InternalStatusCode.Bad_ConnectionLost.getCode();
        }
        if (error instanceof PortUnreachableException) {
            return InternalStatusCode.Bad_ConnectionError.getCode();
        }
        if (error instanceof ProtocolException) {
            return InternalStatusCode.Bad_ProtocolError.getCode();
        }

        // === 超时相关异常 ===
        if (error instanceof TimeoutException || error instanceof io.netty.handler.timeout.TimeoutException) {
            return InternalStatusCode.Bad_Timeout.getCode();
        }
        // 通信错误
        if(error instanceof ChannelException){
            return InternalStatusCode.Bad_CommunicationError.getCode();
        }

        // === 通信相关异常 ===
        if (error instanceof UnsupportedEncodingException) {
            return InternalStatusCode.Bad_EncodingError.getCode();
        }

        if (error instanceof JsonParseException) {
            return InternalStatusCode.Bad_DecodingError.getCode();
        }

        if (error instanceof JsonProcessingException) {
            return InternalStatusCode.Bad_EncodingError.getCode();
        }

        if (error instanceof IOException) {
            // 检查具体的IO异常类型
            String message = error.getMessage();
            if (message != null) {
                message = message.toLowerCase();
                if (message.contains("connection") && message.contains("refused")) {
                    return InternalStatusCode.Bad_ConnectionRefused.getCode();
                }
                if (message.contains("connection") && (message.contains("reset") || message.contains("abort"))) {
                    return InternalStatusCode.Bad_ConnectionLost.getCode();
                }
                if (message.contains("timeout")) {
                    return InternalStatusCode.Bad_Timeout.getCode();
                }
            }
            return InternalStatusCode.Bad_CommunicationError.getCode();
        }

        // === 编解码相关异常 ===


        // === 数据格式相关异常 ===
        if (error instanceof NumberFormatException ||
            error instanceof ParseException ||
            error instanceof DateTimeParseException) {
            return InternalStatusCode.Bad_DataFormatError.getCode();
        }
        if (error instanceof ClassCastException) {
            return InternalStatusCode.Bad_DataTypeIdUnknown.getCode();
        }

        // === 配置和验证相关异常 ===
        if (error instanceof ValidationException) {
            return InternalStatusCode.Bad_ConfigurationError.getCode();
        }
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            return InternalStatusCode.Bad_InvalidParameter.getCode();
        }

        // === 安全相关异常 ===
        if (error instanceof SecurityException) {
            return InternalStatusCode.Bad_UserAccessDenied.getCode();
        }
        if (error instanceof AuthenticationException) {
            return InternalStatusCode.Bad_IdentityTokenInvalid.getCode();
        }

        // === 资源相关异常 ===
        if (error instanceof OutOfMemoryError) {
            return InternalStatusCode.Bad_ResourceUnavailable.getCode();
        }
        if (error instanceof RejectedExecutionException) {
            return InternalStatusCode.Bad_TooManyOperations.getCode();
        }

        // === 数据库相关异常 ===
        if (error instanceof SQLException) {
            return InternalStatusCode.Bad_CommunicationError.getCode();
        }

        // === 中断异常 ===
        if (error instanceof InterruptedException) {
            return InternalStatusCode.Bad_Shutdown.getCode();
        }

        // === 不支持的操作 ===
        if (error instanceof UnsupportedOperationException) {
            return InternalStatusCode.Bad_ServiceUnsupported.getCode();
        }

        // === 根据异常消息进一步推断 ===
        String message = error.getMessage();
        if (message != null) {
            message = message.toLowerCase();

            if (message.contains("connect closed") || message.contains("connection closed")) {
                return InternalStatusCode.Bad_ConnectionLost.getCode();
            }
            if (message.contains("no response")) {
                return InternalStatusCode.Bad_NoCommunication.getCode();
            }

            // 连接相关
            if (message.contains("connection")) {
                if (message.contains("timeout") || message.contains("timed out")) {
                    return InternalStatusCode.Bad_Timeout.getCode();
                }
                if (message.contains("refused") || message.contains("rejected")) {
                    return InternalStatusCode.Bad_ConnectionRefused.getCode();
                }
                if (message.contains("lost") || message.contains("broken") || message.contains("closed")) {
                    return InternalStatusCode.Bad_ConnectionLost.getCode();
                }
                return InternalStatusCode.Bad_ConnectionError.getCode();
            }

            // 协议相关
            if (message.contains("protocol") || message.contains("version")) {
                return InternalStatusCode.Bad_ProtocolError.getCode();
            }

            // 认证相关
            if (message.contains("authentication") || message.contains("unauthorized") || message.contains("access denied")) {
                return InternalStatusCode.Bad_UserAccessDenied.getCode();
            }

            // 编解码相关
            if (message.contains("encode") || message.contains("encoding")) {
                return InternalStatusCode.Bad_EncodingError.getCode();
            }
            if (message.contains("decode") || message.contains("decoding") || message.contains("parse")) {
                return InternalStatusCode.Bad_DecodingError.getCode();
            }

            // 配置相关
            if (message.contains("config") || message.contains("invalid") || message.contains("illegal")) {
                return InternalStatusCode.Bad_ConfigurationError.getCode();
            }

            // 超时相关
            if (message.contains("timeout") || message.contains("timed out")) {
                return InternalStatusCode.Bad_Timeout.getCode();
            }
        }

        // === 默认返回内部错误 ===
        return InternalStatusCode.Bad_InternalError.getCode();
    }

    private static boolean isReasonKey(String token) {
        return token != null && token.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    }

    /**
     * 键已经说明了失败类型时直接对应平台状态码，避免再被异常类型或英文子串带偏。
     */
    private static Long codeFromKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        Long deviceCode = deviceExceptionCode(normalized);
        if (deviceCode != null) {
            return deviceCode;
        }
        if (!normalized.startsWith("error.")) {
            return null;
        }
        if (normalized.contains("read_timeout")) {
            return InternalStatusCode.Bad_ReadTimeout.getCode();
        }
        if (normalized.contains("write_timeout")) {
            return InternalStatusCode.Bad_WriteTimeout.getCode();
        }
        if (normalized.contains("timeout")) {
            return InternalStatusCode.Bad_Timeout.getCode();
        }
        if (normalized.contains("connection_refused") || normalized.contains("connect_refused")) {
            return InternalStatusCode.Bad_ConnectionRefused.getCode();
        }
        if (normalized.contains("connection_lost")
            || normalized.contains("connect_closed")
            || normalized.contains("connection_closed")) {
            return InternalStatusCode.Bad_ConnectionLost.getCode();
        }
        if (normalized.contains("checksum")) {
            return InternalStatusCode.Bad_ChecksumError.getCode();
        }
        if (normalized.contains("not_readable")) {
            return InternalStatusCode.Bad_NotReadable.getCode();
        }
        if (normalized.contains("not_writable")
            || normalized.contains("function_not_writable")
            || normalized.contains("write_not_supported")
            || normalized.contains("unsupported_write")) {
            return InternalStatusCode.Bad_NotWritable.getCode();
        }
        if (normalized.contains("not_connected")
            || normalized.contains("not_started")
            || normalized.contains("no_communication")) {
            return InternalStatusCode.Bad_NoCommunication.getCode();
        }
        if (normalized.contains("disposed")) {
            return InternalStatusCode.Bad_ChannelError.getCode();
        }
        if (normalized.contains("communication")) {
            return InternalStatusCode.Bad_CommunicationError.getCode();
        }
        if (normalized.contains("protocol")
            || normalized.contains("header_pdu")
            || normalized.contains("command_unrecognized")
            || normalized.contains("incorrect_device_message")) {
            return InternalStatusCode.Bad_ProtocolError.getCode();
        }
        if (normalized.contains("config")) {
            return InternalStatusCode.Bad_ConfigurationError.getCode();
        }
        if (normalized.contains("out_of_range") || normalized.contains("address_range")) {
            return InternalStatusCode.Bad_OutOfRange.getCode();
        }
        if (normalized.contains("address")) {
            return InternalStatusCode.Bad_InvalidAddress.getCode();
        }
        if (normalized.contains("access_denied") || normalized.contains("access_restricted")) {
            return InternalStatusCode.Bad_AccessDenied.getCode();
        }
        if (normalized.contains("auth")) {
            return InternalStatusCode.Bad_AuthenticationFailed.getCode();
        }
        if (normalized.contains("busy")) {
            return InternalStatusCode.Bad_ChannelBusy.getCode();
        }
        if (normalized.contains("not_supported")
            || normalized.contains("unsupported")
            || normalized.contains("unrecognized")) {
            return InternalStatusCode.Bad_NotSupported.getCode();
        }
        if (normalized.contains("not_implemented")) {
            return InternalStatusCode.Bad_NotImplemented.getCode();
        }
        if (normalized.contains("decode")
            || normalized.contains("parse_failed")
            || normalized.contains("origin_missing")) {
            return InternalStatusCode.Bad_DecodingError.getCode();
        }
        if (normalized.contains("encode")) {
            return InternalStatusCode.Bad_EncodingError.getCode();
        }
        if (normalized.contains("offline")) {
            return InternalStatusCode.Bad_DeviceOffline.getCode();
        }
        if (normalized.contains("not_found")) {
            return InternalStatusCode.Bad_NotFound.getCode();
        }
        if (normalized.contains("connection") || normalized.contains("connect")) {
            return InternalStatusCode.Bad_ConnectionError.getCode();
        }
        if (normalized.contains("invalid")
            || normalized.contains("required")
            || normalized.contains("mismatch")
            || normalized.contains("empty")) {
            return InternalStatusCode.Bad_InvalidParameter.getCode();
        }
        if (normalized.contains("write_failed") || normalized.contains("device")) {
            return InternalStatusCode.Bad_DeviceError.getCode();
        }
        return InternalStatusCode.Bad_UnexpectedError.getCode();
    }

    /**
     * Modbus 及其派生协议把设备异常码放在消息最后一段，例如 {@code modbus.ReadHoldingRegisters.IllegalDataAddress}。
     */
    private static Long deviceExceptionCode(String key) {
        int dot = key.lastIndexOf('.');
        if (dot < 0 || dot == key.length() - 1) {
            return null;
        }
        return switch (key.substring(dot + 1)) {
            case "illegalfunction" -> InternalStatusCode.Bad_NotSupported.getCode();
            case "illegaldataaddress" -> InternalStatusCode.Bad_InvalidAddress.getCode();
            case "illegaldatavalue" -> InternalStatusCode.Bad_OutOfRange.getCode();
            case "slavedevicefailure" -> InternalStatusCode.Bad_DeviceFailure.getCode();
            case "acknowledge" -> InternalStatusCode.Bad_WaitingForInitialData.getCode();
            case "slavedevicebusy" -> InternalStatusCode.Bad_ChannelBusy.getCode();
            case "memoryparityerror" -> InternalStatusCode.Bad_ChecksumError.getCode();
            case "gatewaypathunavailable", "gatewaytargetdevicefailedtoresponse" ->
                InternalStatusCode.Bad_NoCommunication.getCode();
            case "errorresponse" -> InternalStatusCode.Bad_DeviceError.getCode();
            default -> null;
        };
    }

    private static String genericReason(long code) {
        InternalStatusCode status = InternalStatusCode.fromCode(code);
        if (status == null) {
            return "error.internal_error";
        }
        return switch (status) {
            case Bad_Timeout, Bad_ReadTimeout, Bad_WriteTimeout -> "error.request_timeout";
            case Bad_ConnectionRefused -> "error.connection_refused";
            case Bad_ConnectionLost -> "error.connection_lost";
            case Bad_ConnectionError, Bad_NoCommunication, Bad_ServerNotConnected -> "error.connection";
            case Bad_CommunicationError -> "error.communication";
            case Bad_ProtocolError, Bad_ChecksumError -> "error.protocol";
            case Bad_ConfigurationError -> "error.configuration";
            case Bad_InvalidParameter, Bad_InvalidAddress, Bad_OutOfRange -> "error.invalid_parameter";
            case Bad_NotSupported, Bad_ServiceUnsupported, Bad_NotImplemented -> "error.unsupported";
            case Bad_DecodingError, Bad_EncodingError, Bad_DataFormatError, Bad_DecoderError, Bad_EncoderError ->
                "error.decoding";
            case Bad_DeviceFailure, Bad_DeviceError, Bad_SensorFailure -> "error.device_failure";
            case Bad_ChannelBusy, Bad_TooManyOperations -> "error.channel_busy";
            case Bad_AccessDenied, Bad_UserAccessDenied, Bad_AuthenticationFailed -> "error.access_denied";
            case Bad_ChannelError, Bad_ObjectDeleted, Bad_CollectorNotRunning -> "error.channel_error";
            default -> "error.internal_error";
        };
    }
}
