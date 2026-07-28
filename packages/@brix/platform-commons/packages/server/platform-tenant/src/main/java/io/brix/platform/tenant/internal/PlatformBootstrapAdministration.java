/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.internal;

/**
 * Internal contract for Bootstrap Stage A administration.
 *
 * <p>Consumers may only access this contract through Runtime Shell internal
 * contract resolution. The contract exposes stable DTOs and commands only; it
 * never exposes repositories, entities, transactions, setup codes, or raw setup
 * tokens except through the managed bootstrap session response.</p>
 */
public interface PlatformBootstrapAdministration {

    /** Stable contract identifier declared in Runtime descriptors. */
    String CONTRACT_ID = "brix.internal.platform.bootstrap-administration";

    /** Contract version for Runtime Shell 3.0.10 Phase 5. */
    String CONTRACT_VERSION = "1.0.0";

    /**
     * Returns the minimum public bootstrap status.
     *
     * @return status view without secret material
     */
    BootstrapStatusView status();

    /**
     * Opens a short-lived one-time BOOTSTRAP_SETUP session.
     *
     * @param command setup-code command
     * @return bootstrap session token view
     */
    BootstrapSessionView openSession(BootstrapSessionCommand command);

    /**
     * Creates the first formal platform administrator.
     *
     * @param command creation command
     * @return non-sensitive creation result
     */
    PlatformAdminCreationView createFirstAdmin(CreateFirstPlatformAdminCommand command);
}
