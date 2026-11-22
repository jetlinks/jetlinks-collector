package org.jetlinks.collector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorEditorResourceTest {
    private final AbstractDataCollectorProvider provider = new AbstractDataCollectorProvider(Object.class, Object.class, Object.class) {
        @Override public String getId() { return "org"; }
        @Override public String getName() { return "resource-test"; }
        @Override public Mono<ChannelRuntime> createChannel(ChannelConfiguration config) { return Mono.empty(); }
        @Override public Mono<CollectorRuntime> createCollector(CollectorConfiguration config) { return Mono.empty(); }
        @Override public Mono<PointRuntime> createPoint(PointConfiguration config) { return Mono.empty(); }
    };

    @ParameterizedTest
    @ValueSource(strings = {
        "../org/jetlinks/collector/AbstractDataCollectorProvider.class",
        "/../org/jetlinks/collector/AbstractDataCollectorProvider.class",
        "\\..\\org\\jetlinks\\collector\\AbstractDataCollectorProvider.class"
    })
    void traversalCannotReadResourcesOutsideTheProvider(String path) {
        StepVerifier.create(provider.getEditorResource(path, "fixture", AbstractDataCollectorProvider.class)
                .doOnNext(DataBufferUtils::release).count())
            .expectNext(0L).verifyComplete();
    }

    @Test
    void aResourceWithinTheProviderRemainsReadable() {
        StepVerifier.create(provider.getEditorResource("/jetlinks/collector/AbstractDataCollectorProvider.class",
                "org", AbstractDataCollectorProvider.class).doOnNext(DataBufferUtils::release).count())
            .assertNext(count -> assertTrue(count > 0)).verifyComplete();
    }

    @Test
    void missingResourcesRemainEmpty() {
        StepVerifier.create(provider.getEditorResource("/absent.html", "fixture", AbstractDataCollectorProvider.class)
                .doOnNext(DataBufferUtils::release).count())
            .expectNext(0L).verifyComplete();
    }
}
