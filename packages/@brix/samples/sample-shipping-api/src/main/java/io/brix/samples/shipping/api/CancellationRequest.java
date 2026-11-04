/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.api;

/**
 * The cancellation to read, as {@code sample-shipping/cancellation-request} describes it: the path variable of
 * {@code GET /api/sample-shipping/cancellations/{commandId}}.
 *
 * @param commandId the command a refusal answered with
 */
public record CancellationRequest(String commandId) {
}
