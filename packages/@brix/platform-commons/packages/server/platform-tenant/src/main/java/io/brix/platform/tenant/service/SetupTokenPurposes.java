/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

/**
 * Stable setup-token purpose codes owned by platform-tenant identity setup.
 */
final class SetupTokenPurposes {

    static final String INITIAL_SETUP = "INITIAL_SETUP";
    static final String TENANT_FIRST_OWNER_SETUP = "TENANT_FIRST_OWNER_SETUP";

    private SetupTokenPurposes() {
    }
}
