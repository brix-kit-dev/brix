/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.platform.admin.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mail.javamail.JavaMailSender;

import io.brix.platform.admin.service.EmailSetupLinkNotifier;
import io.brix.platform.admin.service.LoggingNotificationCapability;
import io.runtime.sdk.capability.NotificationCapability;

/**
 * Auto-configuration for the Platform Admin module.
 *
 * <h3>Activation</h3>
 * <p>Automatically activated when {@code platform-admin} is on the classpath
 * and the application is a Servlet-based web application
 * ({@code ConditionalOnWebApplication.Type.SERVLET}).
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C — Implementation. Registers all controllers, services, and
 * repositories defined in the {@code io.brix.platform.admin} package tree.
 *
 * <h3>Architecture Red Lines</h3>
 * <ul>
 *   <li>R-1: This module MUST NOT depend on any {@code enterprise-*} module.</li>
 *   <li>R-2: {@code enterprise-solutions} MUST NOT depend on this module.</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@AutoConfiguration(after = MailSenderAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ComponentScan(basePackages = "io.brix.platform.admin")
public class PlatformAdminAutoConfiguration {

    /**
     * SMTP-backed setup-link delivery.
     *
     * <p>This bean is declared in auto-configuration instead of relying on
     * component-scan conditions so it is evaluated after Spring Boot mail
     * auto-configuration has had a chance to provide {@link JavaMailSender}.</p>
     */
    @Bean
    @Conditional(NonBlankSmtpHostCondition.class)
    @ConditionalOnMissingBean(NotificationCapability.class)
    public NotificationCapability emailSetupLinkNotifier(
            JavaMailSender mailSender,
            PlatformAdminSetupProperties setupProperties) {
        String mailFrom = trimToNull(setupProperties.getMailFrom());
        if (mailFrom == null) {
            throw new IllegalStateException(
                    "brix.platform.admin.setup.mail-from is required for SMTP setup-link delivery");
        }
        return new EmailSetupLinkNotifier(mailSender, setupProperties);
    }

    static final class NonBlankSmtpHostCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return trimToNull(context.getEnvironment().getProperty("spring.mail.host")) != null;
        }
    }

    /**
     * Explicit development-only setup-link delivery fallback.
     *
     * <p>Production deployments must provide a real {@link NotificationCapability}
     * bean (for example SMTP). This fallback is opt-in so a misconfigured
     * standalone deployment cannot report {@code setupLinkSent=true} while only
     * writing the setup URL to server logs.</p>
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "brix.platform.admin.setup",
            name = "logging-notification-enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(NotificationCapability.class)
    public NotificationCapability loggingNotificationCapability(Environment environment) {
        return new LoggingNotificationCapability(environment);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
