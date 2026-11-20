package org.jetlinks.collector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {
    @Test
    void copyKeepsStatusAndSharesOnlyHeaderValues() {
        var failure = Result.error(new IllegalStateException("source failed",
            new IllegalArgumentException("cause")));
        var first = failure.copy("first").withPointId("p1");
        var second = failure.copy("second").withPointId("p2");

        assertEquals(failure.getCode(), first.getCode());
        assertFalse(first.isSuccess());
        assertEquals("first", first.getData());
        assertEquals("second", second.getData());
        assertSame(failure.getHeader("errorStack").orElseThrow(), first.getHeader("errorStack").orElseThrow());
        first.addHeader("custom", "changed");
        assertTrue(failure.getHeader("custom").isEmpty());
        assertTrue(second.getHeader("custom").isEmpty());
        assertTrue(failure.getHeader(CollectorConstants.Headers.pointId).isEmpty());
        assertEquals("p2", second.getHeader(CollectorConstants.Headers.pointId).orElseThrow());

        var success = Result.success(1).copy(2);
        assertTrue(success.isSuccess());
        assertEquals(InternalStatusCode.Good.getCode(), success.getCode());
        assertEquals(2, success.getData());
        assertNull(success.getHeaders());
    }

    @Test
    void reusingAResultReplacesItsPreviousFailureAndHandlesNull() {
        var result = Result.success(1).with(new java.net.ConnectException("connection refused"));
        assertEquals("error.connection_refused", result.getHeader(CollectorConstants.Headers.reason).orElseThrow());
        result.with(new java.util.concurrent.TimeoutException("request timed out"));
        assertEquals(InternalStatusCode.Bad_Timeout.getCode(), result.getCode());
        assertEquals("error.request_timeout", result.getHeader(CollectorConstants.Headers.reason).orElseThrow());
        result.with(null);
        assertEquals(InternalStatusCode.Bad_InternalError.getCode(), result.getCode());
        assertEquals("error.internal_error", result.getHeader(CollectorConstants.Headers.reason).orElseThrow());
        assertFalse(result.isSuccess());
    }
}
