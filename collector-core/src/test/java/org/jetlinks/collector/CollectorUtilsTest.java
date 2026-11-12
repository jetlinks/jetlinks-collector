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
