/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import io.runtime.sdk.annotation.Since;

/**
 * The external calls of host-wired platform code (standard-link plan L8.5).
 *
 * <p>Platform code the host wires itself, outside a plugin's context, gets the external calls of the module it
 * belongs to here; the services are still only those that module's manifest declares. A plugin uses
 * {@code PluginContext.require(ExternalHttpCapability.class)}.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Since("3.3.0")
public interface ExternalHttpModules {

    /**
     * Returns a module's external calls.
     *
     * @param moduleId the module the calling code belongs to
     * @return its capability; its services resolve once the module has started
     */
    ExternalHttpCapability forModule(String moduleId);
}
