/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

import java.time.Instant;
import java.util.Map;

/**
 * Summary of a domain-specific business object associated with a principal.
 *
 * <p>Represents a lightweight view of an industry-specific business entity
 * (e.g., medical case, enrollment, order) that is linked to a Subject
 * (C-side principal) in a tenant.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2A: SDK Contract — used by {@link BusinessObjectCapability}.</p>
 *
 * <h3>Plugin Responsibility</h3>
 * <p>Industry plugins populate this summary from their domain tables.
 * For example, a healthcare plugin maps from {@code medical_case} table,
 * while an education plugin maps from {@code enrollment} table.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see BusinessObjectCapability
 */
public class BusinessObjectSummary {

    private final String id;
    private final String objectType;
    private final String title;
    private final String status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Map<String, Object> metadata;

    /**
     * Constructs a business object summary.
     *
     * @param id         the business object ID
     * @param objectType the plugin-defined object type (e.g., "medical_case", "enrollment", "order")
     * @param title      a human-readable title for display
     * @param status     the current status (plugin-defined)
     * @param createdAt  when the business object was created
     * @param updatedAt  when the business object was last updated
     * @param metadata   additional key-value metadata (nullable)
     */
    public BusinessObjectSummary(String id, String objectType, String title, String status,
                                  Instant createdAt, Instant updatedAt,
                                  Map<String, Object> metadata) {
        this.id = id;
        this.objectType = objectType;
        this.title = title;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.metadata = metadata;
    }

    /**
     * Returns the business object ID.
     *
     * @return the unique identifier within the plugin's domain
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the object type, defined by the industry plugin.
     *
     * <p>Examples: {@code "medical_case"}, {@code "enrollment"}, {@code "order"}.</p>
     *
     * @return the plugin-defined object type
     */
    public String getObjectType() {
        return objectType;
    }

    /**
     * Returns a human-readable title.
     *
     * @return the display title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the current status of the business object.
     *
     * <p>Status values are plugin-defined (e.g., "OPEN", "CLOSED", "IN_PROGRESS").</p>
     *
     * @return the current status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Returns when the business object was created.
     *
     * @return the creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns when the business object was last updated.
     *
     * @return the last update timestamp (nullable)
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Returns additional metadata as key-value pairs.
     *
     * <p>Allows plugins to attach domain-specific data without
     * extending the summary class.</p>
     *
     * @return metadata map, or null if not provided
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
