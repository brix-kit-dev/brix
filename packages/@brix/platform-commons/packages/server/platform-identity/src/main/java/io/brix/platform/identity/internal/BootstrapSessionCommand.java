/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.identity.internal;

/**
 * Command for opening a one-time BOOTSTRAP_SETUP session.
 *
 * @param setupCode operator-provided bootstrap setup code
 */
public record BootstrapSessionCommand(String setupCode) {

    public BootstrapSessionCommand {
        if (setupCode == null || setupCode.isBlank()) {
            throw new IllegalArgumentException("setupCode is required");
        }
    }
}
