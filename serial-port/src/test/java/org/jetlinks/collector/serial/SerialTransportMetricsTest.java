package org.jetlinks.collector.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.metrics.MetricRegistration;
import org.jetlinks.core.monitor.metrics.Metrics;
import org.jetlinks.core.server.ConnectionMetrics;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SerialTransportMetricsTest {
    @Test
    void serialCallbacksExposeActualBytesAndRequestsAndReleaseRegistration() throws Exception {
        var port = mock(SerialPort.class);
        var parser = mock(PayloadParser.class);
        var config = mock(SerialPortConfig.class);
        var monitor = mock(Monitor.class);
        var registry = mock(Metrics.class);
        var registration = mock(MetricRegistration.class);
        var observed = new AtomicReference<ConnectionMetrics>();
        var written = Sinks.<Void>empty();
        var frames = Sinks.many().unicast().<ByteBuf>onBackpressureBuffer();
        when(port.isOpen()).thenReturn(true);
        when(port.addDataListener(any())).thenReturn(true);
        when(port.getSystemPortPath()).thenReturn("test-port");
        when(port.getOutputStream()).thenReturn(new OutputStream() {
            @Override public void write(int value) { }
            @Override public void write(byte[] bytes) { written.tryEmitEmpty(); }
        });
        when(config.getCommunicationInterval()).thenReturn(Duration.ZERO);
        when(parser.handlePayload()).thenReturn(frames.asFlux());
        doAnswer(call -> {frames.tryEmitNext(call.getArgument(0));return null;}).when(parser).handle(any());
        when(monitor.logger()).thenReturn(Monitor.noop().logger());
        when(monitor.metrics()).thenReturn(registry);
        when(registry.observe(eq("transport"), eq(ConnectionMetrics.class), any(ConnectionMetrics.class)))
            .thenAnswer(call -> {observed.set(call.getArgument(2));return registration;});
        var client = new DefaultSerialPortClient(config, port, parser, 32, monitor);
        var metrics = observed.get();
        try {
            StepVerifier.create(client.sendAndReceive(Unpooled.wrappedBuffer(new byte[]{1, 2, 3}), Duration.ofSeconds(5)))
                .then(() -> {
                    written.asMono().block(Duration.ofSeconds(5));
                    client.serialEvent(new SerialPortEvent(port, SerialPort.LISTENING_EVENT_DATA_RECEIVED, new byte[]{4, 5}));
                })
                .assertNext(value -> {assertEquals(2, value.readableBytes());value.release();})
                .verifyComplete();
            assertEquals(3, metrics.getWrittenBytes());assertEquals(2, metrics.getReadBytes());
            assertEquals(1, metrics.getTotalMessages());assertNotNull(metrics.getAckLastDuration());
            StepVerifier.create(client.sendAndReceive(Unpooled.wrappedBuffer(new byte[]{1}), Duration.ofMillis(20)))
                .expectError(RequestTimeoutException.class).verify(Duration.ofSeconds(5));
            assertEquals(1, metrics.getAckTimeouts());assertEquals(1, metrics.getRequestErrors());
            assertEquals(0, metrics.getPendingMessages());
            client.serialEvent(new SerialPortEvent(port, SerialPort.LISTENING_EVENT_PORT_DISCONNECTED));
            assertEquals(0, metrics.getActiveConnections());verify(registration).dispose();
        } finally { client.dispose(); }
    }
}
