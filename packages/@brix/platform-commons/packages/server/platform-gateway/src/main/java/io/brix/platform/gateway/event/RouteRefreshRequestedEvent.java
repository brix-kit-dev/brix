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
 * Route Refresh Requested Event
 * 
 * <p>Route force refresh event triggered by operations personnel or system.
 * Gateway listens to this event to reload all route configurations.</p>
 * 
 * <h3>Usage Scenarios</h3>
 * <ul>
 *   <li>Manual refresh after route configuration changes</li>
 *   <li>Reloading during route troubleshooting</li>
 *   <li>Disaster recovery scenarios</li>
 *   <li>Auto-trigger when operations monitoring detects route anomalies</li>
 * </ul>
 * 
 * <h3>Trigger Methods</h3>
 * <p>Can be triggered via:</p>
 * <ul>
 *   <li>Operations API interface</li>
 *   <li>EventBus publish event</li>
 *   <li>Scheduled task check</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public class RouteRefreshRequestedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Refresh reason
     */
    private final String reason;

    /**
     * Request source (e.g., api, scheduler, operator)
     */
    private final String source;

    /**
     * Whether to force refresh (ignore cache)
     */
    private final boolean force;

    /**
     * Specified module ID to refresh (empty for all)
     */
    private final String targetModuleId;

    /**
     * Event occurrence time
     */
    private final Instant timestamp;

    /**
     * Constructor
     * 
     * @param eventSource    event publisher
     * @param reason         refresh reason
     * @param requestSource  request source
     * @param force          whether to force refresh
     * @param targetModuleId target module ID
     */
    public RouteRefreshRequestedEvent(Object eventSource,
                                      String reason,
                                      String requestSource,
                                      boolean force,
                                      String targetModuleId) {
        super(eventSource);
        this.reason = reason != null ? reason : "Manual refresh";
        this.source = requestSource != null ? requestSource : "unknown";
        this.force = force;
        this.targetModuleId = targetModuleId;
        this.timestamp = Instant.now();
    }

    /**
     * Simplified constructor
     * 
     * @param eventSource event publisher
     * @param reason      refresh reason
     */
    public RouteRefreshRequestedEvent(Object eventSource, String reason) {
        this(eventSource, reason, null, false, null);
    }

    /**
     * Get refresh reason
     * 
     * @return reason description
     */
    public String getReason() {
        return reason;
    }

    /**
     * Get request source
     * 
     * @return source identifier
     */
    public String getRequestSource() {
        return source;
    }

    /**
     * Whether force refresh
     * 
     * @return true indicates ignoring cache for force refresh
     */
    public boolean isForce() {
        return force;
    }

    /**
     * Get target module ID
     * 
     * @return module ID, empty for refresh all
     */
    public String getTargetModuleId() {
        return targetModuleId;
    }

    /**
     * Whether to refresh all routes
     * 
     * @return true indicates refresh all
     */
    public boolean isRefreshAll() {
        return targetModuleId == null || targetModuleId.isEmpty();
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
        return "RouteRefreshRequestedEvent{" +
                "reason='" + reason + '\'' +
                ", source='" + source + '\'' +
                ", force=" + force +
                ", targetModuleId='" + targetModuleId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
