package com.kira.jstoragemark.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class MetricsCollectorTest {

    private MetricsCollector collector;

    @AfterEach
    void tearDown() {
        if (collector != null) {
            collector.stop();
        }
    }

    @Test
    @DisplayName("Constructor should not throw")
    void constructorShouldNotThrow() {
        assertThatNoException().isThrownBy(() ->
                collector = new MetricsCollector(Duration.ofMillis(1000)));
    }

    @Test
    @DisplayName("getMetricsLog should return empty list before start")
    void getMetricsLogShouldBeEmptyBeforeStart() {
        collector = new MetricsCollector(Duration.ofMillis(1000));
        assertThat(collector.getMetricsLog()).isEmpty();
    }

    @Test
    @DisplayName("getMetricsLog should return unmodifiable list")
    void getMetricsLogShouldBeUnmodifiable() {
        collector = new MetricsCollector(Duration.ofMillis(1000));
        assertThatThrownBy(() -> collector.getMetricsLog().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("start and stop should complete without error")
    void startAndStopShouldComplete() {
        collector = new MetricsCollector(Duration.ofMillis(5000));
        assertThatNoException().isThrownBy(() -> {
            collector.start();
            Thread.sleep(100);
            collector.stop();
        });
    }

    @Test
    @DisplayName("start should be idempotent with subsequent stop")
    void startShouldBeIdempotent() {
        collector = new MetricsCollector(Duration.ofMillis(5000));
        assertThatNoException().isThrownBy(() -> {
            collector.start();
            collector.start();
            Thread.sleep(50);
            collector.stop();
        });
    }

    @Test
    @DisplayName("stop on idle collector should complete without error")
    void stopOnIdleCollectorShouldComplete() {
        collector = new MetricsCollector(Duration.ofMillis(1000));
        assertThatNoException().isThrownBy(() -> collector.stop());
    }
}
