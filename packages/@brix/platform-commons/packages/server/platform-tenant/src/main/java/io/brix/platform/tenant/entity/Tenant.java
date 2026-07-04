/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.platform.tenant.entity;

import java.time.OffsetDateTime;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.brix.platform.tenant.enums.MfaPolicy;
import io.brix.platform.tenant.enums.TenantStatus;
import io.brix.platform.tenant.enums.ThemeMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Tenant Entity representing a tenant organization in the multi-tenant system.
 *
 * <p>A tenant is an isolated organizational unit within the platform.
 * Each tenant has its own data space, users, and configurations.
 *
 * <h3>System-Level Entity</h3>
 * <p>This is a system-level entity (no tenant_id) as the sys_tenant table
 * is the root of the multi-tenancy model.
 *
 * <h3>Key Properties</h3>
 * <ul>
 *   <li><b>id:</b> Snowflake-generated unique identifier</li>
 *   <li><b>code:</b> Unique business code for tenant (used in URLs, APIs)</li>
 *   <li><b>name:</b> Human-readable display name</li>
 *   <li><b>status:</b> Tenant lifecycle status</li>
 * </ul>
 *
 * <h3>Lifecycle States</h3>
 * <ul>
 *   <li>PENDING_ACTIVATION → Initial state after creation</li>
 *   <li>ACTIVE → Normal operational state</li>
 *   <li>SUSPENDED → Temporarily disabled</li>
 *   <li>TERMINATED → Permanently deleted</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantStatus
 */
@Entity
@Table(
    name = "sys_tenant",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sys_tenant_code", columnNames = "code")
    },
    indexes = {
        @Index(name = "idx_sys_tenant_status", columnList = "status"),
        @Index(name = "idx_sys_tenant_created_at", columnList = "created_at DESC")
    }
)
public class Tenant {

    private static final String DEFAULT_ALLOWED_LOGIN_METHODS_JSON = "[\"phone_sms\", \"email_password\"]";
    private static final String DEFAULT_PASSWORD_POLICY_JSON = "{\"minLength\":8,\"requireUppercase\":false,\"requireLowercase\":true,\"requireNumbers\":true,\"requireSpecialChars\":false,\"maxAgeDays\":0,\"historyCount\":0}";
    private static final String DEFAULT_NOTIFICATION_CHANNELS_JSON = "[\"in_app\"]";
    private static final String DEFAULT_EMPTY_JSON_OBJECT = "{}";

    /**
     * Primary key - Snowflake-generated unique identifier.
     *
     * <p>IDs are generated using the Snowflake algorithm to ensure
     * global uniqueness across distributed deployments.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Unique business code for the tenant.
     *
     * <p>Used for:
     * <ul>
     *   <li>URL routing (e.g., code.platform.com)</li>
     *   <li>API authentication headers</li>
     *   <li>External integrations</li>
     * </ul>
     *
     * <p>Must be unique across all tenants. Typically lowercase alphanumeric
     * with hyphens (e.g., "acme-corp", "demo-tenant").
     */
    @Column(name = "code", nullable = false, length = 64, unique = true)
    private String code;

    /**
     * Human-readable display name for the tenant.
     *
     * <p>Used in UI, reports, and communications.
     * Does not need to be unique.
     */
    @Column(name = "name", nullable = false, length = 256)
    private String name;

