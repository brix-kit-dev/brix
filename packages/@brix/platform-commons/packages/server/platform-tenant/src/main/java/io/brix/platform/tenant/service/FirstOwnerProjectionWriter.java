/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import io.brix.platform.tenant.event.TenantFirstOwnerAcceptedEvent;

/**
 * Writes the business side effect for a consumed FIRST_OWNER accepted event.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@FunctionalInterface
public interface FirstOwnerProjectionWriter {

    /**
     * Writes the projection side effect in the active Consumer Owner transaction.
     *
     * @param messageId canonical message id
     * @param event accepted-owner event payload
     */
    void write(String messageId, TenantFirstOwnerAcceptedEvent event);
}
