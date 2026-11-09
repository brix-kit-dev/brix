package io.brix.platform.notification;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import io.brix.platform.notification.delivery.EmailDeliveryException;

/**
 * The delivery meters (notification decision D107, standard-link point 14a): {@value #SENDS}, deliveries and how
 * long they took, by {@code template} and {@code outcome}.
 *
 * <p>Each known template starts with every outcome at zero. A series that first appears at its first failure
 * shows Prometheus no increase, so an alert on failures would miss the first one after a start.</p>
 *
 * @author Brix Platform Team
 * @since 3.3.0
 */
public final class MicrometerNotificationMetrics implements NotificationMetrics {

    /** Deliveries, by template and outcome. */
    public static final String SENDS = "brix.notification.sends";

    private static final List<String> OUTCOMES = List.of(SENT, EmailDeliveryException.Reason.REJECTED.tag(),
            EmailDeliveryException.Reason.TIMEOUT.tag(), EmailDeliveryException.Reason.FAILED.tag());

    private final Supplier<MeterRegistry> registry;

    /**
     * Creates the meters, and starts the known templates at zero.
     *
     * @param registry supplies the meter registry; records nothing while it supplies {@code null}
     * @param templateKeys the templates to start at zero
     */
    public MicrometerNotificationMetrics(Supplier<MeterRegistry> registry, Collection<String> templateKeys) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        MeterRegistry meters = registry.get();
        if (meters != null) {
            for (String templateKey : templateKeys) {
                for (String outcome : OUTCOMES) {
                    timer(meters, templateKey, outcome);
                }
            }
        }
    }

    @Override
    public void delivered(String templateKey, String outcome, Duration took) {
        MeterRegistry meters = registry.get();
        if (meters != null) {
            timer(meters, templateKey, outcome).record(took);
        }
    }

    private static Timer timer(MeterRegistry meters, String templateKey, String outcome) {
        return Timer.builder(SENDS)
                .description("Notifications handed to the delivery adapter, and how long delivery took")
                .tag("template", templateKey)
                .tag("outcome", outcome)
                .register(meters);
    }
}
