/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.api;

/**
 * Where a cancellation stands, as {@code sample-shipping/cancellation} describes it: the status of the
 * {@code sample-orders.cancel-order} command a refusal sent.
 *
 * @param commandId the command
 * @param status where it stands, such as {@code QUEUED}, {@code SUCCEEDED} or {@code DUPLICATE}
 * @param errorCode why a rejected, conflicting or parked command did not run; otherwise {@code null}
 * @param originalCommandId for a duplicate or a conflict, the earlier command that holds the order's key; otherwise
 *     {@code null}
 */
public record Cancellation(String commandId, String status, String errorCode, String originalCommandId) {
}
