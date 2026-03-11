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
 * requestsignatureconfigurationproperty
 * 
 * <p>P105 task：requestsign+ IP whitename
 * 
 * <p>configuration HMAC-SHA256 requestsignatureverifyofrelatedparameter
 * 
 * <p>configurationexample
 * <pre>
 * gateway:
 *   signature:
 *     enabled: true
 *     secret-key: your-secret-key
 *     timestamp-tolerance-seconds: 300
 *     protected-paths:
 *       - /open-api/**
 * </pre>
 *
 * @author Brix Platform Authors Platform
 * @version 1.0.0
 * @since 2025-12-13
 */
@Component
@ConfigurationProperties(prefix = "gateway.signature")
public class SignatureProperties {

    /**
     * whetherenablesignatureverify
     */
    private boolean enabled = true;

    /**
     * signaturesecret key（HMAC-SHA256
     * productionenvironmentmustviaenvironmentvariableconfiguration
     */
    private String secretKey = "brix-default-signature-key-change-in-production";

    /**
     * timestampallowenduretime（seconds）
     * exceedthistimeofrequestviewisre-release attack
     */
    private int timestampToleranceSeconds = 300;

    /**
     * needsignatureverifyofpath
     */
    private List<String> protectedPaths = new ArrayList<>(List.of("/open-api/**"));

    /**
     * signaturerequestheadername
     */
    private String signatureHeader = "X-Signature";

    /**
     * Timestamp header name
     */
    private String timestampHeader = "X-Timestamp";

    /**
     * Nonce requestheadername
     */
    private String nonceHeader = "X-Nonce";

    /**
     * Signature algorithm
     */
    private String algorithm = "HmacSHA256";

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public int getTimestampToleranceSeconds() {
        return timestampToleranceSeconds;
    }

    public void setTimestampToleranceSeconds(int timestampToleranceSeconds) {
        this.timestampToleranceSeconds = timestampToleranceSeconds;
    }

    public List<String> getProtectedPaths() {
        return protectedPaths;
    }

    public void setProtectedPaths(List<String> protectedPaths) {
        this.protectedPaths = protectedPaths;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public void setSignatureHeader(String signatureHeader) {
        this.signatureHeader = signatureHeader;
    }

    public String getTimestampHeader() {
        return timestampHeader;
    }

    public void setTimestampHeader(String timestampHeader) {
        this.timestampHeader = timestampHeader;
    }

    public String getNonceHeader() {
        return nonceHeader;
    }

    public void setNonceHeader(String nonceHeader) {
        this.nonceHeader = nonceHeader;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}
