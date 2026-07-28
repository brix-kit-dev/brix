package io.brix.platform.notification.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.brix.platform.notification.NotificationApplicationService;
import io.brix.platform.notification.delivery.EmailDeliveryAdapter;
import io.brix.platform.notification.template.StrictTemplateRenderer;
import io.brix.platform.notification.template.TemplateRepository;
import io.runtime.sdk.capability.NotificationCapability;

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
     * @return notification capability
     */
    @Bean
    public NotificationCapability notificationCapability(
            EmailDeliveryAdapter emailDeliveryAdapter,
            PlatformNotificationProperties properties) {
        ObjectMapper objectMapper = new ObjectMapper();
        ClassLoader classLoader = PlatformNotificationAutoConfiguration.class.getClassLoader();
        return new NotificationApplicationService(
                new TemplateRepository(objectMapper, classLoader),
                new StrictTemplateRenderer(),
                emailDeliveryAdapter,
                properties.getDefaultLocale());
    }
}
