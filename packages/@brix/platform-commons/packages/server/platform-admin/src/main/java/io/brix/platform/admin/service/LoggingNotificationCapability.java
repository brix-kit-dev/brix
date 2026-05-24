package io.brix.platform.admin.service;

import java.util.Arrays;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import io.runtime.sdk.capability.NotificationCapability;

/**
 * Non-production setup-link notification adapter that writes links to the server log.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class LoggingNotificationCapability implements NotificationCapability {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationCapability.class);

    private final Environment environment;

    public LoggingNotificationCapability(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void rejectProductionFallback() {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(profile -> profile.contains("prod"));
        if (production) {
            throw new IllegalStateException("A production NotificationCapability is required for setup links");
        }
    }

    @Override
    public void sendSetupLink(String email, String setupUrl, String purpose) {
        log.warn("[PlatformAdmin] setup link generated for email={}, purpose={}, setupUrl={}",
                email, purpose, setupUrl);
    }
}
