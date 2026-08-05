/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.sdk.capability;

import io.runtime.sdk.annotation.Since;

/**
 * Identity-owned capability for FIRST_OWNER invitee setup.
 *
 * <p>The tenant owner calls this narrow contract when it needs identity e-mail
 * lookup or setup-token issuance. The contract intentionally excludes tenant
 * invitation persistence, membership creation, and tenant activation.</p>
 *
 * @since 3.2.0
 */
@Since("3.2.0")
public interface FirstOwnerInviteeIdentitySetupCapability {

    boolean sendSetupIfRequired(
            Long tenantId,
            String inviteeEmail,
            String platformOperatorRef,
            String locale);

    String requireIdentityEmail(Long identityId);

    String requireActiveIdentityEmail(Long identityId);

    final class IdentitySetupException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String code;

        public IdentitySetupException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
