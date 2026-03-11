/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.gateway.config.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IP whitelistconfigurationproperty
 * 
 * <p>P105 task：requestsign+ IP whitename
 * 
 * <p>configuration IP whitelistverifyofrelatedparameter
 * 
 * <p>configurationexample
 * <pre>
 * gateway:
 *   ip-whitelist:
 *     enabled: true
 *     allowed-ips:
 *       - 127.0.0.1
 *       - 192.168.1.0/24
 *       - 10.0.0.0/8
 *     protected-paths:
 *       - /open-api/**
 * </pre>
 * 
 * <p>supportIP format
 * <ul>
 *   <li>single IP92.168.1.100</li>
 *   <li>CIDR format92.168.1.0/24</li>
 *   <li>IP range92.168.1.1-192.168.1.255</li>
 * </ul>
 *
 * @author Brix Platform Authors Platform
 * @version 1.0.0
 * @since 2025-12-13
 */
@Component
@ConfigurationProperties(prefix = "gateway.ip-whitelist")
public class IpWhitelistProperties {

    /**
     * whetherenable IP whitename
     */
    private boolean enabled = true;

    /**
     * allowIP addresslist
     * supportsingle IP、CIDR、IP range
     */
    private List<String> allowedIps = new ArrayList<>(List.of(
            "127.0.0.1",
            "0:0:0:0:0:0:0:1",  // IPv6 localhost
            "192.168.0.0/16",
            "10.0.0.0/8",
            "172.16.0.0/12"
    ));

    /**
     * needIP whitelistverifyofpath
     */
    private List<String> protectedPaths = new ArrayList<>(List.of("/open-api/**"));

    /**
     * whethertrust X-Forwarded-For request
     * onusereversetogenerationmanagetimesetis true
     */
    private boolean trustXForwardedFor = true;

    /**
     * refreshinterval（seconds
     * configurationchangemoreaftergenerateeffectofdelaytime
     */
    private int refreshIntervalSeconds = 30;

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedIps() {
        return allowedIps;
    }

    public void setAllowedIps(List<String> allowedIps) {
        this.allowedIps = allowedIps;
    }

    public List<String> getProtectedPaths() {
        return protectedPaths;
    }

    public void setProtectedPaths(List<String> protectedPaths) {
        this.protectedPaths = protectedPaths;
    }

    public boolean isTrustXForwardedFor() {
        return trustXForwardedFor;
    }

    public void setTrustXForwardedFor(boolean trustXForwardedFor) {
        this.trustXForwardedFor = trustXForwardedFor;
    }

    public int getRefreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    public void setRefreshIntervalSeconds(int refreshIntervalSeconds) {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
    }
}
