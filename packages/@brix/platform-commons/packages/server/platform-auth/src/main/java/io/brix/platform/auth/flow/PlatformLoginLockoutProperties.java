package io.brix.platform.auth.flow;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for platform-admin password failure lockout.
 */
@ConfigurationProperties(prefix = "brix.platform.auth.lockout")
public class PlatformLoginLockoutProperties {

    private int maxFailedAttempts = 5;
    private int lockMinutes = 15;

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException("maxFailedAttempts must be at least 1");
        }
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(int lockMinutes) {
        if (lockMinutes < 1) {
            throw new IllegalArgumentException("lockMinutes must be at least 1");
        }
        this.lockMinutes = lockMinutes;
    }
}