    /**
     * Tenant lifecycle status.
     *
     * <p>Controls tenant accessibility and capabilities.
     * Stored as VARCHAR in database.
     *
     * @see TenantStatus
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TenantStatus status = TenantStatus.PENDING_ACTIVATION;

    // ========================================================================
    // Configuration Fields (V010)
    // ========================================================================

    @Column(name = "default_locale", length = 20)
    private String defaultLocale = "zh-CN";

    @Column(name = "default_timezone", length = 50)
    private String defaultTimezone = "UTC";

    @Column(name = "default_date_format", length = 20)
    private String defaultDateFormat = "YYYY-MM-DD";

    @Column(name = "default_time_format", length = 5)
    private String defaultTimeFormat = "24h";

    @Column(name = "default_currency", length = 10)
    private String defaultCurrency = "CNY";

    @Enumerated(EnumType.STRING)
    @Column(name = "default_theme", length = 10)
    private ThemeMode defaultTheme = ThemeMode.LIGHT;

    @Column(name = "session_timeout_min")
    private Integer sessionTimeoutMinutes = 60;

    @Enumerated(EnumType.STRING)
    @Column(name = "mfa_policy", length = 20)
    private MfaPolicy mfaPolicy = MfaPolicy.OPTIONAL;

    @Column(name = "allowed_login_methods", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String allowedLoginMethods = DEFAULT_ALLOWED_LOGIN_METHODS_JSON;

    @Column(name = "password_policy", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String passwordPolicy = DEFAULT_PASSWORD_POLICY_JSON;

    @Column(name = "notification_channels", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String notificationChannels = DEFAULT_NOTIFICATION_CHANNELS_JSON;

    @Column(name = "business_hours", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String businessHours = DEFAULT_EMPTY_JSON_OBJECT;

    @Column(name = "settings", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String settings = DEFAULT_EMPTY_JSON_OBJECT;

    // ========================================================================
    // Branding Fields (V010)
    // ========================================================================

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Column(name = "favicon_url", length = 512)
    private String faviconUrl;

    @Column(name = "primary_color", length = 20)
    private String primaryColor = "#1976d2";

    @Column(name = "secondary_color", length = 20)
    private String secondaryColor;

    @Column(name = "login_page_title", length = 256)
    private String loginPageTitle;

    @Column(name = "login_page_subtitle", length = 512)
    private String loginPageSubtitle;

    @Column(name = "login_page_bg_url", length = 512)
    private String loginPageBgUrl;

    // ========================================================================
    // Quota Fields (V012)
    // ========================================================================

    /**
     * Maximum number of B-side members (Actor) allowed.
     *
     * <p>Hard limit enforced by {@link io.brix.platform.tenant.service.TenantQuotaService}.
     * A value of 0 means unlimited (no quota enforcement).
     *
     * @since 3.2.0
     */
    @Column(name = "max_users", nullable = false)
    private int maxUsers = 0;

    /**
     * Maximum number of C-side principals (Subject) allowed.
     *
     * <p>Hard limit enforced by {@link io.brix.platform.tenant.service.TenantQuotaService}.
     * A value of 0 means unlimited (no quota enforcement).
     *
     * @since 3.2.0
     */
    @Column(name = "max_principals", nullable = false)
    private int maxPrincipals = 0;

    /**
     * Record creation timestamp.
     *
     * <p>Set automatically when the entity is first persisted.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Last update timestamp.
     *
     * <p>Updated automatically when the entity is modified.
     */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // ========================================================================
    // Constructors
    // ========================================================================

    /**
     * Default constructor required by JPA.
     */
    public Tenant() {
    }

    /**
     * Creates a new tenant with the specified code and name.
     *
     * <p>The tenant is created in PENDING_ACTIVATION status.
     * ID must be set separately using IdGenerator.
     *
     * @param code unique tenant code
     * @param name display name
     */
    public Tenant(String code, String name) {
        this.code = code;
        this.name = name;
        this.status = TenantStatus.PENDING_ACTIVATION;
    }

    // ========================================================================
    // JPA Lifecycle Callbacks
    // ========================================================================

    /**
     * Sets creation timestamp before persist.
     */
    @PrePersist
    protected void onCreate() {
        applyJsonDefaults();
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * Updates the updated_at timestamp before update.
     */
    @PreUpdate
    protected void onUpdate() {
        applyJsonDefaults();
        this.updatedAt = OffsetDateTime.now();
    }

    private void applyJsonDefaults() {
        if (allowedLoginMethods == null) {
            allowedLoginMethods = DEFAULT_ALLOWED_LOGIN_METHODS_JSON;
        }
        if (passwordPolicy == null) {
            passwordPolicy = DEFAULT_PASSWORD_POLICY_JSON;
        }
        if (notificationChannels == null) {
            notificationChannels = DEFAULT_NOTIFICATION_CHANNELS_JSON;
        }
        if (businessHours == null) {
            businessHours = DEFAULT_EMPTY_JSON_OBJECT;
        }
        if (settings == null) {
            settings = DEFAULT_EMPTY_JSON_OBJECT;
        }
    }

    // ========================================================================
    // Business Methods
    // ========================================================================

    /**
     * Checks if the tenant is active and operational.
     *
     * @return true if tenant status is ACTIVE
     */
    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }

