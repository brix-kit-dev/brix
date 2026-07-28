package io.infra.adapter.email.smtp;

import java.util.Map;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import io.brix.platform.notification.delivery.EmailDeliveryAdapter;
import io.brix.platform.notification.delivery.EmailMessage;
import io.runtime.sdk.capability.NotificationException;

/**
 * SMTP-backed implementation of the email delivery adapter port.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class SmtpEmailDeliveryAdapter implements EmailDeliveryAdapter {

    private final JavaMailSender mailSender;
    private final SmtpEmailAdapterProperties properties;

    /**
     * Creates an SMTP delivery adapter.
     *
     * @param mailSender Spring mail sender
     * @param properties SMTP adapter properties
     */
    public SmtpEmailDeliveryAdapter(JavaMailSender mailSender, SmtpEmailAdapterProperties properties) {
        this.mailSender = mailSender;
        this.properties = validate(properties);
    }

    @Override
    public void deliver(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.getFrom().trim());
        mail.setTo(message.recipientEmail());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        try {
            mailSender.send(mail);
        } catch (MailException ex) {
            throw new NotificationException(
                    NotificationException.Code.DELIVERY_FAILED,
                    Map.of(),
                    ex);
        }
    }

    private static SmtpEmailAdapterProperties validate(SmtpEmailAdapterProperties properties) {
        if (properties == null
                || isBlank(properties.getFrom())
                || properties.getFrom().contains("\r")
                || properties.getFrom().contains("\n")) {
            throw new NotificationException(
                    NotificationException.Code.DELIVERY_UNAVAILABLE,
                    Map.of("config", "from"));
        }
        if (!properties.isTlsEnabled()) {
            throw new NotificationException(
                    NotificationException.Code.DELIVERY_UNAVAILABLE,
                    Map.of("config", "tls"));
        }
        if (!properties.isCertificateHostnameValidation()) {
            throw new NotificationException(
                    NotificationException.Code.DELIVERY_UNAVAILABLE,
                    Map.of("config", "certificateHostnameValidation"));
        }
        return properties;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
