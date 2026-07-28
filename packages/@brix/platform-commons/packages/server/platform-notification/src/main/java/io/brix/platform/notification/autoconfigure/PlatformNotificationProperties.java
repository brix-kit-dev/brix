package io.brix.platform.notification.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the platform notification provider.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@ConfigurationProperties(prefix = "brix.platform.notification")
public class PlatformNotificationProperties {

    /** Whether the managed notification provider is enabled. */
    private boolean enabled = true;

    /** Default locale used when a request does not specify one. */
    private String defaultLocale = "en-US";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }
}