    /**
     * Checks if the tenant can be activated.
     *
     * @return true if tenant can transition to ACTIVE status
     */
    public boolean canBeActivated() {
        return status != null && status.canBeActivated();
    }

    /**
     * Activates the tenant.
     *
     * @throws IllegalStateException if tenant cannot be activated
     */
    public void activate() {
        if (!canBeActivated()) {
            throw new IllegalStateException(
                "Cannot activate tenant in status: " + status
            );
        }
        this.status = TenantStatus.ACTIVE;
    }

    /**
     * Suspends the tenant.
     *
     * @throws IllegalStateException if tenant is not active
     */
    public void suspend() {
        if (status != TenantStatus.ACTIVE) {
            throw new IllegalStateException(
                "Can only suspend active tenants, current status: " + status
            );
        }
        this.status = TenantStatus.SUSPENDED;
    }

    /**
     * Terminates the tenant permanently.
     *
     * <p><b>Warning:</b> This is irreversible.
     */
    public void terminate() {
        if (status == TenantStatus.TERMINATED) {
            return; // Already terminated
        }
        this.status = TenantStatus.TERMINATED;
    }

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // --- Configuration Fields ---

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public String getDefaultTimezone() {
        return defaultTimezone;
    }

    public void setDefaultTimezone(String defaultTimezone) {
        this.defaultTimezone = defaultTimezone;
    }

    public String getDefaultDateFormat() {
        return defaultDateFormat;
    }

    public void setDefaultDateFormat(String defaultDateFormat) {
        this.defaultDateFormat = defaultDateFormat;
    }

    public String getDefaultTimeFormat() {
        return defaultTimeFormat;
    }

    public void setDefaultTimeFormat(String defaultTimeFormat) {
        this.defaultTimeFormat = defaultTimeFormat;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public ThemeMode getDefaultTheme() {
        return defaultTheme;
    }

    public void setDefaultTheme(ThemeMode defaultTheme) {
        this.defaultTheme = defaultTheme;
    }

    public Integer getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }

    public void setSessionTimeoutMinutes(Integer sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }

    public MfaPolicy getMfaPolicy() {
        return mfaPolicy;
    }

    public void setMfaPolicy(MfaPolicy mfaPolicy) {
        this.mfaPolicy = mfaPolicy;
    }

    public String getAllowedLoginMethods() {
        return allowedLoginMethods;
    }

    public void setAllowedLoginMethods(String allowedLoginMethods) {
        this.allowedLoginMethods = allowedLoginMethods;
    }

    public String getPasswordPolicy() {
        return passwordPolicy;
    }

    public void setPasswordPolicy(String passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }

    public String getNotificationChannels() {
        return notificationChannels;
    }

    public void setNotificationChannels(String notificationChannels) {
        this.notificationChannels = notificationChannels;
    }

    public String getBusinessHours() {
        return businessHours;
    }

    public void setBusinessHours(String businessHours) {
        this.businessHours = businessHours;
    }

    public String getSettings() {
        return settings;
    }

    public void setSettings(String settings) {
        this.settings = settings;
    }

    // --- Branding Fields ---

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getFaviconUrl() {
        return faviconUrl;
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public String getLoginPageTitle() {
        return loginPageTitle;
    }

    public void setLoginPageTitle(String loginPageTitle) {
        this.loginPageTitle = loginPageTitle;
    }

    public String getLoginPageSubtitle() {
        return loginPageSubtitle;
    }

    public void setLoginPageSubtitle(String loginPageSubtitle) {
        this.loginPageSubtitle = loginPageSubtitle;
    }

    public String getLoginPageBgUrl() {
        return loginPageBgUrl;
    }

    public void setLoginPageBgUrl(String loginPageBgUrl) {
        this.loginPageBgUrl = loginPageBgUrl;
    }

    // --- Quota Fields ---

    public int getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(int maxUsers) {
        this.maxUsers = maxUsers;
    }

    public int getMaxPrincipals() {
        return maxPrincipals;
    }

    public void setMaxPrincipals(int maxPrincipals) {
        this.maxPrincipals = maxPrincipals;
    }

    // ========================================================================
    // Object Methods
    // ========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tenant tenant = (Tenant) o;
        return Objects.equals(id, tenant.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Tenant{" +
               "id=" + id +
               ", code='" + code + '\'' +
               ", name='" + name + '\'' +
               ", status=" + status +
               '}';
    }
}
