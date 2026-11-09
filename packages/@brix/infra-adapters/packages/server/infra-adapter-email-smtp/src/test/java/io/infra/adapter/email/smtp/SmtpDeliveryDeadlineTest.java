package io.infra.adapter.email.smtp;

import static io.infra.adapter.email.smtp.SmtpEmailDeliveryAdapterTest.message;
import static io.infra.adapter.email.smtp.SmtpEmailDeliveryAdapterTest.properties;
import static io.infra.adapter.email.smtp.SmtpEmailDeliveryAdapterTest.sender;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.brix.platform.notification.delivery.EmailDeliveryException;
import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.sdk.plugin.AccessDecision;
import io.runtime.sdk.plugin.CallerIdentity;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.InvocationScope;

/**
 * A delivery as a whole ends by the deadline of the call it runs in, or by the send timeout outside one, and the
 * server does not get a message it has not acknowledged by then (standard-link point 14a, D104).
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class SmtpDeliveryDeadlineTest {

    @Test
    void endsAtTheCallsDeadlineBeforeTheServerHasTheMessage() throws IOException {
        // Each reply takes a second, well inside the read timeout; the call has 2.5 seconds.
        try (FakeSmtpServer server = FakeSmtpServer.start(FakeSmtpServer.Behaviour.SLOW, Duration.ofSeconds(1));
             SmtpEmailDeliveryAdapter adapter = new SmtpEmailDeliveryAdapter(sender(server.port()), properties())) {
            long started = System.nanoTime();
            EmailDeliveryException ex = assertThrows(EmailDeliveryException.class,
                    () -> within(Instant.now().plusMillis(2500), () -> adapter.deliver(message())));
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            assertEquals(EmailDeliveryException.Reason.TIMEOUT, ex.reason());
            assertTrue(took.compareTo(Duration.ofMillis(2400)) >= 0, took.toString());
            assertTrue(took.compareTo(Duration.ofMillis(4000)) < 0, took.toString());
            assertEquals(0, server.endsOfData());
            assertTrue(server.accepted().isEmpty());
        }
    }

    @Test
    void endsAtTheCallsDeadlineWhileTheServerHoldsBackTheConnect() throws IOException {
        // The connect timeout is five seconds and the call has one. Ending the connect at the deadline must not make
        // JavaMail connect again on a socket of its own, which nothing would close.
        try (HeldBackPort port = HeldBackPort.open();
             SmtpEmailDeliveryAdapter adapter = new SmtpEmailDeliveryAdapter(sender(port.port()), properties())) {
            assumeTrue(port.holdsBack(), "this platform does not hold back a connect to a full accept queue");
            long started = System.nanoTime();
            EmailDeliveryException ex = assertThrows(EmailDeliveryException.class,
                    () -> within(Instant.now().plusSeconds(1), () -> adapter.deliver(message())));
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            assertEquals(EmailDeliveryException.Reason.TIMEOUT, ex.reason());
            assertEquals("email delivery timeout: the delivery's time ran out", ex.getMessage());
            assertTrue(took.compareTo(Duration.ofMillis(900)) >= 0, took.toString());
            assertTrue(took.compareTo(Duration.ofMillis(3000)) < 0, took.toString());
        }
    }

    @Test
    void endsAtTheSendTimeoutOutsideAnyCall() throws IOException {
        SmtpEmailAdapterProperties properties = properties();
        properties.setSendTimeout(Duration.ofMillis(1500));
        try (FakeSmtpServer server = FakeSmtpServer.start(FakeSmtpServer.Behaviour.SLOW, Duration.ofSeconds(1));
             SmtpEmailDeliveryAdapter adapter = new SmtpEmailDeliveryAdapter(sender(server.port()), properties)) {
            long started = System.nanoTime();
            EmailDeliveryException ex = assertThrows(EmailDeliveryException.class, () -> adapter.deliver(message()));
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            assertEquals(EmailDeliveryException.Reason.TIMEOUT, ex.reason());
            assertTrue(took.compareTo(Duration.ofMillis(1400)) >= 0, took.toString());
            assertTrue(took.compareTo(Duration.ofMillis(3000)) < 0, took.toString());
            assertEquals(0, server.endsOfData());
        }
    }

    @Test
    void theSendTimeoutBoundsACallWithALaterDeadline() throws IOException {
        SmtpEmailAdapterProperties properties = properties();
        properties.setSendTimeout(Duration.ofMillis(1500));
        try (FakeSmtpServer server = FakeSmtpServer.start(FakeSmtpServer.Behaviour.SLOW, Duration.ofSeconds(1));
             SmtpEmailDeliveryAdapter adapter = new SmtpEmailDeliveryAdapter(sender(server.port()), properties)) {
            long started = System.nanoTime();
            assertThrows(EmailDeliveryException.class,
                    () -> within(Instant.now().plusSeconds(60), () -> adapter.deliver(message())));

            assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofMillis(3000)) < 0);
        }
    }

    @Test
    void doesNotConnectOnceTheDeadlineHasPassed() throws IOException {
        try (FakeSmtpServer server = FakeSmtpServer.start(FakeSmtpServer.Behaviour.NORMAL);
             SmtpEmailDeliveryAdapter adapter = new SmtpEmailDeliveryAdapter(sender(server.port()), properties())) {
            EmailDeliveryException ex = assertThrows(EmailDeliveryException.class,
                    () -> within(Instant.now().minusMillis(1), () -> adapter.deliver(message())));

            assertEquals(EmailDeliveryException.Reason.TIMEOUT, ex.reason());
            // The check before connecting stopped it, not the end of the delivery's time racing the connect; the
            // failure line names that step.
            assertEquals("email delivery timeout: the deadline had passed before connecting", ex.getMessage());
            assertEquals(0, server.connections());
        }
    }

    @Test
    void aGoodbyeThatHangsAfterTheServerAcceptedStillCountsAsSent() throws IOException {
        // The server accepts the message at once and answers QUIT only after five seconds; the delivery has one.
        SmtpEmailAdapterProperties properties = properties();
        properties.setSendTimeout(Duration.ofSeconds(1));
        try (FakeSmtpServer server = FakeSmtpServer.start(FakeSmtpServer.Behaviour.SLOW_QUIT, Duration.ofSeconds(5));
             SmtpEmailDeliveryAdapter adapter = new SmtpEmailDeliveryAdapter(sender(server.port()), properties)) {
            long started = System.nanoTime();
            adapter.deliver(message());
            Duration took = Duration.ofNanos(System.nanoTime() - started);

            assertEquals(1, server.accepted().size());
            assertTrue(took.compareTo(Duration.ofMillis(900)) >= 0, took.toString());
            assertTrue(took.compareTo(Duration.ofMillis(3000)) < 0, took.toString());
        }
    }

    @Test
    void aDeliveryThatRanOutLeavesTheNextOneAlone() throws IOException {
        SmtpEmailAdapterProperties properties = properties();
        properties.setSendTimeout(Duration.ofMillis(500));
        try (FakeSmtpServer slow = FakeSmtpServer.start(FakeSmtpServer.Behaviour.SLOW, Duration.ofSeconds(1));
             FakeSmtpServer normal = FakeSmtpServer.start(FakeSmtpServer.Behaviour.NORMAL);
             SmtpEmailDeliveryAdapter late = new SmtpEmailDeliveryAdapter(sender(slow.port()), properties);
             SmtpEmailDeliveryAdapter adapter = new SmtpEmailDeliveryAdapter(sender(normal.port()), properties())) {
            assertThrows(EmailDeliveryException.class, () -> late.deliver(message()));

            adapter.deliver(message());

            assertEquals(1, normal.accepted().size());
        }
    }

    private static void within(Instant deadline, Runnable work) {
        RuntimeInvocation call = new RuntimeInvocation(CallerIdentity.service("test-owner"),
                RuntimeInvocation.Kind.HTTP, Set.of(), InvocationScope.platform(), AccessDecision.none(),
                Optional.empty(), deadline, CancellationSignal.none(), 0);
        RuntimeInvocations.within(call, () -> {
            work.run();
            return null;
        });
    }
}
