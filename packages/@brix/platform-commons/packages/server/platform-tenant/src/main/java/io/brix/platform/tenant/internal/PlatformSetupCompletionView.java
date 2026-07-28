/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.internal;

/**
 * Setup completion result.
 *
 * @param activated whether the identity became ACTIVE
 */
public record PlatformSetupCompletionView(boolean activated) {
}
