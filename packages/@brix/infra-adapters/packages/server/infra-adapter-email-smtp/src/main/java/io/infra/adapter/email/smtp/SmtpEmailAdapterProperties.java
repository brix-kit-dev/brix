package io.infra.adapter.email.smtp;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the SMTP email delivery adapter.
 *
 * <p>The four time limits bound every exchange with the server (root BRX-RS-RESILIENCE-001; notification
 * decision D104, standard-link point 14a). JavaMail waits forever on each step unless told otherwise.</p>
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

    /** How long connecting to the server may take. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** How long one read of the server's reply may take, the TLS handshake's included. */
    private Duration readTimeout = Duration.ofSeconds(10);

    /** How long one write to the server may take. */
    private Duration writeTimeout = Duration.ofSeconds(10);

    /**
     * How long one delivery may take in all. A delivery made while the runtime handles a call ends at that call's
     * deadline when it comes sooner.
     */
    private Duration sendTimeout = Duration.ofSeconds(30);

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

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }
}
