/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.event;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.runtime.orchestrator.plugin.PluginRuntimeManager;

/**
 * The publishers and subscribers the plugin runtime knows from the manifests.
 */
public final class PluginEventSubscribers implements EventSubscribers {

    private final PluginRuntimeManager plugins;

    public PluginEventSubscribers(PluginRuntimeManager plugins) {
        this.plugins = Objects.requireNonNull(plugins, "plugins must not be null");
    }

    @Override
    public boolean ready() {
        return plugins.ready();
    }

    @Override
    public Optional<String> publisherOf(String eventType) {
        return plugins.eventPublisher(eventType);
    }

    @Override
    public List<DeclaredSubscriber> subscribersOf(String eventType) {
        return declared(plugins.eventSubscribers(eventType));
    }

    @Override
    public List<DeclaredSubscriber> subscriptions() {
        return declared(plugins.eventSubscribers());
    }

    @Override
    public Set<String> publishedEventTypes() {
        return plugins.eventPublications();
    }

    private static List<DeclaredSubscriber> declared(List<PluginRuntimeManager.EventSubscriber> subscribers) {
        return subscribers.stream()
            .map(subscriber -> new DeclaredSubscriber(subscriber.pluginId(), subscriber.subscription(), subscriber.handler()))
            .toList();
    }
}
