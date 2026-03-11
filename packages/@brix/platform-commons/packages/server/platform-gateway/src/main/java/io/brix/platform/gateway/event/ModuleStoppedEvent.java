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
package io.brix.platform.gateway.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Module Stopped Event
 * 
 * <p>Published by Runtime Orchestrator when a module stops.
 * Gateway listens to this event to remove routes registered by the module.</p>
 * 
 * <h3>Event Source</h3>
 * <p>Published by Runtime Orchestrator when module enters STOPPED state,
 * transmitted to gateway via EventBusCapability.</p>
 * 
 * <h3>Event Data</h3>
 * <ul>
 *   <li>moduleId - Module unique identifier</li>
 *   <li>reason - Stop reason</li>
 *   <li>graceful - Whether graceful shutdown</li>
 *   <li>timestamp - Event occurrence time</li>
 * </ul>
 * 
 * <h3>Relationship with Manifest</h3>
 * <p>Corresponds to events.subscriptions configuration in module-manifest.yaml:</p>
 * <pre>{@code
 * events:
 *   subscriptions:
 *     - topic: "brix.module.lifecycle"
 *       event-type: "ModuleStoppedEvent"
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public class ModuleStoppedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Stop reason enumeration
     */
    public enum StopReason {
        /** Normal stop */
        NORMAL,
        /** Unloaded */
        UNLOAD,
        /** Caused by error */
        ERROR,
        /** Timeout */
        TIMEOUT,
        /** System shutdown */
        SHUTDOWN
    }

    /**
     * Module unique identifier
     */
    private final String moduleId;

    /**
     * Stop reason
     */
    private final StopReason reason;

    /**
     * Stop reason description
     */
    private final String reasonMessage;

    /**
     * Whether graceful shutdown
     */
    private final boolean graceful;

    /**
     * Event occurrence time
     */
    private final Instant timestamp;

    /**
     * Constructor
     * 
     * @param source        event source
     * @param moduleId      module unique identifier
     * @param reason        stop reason
     * @param reasonMessage stop reason description
     * @param graceful      whether graceful shutdown
     */
    public ModuleStoppedEvent(Object source,
                              String moduleId,
                              StopReason reason,
                              String reasonMessage,
                              boolean graceful) {
        super(source);
        this.moduleId = moduleId;
        this.reason = reason != null ? reason : StopReason.NORMAL;
        this.reasonMessage = reasonMessage;
        this.graceful = graceful;
        this.timestamp = Instant.now();
    }

    /**
     * Simplified constructor (normal stop)
     * 
     * @param source   event source
     * @param moduleId module unique identifier
     */
    public ModuleStoppedEvent(Object source, String moduleId) {
        this(source, moduleId, StopReason.NORMAL, null, true);
    }

    /**
     * Get module unique identifier
     * 
     * @return module ID
     */
    public String getModuleId() {
        return moduleId;
    }

    /**
     * Get stop reason
     * 
     * @return stop reason enumeration
     */
    public StopReason getReason() {
        return reason;
    }

    /**
     * Get stop reason description
     * 
     * @return reason description
     */
    public String getReasonMessage() {
        return reasonMessage;
    }

    /**
     * Whether graceful shutdown
     * 
     * @return true indicates graceful shutdown
     */
    public boolean isGraceful() {
        return graceful;
    }

    /**
     * Get event occurrence time
     * 
     * @return timestamp
     */
    public Instant getEventTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "ModuleStoppedEvent{" +
                "moduleId='" + moduleId + '\'' +
                ", reason=" + reason +
                ", reasonMessage='" + reasonMessage + '\'' +
                ", graceful=" + graceful +
                ", timestamp=" + timestamp +
                '}';
    }
}
