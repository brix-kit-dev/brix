/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.service;

import io.brix.platform.identity.dto.AuditEvent;

/**
 * Transitional audit sink used by copied platform identity services.
 */
public interface AuditService {

    void log(AuditEvent event);
}
