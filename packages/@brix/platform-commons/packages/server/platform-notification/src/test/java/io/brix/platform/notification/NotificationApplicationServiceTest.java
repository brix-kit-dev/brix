package io.brix.platform.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import io.brix.platform.notification.delivery.EmailDeliveryAdapter;
import io.brix.platform.notification.delivery.EmailMessage;
import io.brix.platform.notification.template.StrictTemplateRenderer;
import io.brix.platform.notification.template.TemplateRepository;
import io.runtime.sdk.capability.NotificationException;
import io.runtime.sdk.capability.NotificationRequest;
import io.runtime.sdk.capability.NotificationTemplateKeys;

class NotificationApplicationServiceTest {

    private static final String TENANT_OWNER_SETUP_TEMPLATE = "tenant.owner.setup.initial";

    private final CapturingAdapter adapter = new CapturingAdapter();
    private final NotificationApplicationService service = new NotificationApplicationService(
            new TemplateRepository(new ObjectMapper(), getClass().getClassLoader()),
            new StrictTemplateRenderer(),
            adapter,
            "en-US");

    @Test
    void sendsRenderedTemplateThroughReplaceableAdapter() {
        service.send(new NotificationRequest(
                null,
                "admin@example.invalid",
                NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL,
                "en-US",
                Map.of("setupUrl", "https://setup.example.invalid/token/raw-secret")));

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

    private static final class CapturingAdapter implements EmailDeliveryAdapter {
        private final AtomicReference<EmailMessage> message = new AtomicReference<>();

        @Override
        public void deliver(EmailMessage message) {
            this.message.set(message);
        }
    }
}
