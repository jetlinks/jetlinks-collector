package org.jetlinks.collector;

import org.jetlinks.collector.exception.ChannelException;
import org.jetlinks.collector.exception.CollectorException;
import org.jetlinks.collector.exception.CollectorFailure;
import org.jetlinks.collector.exception.PointException;
import org.junit.jupiter.api.Test;

import javax.naming.OperationNotSupportedException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorUtilsTest {

    @Test
    void disposedKeyUsesChannelErrorAndKeepsReason() {
        IllegalStateException error = new IllegalStateException("error.melsec_point_disposed");

        Result<Void> result = Result.error(error);

        assertEquals(InternalStatusCode.Bad_ChannelError.getCode(), result.getCode());
        assertEquals("error.melsec_point_disposed", result.getHeader(CollectorConstants.Headers.reason).orElse(null));
    }

    @Test
    void communicationKeyIsNotInternalError() {
        OperationNotSupportedException error = new OperationNotSupportedException("error.melsec_communication_error");

        Result<Void> result = Result.error(error);

        assertEquals(InternalStatusCode.Bad_CommunicationError.getCode(), result.getCode());
        assertEquals("error.melsec_communication_error", result.getHeader(CollectorConstants.Headers.reason).orElse(null));
    }

    @Test
    void requestTimeoutKeyStaysTimeout() {
        TimeoutException error = new TimeoutException("error.xinjie_request_timeout");

        Result<Void> result = Result.error(error);

        assertEquals(InternalStatusCode.Bad_Timeout.getCode(), result.getCode());
        assertEquals("error.xinjie_request_timeout", result.getHeader(CollectorConstants.Headers.reason).orElse(null));
    }

    @Test
    void modbusExceptionCodeIsAddressErrorNotConfiguration() {
        RuntimeException error = new RuntimeException("modbus.ReadHoldingRegisters.IllegalDataAddress");

        Result<Void> result = Result.error(error);

        assertEquals(InternalStatusCode.Bad_InvalidAddress.getCode(), result.getCode());
        assertEquals("modbus.ReadHoldingRegisters.IllegalDataAddress",
                     result.getHeader(CollectorConstants.Headers.reason).orElse(null));
    }

    @Test
    void deltaExceptionCodeUsesTheSameDeviceMapping() {
        assertEquals(InternalStatusCode.Bad_DeviceFailure.getCode(),
                     CollectorUtils.inferErrorCode(new Exception("delta.ReadHoldingRegisters.SlaveDeviceFailure")));
    }

    @Test
    void closedConnectionWithoutConnectionWordIsConnectionLost() {
        RuntimeException error = new RuntimeException("modbus connect closed");

        Result<Void> result = Result.error(error);

        assertEquals(InternalStatusCode.Bad_ConnectionLost.getCode(), result.getCode());
        assertEquals("error.connection_lost", result.getHeader(CollectorConstants.Headers.reason).orElse(null));
    }

    @Test
    void englishTimeoutGetsPlatformTimeoutReason() {
        Result<Void> result = Result.error(new TimeoutException("request timed out after 2000ms milliseconds."));

        assertEquals(InternalStatusCode.Bad_Timeout.getCode(), result.getCode());
        assertEquals("error.request_timeout", result.getHeader(CollectorConstants.Headers.reason).orElse(null));
    }

    @Test
    void quantityKeyIsOutOfRange() {
        assertEquals(InternalStatusCode.Bad_OutOfRange.getCode(),
                     CollectorUtils.inferErrorCode(new IllegalArgumentException("error.modbus_quantity_out_of_range")));
    }

    @Test
    void collectorExceptionKeepsItsCode() {
        CollectorException error = new CollectorException(
            "collector",
            "error.collector.out_of_codec_index",
            null,
            InternalStatusCode.Bad_DecodingError.getCode(),
            1,
            2);

        Result<Void> result = Result.error(error);

        assertEquals(InternalStatusCode.Bad_DecodingError.getCode(), result.getCode());
        assertEquals("error.collector.out_of_codec_index", result.getHeader(CollectorConstants.Headers.reason).orElse(null));
    }

    @Test
    void connectExceptionStaysRefused() {
        Result<Void> result = Result.error(new ConnectException("Connection refused"));

        assertEquals(InternalStatusCode.Bad_ConnectionRefused.getCode(), result.getCode());
        assertEquals("error.connection_refused", result.getHeader(CollectorConstants.Headers.reason).orElse(null));
    }

    @Test
    void explicitFailureKeepsTheStatusChosenByThePlugin() {
        CollectorFailure error = new CollectorFailure(InternalStatusCode.Bad_ReadTimeout, "error.hart_timeout");

        Result<Void> result = Result.error(error);

        assertEquals(InternalStatusCode.Bad_ReadTimeout.getCode(), result.getCode());
        assertEquals("error.hart_timeout", result.getHeader(CollectorConstants.Headers.reason).orElse(null));
    }

    @Test
    void wrappedDeclaredFailureKeepsItsCodeAndReason() {
        Throwable error = new IllegalStateException("error.handle_serial_port_error",
            new java.util.concurrent.CompletionException(new CollectorFailure(
                InternalStatusCode.Bad_TooManyOperations, "error.channel_busy")));

        Result<Void> result = Result.error(error);

        assertEquals(InternalStatusCode.Bad_TooManyOperations.getCode(), result.getCode());
        assertEquals("error.channel_busy", result.getHeader(CollectorConstants.Headers.reason).orElse(null));
    }

    @Test
    void wrappedCollectorExceptionKeepsItsDeclaredCode() {
        Throwable error = new RuntimeException(new CollectorException(
            "collector", InternalStatusCode.Bad_ConnectionLost, "error.connection_lost"));

        assertEquals(InternalStatusCode.Bad_ConnectionLost.getCode(), CollectorUtils.inferErrorCode(error));
        assertEquals("error.connection_lost", CollectorUtils.reasonOf(error));
    }

    @Test
    void outerDeclaredFailureWinsOverItsCause() {
        Throwable error = new CollectorFailure(InternalStatusCode.Bad_DeviceError, "error.device_error",
            new CollectorFailure(InternalStatusCode.Bad_TooManyOperations, "error.channel_busy"));

        assertEquals(InternalStatusCode.Bad_DeviceError.getCode(), CollectorUtils.inferErrorCode(error));
        assertEquals("error.device_error", CollectorUtils.reasonOf(error));
    }

    @Test
    void circularCausesDoNotRecurseOrChangeOrdinaryInference() {
        Throwable outer = new IllegalStateException("ordinary wrapper");
        Throwable inner = new RuntimeException("inner", outer);
        outer.initCause(inner);

        assertEquals(InternalStatusCode.Bad_InvalidParameter.getCode(), CollectorUtils.inferErrorCode(outer));
        assertEquals("error.invalid_parameter", CollectorUtils.reasonOf(outer));
    }

    @Test
    void zeroStatusStillFindsDeclaredCauseOrInfersTheReason() {
        var declared = new CollectorFailure(InternalStatusCode.Bad_DeviceError, "error.device_error");
        var wrapper = new CollectorFailure("error.read_timeout", 0, declared) {};
        assertEquals(InternalStatusCode.Bad_DeviceError.getCode(), CollectorUtils.inferErrorCode(wrapper));
        assertEquals("error.device_error", CollectorUtils.reasonOf(wrapper));

        var undeclared = new CollectorFailure("error.read_timeout", 0, null) {};
        assertEquals(InternalStatusCode.Bad_ReadTimeout.getCode(), CollectorUtils.inferErrorCode(undeclared));
        assertEquals("error.read_timeout", CollectorUtils.reasonOf(undeclared));
        assertEquals(InternalStatusCode.Bad_InternalError.getCode(), CollectorUtils.inferErrorCode(null));
        assertNull(CollectorUtils.reasonKey(null));
    }

    @Test
    void reasonKeysKeepTheirSyntaxAndMessageTokenHandling() {
        for (String key : new String[]{"error.read_timeout", "modbus.ReadHoldingRegisters.IllegalDataAddress", "a.0_B.c"}) {
            assertEquals(key, CollectorUtils.reasonKey(new RuntimeException("  " + key + " details")));
        }
        for (String message : new String[]{"", "ordinary", ".invalid", "error.", "error..invalid", "1.invalid", "error.bad!", "error.中文"}) {
            assertNull(CollectorUtils.reasonKey(new RuntimeException(message)), message);
        }
    }

    @Test
    void pointAndChannelExceptionsCarryTheirStatus() {
        Result<Void> point = Result.error(new PointException(
            "holding-0", InternalStatusCode.Bad_ObjectDeleted, "error.modbus_point_disposed"));
        Result<Void> channel = Result.error(new ChannelException(
            "modbus-tcp", InternalStatusCode.Bad_ConnectionLost, "error.modbus_connection_lost"));

        assertEquals(InternalStatusCode.Bad_ObjectDeleted.getCode(), point.getCode());
        assertEquals("error.modbus_point_disposed", point.getHeader(CollectorConstants.Headers.reason).orElse(null));
        assertEquals(InternalStatusCode.Bad_ConnectionLost.getCode(), channel.getCode());
        assertEquals("error.modbus_connection_lost", channel.getHeader(CollectorConstants.Headers.reason).orElse(null));
    }

    @Test
    void unknownStatusCodeLookupIsEmpty() {
        assertFalse(StatusCodes.findCode(1L).isPresent());
        assertTrue(StatusCodes.findCode(InternalStatusCode.Bad_Timeout.getCode()).isPresent());
    }
}
