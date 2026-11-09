package io.brix.platform.notification.autoconfigure;

import java.util.List;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.brix.platform.notification.MicrometerNotificationMetrics;
import io.brix.platform.notification.NotificationApplicationService;
import io.brix.platform.notification.NotificationMetrics;
import io.brix.platform.notification.delivery.EmailDeliveryAdapter;
import io.brix.platform.notification.template.StrictTemplateRenderer;
import io.brix.platform.notification.template.TemplateRepository;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.capability.NotificationTemplateKeys;

/**
 * Spring Boot auto-configuration for the managed notification capability.
 *
 * <p>This configuration requires an {@link EmailDeliveryAdapter}. It does not
 * install hidden fallback providers.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@AutoConfiguration
@ConditionalOnClass(NotificationCapability.class)
@ConditionalOnProperty(prefix = "brix.platform.notification", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PlatformNotificationProperties.class)
public class PlatformNotificationAutoConfiguration {

    /**
     * Registers the managed notification capability.
     *
     * @param emailDeliveryAdapter required email delivery adapter
     * @param properties notification properties
     * @param metrics the delivery meters, when the host has Micrometer
     * @return notification capability
     */
    @Bean
    public NotificationCapability notificationCapability(
            EmailDeliveryAdapter emailDeliveryAdapter,
            PlatformNotificationProperties properties,
            ObjectProvider<NotificationMetrics> metrics) {
        ObjectMapper objectMapper = JsonMapper.builder().configureForJackson2().build();
        ClassLoader classLoader = PlatformNotificationAutoConfiguration.class.getClassLoader();
        return new NotificationApplicationService(
                new TemplateRepository(objectMapper, classLoader),
                new StrictTemplateRenderer(),
                emailDeliveryAdapter,
                properties.getDefaultLocale(),
                metrics.getIfAvailable(() -> NotificationMetrics.NOOP));
    }

    /** Delivery meters, when the host has Micrometer (notification decision D107, standard-link point 14a). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class NotificationMeters {

        /**
         * Registers the delivery meters, with the platform's templates started at zero.
         *
         * @param registry the host's meter registry
         * @return the meters
         */
        @Bean
        @ConditionalOnMissingBean
        NotificationMetrics notificationMetrics(ObjectProvider<MeterRegistry> registry) {
            return new MicrometerNotificationMetrics(registry::getIfAvailable, List.of(
                    NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL,
                    NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_PASSWORD_RESET,
                    NotificationTemplateKeys.TENANT_OWNER_INVITATION_INITIAL,
                    NotificationTemplateKeys.TENANT_OWNER_SETUP_INITIAL));
        }
    }
}
