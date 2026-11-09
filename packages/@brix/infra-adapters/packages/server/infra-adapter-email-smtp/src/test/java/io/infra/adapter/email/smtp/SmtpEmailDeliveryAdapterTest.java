package io.infra.adapter.email.smtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;

import jakarta.mail.MessagingException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import io.brix.platform.notification.delivery.EmailDeliveryException;
import io.brix.platform.notification.delivery.EmailMessage;
import io.runtime.sdk.capability.NotificationException;

/**
 * The SMTP adapter's own timeouts and the settings it refuses (standard-link point 14a, D104), and how it names a
 * failure (D107).
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class SmtpEmailDeliveryAdapterTest {

    @Test
    void sendsPlainTextMailWithTrustedFrom() throws IOException {
        try (FakeSmtpServer server = FakeSmtpServer.start(FakeSmtpServer.Behaviour.NORMAL);
             SmtpEmailDeliveryAdapter adapter = new SmtpEmailDeliveryAdapter(sender(server.port()), properties())) {
            adapter.deliver(new EmailMessage("admin@example.invalid", "Subject", "Body"));

            assertEquals(1, server.accepted().size());
            String data = server.accepted().get(0);
            assertTrue(data.contains("From: platform@example.invalid"), data);
            assertTrue(data.contains("To: admin@example.invalid"), data);
            assertTrue(data.contains("Subject: Subject"), data);
            assertTrue(data.contains("\nBody\n"), data);
        }
    }

    @Test
    void setsItsTimeoutsAndSocketsOnTheSender() {
        JavaMailSenderImpl sender = sender(25);
        try (SmtpEmailDeliveryAdapter ignored = new SmtpEmailDeliveryAdapter(sender, properties())) {
            assertEquals("5000", sender.getJavaMailProperties().getProperty("mail.smtp.connectiontimeout"));
            assertEquals("10000", sender.getJavaMailProperties().getProperty("mail.smtp.timeout"));
            assertEquals("10000", sender.getJavaMailProperties().getProperty("mail.smtp.writetimeout"));
            assertInstanceOf(SocketFactory.class, sender.getJavaMailProperties().get("mail.smtp.socketFactory"));
            assertEquals("false", sender.getJavaMailProperties().getProperty("mail.smtp.socketFactory.fallback"));
            assertEquals("5000", sender.getSession().getProperty("mail.smtp.connectiontimeout"));
        }
    }

    @Test
    void setsThemUnderTheProtocolInUse() {
        JavaMailSenderImpl sender = sender(465);
        sender.setProtocol("smtps");
        try (SmtpEmailDeliveryAdapter ignored = new SmtpEmailDeliveryAdapter(sender, properties())) {
            assertEquals("5000", sender.getJavaMailProperties().getProperty("mail.smtps.connectiontimeout"));
            assertEquals("10000", sender.getJavaMailProperties().getProperty("mail.smtps.timeout"));
            assertEquals("10000", sender.getJavaMailProperties().getProperty("mail.smtps.writetimeout"));
            assertEquals("false", sender.getJavaMailProperties().getProperty("mail.smtps.socketFactory.fallback"));
            assertNull(sender.getJavaMailProperties().getProperty("mail.smtp.timeout"));
        }
    }

    @Test
    void keepsTheHostsOtherJavaMailSettings() {
        JavaMailSenderImpl sender = sender(25);
        sender.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", "true");
        try (SmtpEmailDeliveryAdapter ignored = new SmtpEmailDeliveryAdapter(sender, properties())) {
            assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"));
        }
    }

    @Test
    void aConnectTheServerNeverTakesEndsAtTheConnectTimeout() throws IOException {
        SmtpEmailAdapterProperties properties = properties();
        properties.setConnectTimeout(Duration.ofMillis(300));
        // Without the connect timeout, the send timeout would end it, after five seconds.
        properties.setSendTimeout(Duration.ofSeconds(5));
        try (HeldBackPort port = HeldBackPort.open();
             SmtpEmailDeliveryAdapter adapter = new SmtpEmailDeliveryAdapter(sender(port.port()), properties)) {
            assumeTrue(port.holdsBack(), "this platform does not hold back a connect to a full accept queue");
            long started = System.nanoTime();
            EmailDeliveryException ex = assertThrows(EmailDeliveryException.class, () -> adapter.deliver(message()));
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            assertEquals(EmailDeliveryException.Reason.TIMEOUT, ex.reason());
            assertTrue(took.compareTo(Duration.ofMillis(250)) >= 0, took.toString());
            assertTrue(took.compareTo(Duration.ofSeconds(3)) < 0, took.toString());
        }
    }

    @Test
    void aServerThatNeverAnswersEndsAtTheReadTimeout() throws IOException {
        SmtpEmailAdapterProperties properties = properties();
        properties.setReadTimeout(Duration.ofMillis(300));
        try (FakeSmtpServer server = FakeSmtpServer.start(FakeSmtpServer.Behaviour.SILENT);
             SmtpEmailDeliveryAdapter adapter = new SmtpEmailDeliveryAdapter(sender(server.port()), properties)) {
            long started = System.nanoTime();
            EmailDeliveryException ex = assertThrows(EmailDeliveryException.class, () -> adapter.deliver(message()));
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            assertEquals(EmailDeliveryException.Reason.TIMEOUT, ex.reason());
            assertTrue(took.compareTo(Duration.ofMillis(250)) >= 0, took.toString());
            assertTrue(took.compareTo(Duration.ofSeconds(3)) < 0, took.toString());
        }
    }

    @Test
    void theMailHealthChecksConnectionIsBoundedToo() throws IOException {
        SmtpEmailAdapterProperties properties = properties();
        properties.setReadTimeout(Duration.ofMillis(300));
        try (FakeSmtpServer server = FakeSmtpServer.start(FakeSmtpServer.Behaviour.SILENT)) {
            JavaMailSenderImpl sender = sender(server.port());
            try (SmtpEmailDeliveryAdapter ignored = new SmtpEmailDeliveryAdapter(sender, properties)) {
                // No delivery's deadline covers the health check, only the read timeout does. Without it the check
                // would wait for good: the test gives up on it after three seconds, and closing the server frees it.
                assertTimeoutPreemptively(Duration.ofSeconds(3),
                        () -> assertThrows(MessagingException.class, sender::testConnection));
            }
        }
    }

    @Test
    void aRefusedRecipientIsRejectedWithItsReplyCodeButNotItsText() throws IOException {
        try (FakeSmtpServer server = FakeSmtpServer.start(FakeSmtpServer.Behaviour.REJECT_RECIPIENT);
             SmtpEmailDeliveryAdapter adapter = new SmtpEmailDeliveryAdapter(sender(server.port()), properties())) {
            EmailDeliveryException ex = assertThrows(EmailDeliveryException.class,
                    () -> adapter.deliver(new EmailMessage("nobody@example.invalid", "Subject", "Body")));

            assertEquals(EmailDeliveryException.Reason.REJECTED, ex.reason());
            assertEquals("email delivery rejected: reply 550", ex.getMessage());
            assertNull(ex.getCause());
            assertTrue(server.accepted().isEmpty());
        }
    }

    @Test
    void aServerThatCannotBeReachedFails() throws IOException {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = probe.getLocalPort();
        }
        try (SmtpEmailDeliveryAdapter adapter = new SmtpEmailDeliveryAdapter(sender(closedPort), properties())) {
            EmailDeliveryException ex = assertThrows(EmailDeliveryException.class, () -> adapter.deliver(message()));

            assertEquals(EmailDeliveryException.Reason.FAILED, ex.reason());
            assertFalse(ex.getMessage().contains("admin@example.invalid"));
        }
    }

    @Test
    void refusesASenderWhoseConnectionsItCannotBound() {
        NotificationException ex = assertThrows(NotificationException.class, () ->
                new SmtpEmailDeliveryAdapter(mock(JavaMailSender.class), properties()));

        assertEquals(NotificationException.Code.DELIVERY_UNAVAILABLE, ex.errorCode());
        assertEquals(Map.of("config", "mailSender"), ex.safeParameters());
    }

    @Test
    void refusesASenderWithoutAHost() {
        NotificationException ex = assertThrows(NotificationException.class, () ->
                new SmtpEmailDeliveryAdapter(new JavaMailSenderImpl(), properties()));

        assertEquals(Map.of("config", "host"), ex.safeParameters());
    }

    @Test
    void refusesAProtocolThatIsNotSmtp() {
        JavaMailSenderImpl sender = sender(25);
        sender.setProtocol("imap");
        NotificationException ex = assertThrows(NotificationException.class, () ->
                new SmtpEmailDeliveryAdapter(sender, properties()));

        assertEquals(Map.of("config", "protocol"), ex.safeParameters());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout",
        "mail.smtp.socketFactory.class", "mail.smtp.socketFactory.fallback", "mail.smtp.ssl.socketFactory.class",
        "mail.smtp.proxy.host", "mail.smtp.socks.host"})
    void refusesASettingItMakesItself(String key) {
        JavaMailSenderImpl sender = sender(25);
        sender.getJavaMailProperties().setProperty(key, "30000");
        NotificationException ex = assertThrows(NotificationException.class, () ->
                new SmtpEmailDeliveryAdapter(sender, properties()));

        assertEquals(NotificationException.Code.DELIVERY_UNAVAILABLE, ex.errorCode());
        assertEquals(Map.of("config", key), ex.safeParameters());
    }

    @Test
    void refusesASocketFactoryInstanceOfTheHosts() {
        JavaMailSenderImpl sender = sender(25);
        sender.getJavaMailProperties().put("mail.smtp.socketFactory", SocketFactory.getDefault());
        NotificationException ex = assertThrows(NotificationException.class, () ->
                new SmtpEmailDeliveryAdapter(sender, properties()));

        assertEquals(Map.of("config", "mail.smtp.socketFactory"), ex.safeParameters());
    }

    @ParameterizedTest
    @ValueSource(strings = {"connectTimeout", "readTimeout", "writeTimeout", "sendTimeout"})
    void refusesATimeLimitThatIsNotPositive(String name) {
        SmtpEmailAdapterProperties properties = properties();
        switch (name) {
            case "connectTimeout" -> properties.setConnectTimeout(Duration.ZERO);
            case "readTimeout" -> properties.setReadTimeout(Duration.ofSeconds(-1));
            case "writeTimeout" -> properties.setWriteTimeout(null);
            default -> properties.setSendTimeout(Duration.ZERO);
        }
        NotificationException ex = assertThrows(NotificationException.class, () ->
                new SmtpEmailDeliveryAdapter(sender(25), properties));

        assertEquals(Map.of("config", name), ex.safeParameters());
    }

    @Test
    void rejectsMissingFrom() {
        SmtpEmailAdapterProperties properties = properties();
        properties.setFrom(" ");

        NotificationException ex = assertThrows(NotificationException.class, () ->
                new SmtpEmailDeliveryAdapter(mock(JavaMailSender.class), properties));

        assertEquals(NotificationException.Code.DELIVERY_UNAVAILABLE, ex.errorCode());
    }

    @Test
    void rejectsDisabledTls() {
        SmtpEmailAdapterProperties properties = properties();
        properties.setTlsEnabled(false);

        NotificationException ex = assertThrows(NotificationException.class, () ->
                new SmtpEmailDeliveryAdapter(mock(JavaMailSender.class), properties));

        assertEquals(NotificationException.Code.DELIVERY_UNAVAILABLE, ex.errorCode());
    }

    @Test
    void rejectsDisabledHostnameValidation() {
        SmtpEmailAdapterProperties properties = properties();
        properties.setCertificateHostnameValidation(false);

        NotificationException ex = assertThrows(NotificationException.class, () ->
                new SmtpEmailDeliveryAdapter(mock(JavaMailSender.class), properties));

        assertEquals(NotificationException.Code.DELIVERY_UNAVAILABLE, ex.errorCode());
    }

    static JavaMailSenderImpl sender(int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(port);
        sender.setProtocol("smtp");
        sender.setDefaultEncoding("UTF-8");
        return sender;
    }

    static SmtpEmailAdapterProperties properties() {
        SmtpEmailAdapterProperties properties = new SmtpEmailAdapterProperties();
        properties.setFrom("platform@example.invalid");
        properties.setTlsEnabled(true);
        properties.setCertificateHostnameValidation(true);
        return properties;
    }

    static EmailMessage message() {
        return new EmailMessage("admin@example.invalid", "Subject", "Body");
    }
}
