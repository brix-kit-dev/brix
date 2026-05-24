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
package io.brix.platform.admin.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Configuration for platform-admin setup-link delivery. */
@Component
@ConfigurationProperties(prefix = "brix.platform.admin.setup")
public class PlatformAdminSetupProperties {

    private final Environment environment;

    public PlatformAdminSetupProperties(Environment environment) {
        this.environment = environment;
    }

    /** Public frontend setup URL, without token query parameter. */
    private String baseUrl;

    /** Mail sender address used when JavaMail delivery is enabled. */
    private String mailFrom;

    /** Setup token TTL in seconds. */
    private long tokenTtlSeconds = 86400L;

    /** Development-only switch. Production profiles may never enable it. */
    private boolean returnUrlForDevOnly = false;

    @PostConstruct
    void validateProductionSafety() {
        boolean enabled = returnUrlForDevOnly
                || environment.getProperty("platform.admin.setup.return-url-for-dev-only", Boolean.class, false);
        if (enabled && hasProductionProfile()) {
            throw new IllegalStateException(
                    "platform.admin.setup.return-url-for-dev-only must not be enabled in production profiles");
        }
    }

    public String buildSetupUrl(String setupToken) {
        if (setupToken == null || setupToken.isBlank()) {
            throw new IllegalArgumentException("setupToken is required");
        }
        String url = trimToNull(baseUrl);
        if (url == null) {
            throw new IllegalStateException("brix.platform.admin.setup.base-url is required");
        }
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "token=" + URLEncoder.encode(setupToken, StandardCharsets.UTF_8);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public boolean isReturnUrlForDevOnly() {
        return returnUrlForDevOnly;
    }

    public void setReturnUrlForDevOnly(boolean returnUrlForDevOnly) {
        this.returnUrlForDevOnly = returnUrlForDevOnly;
    }

    private boolean hasProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(profile -> profile.contains("prod"));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}