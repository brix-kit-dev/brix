package io.brix.platform.notification.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.brix.platform.notification.MicrometerNotificationMetrics;
import io.brix.platform.notification.NotificationMetrics;
import io.brix.platform.notification.delivery.EmailDeliveryAdapter;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.capability.NotificationRequest;
import io.runtime.sdk.capability.NotificationTemplateKeys;

/**
 * The delivery meters are wired when the host has Micrometer, with the platform's templates at zero, and the
 * capability still starts without it (standard-link point 14a, D107).
 */
class PlatformNotificationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformNotificationAutoConfiguration.class))
            .withBean(EmailDeliveryAdapter.class, () -> message -> { });

    @Test
    void wiresTheMetersWithThePlatformsTemplatesAtZero() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new).run(context -> {
            assertThat(context).hasSingleBean(NotificationCapability.class);
            assertThat(context.getBean(NotificationMetrics.class)).isInstanceOf(MicrometerNotificationMetrics.class);
            MeterRegistry registry = context.getBean(MeterRegistry.class);
            assertThat(registry.find(MicrometerNotificationMetrics.SENDS).timers()).hasSize(16);
            assertThat(registry.find(MicrometerNotificationMetrics.SENDS)
                    .tags("template", "tenant.owner.invitation.initial", "outcome", "rejected").timer())
                    .isNotNull();

            context.getBean(NotificationCapability.class).send(new NotificationRequest(null, "admin@example.invalid",
                    NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL, "en-US",
                    Map.of("setupUrl", "https://setup.example.invalid/token/raw-secret")));

            assertThat(registry.find(MicrometerNotificationMetrics.SENDS)
                    .tags("template", NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL, "outcome", "sent")
                    .timer().count()).isEqualTo(1L);
        });
    }

    @Test
    void startsWithoutMicrometer() {
        runner.withClassLoader(new FilteredClassLoader(MeterRegistry.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NotificationCapability.class);
            assertThat(context).doesNotHaveBean(NotificationMetrics.class);
        });
    }
}
