package org.jetlinks.collector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {
    static class CountedFailure extends IllegalStateException {
        final java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        @Override public StackTraceElement[] getStackTrace() { reads.incrementAndGet(); return super.getStackTrace(); }
    }

    @Test
    void errorDetailsAreLazyAndCopiesShareOneConcurrentMaterialization() {
        var error = new CountedFailure();
        var result = Result.error(error).withPointId("p");
        var copy = result.copy("copy");
        assertTrue(result.getHeader("errorType").isPresent());
        assertEquals("p", result.getHeader(CollectorConstants.Headers.pointId).orElseThrow());
        assertEquals(0, error.reads.get());
        var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> result.getHeader("errorStack").orElseThrow());
        var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> copy.getHeaders().get("errorStack"));
        assertInstanceOf(String.class, first.join());
        assertSame(first.join(), second.join());
        assertEquals(1, error.reads.get());
        assertInstanceOf(String.class, result.computeHeader("errorStack", (key, value) -> value));
    }

    @Test
    void exposedOrSuppliedMapsNeverReceiveLazyHeaderValues() {
        var result = Result.success(1).withPointId("p");
        var exposed = result.getHeaders();
        var first = new CountedFailure();
        result.with(first);
        assertInstanceOf(String.class, exposed.get("errorStack"));
        assertEquals(1, first.reads.get());
        var supplied = new java.util.concurrent.ConcurrentHashMap<String,Object>();
        result = Result.success(1);
        result.setHeaders(supplied);
        result.with(new CountedFailure());
        assertInstanceOf(String.class, supplied.get("errorStack"));
    }

    @Test
    void headerComputationReceivesTheOriginalStringContract() {
        var error = new CountedFailure();
        var result = Result.error(error);
        assertEquals(0, error.reads.get());
        Object computed = result.computeHeader("errorStack", (key, value) -> {
            assertInstanceOf(String.class, value);
            return value + " extra";
        });
        assertEquals(computed, result.getHeaders().get("errorStack"));
        assertEquals(1, error.reads.get());
    }

    @Test
    void replacementRemovalAndExplicitValuesDoNotForceDiscardedDetails() {
        var first = new CountedFailure(); var second = new CountedFailure();
        var result = Result.error(first).with(second);
        result.removeHeader("errorStack");
        assertTrue(result.getHeader("errorStack").isEmpty());
        assertEquals(0, first.reads.get()); assertEquals(0, second.reads.get());
        result.with(first).addHeader("errorStack", "explicit");
        assertEquals("explicit", result.getHeaders().get("errorStack"));
        assertEquals(0, first.reads.get());
    }

    @Test
    void externalizationAndJsonKeepStringHeaders() throws Exception {
        var result = Result.error(new CountedFailure());
        var buffer = new java.io.ByteArrayOutputStream();
        try (var output = new java.io.ObjectOutputStream(buffer)) { output.writeObject(result); }
        try (var input = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(buffer.toByteArray()))) {
            var restored = (Result<?>) input.readObject();
            assertEquals(result.getHeader("errorStack"), restored.getHeader("errorStack"));
        }
        String json = com.alibaba.fastjson.JSON.toJSONString(Result.error(new CountedFailure()));
        assertInstanceOf(String.class, com.alibaba.fastjson.JSON.parseObject(json).getJSONObject("headers").get("errorStack"));
    }
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
