/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.outbox;

import java.util.Map;

/**
 * Every owner's outbox the relay serves (root BRX-RS-MESSAGE-023: each owner has its own
 * {@code {storage_id}_outbox}). The persistence adapter reads them from the plugin manifests'
 * {@code data.outbox} (P4 design D12).
 */
public interface OutboxStoreRegistry {

    /**
     * Returns one store per outbox table.
     *
     * @return the stores, by outbox table name
     */
    Map<String, OutboxMessageStore> stores();
}
