package io.infra.adapter.email.smtp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the SMTP email delivery adapter.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@ConfigurationProperties(prefix = "brix.infra.email.smtp")
public class SmtpEmailAdapterProperties {

    /** Whether the SMTP adapter is enabled. */
    private boolean enabled = true;

    /** Trusted sender address used as the SMTP From header. */
    private String from;

    /** Whether TLS or STARTTLS is required by the adapter profile. */
    private boolean tlsEnabled = true;

    /** Whether SMTP certificate host name validation is required. */
    private boolean certificateHostnameValidation = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public void setTlsEnabled(boolean tlsEnabled) {
        this.tlsEnabled = tlsEnabled;
    }

    public boolean isCertificateHostnameValidation() {
        return certificateHostnameValidation;
    }

    public void setCertificateHostnameValidation(boolean certificateHostnameValidation) {
        this.certificateHostnameValidation = certificateHostnameValidation;
    }
}
