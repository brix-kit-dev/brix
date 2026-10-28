/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.infra.adapter.outbox;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;
import io.runtime.manifest.validation.PluginManifestValidator;
import io.runtime.orchestrator.outbox.OutboxMessageStore;
import io.runtime.orchestrator.outbox.OutboxStoreRegistry;

/**
 * One store for each outbox the active plugin manifests declare under {@code data.outbox} (root
 * BRX-RS-MESSAGE-023; P4 design D12). The manifests are the only source of the table names; nothing in
 * the Host configuration repeats them.
 */
public final class ManifestOutboxStoreRegistry implements OutboxStoreRegistry {

    private final ClassLoader classLoader;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private volatile Map<String, OutboxMessageStore> stores;

    /**
     * Creates the registry.
     *
     * @param classLoader sees every plugin manifest
     * @param jdbcTemplate shared JDBC access
     * @param transactionTemplate transactions for claiming
     */
    public ManifestOutboxStoreRegistry(
            ClassLoader classLoader, JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
    }

    @Override
    public Map<String, OutboxMessageStore> stores() {
        Map<String, OutboxMessageStore> found = stores;
        if (found == null) {
            synchronized (this) {
                found = stores;
                if (found == null) {
                    Map<String, OutboxMessageStore> created = new TreeMap<>();
                    for (Map.Entry<String, String> outbox : outboxes(classLoader).entrySet()) {
                        created.put(outbox.getValue(),
                            new JdbcOutboxMessageStore(jdbcTemplate, transactionTemplate, outbox.getValue()));
                    }
                    found = Collections.unmodifiableMap(created);
                    stores = found;
                }
            }
        }
        return found;
    }

    /**
     * Reads the outbox each active manifest declares.
     *
     * @param classLoader sees every plugin manifest
     * @return outbox table by plugin id
     */
    static Map<String, String> outboxes(ClassLoader classLoader) {
        PluginManifestLoader loader = new PluginManifestLoader();
        Map<String, String> outboxes = new TreeMap<>();
        Map<String, String> owners = new TreeMap<>();
        try {
            for (URL resource : Collections.list(classLoader.getResources(PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE))) {
                PluginManifest manifest;
                try (InputStream input = resource.openStream()) {
                    manifest = loader.load(input);
                }
                PluginManifest.DataSection data = manifest.getData();
                if (data == null || data.getOutbox() == null || data.getOutbox().isBlank()) {
                    continue;
                }
                String pluginId = manifest.getMetadata().getPluginId();
                String previous = owners.putIfAbsent(data.getOutbox(), pluginId);
                if (previous != null) {
                    throw new IllegalStateException("Outbox " + data.getOutbox() + " is declared by both " + previous
                        + " and " + pluginId);
                }
                outboxes.put(pluginId, data.getOutbox());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read the plugin manifests for their outboxes", e);
        }
        return outboxes;
    }
}
