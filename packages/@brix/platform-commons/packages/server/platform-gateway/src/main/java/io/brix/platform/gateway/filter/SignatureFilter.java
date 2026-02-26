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
 * 请求签名校验过滤器。
 *
 * <p>使用 HMAC-SHA256 算法校验请求签名，防止请求篡改和重放攻击。
 *
 * <p>签名要素：
 * <ul>
 *   <li>X-Timestamp - Unix 时间戳（秒级）</li>
 *   <li>X-Nonce - 随机字符串</li>
 *   <li>X-Signature - HMAC-SHA256 签名</li>
 * </ul>
 *
 * <p>签名算法：
 * <pre>
 * stringToSign = timestamp + "\n" + nonce + "\n" + sha256(requestBody)
 * signature = Base64(HMAC-SHA256(secretKey, stringToSign))
 * </pre>
 *
 * <p>防护措施：
 * <ul>
 *   <li>时间戳防重放：超过 5 分钟的请求拒绝</li>
 *   <li>Nonce 防重放：可结合 Redis 实现（当前版本未实现）</li>
 *   <li>Body Hash：防止请求体被篡改</li>
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
     * 过滤器顺序：在 IP 白名单之后执行。
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
        // 检查是否启用
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 检查路径是否需要签名校验
        if (!isProtectedPath(path)) {
            return chain.filter(exchange);
        }

        log.debug("[SignatureFilter] 校验请求签名: {} {}", request.getMethod(), path);

        // 获取签名相关请求头
        String signatureHeader = request.getHeaders().getFirst(properties.getSignatureHeader());
        String signature = signatureHeader != null ? signatureHeader : "";
        String timestampHeader = request.getHeaders().getFirst(properties.getTimestampHeader());
        String timestamp = timestampHeader != null ? timestampHeader : "";
        String nonceHeader = request.getHeaders().getFirst(properties.getNonceHeader());
        String nonce = nonceHeader != null ? nonceHeader : "";

        // 校验必要参数
        if (!StringUtils.hasText(signature)) {
            log.warn("[SignatureFilter] 请求缺少签名头: {}", path);
            return rejectRequest(exchange, "MISSING_SIGNATURE", "缺少请求签名");
        }

        if (!StringUtils.hasText(timestamp)) {
            log.warn("[SignatureFilter] 请求缺少时间戳头: {}", path);
            return rejectRequest(exchange, "MISSING_TIMESTAMP", "缺少时间戳");
        }

        if (!StringUtils.hasText(nonce)) {
            log.warn("[SignatureFilter] 请求缺少 Nonce 头: {}", path);
            return rejectRequest(exchange, "MISSING_NONCE", "缺少随机数");
        }

        // 校验时间戳
        if (!isTimestampValid(timestamp)) {
            log.warn("[SignatureFilter] 时间戳无效或已过期: {}", timestamp);
            return rejectRequest(exchange, "INVALID_TIMESTAMP", "时间戳无效或已过期");
        }

        // 读取请求体并校验签名
        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bodyBytes);
                    DataBufferUtils.release(dataBuffer);

                    // 计算并校验签名
                    String expectedSignature = calculateSignature(timestamp, nonce, bodyBytes);
                    if (expectedSignature == null || !expectedSignature.equals(signature)) {
                        log.warn("[SignatureFilter] 签名校验失败: path={}, expected={}, actual={}", 
                                path, expectedSignature, signature);
                        return rejectRequest(exchange, "INVALID_SIGNATURE", "签名校验失败");
                    }

                    log.debug("[SignatureFilter] 签名校验通过: {}", path);

                    // 重建请求体
                    ServerHttpRequest mutatedRequest = request.mutate().build();
                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(mutatedRequest)
                            .build();

                    return chain.filter(mutatedExchange);
                });
    }

    /**
     * 检查路径是否需要签名校验。
     *
     * @param path 请求路径
     * @return 如果需要校验返回 true
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
     * 校验时间戳是否在有效范围内。
     *
     * @param timestampStr 时间戳字符串（秒级）
     * @return 如果有效返回 true
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
     * 计算请求签名。
     *
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param body 请求体
     * @return Base64 编码的签名
     */
    private String calculateSignature(String timestamp, String nonce, byte[] body) {
        try {
            // 计算请求体的 SHA256 哈希
            String bodyHash = sha256Hex(body);

            // 构建待签名字符串
            String stringToSign = timestamp + "\n" + nonce + "\n" + bodyHash;

            // 使用 HMAC-SHA256 计算签名
            Mac mac = Mac.getInstance(properties.getAlgorithm());
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    properties.getSecretKey().getBytes(StandardCharsets.UTF_8),
                    properties.getAlgorithm()
            );
            mac.init(secretKeySpec);
            byte[] signatureBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("[SignatureFilter] 计算签名失败", e);
            return null;
        }
    }

    /**
     * 计算 SHA256 哈希（十六进制）。
     *
     * @param data 数据
     * @return 十六进制哈希值
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
     * 拒绝请求，返回 401 错误。
     *
     * @param exchange ServerWebExchange
     * @param errorCode 错误代码
     * @param message 错误消息
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
