package io.brix.platform.admin.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mail.javamail.JavaMailSender;

import io.brix.platform.admin.service.EmailSetupLinkNotifier;
import io.runtime.sdk.capability.NotificationCapability;

class PlatformAdminAutoConfigurationTest {

    private final PlatformAdminAutoConfiguration autoConfiguration = new PlatformAdminAutoConfiguration();
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final PlatformAdminSetupProperties setupProperties = mock(PlatformAdminSetupProperties.class);

    @Test
    void smtpNotifierConditionRejectsBlankSmtpHost() {
        assertFalse(matchesSmtpHost(null));
        assertFalse(matchesSmtpHost(" "));
        assertTrue(matchesSmtpHost("mailpit"));
    }

    @Test
    void smtpNotifierRequiresNonBlankMailFrom() {
        when(setupProperties.getMailFrom()).thenReturn("");

        assertThrows(IllegalStateException.class, () ->
                autoConfiguration.emailSetupLinkNotifier(mailSender, setupProperties));
    }

    @Test
    void smtpNotifierIsCreatedWhenDeliveryConfigIsComplete() {
        when(setupProperties.getMailFrom()).thenReturn("platform-admin@example.invalid");

        NotificationCapability notifier =
                autoConfiguration.emailSetupLinkNotifier(mailSender, setupProperties);

        assertInstanceOf(EmailSetupLinkNotifier.class, notifier);
    }

    private static boolean matchesSmtpHost(String host) {
        MockEnvironment environment = new MockEnvironment();
        if (host != null) {
            environment.setProperty("spring.mail.host", host);
        }
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return new PlatformAdminAutoConfiguration.NonBlankSmtpHostCondition()
                .matches(context, mock(AnnotatedTypeMetadata.class));
    }
}
