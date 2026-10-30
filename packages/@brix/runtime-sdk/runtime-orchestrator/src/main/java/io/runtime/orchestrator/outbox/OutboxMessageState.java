/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.OffsetDateTime;

/**
 * Where an outbox message stands (Kafka design §14 K14, K16).
 *
 * @param status {@code PENDING}, {@code IN_FLIGHT}, {@code PUBLISHED}, {@code PARKED} or {@code DISCARDED}
 * @param publishedAt when it was published, or {@code null}
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public record OutboxMessageState(String status, OffsetDateTime publishedAt) {
}
