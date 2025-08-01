package org.jetlinks.collector;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.ChannelException;
import jakarta.validation.ValidationException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.channels.ClosedChannelException;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.format.DateTimeParseException;
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
    public static long inferErrorCode(Throwable error) {
        if (error == null) {
            return InternalStatusCode.Bad_InternalError.getCode();
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
}
