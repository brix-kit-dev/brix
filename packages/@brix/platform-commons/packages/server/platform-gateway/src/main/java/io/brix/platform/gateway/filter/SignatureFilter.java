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
package io.brix.platform.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import io.brix.platform.gateway.config.security.SignatureProperties;

/**
 * requestsignatureverifyfilter。
 *
 * <p>use HMAC-SHA256 algorithmverifyrequestsignature，Prevent request tamperingandre-release attack。
 *
 * <p>signaturemustelement：
 * <ul>
 *   <li>X-Timestamp - Unix timestamp（secondslevel）</li>
 *   <li>X-Nonce - randomcharacterstring</li>
 *   <li>X-Signature - HMAC-SHA256 signature</li>
 * </ul>
 *
 * <p>signaturealgorithm：
 * <pre>
 * stringToSign = timestamp + "\n" + nonce + "\n" + sha256(requestBody)
 * signature = Base64(HMAC-SHA256(secretKey, stringToSign))
 * </pre>
 *
 * <p>preventprotection measure：
 * <ul>
 *   <li>timestamppreventre-release：exceed 5 minuteofrequestrejected</li>
 *   <li>Nonce preventre-release：canresultcombine Redis implementation（whenbeforeversionnot yetimplementation）</li>
 *   <li>Body Hash：preventrequestbodybetamper</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since 2025-12-13
 */
@Component
public class SignatureFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SignatureFilter.class);

    /**
     * filterorder：on IP whitelistofafterexecute。
     */
    private static final int FILTER_ORDER = -195;

    private final SignatureProperties properties;
    private final AntPathMatcher pathMatcher;

    public SignatureFilter(SignatureProperties properties) {
        this.properties = properties;
        this.pathMatcher = new AntPathMatcher();
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // checkwhetherenable
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // checkpathwhetherneedsignatureverify
        if (!isProtectedPath(path)) {
            return chain.filter(exchange);
        }

        log.debug("[SignatureFilter] verifyrequestsignature: {} {}", request.getMethod(), path);

        // obtainsignaturerelatedrequestheader
        String signatureHeader = request.getHeaders().getFirst(properties.getSignatureHeader());
        String signature = signatureHeader != null ? signatureHeader : "";
        String timestampHeader = request.getHeaders().getFirst(properties.getTimestampHeader());
        String timestamp = timestampHeader != null ? timestampHeader : "";
        String nonceHeader = request.getHeaders().getFirst(properties.getNonceHeader());
        String nonce = nonceHeader != null ? nonceHeader : "";

        // verifymustmustparameter
        if (!StringUtils.hasText(signature)) {
            log.warn("[SignatureFilter] requestmissingsignatureheader: {}", path);
            return rejectRequest(exchange, "MISSING_SIGNATURE", "missingrequestsignature");
        }

        if (!StringUtils.hasText(timestamp)) {
            log.warn("[SignatureFilter] requestmissingtimestampheader: {}", path);
            return rejectRequest(exchange, "MISSING_TIMESTAMP", "missingtimestamp");
        }

        if (!StringUtils.hasText(nonce)) {
            log.warn("[SignatureFilter] requestmissing Nonce header: {}", path);
            return rejectRequest(exchange, "MISSING_NONCE", "missingrandomcount");
        }

        // verifytimestamp
        if (!isTimestampValid(timestamp)) {
            log.warn("[SignatureFilter] timestampnoeffectoralreadyexpire: {}", timestamp);
            return rejectRequest(exchange, "INVALID_TIMESTAMP", "timestampnoeffectoralreadyexpire");
        }

        // readrequestbodyandverifysignature
        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bodyBytes);
                    DataBufferUtils.release(dataBuffer);

                    // calculateandverifysignature
                    String expectedSignature = calculateSignature(timestamp, nonce, bodyBytes);
                    if (expectedSignature == null || !expectedSignature.equals(signature)) {
                        log.warn("[SignatureFilter] signatureverifyfailed: path={}, expected={}, actual={}", 
                                path, expectedSignature, signature);
                        return rejectRequest(exchange, "INVALID_SIGNATURE", "signatureverifyfailed");
                    }

                    log.debug("[SignatureFilter] signatureverifyvia: {}", path);

                    // re-buildrequestbody
                    ServerHttpRequest mutatedRequest = request.mutate().build();
                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(mutatedRequest)
                            .build();

                    return chain.filter(mutatedExchange);
                });
    }

    /**
     * checkpathwhetherneedsignatureverify。
     *
     * @param path requestpath
     * @return ifneedverifyreturn true
     */
    private boolean isProtectedPath(String path) {
        for (String pattern : properties.getProtectedPaths()) {
            if (pathMatcher.match(Objects.requireNonNull(pattern), Objects.requireNonNull(path))) {
                return true;
            }
        }
        return false;
    }

    /**
     * verifytimestampwhetheronvalidrangein。
     *
     * @param timestampStr timestampcharacterstring（secondslevel）
     * @return ifvalidreturn true
     */
    private boolean isTimestampValid(String timestampStr) {
        try {
            long timestamp = Long.parseLong(timestampStr);
            long currentTime = Instant.now().getEpochSecond();
            long difference = Math.abs(currentTime - timestamp);
            return difference <= properties.getTimestampToleranceSeconds();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * calculaterequestsignature。
     *
     * @param timestamp timestamp
     * @param nonce randomcount
     * @param body requestbody
     * @return Base64 compilecodeofsignature
     */
    private String calculateSignature(String timestamp, String nonce, byte[] body) {
        try {
            // calculaterequestbodyof SHA256 hash
            String bodyHash = sha256Hex(body);

            // buildwaitsignaturecharacterstring
            String stringToSign = timestamp + "\n" + nonce + "\n" + bodyHash;

            // use HMAC-SHA256 calculatesignature
            Mac mac = Mac.getInstance(properties.getAlgorithm());
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    properties.getSecretKey().getBytes(StandardCharsets.UTF_8),
                    properties.getAlgorithm()
            );
            mac.init(secretKeySpec);
            byte[] signatureBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("[SignatureFilter] calculatesignaturefailed", e);
            return null;
        }
    }

    /**
     * calculate SHA256 hash（hexadecimal）。
     *
     * @param data countdata
     * @return hexadecimalhashvalue
     */
    private String sha256Hex(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * rejectedrequest，return 401 error。
     *
     * @param exchange ServerWebExchange
     * @param errorCode errorgenerationcode
     * @param message errormessage
     * @return Mono&lt;Void&gt;
     */
    private Mono<Void> rejectRequest(ServerWebExchange exchange, String errorCode, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"code\":401,\"errorCode\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                errorCode, message, Instant.now().toString()
        );

        DataBuffer buffer = response.bufferFactory().wrap(Objects.requireNonNull(body.getBytes(StandardCharsets.UTF_8)));
        Mono<DataBuffer> payload = Mono.just(Objects.requireNonNull(buffer));
        return response.writeWith(Objects.requireNonNull(payload));
    }
}
