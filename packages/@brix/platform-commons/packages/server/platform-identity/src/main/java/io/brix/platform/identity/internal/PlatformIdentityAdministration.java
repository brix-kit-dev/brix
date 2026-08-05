/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.identity.internal;

/**
 * Internal contract for platform identity setup operations.
 */
public interface PlatformIdentityAdministration {

    /** Stable contract identifier declared in Runtime descriptors. */
    String CONTRACT_ID = "brix.internal.platform.identity-administration";

    /** Contract version for Runtime Shell 3.0.10 Phase 5. */
    String CONTRACT_VERSION = "1.0.0";

    /**
     * Validates a setup token without consuming it.
     *
     * @param setupToken raw setup token
     * @return validation view
     */
    SetupTokenView validateSetupToken(String setupToken);

    /**
     * Initializes TOTP enrollment for a setup token.
     *
     * @param setupToken raw setup token
     * @return TOTP challenge view
     */
    SetupTotpChallengeView initTotp(String setupToken);

    /**
     * Completes password and TOTP setup.
     *
     * @param command setup completion command
     * @return completion view
     */
    PlatformSetupCompletionView completeSetup(CompletePlatformSetupCommand command);

    /**
     * Lists platform administrators as an owner-side read projection.
     *
     * @param request pagination and sort request
     * @return page of platform administrator views
     */
    PlatformPageView<PlatformAdminView> listPlatformAdmins(PlatformPageRequest request);
}
