/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized delivery metadata for FIRST_OWNER invitation links.
 *
 * <p>Layer 2C owns token issuance and notification composition. Host/runtime
 * deployment only supplies the public acceptance entry URL via configuration.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@ConfigurationProperties(prefix = "brix.tenant.first-owner-invitation")
public class FirstOwnerInvitationProperties {

    /** Public acceptance page URL without token query material. */
    private String inviteBaseUrl;

    /** Public identity setup page URL without token query material. */
    private String setupBaseUrl;

    public String getInviteBaseUrl() {
        return inviteBaseUrl;
    }

    public void setInviteBaseUrl(String inviteBaseUrl) {
        this.inviteBaseUrl = inviteBaseUrl;
    }

    public String getSetupBaseUrl() {
        return setupBaseUrl;
    }

    public void setSetupBaseUrl(String setupBaseUrl) {
        this.setupBaseUrl = setupBaseUrl;
    }
}
