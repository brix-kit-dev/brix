/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.event;

import java.util.List;
import java.util.Optional;

import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;

/**
 * Who publishes and who subscribes to an event type, as the plugin manifests declare it.
 */
public interface EventSubscribers {

    /**
     * Whether the plugins have started. Until then no publisher or subscriber is known yet, so nothing
     * may be delivered or judged.
     *
     * @return whether events can be delivered
     */
    boolean ready();

    /**
     * Returns the plugin whose manifest declares the event type under {@code events.publishes}.
     *
     * @param eventType event type
     * @return the only declaring plugin, or empty when none or several declare it
     */
    Optional<String> publisherOf(String eventType);

    /**
     * Returns every declared subscription to the event type, bound or not.
     *
     * @param eventType event type
     * @return the subscriptions, each with its handler once the plugin has started and bound it
     */
    List<DeclaredSubscriber> subscribersOf(String eventType);

    /**
     * One declared subscription.
     *
     * @param pluginId subscriber plugin, the owner of the inbox
     * @param subscription the manifest subscription
     * @param handler the bound handler, or {@code null} while the plugin is not started or has not bound it
     */
    record DeclaredSubscriber(String pluginId, PluginRuntimeDescriptor.EventSubscription subscription, BoundEventHandler handler) {
    }
}
