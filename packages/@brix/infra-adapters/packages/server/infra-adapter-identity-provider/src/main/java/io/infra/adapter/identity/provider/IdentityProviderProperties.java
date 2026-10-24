package io.infra.adapter.identity.provider;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the identity protocol surface.
 *
 * <p>The Host Composition selects this adapter explicitly with
 * {@code brix.identity-provider.enabled=true} (root {@code BRX-RS-SECURITY-011}). Lifetimes
 * default to the identity-auth 1.0 §12 security profile values and may only be tightened.</p>
 *
 * @since 3.2.0
 */
@ConfigurationProperties(prefix = "brix.identity-provider")
public class IdentityProviderProperties {

    private boolean enabled = false;

    /**
     * Public origin of the identity protocol surface and its browser clients, for example
     * {@code https://console.example.com}. It is the OpenID Connect issuer and the base of
     * every redirect URI; request headers never change it.
     */
    private String issuer;

    /**
     * Base URL the BFF uses to call this process's token endpoint. Defaults to
     * {@code http://127.0.0.1:{local.server.port}}.
     */
    private String internalBaseUrl;

    private final TenantConsole tenantConsole = new TenantConsole();
    private final Session session = new Session();
    private final Login login = new Login();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getInternalBaseUrl() {
        return internalBaseUrl;
    }

    public void setInternalBaseUrl(String internalBaseUrl) {
        this.internalBaseUrl = internalBaseUrl;
    }

    public TenantConsole getTenantConsole() {
        return tenantConsole;
    }

    public Session getSession() {
        return session;
    }

    public Login getLogin() {
        return login;
    }

    /**
     * Returns the issuer as an origin without a trailing slash, or fails when it is not an
     * absolute http(s) origin.
     */
    public String requireIssuerOrigin() {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("brix.identity-provider.issuer is required");
        }
        URI uri = URI.create(issuer.trim());
        boolean http = "https".equals(uri.getScheme()) || "http".equals(uri.getScheme());
        boolean originOnly = (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                && uri.getQuery() == null && uri.getFragment() == null && uri.getUserInfo() == null;
        if (!http || uri.getHost() == null || !originOnly) {
            throw new IllegalStateException(
                    "brix.identity-provider.issuer must be an http(s) origin such as https://console.example.com");
        }
        String value = uri.toString();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** The {@code tenant-console} client (identity-auth {@code BRX-IA-CLIENT-001}). */
    public static class TenantConsole {

        /** Console path; the BFF accepts return paths under it and lands there after login. */
        private String appPath = "/tenant";

        /**
         * Tenant Owner API that describes the caller's context and the effective permissions
         * the Runtime grant table gives it (multi-tenant 5.1 BRX-TENANT-AUTHZ-009).
         */
        private String contextPath = "/api/tenant/session";

        /**
         * Tenant Owner entry that accepts a FIRST_OWNER invitation (multi-tenant 5.1
         * BRX-TENANT-ENTRY-005); the hosted invitation page calls it after both factors.
         */
        private String firstOwnerAcceptPath = "/api/tenant/first-owner-invitations/accept";

        /**
         * Client secret of the BFF. When empty, every process generates its own secret; the
         * BFF only calls the token endpoint of its own process.
         */
        private String clientSecret;

        public String getAppPath() {
            return appPath;
        }

        public String getFirstOwnerAcceptPath() {
            return firstOwnerAcceptPath;
        }

        public void setFirstOwnerAcceptPath(String firstOwnerAcceptPath) {
            this.firstOwnerAcceptPath = firstOwnerAcceptPath;
        }

        public void setAppPath(String appPath) {
            this.appPath = appPath;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public String getContextPath() {
            return contextPath;
        }

        public void setContextPath(String contextPath) {
            this.contextPath = contextPath;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }

    /** BFF session lifetimes (identity-auth §12: staff idle 1-2 hours). */
    public static class Session {

        private Duration idleTimeout = Duration.ofHours(2);
        private Duration absoluteTimeout = Duration.ofHours(12);

        public Duration getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public Duration getAbsoluteTimeout() {
            return absoluteTimeout;
        }

        public void setAbsoluteTimeout(Duration absoluteTimeout) {
            this.absoluteTimeout = absoluteTimeout;
        }
    }

    /** Hosted login limits (root {@code BRX-RS-SECURITY-014}: throttle per account and source). */
    public static class Login {

        private Duration transactionTimeout = Duration.ofMinutes(10);
        /** A credential setup may take longer: the invitee may first install an authenticator app. */
        private Duration setupTimeout = Duration.ofMinutes(30);
        private int maxFailuresPerAccountAndSource = 5;
        private int maxRequestsPerSourcePerMinute = 300;
        private Duration throttleWindow = Duration.ofMinutes(15);

        public Duration getTransactionTimeout() {
            return transactionTimeout;
        }

        public Duration getSetupTimeout() {
            return setupTimeout;
        }

        public void setSetupTimeout(Duration setupTimeout) {
            this.setupTimeout = setupTimeout;
        }

        public void setTransactionTimeout(Duration transactionTimeout) {
            this.transactionTimeout = transactionTimeout;
        }

        public int getMaxFailuresPerAccountAndSource() {
            return maxFailuresPerAccountAndSource;
        }

        public void setMaxFailuresPerAccountAndSource(int maxFailuresPerAccountAndSource) {
            this.maxFailuresPerAccountAndSource = maxFailuresPerAccountAndSource;
        }

        public int getMaxRequestsPerSourcePerMinute() {
            return maxRequestsPerSourcePerMinute;
        }

        public void setMaxRequestsPerSourcePerMinute(int maxRequestsPerSourcePerMinute) {
            this.maxRequestsPerSourcePerMinute = maxRequestsPerSourcePerMinute;
        }

        public Duration getThrottleWindow() {
            return throttleWindow;
        }

        public void setThrottleWindow(Duration throttleWindow) {
            this.throttleWindow = throttleWindow;
        }
    }
}
