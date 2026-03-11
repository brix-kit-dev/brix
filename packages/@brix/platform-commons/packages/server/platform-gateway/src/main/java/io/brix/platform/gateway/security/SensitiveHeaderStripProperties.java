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
package io.brix.platform.gateway.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * sensitiverequestheaderstripconfiguration
 * <p>
 * used forconfigurationGatewayneedstripofsensitiverequestheader，preventclientforge identityinformation
 * </p>
 * <p>
 * configurationexample（application.yml）：
 * <pre>
 * gateway:
 *   security:
 *     header-strip:
 *       enabled: true
 *       headers:
 *         - x-user-id
 *         - x-tenant-id
 *         - x-role
 *         - x-roles
 *         - x-permissions
 *       log-stripped: true
 * </pre>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ConfigurationProperties(prefix = "gateway.security.header-strip")
public class SensitiveHeaderStripProperties {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveHeaderStripProperties.class);

    /**
     * defaultneedstripofsensitiveheader（MVP Red Line Requirements
     */
    private static final List<String> DEFAULT_SENSITIVE_HEADERS = List.of(
            "x-user-id",
            "x-tenant-id",
            "x-role",
            "x-roles",
            "x-permissions",
            "x-user-name",
            "x-internal-call"
    );

    /**
     * whetherenablesensitiveheaderstrip
     */
    private boolean enabled = true;

    /**
     * needstripofrequestheaderlist（notdistinguishsizewrite
     */
    private List<String> headers = new ArrayList<>(DEFAULT_SENSITIVE_HEADERS);

    /**
     * whetherrecordstripoperationlog
     */
    private boolean logStripped = true;

    /**
     * whetheronlogindisplaybestripoforiginalvalue（productionenvironmentrecommended false
     */
    private boolean logStrippedValue = false;

    /**
     * excludestripofpath（internalservicebetweencallcancanneedretaintheseheader
     */
    private List<String> excludePaths = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.warn("[brix]  Sensitive header stripping is DISABLED. " +
                    "This may allow header spoofing attacks!");
            return;
        }

        // willallhasheaderconvertislowercasetoconveniencenotdistinguishsizewritematch
        headers = headers.stream()
                .map(String::toLowerCase)
                .distinct()
                .toList();

        logger.info("[brix] Sensitive header stripping enabled for {} header(s): {}",
                headers.size(), headers);
    }

    /**
     * checkspecifyofheaderwhethershouldthisbestrip
     *
     * @param headerName requestheadername
     * @return true ifshouldthisstrip
     */
    public boolean shouldStrip(String headerName) {
        if (!enabled || headerName == null) {
            return false;
        }
        return headers.contains(headerName.toLowerCase());
    }

    /**
     * obtainallhasneedstripofheadernamecollection（lowercase
     *
     * @return headernamecollection
     */
    public Set<String> getHeadersAsSet() {
        return Set.copyOf(headers);
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public boolean isLogStripped() {
        return logStripped;
    }

    public void setLogStripped(boolean logStripped) {
        this.logStripped = logStripped;
    }

    public boolean isLogStrippedValue() {
        return logStrippedValue;
    }

    public void setLogStrippedValue(boolean logStrippedValue) {
        this.logStrippedValue = logStrippedValue;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }
}
