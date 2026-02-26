package io.brix.platform.gateway.filter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import io.brix.platform.gateway.config.security.IpWhitelistProperties;

/**
 * IP 白名单过滤器
 * 
 * <p>P105 任务：请求签+ IP 白名
 * 
 * <p>校验请求来源 IP 是否在白名单内，防止未授权访问
 * 
 * <p>支持IP 格式
 * <ul>
 *   <li>单个 IP92.168.1.100</li>
 *   <li>CIDR 格式92.168.1.0/24</li>
 *   <li>IPv6 地址:1</li>
 * </ul>
 * 
 * <p>IP 获取优先级：
 * <ol>
 *   <li>X-Forwarded-For 请求头（第一IP</li>
 *   <li>X-Real-IP 请求</li>
 *   <li>远程地址</li>
 * </ol>
 *
 * @author Brix Platform Authors Platform
 * @version 1.0.0
 * @since 2025-12-13
 */
@Component
public class IpWhitelistFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(IpWhitelistFilter.class);

    /**
     * 过滤器顺序：在签名校验之前执
     */
    private static final int FILTER_ORDER = -196;

    private final IpWhitelistProperties properties;
    private final AntPathMatcher pathMatcher;
    
    /**
     * 解析后的 IP 规则列表
     */
    private List<IpRule> ipRules;

    public IpWhitelistFilter(IpWhitelistProperties properties) {
        this.properties = properties;
        this.pathMatcher = new AntPathMatcher();
    }

    @PostConstruct
    public void init() {
        refreshIpRules();
        log.info("[IpWhitelistFilter] 初始化完成，白名单规则数：{}", ipRules.size());
    }

    /**
     * 刷新 IP 规则
     */
    public void refreshIpRules() {
        this.ipRules = new ArrayList<>();
        for (String ip : properties.getAllowedIps()) {
            try {
                ipRules.add(parseIpRule(ip));
            } catch (UnknownHostException | NumberFormatException e) {
                log.warn("[IpWhitelistFilter] 鏃犳硶瑙ｆ瀽 IP 瑙勫垯: {}", ip, e);
            }
        }
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 检查是否启
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 检查路径是否需IP 白名单校
        if (!isProtectedPath(path)) {
            return chain.filter(exchange);
        }

        // 获取客户IP
        String clientIp = getClientIp(request);
        log.debug("[IpWhitelistFilter] 鏍￠獙 IP: {} -> {}", clientIp, path);

        // 校验 IP 是否在白名单
        if (!isIpAllowed(clientIp)) {
            log.warn("[IpWhitelistFilter] IP 不在白名单内: {} -> {}", clientIp, path);
            return rejectRequest(exchange, clientIp);
        }

        log.debug("[IpWhitelistFilter] IP 鏍￠獙閫氳繃: {}", clientIp);
        return chain.filter(exchange);
    }

    /**
     * 检查路径是否需IP 白名单校
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
     * 获取客户端真IP
     *
     * @param request HTTP 请求
     * @return 客户IP
     */
    private String getClientIp(ServerHttpRequest request) {
        // X-Forwarded-For 获取
        if (properties.isTrustXForwardedFor()) {
            String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
            if (StringUtils.hasText(xForwardedFor)) {
                // 取第一IP（最原始的客户端 IP
                String[] ips = Objects.requireNonNull(xForwardedFor).split(",");
                if (ips.length > 0 && ips[0] != null) {
                    return ips[0].trim();
                }
            }

            // X-Real-IP 获取
            String xRealIp = request.getHeaders().getFirst("X-Real-IP");
            if (StringUtils.hasText(xRealIp)) {
                return Objects.requireNonNull(xRealIp).trim();
            }
        }

        // 从远程地址获取
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * 检IP 是否在白名单
     *
     * @param clientIp 客户IP
     * @return 如果在白名单内返回 true
     */
    private boolean isIpAllowed(String clientIp) {
        if ("unknown".equals(clientIp)) {
            return false;
        }

        try {
            InetAddress clientAddress = InetAddress.getByName(clientIp);
            
            for (IpRule rule : ipRules) {
                if (rule.matches(clientAddress)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            log.warn("[IpWhitelistFilter] 无法解析客户IP: {}", clientIp);
        }

        return false;
    }

    /**
     * 瑙ｆ瀽 IP 瑙勫垯
     *
     * @param ip IP 字符
     * @return IP 瑙勫垯
     */
    private IpRule parseIpRule(String ip) throws UnknownHostException {
        if (ip.contains("/")) {
            // CIDR 格式
            String[] parts = ip.split("/");
            InetAddress address = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            return new CidrIpRule(address, prefixLength);
        } else {
            // 单个 IP
            InetAddress address = InetAddress.getByName(ip);
            return new SingleIpRule(address);
        }
    }

    /**
     * 拒绝请求，返403 错误
     *
     * @param exchange ServerWebExchange
     * @param clientIp 客户IP
     * @return Mono<Void>
     */
    private Mono<Void> rejectRequest(ServerWebExchange exchange, String clientIp) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"code\":403,\"errorCode\":\"IP_NOT_ALLOWED\",\"message\":\"IP 地址不在白名单内\",\"clientIp\":\"%s\",\"timestamp\":\"%s\"}",
                clientIp, Instant.now().toString()
        );

        DataBuffer buffer = response.bufferFactory().wrap(Objects.requireNonNull(body.getBytes(StandardCharsets.UTF_8)));
        Mono<DataBuffer> payload = Mono.just(Objects.requireNonNull(buffer));
        return response.writeWith(Objects.requireNonNull(payload));
    }

    // ==================== IP 规则内部====================

    /**
     * IP 规则接口
     */
    private interface IpRule {
        boolean matches(InetAddress address);
    }

    /**
     * 单个 IP 规则
     */
    private static class SingleIpRule implements IpRule {
        private final InetAddress address;

        SingleIpRule(InetAddress address) {
            this.address = address;
        }

        @Override
        public boolean matches(InetAddress target) {
            return address.equals(target);
        }
    }

    /**
     * CIDR 格式 IP 规则
     */
    private static class CidrIpRule implements IpRule {
        private final byte[] networkAddress;
        private final int prefixLength;

        CidrIpRule(InetAddress address, int prefixLength) {
            this.networkAddress = address.getAddress();
            this.prefixLength = prefixLength;
        }

        @Override
        public boolean matches(InetAddress target) {
            byte[] targetAddress = target.getAddress();
            
            // 地址长度不同（IPv4 vs IPv6
            if (networkAddress.length != targetAddress.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            // 比较完整字节
            for (int i = 0; i < fullBytes; i++) {
                if (networkAddress[i] != targetAddress[i]) {
                    return false;
                }
            }

            // 比较剩余
            if (remainingBits > 0 && fullBytes < networkAddress.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((networkAddress[fullBytes] & mask) != (targetAddress[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        }
    }
}
