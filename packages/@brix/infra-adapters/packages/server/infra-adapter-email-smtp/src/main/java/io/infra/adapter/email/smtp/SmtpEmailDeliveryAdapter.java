package io.infra.adapter.email.smtp;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMailMessage;

import io.brix.platform.notification.delivery.EmailDeliveryAdapter;
import io.brix.platform.notification.delivery.EmailDeliveryException;
import io.brix.platform.notification.delivery.EmailDeliveryException.Reason;
import io.brix.platform.notification.delivery.EmailMessage;
import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.sdk.capability.NotificationException;

/**
 * SMTP-backed implementation of the email delivery adapter port.
 *
 * <p>Every exchange with the server is bounded (root BRX-RS-RESILIENCE-001; notification decision D104,
 * standard-link point 14a):</p>
 * <ul>
 *   <li>each connect, read and write has its own timeout: {@code brix.infra.email.smtp.connect-timeout},
 *   {@code read-timeout} and {@code write-timeout}, 5, 10 and 10 seconds unless the host sets others. They are set
 *   on the mail sender itself, so the connections of the mail health check are bounded too;</li>
 *   <li>the whole delivery ends at the deadline of the call it runs in, or after {@code send-timeout}, 30 seconds
 *   unless the host sets another, whichever comes first. Then the adapter closes the connection: the server does
 *   not accept a message whose end it has not acknowledged, and the delivery fails as a timeout. When that moment
 *   has passed already, the adapter does not connect.</li>
 * </ul>
 * <p>The server accepts a message when it acknowledges the message's end. Saying goodbye can fail after that,
 * and the delivery still counts as accepted: failing it then would roll back the caller's write while the
 * message is on its way.</p>
 *
 * <p>The adapter sets the timeouts and the socket factory of the sender's JavaMail session, and turns off JavaMail's
 * fallback from that factory: when a connect through it fails, JavaMail would otherwise connect again on a socket of
 * its own, which the deadline cannot close. A host that sets any of these in {@code spring.mail.properties}, or a
 * proxy the adapter cannot reach the sockets of, is refused at startup.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class SmtpEmailDeliveryAdapter implements EmailDeliveryAdapter, AutoCloseable {

    /** The JavaMail settings the adapter makes itself, after {@code mail.<protocol>}. */
    private static final List<String> OWNED = List.of(".connectiontimeout", ".timeout", ".writetimeout",
            ".socketFactory", ".socketFactory.class", ".socketFactory.fallback", ".ssl.socketFactory",
            ".ssl.socketFactory.class", ".proxy.host", ".socks.host");

    /** An SMTP reply code at the start of a server reply. */
    private static final Pattern REPLY_CODE = Pattern.compile("^\\s*([2-5]\\d\\d)(?:[\\s-]|$)");

    private final JavaMailSenderImpl mailSender;
    private final SmtpEmailAdapterProperties properties;
    private final String protocol;
    private final ScheduledThreadPoolExecutor deadlines;

    /**
     * Creates an SMTP delivery adapter, and bounds the sender's connections.
     *
     * @param mailSender Spring mail sender; a {@link JavaMailSenderImpl} with a host
     * @param properties SMTP adapter properties
     */
    public SmtpEmailDeliveryAdapter(JavaMailSender mailSender, SmtpEmailAdapterProperties properties) {
        this.properties = validate(properties);
        this.mailSender = sender(mailSender);
        this.protocol = protocol(this.mailSender);
        bound(this.mailSender, protocol, this.properties);
        this.deadlines = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "brix-smtp-deadline");
            thread.setDaemon(true);
            return thread;
        });
        this.deadlines.setRemoveOnCancelPolicy(true);
    }

    @Override
    public void deliver(EmailMessage message) {
        Duration timeLeft = timeLeft();
        if (timeLeft.isZero() || timeLeft.isNegative()) {
            throw new EmailDeliveryException(Reason.TIMEOUT, "the deadline had passed before connecting", null);
        }
        MimeMessage mime = mime(message);
        try (SmtpExchange exchange = SmtpExchange.open(deadlines, timeLeft)) {
            Transport transport = null;
            try {
                transport = mailSender.getSession().getTransport(protocol);
                transport.connect(mailSender.getHost(), mailSender.getPort(), username(), password());
                transport.sendMessage(mime, mime.getAllRecipients());
            } catch (MessagingException ex) {
                throw failure(ex, exchange.expired());
            } finally {
                quit(transport);
            }
        }
    }

    /** Stops the thread that ends deliveries at their deadline. */
    @Override
    public void close() {
        deadlines.shutdownNow();
    }

    /** The earlier of the adapter's send timeout and the deadline of the call the delivery runs in. */
    private Duration timeLeft() {
        Instant now = Instant.now();
        Instant own = now.plus(properties.getSendTimeout());
        Instant deadline = RuntimeInvocations.current().map(RuntimeInvocation::deadline)
                .filter(outer -> outer.isBefore(own))
                .orElse(own);
        return Duration.between(now, deadline);
    }

    private MimeMessage mime(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.getFrom().trim());
        mail.setTo(message.recipientEmail());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        MimeMessage mime = mailSender.createMimeMessage();
        try {
            mail.copyTo(new MimeMailMessage(mime));
            mime.setSentDate(new Date());
            mime.saveChanges();
            return mime;
        } catch (MessagingException | MailException ex) {
            // The parse failure quotes the address; the cause stays out of logs.
            throw new EmailDeliveryException(Reason.FAILED, "the message could not be built", null);
        }
    }

    /** The user name as {@link JavaMailSenderImpl} passes it: an empty one means none. */
    private String username() {
        String username = mailSender.getUsername();
        return "".equals(username) ? null : username;
    }

    private String password() {
        String password = mailSender.getPassword();
        return "".equals(mailSender.getUsername()) && "".equals(password) ? null : password;
    }

    /**
     * Classifies a failed delivery. A refusal quotes the server's reply, and a reply can quote the full address, so a
     * failure with a refusal in it keeps only the reply code, not the cause.
     */
    private static EmailDeliveryException failure(MessagingException ex, boolean expired) {
        String code = null;
        String timedOut = null;
        boolean refused = false;
        int depth = 0;
        for (Throwable cause = ex; cause != null && depth < 16; cause = cause.getCause(), depth++) {
            refused |= cause instanceof SendFailedException || cause instanceof AuthenticationFailedException;
            if (timedOut == null && cause instanceof SocketTimeoutException timeout) {
                timedOut = timeout.getMessage() == null ? "socket timeout" : timeout.getMessage();
            }
            if (code == null && cause.getMessage() != null) {
                Matcher reply = REPLY_CODE.matcher(cause.getMessage());
                code = reply.find() ? reply.group(1) : null;
            }
        }
        Throwable kept = refused ? null : ex;
        if (expired) {
            return new EmailDeliveryException(Reason.TIMEOUT, "the delivery's time ran out", kept);
        }
        if (timedOut != null) {
            return new EmailDeliveryException(Reason.TIMEOUT, timedOut, kept);
        }
        if (refused) {
            return new EmailDeliveryException(Reason.REJECTED, code == null ? "refused" : "reply " + code, null);
        }
        return new EmailDeliveryException(Reason.FAILED, ex.getClass().getSimpleName(), ex);
    }

    /** Says goodbye. The deadline still bounds it; a failure here does not undo an accepted message. */
    private static void quit(Transport transport) {
        if (transport == null) {
            return;
        }
        try {
            transport.close();
        } catch (MessagingException | RuntimeException ignored) {
            // The server accepted the message when it acknowledged its end, or the delivery has failed already.
        }
    }

    private static JavaMailSenderImpl sender(JavaMailSender mailSender) {
        if (!(mailSender instanceof JavaMailSenderImpl sender)) {
            throw unavailable("mailSender");
        }
        if (isBlank(sender.getHost())) {
            throw unavailable("host");
        }
        return sender;
    }

    private static String protocol(JavaMailSenderImpl sender) {
        String protocol = sender.getProtocol() != null ? sender.getProtocol()
                : sender.getJavaMailProperties().getProperty("mail.transport.protocol",
                        JavaMailSenderImpl.DEFAULT_PROTOCOL);
        if (!"smtp".equals(protocol) && !"smtps".equals(protocol)) {
            throw unavailable("protocol");
        }
        return protocol;
    }

    /** Sets the timeouts and the socket factory on the sender's session; its next session is made with them. */
    private static void bound(JavaMailSenderImpl sender, String protocol, SmtpEmailAdapterProperties properties) {
        Properties original = sender.getJavaMailProperties();
        Properties mail = new Properties();
        mail.putAll(original);
        for (String name : original.stringPropertyNames()) {
            mail.setProperty(name, original.getProperty(name));
        }
        String prefix = "mail." + protocol;
        for (String key : OWNED) {
            if (mail.get(prefix + key) != null) {
                throw unavailable(prefix + key);
            }
        }
        mail.setProperty(prefix + ".connectiontimeout", Long.toString(properties.getConnectTimeout().toMillis()));
        mail.setProperty(prefix + ".timeout", Long.toString(properties.getReadTimeout().toMillis()));
        mail.setProperty(prefix + ".writetimeout", Long.toString(properties.getWriteTimeout().toMillis()));
        mail.put(prefix + ".socketFactory", new ExchangeSocketFactory());
        // A connect through the factory that the deadline ended must not be tried again on a socket it cannot close.
        mail.setProperty(prefix + ".socketFactory.fallback", "false");
        sender.setJavaMailProperties(mail);
    }

    private static SmtpEmailAdapterProperties validate(SmtpEmailAdapterProperties properties) {
        if (properties == null
                || isBlank(properties.getFrom())
                || properties.getFrom().contains("\r")
                || properties.getFrom().contains("\n")) {
            throw unavailable("from");
        }
        if (!properties.isTlsEnabled()) {
            throw unavailable("tls");
        }
        if (!properties.isCertificateHostnameValidation()) {
            throw unavailable("certificateHostnameValidation");
        }
        requireMillis(properties.getConnectTimeout(), "connectTimeout");
        requireMillis(properties.getReadTimeout(), "readTimeout");
        requireMillis(properties.getWriteTimeout(), "writeTimeout");
        requireMillis(properties.getSendTimeout(), "sendTimeout");
        return properties;
    }

    /** A timeout JavaMail can take: at least a millisecond, and an int of milliseconds. */
    private static void requireMillis(Duration timeout, String name) {
        if (timeout == null || timeout.toMillis() < 1 || timeout.toMillis() > Integer.MAX_VALUE) {
            throw unavailable(name);
        }
    }

    private static NotificationException unavailable(String config) {
        return new NotificationException(NotificationException.Code.DELIVERY_UNAVAILABLE, Map.of("config", config));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
