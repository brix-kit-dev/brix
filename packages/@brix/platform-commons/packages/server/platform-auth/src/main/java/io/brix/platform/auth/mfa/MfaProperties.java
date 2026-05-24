package io.brix.platform.auth.mfa;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for platform MFA capabilities.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@ConfigurationProperties(prefix = "brix.platform.mfa")
public class MfaProperties {

    private String issuer = "Brix";

    private int totpPeriodSeconds = 30;

    private int totpDigits = 6;

    private int totpWindow = 1;

    private String encryptionKey;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public int getTotpPeriodSeconds() {
        return totpPeriodSeconds;
    }

    public void setTotpPeriodSeconds(int totpPeriodSeconds) {
        this.totpPeriodSeconds = totpPeriodSeconds;
    }

    public int getTotpDigits() {
        return totpDigits;
    }

    public void setTotpDigits(int totpDigits) {
        this.totpDigits = totpDigits;
    }

    public int getTotpWindow() {
        return totpWindow;
    }

    public void setTotpWindow(int totpWindow) {
        this.totpWindow = totpWindow;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}