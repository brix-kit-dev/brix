/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.util.List;
import java.util.Optional;

import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;

/**
 * Who provides and who submits each command type, as the plugin manifests declare.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public interface CommandProviders {

    /**
     * Whether the plugins have started, so every provider and consumer is known.
     *
     * @return readiness
     */
    boolean ready();

    /**
     * Returns the one provider of a command type.
     *
     * @param commandType the command type
     * @return the provider, or empty when none or several provide it
     */
    Optional<PluginRuntimeManager.CommandProvider> providerOf(String commandType);

    /**
     * Returns every provided command.
     *
     * @return the providers
     */
    List<PluginRuntimeManager.CommandProvider> providers();

    /**
     * Returns how a plugin declares the command type it submits.
     *
     * @param pluginId the submitting plugin
     * @param commandType the command type
     * @return the declaration, or empty when the plugin does not declare it
     */
    Optional<PluginRuntimeDescriptor.ConsumedCommand> consumed(String pluginId, String commandType);

    /**
     * Reads the providers from the plugin runtime.
     *
     * @param runtime the plugin runtime
     * @return the providers
     */
    static CommandProviders of(PluginRuntimeManager runtime) {
        return new CommandProviders() {
            @Override
            public boolean ready() {
                return runtime.ready();
            }

            @Override
            public Optional<PluginRuntimeManager.CommandProvider> providerOf(String commandType) {
                return runtime.commandProvider(commandType);
            }

            @Override
            public List<PluginRuntimeManager.CommandProvider> providers() {
                return runtime.commandProviders();
            }

            @Override
            public Optional<PluginRuntimeDescriptor.ConsumedCommand> consumed(String pluginId, String commandType) {
                return runtime.consumedCommand(pluginId, commandType);
            }
        };
    }
}
