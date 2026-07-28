package io.runtime.sdk.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class NotificationRequestTest {

    @Test
    void toStringMasksRecipientAndVariables() {
        NotificationRequest request = new NotificationRequest(
                10L,
                "admin@example.invalid",
                NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL,
                "en-US",
                Map.of("setupUrl", "https://setup.example.invalid/token/raw-secret"));

        String output = request.toString();

        assertTrue(output.contains("a***@e***.invalid"));
        assertTrue(output.contains("setupUrl"));
        assertFalse(output.contains("admin@example.invalid"));
        assertFalse(output.contains("raw-secret"));
        assertFalse(output.contains("https://setup.example.invalid"));
    }

    @Test
    void rejectsInvalidVariableName() {
        assertThrows(NotificationException.class, () -> new NotificationRequest(
                null,
                "admin@example.invalid",
                NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL,
                "en-US",
                Map.of("../setupUrl", "https://setup.example.invalid/token/raw-secret")));
    }

    @Test
    void defaultSendBridgesLegacyInitialSetupProvider() {
        LegacyProvider provider = new LegacyProvider();
        provider.send(new NotificationRequest(
                null,
                "admin@example.invalid",
                NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL,
                "en-US",
                Map.of("setupUrl", "https://setup.example.invalid/token/raw-secret")));

        assertEquals("admin@example.invalid", provider.email.get());
        assertEquals("https://setup.example.invalid/token/raw-secret", provider.setupUrl.get());
        assertEquals("INITIAL_SETUP", provider.purpose.get());
    }

    private static final class LegacyProvider implements NotificationCapability {
        private final AtomicReference<String> email = new AtomicReference<>();
        private final AtomicReference<String> setupUrl = new AtomicReference<>();
        private final AtomicReference<String> purpose = new AtomicReference<>();

        @Override
        public void sendSetupLink(String email, String setupUrl, String purpose) {
            this.email.set(email);
            this.setupUrl.set(setupUrl);
            this.purpose.set(purpose);
        }
    }
}
