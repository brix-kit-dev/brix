package io.brix.platform.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.List;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

/**
 * The delivery meters start every known template and outcome at zero, so the first failure after a start shows
 * up as an increase (standard-link point 14a, D107).
 */
class MicrometerNotificationMetricsTest {

    @Test
    void startsEveryKnownTemplateAndOutcomeAtZero() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new MicrometerNotificationMetrics(() -> registry,
                List.of("platform.admin.setup.initial", "tenant.owner.setup.initial"));

        for (String template : List.of("platform.admin.setup.initial", "tenant.owner.setup.initial")) {
            for (String outcome : List.of("sent", "rejected", "timeout", "failed")) {
                Timer timer = registry.find(MicrometerNotificationMetrics.SENDS)
                        .tags("template", template, "outcome", outcome).timer();
                assertNotNull(timer, template + " " + outcome);
                assertEquals(0, timer.count());
            }
        }
        assertEquals(8, registry.find(MicrometerNotificationMetrics.SENDS).timers().size());
    }

    @Test
    void countsAndTimesADelivery() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNotificationMetrics metrics =
                new MicrometerNotificationMetrics(() -> registry, List.of("platform.admin.setup.initial"));

        metrics.delivered("platform.admin.setup.initial", "timeout", Duration.ofMillis(1500));

        Timer timer = registry.find(MicrometerNotificationMetrics.SENDS)
                .tags("template", "platform.admin.setup.initial", "outcome", "timeout").timer();
        assertEquals(1, timer.count());
        assertEquals(1500, (long) timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    void recordsNothingWithoutARegistry() {
        MicrometerNotificationMetrics metrics =
                new MicrometerNotificationMetrics(() -> null, List.of("platform.admin.setup.initial"));

        metrics.delivered("platform.admin.setup.initial", "sent", Duration.ofMillis(10));
    }
}
