package io.infra.adapter.email.smtp;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;

import io.brix.platform.notification.delivery.EmailDeliveryAdapter;

/**
 * Spring Boot auto-configuration for SMTP notification delivery.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@AutoConfiguration(after = MailSenderAutoConfiguration.class)
@ConditionalOnClass({JavaMailSender.class, EmailDeliveryAdapter.class})
@ConditionalOnProperty(prefix = "brix.infra.email.smtp", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SmtpEmailAdapterProperties.class)
public class SmtpEmailAdapterAutoConfiguration {

    /**
     * Registers the SMTP email delivery adapter.
     *
     * @param mailSender Java mail sender
     * @param properties SMTP adapter properties
     * @return email delivery adapter
     */
    @Bean
    public EmailDeliveryAdapter smtpEmailDeliveryAdapter(
            JavaMailSender mailSender,
            SmtpEmailAdapterProperties properties) {
        return new SmtpEmailDeliveryAdapter(mailSender, properties);
    }
}
