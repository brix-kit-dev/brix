package io.infra.adapter.email.smtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import io.brix.platform.notification.delivery.EmailMessage;
import io.runtime.sdk.capability.NotificationException;

class SmtpEmailDeliveryAdapterTest {

    @Test
    void sendsPlainTextMailWithTrustedFrom() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AtomicReference<SimpleMailMessage> captured = new AtomicReference<>();
        doAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        SmtpEmailDeliveryAdapter adapter = new SmtpEmailDeliveryAdapter(mailSender, validProperties());
        adapter.deliver(new EmailMessage("admin@example.invalid", "Subject", "Body"));

        SimpleMailMessage message = captured.get();
        assertEquals("platform@example.invalid", message.getFrom());
        assertEquals("admin@example.invalid", message.getTo()[0]);
        assertEquals("Subject", message.getSubject());
        assertEquals("Body", message.getText());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void rejectsMissingFrom() {
        SmtpEmailAdapterProperties properties = validProperties();
        properties.setFrom(" ");

        NotificationException ex = assertThrows(NotificationException.class, () ->
                new SmtpEmailDeliveryAdapter(mock(JavaMailSender.class), properties));

        assertEquals(NotificationException.Code.DELIVERY_UNAVAILABLE, ex.errorCode());
    }

    @Test
    void rejectsDisabledTls() {
        SmtpEmailAdapterProperties properties = validProperties();
        properties.setTlsEnabled(false);

        NotificationException ex = assertThrows(NotificationException.class, () ->
                new SmtpEmailDeliveryAdapter(mock(JavaMailSender.class), properties));

        assertEquals(NotificationException.Code.DELIVERY_UNAVAILABLE, ex.errorCode());
    }

    @Test
    void rejectsDisabledHostnameValidation() {
        SmtpEmailAdapterProperties properties = validProperties();
        properties.setCertificateHostnameValidation(false);

        NotificationException ex = assertThrows(NotificationException.class, () ->
                new SmtpEmailDeliveryAdapter(mock(JavaMailSender.class), properties));

        assertEquals(NotificationException.Code.DELIVERY_UNAVAILABLE, ex.errorCode());
    }

    private static SmtpEmailAdapterProperties validProperties() {
        SmtpEmailAdapterProperties properties = new SmtpEmailAdapterProperties();
        properties.setFrom("platform@example.invalid");
        properties.setTlsEnabled(true);
        properties.setCertificateHostnameValidation(true);
        return properties;
    }
}
