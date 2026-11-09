package io.brix.platform.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.brix.platform.notification.delivery.EmailDeliveryAdapter;
import io.brix.platform.notification.delivery.EmailDeliveryException;
import io.brix.platform.notification.delivery.EmailMessage;
import io.brix.platform.notification.template.StrictTemplateRenderer;
import io.brix.platform.notification.template.TemplateRepository;
import io.runtime.sdk.capability.NotificationException;
import io.runtime.sdk.capability.NotificationRequest;
import io.runtime.sdk.capability.NotificationTemplateKeys;

class NotificationApplicationServiceTest {

    private static final String TENANT_OWNER_SETUP_TEMPLATE = "tenant.owner.setup.initial";
    private static final String TRACEPARENT = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

    private final CapturingAdapter adapter = new CapturingAdapter();
    private final RecordingMetrics metrics = new RecordingMetrics();
    private final NotificationApplicationService service = new NotificationApplicationService(
            new TemplateRepository(new ObjectMapper(), getClass().getClassLoader()),
            new StrictTemplateRenderer(),
            adapter,
            "en-US",
            metrics);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger serviceLog = (Logger) LoggerFactory.getLogger(NotificationApplicationService.class);

    @BeforeEach
    void captureLogs() {
        logs.start();
        serviceLog.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        serviceLog.detachAppender(logs);
        MDC.clear();
    }

    @Test
    void sendsRenderedTemplateThroughReplaceableAdapter() {
        service.send(setupLink());

        EmailMessage message = adapter.message.get();
        assertEquals("admin@example.invalid", message.recipientEmail());
        assertEquals("Complete your Brix platform admin setup", message.subject());
        assertFalse(message.toString().contains("admin@example.invalid"));
        assertFalse(message.toString().contains("raw-secret"));
    }

    @Test
    void supportsChineseTenantOwnerInvitationTemplate() {
        service.send(new NotificationRequest(
                10L,
                "owner@example.invalid",
                NotificationTemplateKeys.TENANT_OWNER_INVITATION_INITIAL,
                "zh-CN",
                Map.of("inviteUrl", "https://setup.example.invalid/invite/raw-secret")));

        assertEquals("接受 Brix 租户所有者邀请", adapter.message.get().subject());
    }

    @Test
    void supportsTenantOwnerSetupTemplate() {
        service.send(new NotificationRequest(
                10L,
                "owner@example.invalid",
                TENANT_OWNER_SETUP_TEMPLATE,
                "en-US",
                Map.of("setupUrl", "https://setup.example.invalid/token/raw-secret")));

        assertEquals("Complete your Brix tenant owner setup", adapter.message.get().subject());
    }

    @Test
    void rejectsMissingTemplate() {
        NotificationException ex = assertThrows(NotificationException.class, () -> service.send(
                new NotificationRequest(
                        null,
                        "admin@example.invalid",
                        "platform.admin.unknown",
                        "en-US",
                        Map.of("setupUrl", "https://setup.example.invalid/token/raw-secret"))));

        assertEquals(NotificationException.Code.TEMPLATE_NOT_FOUND, ex.errorCode());
        assertTrue(metrics.deliveries.isEmpty(), "a template that does not load is not a delivery");
    }

    @Test
    void rejectsExtraVariables() {
        NotificationException ex = assertThrows(NotificationException.class, () -> service.send(
                new NotificationRequest(
                        null,
                        "admin@example.invalid",
                        NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL,
                        "en-US",
                        Map.of(
                                "setupUrl", "https://setup.example.invalid/token/raw-secret",
                                "extra", "value"))));

        assertEquals(NotificationException.Code.TEMPLATE_INVALID, ex.errorCode());
    }

    @Test
    void countsAnAcceptedMessageAsSent() {
        service.send(setupLink());

        assertEquals(List.of(NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL + "|sent"), metrics.deliveries);
        assertTrue(warnings().isEmpty());
    }

    @Test
    void aRefusedMessageIsCountedAsRejectedAndLoggedWithoutItsLinkOrAddress() {
        EmailDeliveryException refusal =
                new EmailDeliveryException(EmailDeliveryException.Reason.REJECTED, "reply 550", null);
        adapter.failure = refusal;
        MDC.put("traceparent", TRACEPARENT);

        NotificationException ex = assertThrows(NotificationException.class, () -> service.send(setupLink()));

        assertEquals(NotificationException.Code.DELIVERY_FAILED, ex.errorCode());
        assertEquals(Map.of(), ex.safeParameters());
        assertSame(refusal, ex.getCause());
        assertEquals(List.of(NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL + "|rejected"), metrics.deliveries);
        List<String> warnings = warnings();
        assertEquals(1, warnings.size());
        String line = warnings.get(0);
        assertTrue(line.startsWith("event=notification.delivery_failed outcome=rejected "), line);
        assertTrue(line.contains("templateKey=platform.admin.setup.initial"), line);
        assertTrue(line.contains("recipient=a***@e***.invalid"), line);
        assertTrue(line.contains("traceId=0af7651916cd43dd8448eb211c80319c"), line);
        assertTrue(line.contains("detail=email delivery rejected: reply 550"), line);
        assertFalse(line.contains("admin@example.invalid"), line);
        assertFalse(line.contains("raw-secret"), line);
        assertFalse(line.contains("https://"), line);
    }

    @Test
    void aMessageThatRanOutOfTimeIsCountedAsTimeout() {
        adapter.failure = new EmailDeliveryException(EmailDeliveryException.Reason.TIMEOUT, "Read timed out", null);
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");

        assertThrows(NotificationException.class, () -> service.send(setupLink()));

        assertEquals(List.of(NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL + "|timeout"), metrics.deliveries);
        assertTrue(warnings().get(0).contains("traceId=4bf92f3577b34da6a3ce929d0e0e4736"), warnings().get(0));
    }

    @Test
    void anUnexpectedAdapterFailureIsCountedAsFailedWithTheCodeCallersHadBefore() {
        adapter.failure = new IllegalStateException("adapter broke");

        NotificationException ex = assertThrows(NotificationException.class, () -> service.send(setupLink()));

        assertEquals(NotificationException.Code.DELIVERY_FAILED, ex.errorCode());
        assertEquals(Map.of("templateKey", NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL), ex.safeParameters());
        assertEquals(List.of(NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL + "|failed"), metrics.deliveries);
        String line = warnings().get(0);
        assertTrue(line.contains("traceId=-"), line);
        assertTrue(line.contains("detail=IllegalStateException"), line);
        assertFalse(line.contains("adapter broke"), line);
    }

    private static NotificationRequest setupLink() {
        return new NotificationRequest(
                null,
                "admin@example.invalid",
                NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL,
                "en-US",
                Map.of("setupUrl", "https://setup.example.invalid/token/raw-secret"));
    }

    private List<String> warnings() {
        return logs.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static final class CapturingAdapter implements EmailDeliveryAdapter {
        private final AtomicReference<EmailMessage> message = new AtomicReference<>();
        private RuntimeException failure;

        @Override
        public void deliver(EmailMessage message) {
            if (failure != null) {
                throw failure;
            }
            this.message.set(message);
        }
    }

    private static final class RecordingMetrics implements NotificationMetrics {
        private final List<String> deliveries = new CopyOnWriteArrayList<>();

        @Override
        public void delivered(String templateKey, String outcome, Duration took) {
            assertFalse(took.isNegative());
            deliveries.add(templateKey + "|" + outcome);
        }
    }
}
