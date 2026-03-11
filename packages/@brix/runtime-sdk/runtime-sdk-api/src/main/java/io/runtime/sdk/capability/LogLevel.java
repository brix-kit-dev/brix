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

/**
 * Log Level Enumeration
 * 
 * <p>Defines observability log levels, ordered from lowest to highest.</p>
 * 
 * <h3>Level Description</h3>
 * <ul>
 *   <li><b>TRACE</b>: Most detailed debug information, typically only enabled in development</li>
 *   <li><b>DEBUG</b>: Debug information for troubleshooting</li>
 *   <li><b>INFO</b>: Important business events like user login, order creation</li>
 *   <li><b>WARN</b>: Warning information like missing configuration, performance degradation</li>
 *   <li><b>ERROR</b>: Error information requiring attention but not affecting system operation</li>
 * </ul>
 * 
 * <h3>Level Selection Guidelines</h3>
 * <table border="1">
 *   <tr><th>Scenario</th><th>Recommended Level</th></tr>
 *   <tr><td>Method entry/exit tracing</td><td>TRACE</td></tr>
 *   <tr><td>Variable values, conditional branches</td><td>DEBUG</td></tr>
 *   <tr><td>Business events (login, order)</td><td>INFO</td></tr>
 *   <tr><td>Recoverable exceptions</td><td>WARN</td></tr>
 *   <tr><td>Unrecoverable errors</td><td>ERROR</td></tr>
 * </table>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ObservabilityCapability#log(LogLevel, String, Object...)
 */
public enum LogLevel {

    /**
     * Trace level - Most detailed debug information
     */
    TRACE(0),

    /**
     * Debug level - For development debugging
     */
    DEBUG(1),

    /**
     * Info level - Important business events
     */
    INFO(2),

    /**
     * Warn level - Potential issues
     */
    WARN(3),

    /**
     * Error level - Errors requiring attention
     */
    ERROR(4);

    /**
     * Level ordinal for level comparison
     */
    private final int level;

    LogLevel(int level) {
        this.level = level;
    }

    /**
     * Gets the level ordinal
     * 
     * @return the level ordinal
     */
    public int getLevel() {
        return level;
    }

    /**
     * Checks if the current level is greater than or equal to the specified level
     * 
     * @param other the level to compare against
     * @return true if the current level >= specified level
     */
    public boolean isEnabledFor(LogLevel other) {
        return this.level >= other.level;
    }
}
