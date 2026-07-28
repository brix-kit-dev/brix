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
package io.runtime.orchestrator.bootstrap;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime Shell bootstrap configuration properties.
 *
 * <p>These properties describe Host composition requirements without putting
 * plugin lifecycle logic into Host source code.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
@ConfigurationProperties(prefix = "brix.runtime-shell")
public class RuntimeShellBootstrapProperties {

    /**
     * Composition-required backend plugin ids.
     */
    private List<String> requiredPlugins = new ArrayList<>();
    private List<String> requiredOperationalModules = new ArrayList<>();
    private String runtimeVersion = "3.2.0";
    private HostMode hostMode = HostMode.EMBEDDED;
    private int fatalExitCode = 70;

    /**
     * Maximum endpoint handling duration in milliseconds.
     */
    private long endpointDeadlineMillis = 30000;

    /**
     * Returns required plugin ids.
     *
     * @return required plugin ids
     */
    public List<String> getRequiredPlugins() {
        return requiredPlugins;
    }

    /**
     * Sets required plugin ids.
     *
     * @param requiredPlugins required plugin ids
     */
    public void setRequiredPlugins(List<String> requiredPlugins) {
        this.requiredPlugins = requiredPlugins != null ? requiredPlugins : new ArrayList<>();
    }

    /**
     * Returns Composition-required platform operational module ids.
     *
     * @return required operational module ids
     */
    public List<String> getRequiredOperationalModules() {
        return requiredOperationalModules;
    }

    /**
     * Sets Composition-required platform operational module ids.
     *
     * @param requiredOperationalModules required operational module ids
     */
    public void setRequiredOperationalModules(List<String> requiredOperationalModules) {
        this.requiredOperationalModules = requiredOperationalModules != null
            ? requiredOperationalModules
            : new ArrayList<>();
    }

    /**
     * Returns the exact L2B Runtime version used for descriptor matching.
     *
     * @return Runtime version
     */
    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    /**
     * Sets the exact L2B Runtime version.
     *
     * @param runtimeVersion Runtime version
     */
    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    /**
     * Returns the Host execution mode.
     *
     * @return Host mode
     */
    public HostMode getHostMode() {
        return hostMode;
    }

    /**
     * Sets the Host execution mode.
     *
     * @param hostMode Host mode
     */
    public void setHostMode(HostMode hostMode) {
        this.hostMode = hostMode;
    }

    /**
     * Returns the non-zero Standalone/Local fatal exit code.
     *
     * @return fatal exit code
     */
    public int getFatalExitCode() {
        return fatalExitCode;
    }

    /**
     * Sets the Standalone/Local fatal exit code.
     *
     * @param fatalExitCode non-zero exit code
     */
    public void setFatalExitCode(int fatalExitCode) {
        this.fatalExitCode = fatalExitCode;
    }

    /**
     * Returns maximum endpoint handling duration.
     *
     * @return endpoint deadline in milliseconds
     */
    public long getEndpointDeadlineMillis() {
        return endpointDeadlineMillis;
    }

    /**
     * Sets maximum endpoint handling duration.
     *
     * @param endpointDeadlineMillis endpoint deadline in milliseconds
     */
    public void setEndpointDeadlineMillis(long endpointDeadlineMillis) {
        this.endpointDeadlineMillis = endpointDeadlineMillis;
    }

    /**
     * Supported Host process ownership modes.
     */
    public enum HostMode {
        /** Standalone process owned by the Runtime Shell wrapper. */
        STANDALONE,
        /** Embedded container whose owner observes the fatal future. */
        EMBEDDED,
        /** Local developer process with Standalone fatal semantics. */
        LOCAL
    }
}
