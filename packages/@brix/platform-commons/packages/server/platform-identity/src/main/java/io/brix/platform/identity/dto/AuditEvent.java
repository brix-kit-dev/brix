/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.dto;

/**
 * Minimal audit event DTO used by the copied identity/bootstrap services.
 *
 * <p>This is a transitional owner-local type. Platform audit ownership remains
 * a later cleanup slice.</p>
 */
public final class AuditEvent {

    private Long createdBy;
    private String action;
    private String resourceType;
    private String resourceId;
    private String description;
    private boolean success = true;

    public static Builder builder() {
        return new Builder();
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSuccess() {
        return success;
    }

    public static final class Builder {

        private final AuditEvent event = new AuditEvent();

        public Builder createdBy(Long createdBy) {
            event.createdBy = createdBy;
            return this;
        }

        public Builder action(String action) {
            event.action = action;
            return this;
        }

        public Builder resourceType(String resourceType) {
            event.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(String resourceId) {
            event.resourceId = resourceId;
            return this;
        }

        public Builder description(String description) {
            event.description = description;
            return this;
        }

        public Builder success(boolean success) {
            event.success = success;
            return this;
        }

        public AuditEvent build() {
            return event;
        }
    }
}
