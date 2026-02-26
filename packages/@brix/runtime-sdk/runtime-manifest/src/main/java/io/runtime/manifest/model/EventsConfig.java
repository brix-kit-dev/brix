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
package io.runtime.manifest.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Events Configuration.
 *
 * <p>Declares events published and subscribed by the module for declarative event binding.</p>
 *
 * <h4>Example Configuration</h4>
 * <pre>{@code
 * events:
 *   publishes:
 *     - type: "io.brix.app.booking.event.ReservationCreatedEvent"
 *       schema: "schemas/reservation-created.json"
 *       description: "Reservation created event"
 *   subscribes:
 *     - type: "io.brix.app.identity.event.UserCreatedEvent"
 *       handler: "io.brix.app.booking.handler.BookingEventHandler.onUserCreated"
 * }</pre>
 *
 * <p>Extracted from ModuleManifest.java as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ModuleManifest
 */
public class EventsConfig {

    /**
     * Published events list.
     */
    private List<EventPublishConfig> publishes = new ArrayList<>();

    /**
     * Subscribed events list.
     */
    private List<EventSubscribeConfig> subscribes = new ArrayList<>();

    // ==================== Getters and Setters ====================

    public List<EventPublishConfig> getPublishes() { 
        return publishes; 
    }
    
    public void setPublishes(List<EventPublishConfig> publishes) { 
        this.publishes = publishes != null ? publishes : new ArrayList<>(); 
    }
    
    public List<EventSubscribeConfig> getSubscribes() { 
        return subscribes; 
    }
    
    public void setSubscribes(List<EventSubscribeConfig> subscribes) { 
        this.subscribes = subscribes != null ? subscribes : new ArrayList<>(); 
    }

    @Override
    public String toString() {
        return "EventsConfig{" +
               "publishes=" + publishes.size() + 
               ", subscribes=" + subscribes.size() + 
               '}';
    }
}
