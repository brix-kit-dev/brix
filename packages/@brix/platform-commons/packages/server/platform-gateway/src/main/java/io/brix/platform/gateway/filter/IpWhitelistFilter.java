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
 * IP Whitelist Filter
 * 
 * <p>P105 task：requestsign+ IP whitename
 * 
 * <p>verifyrequestorigin IP whetheronwhitelistin，preventnot yetauthorizationaccess
 * 
 * <p>supportIP format
 * <ul>
 *   <li>single IP92.168.1.100</li>
 *   <li>CIDR format92.168.1.0/24</li>
 *   <li>IPv6 address:1</li>
 * </ul>
 * 
 * <p>IP obtainpriority：
 * <ol>
 *   <li>X-Forwarded-For requestheader（firstIP</li>
 *   <li>X-Real-IP request</li>
 *   <li>remoteaddress</li>
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
     * filterorder：onsignatureverifybeforeexecute
     */
    private static final int FILTER_ORDER = -196;

    private final IpWhitelistProperties properties;
    private final AntPathMatcher pathMatcher;
    
    /**
     * parseafterof IP rulelist
     */
    private List<IpRule> ipRules;

    public IpWhitelistFilter(IpWhitelistProperties properties) {
        this.properties = properties;
        this.pathMatcher = new AntPathMatcher();
    }

    @PostConstruct
    public void init() {
        refreshIpRules();
        log.info("[IpWhitelistFilter] initializationcomplete，whitelistrulecount：{}", ipRules.size());
    }

    /**
     * refresh IP rule
     */
    public void refreshIpRules() {
        this.ipRules = new ArrayList<>();
        for (String ip : properties.getAllowedIps()) {
            try {
                ipRules.add(parseIpRule(ip));
            } catch (UnknownHostException | NumberFormatException e) {
                log.warn("[IpWhitelistFilter] ｆ IP : {}", ip, e);
            }
        }
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // checkwhetherstart
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // checkpathwhetherneedIP whitelistverify
        if (!isProtectedPath(path)) {
            return chain.filter(exchange);
        }

        // obtaincustomerIP
        String clientIp = getClientIp(request);
        log.debug("[IpWhitelistFilter] ￠ IP: {} -> {}", clientIp, path);

        // verify IP whetheronwhitelist
        if (!isIpAllowed(clientIp)) {
            log.warn("[IpWhitelistFilter] IP notonwhitelistin: {} -> {}", clientIp, path);
            return rejectRequest(exchange, clientIp);
        }

        log.debug("[IpWhitelistFilter] IP ￠: {}", clientIp);
        return chain.filter(exchange);
    }

    /**
     * checkpathwhetherneedIP whitelistverify
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
     * obtainclienttrueIP
     *
     * @param request HTTP request
     * @return customerIP
     */
    private String getClientIp(ServerHttpRequest request) {
        // X-Forwarded-For obtain
        if (properties.isTrustXForwardedFor()) {
            String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
            if (StringUtils.hasText(xForwardedFor)) {
                // take firstIP（mostoriginalofclient IP
                String[] ips = Objects.requireNonNull(xForwardedFor).split(",");
                if (ips.length > 0 && ips[0] != null) {
                    return ips[0].trim();
                }
            }

            // X-Real-IP obtain
            String xRealIp = request.getHeaders().getFirst("X-Real-IP");
            if (StringUtils.hasText(xRealIp)) {
                return Objects.requireNonNull(xRealIp).trim();
            }
        }

        // fromremoteaddressobtain
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * checkIP whetheronwhitelist
     *
     * @param clientIp customerIP
     * @return ifonwhitelistinreturn true
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
            log.warn("[IpWhitelistFilter] nomethodparsecustomerIP: {}", clientIp);
        }

        return false;
    }

    /**
     * ｆ IP 
     *
     * @param ip IP character
     * @return IP 
     */
    private IpRule parseIpRule(String ip) throws UnknownHostException {
        if (ip.contains("/")) {
            // CIDR format
            String[] parts = ip.split("/");
            InetAddress address = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            return new CidrIpRule(address, prefixLength);
        } else {
            // single IP
            InetAddress address = InetAddress.getByName(ip);
            return new SingleIpRule(address);
        }
    }

    /**
     * rejectedrequest，return403 error
     *
     * @param exchange ServerWebExchange
     * @param clientIp customerIP
     * @return Mono<Void>
     */
    private Mono<Void> rejectRequest(ServerWebExchange exchange, String clientIp) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"code\":403,\"errorCode\":\"IP_NOT_ALLOWED\",\"message\":\"IP addressnotonwhitelistin\",\"clientIp\":\"%s\",\"timestamp\":\"%s\"}",
                clientIp, Instant.now().toString()
        );

        DataBuffer buffer = response.bufferFactory().wrap(Objects.requireNonNull(body.getBytes(StandardCharsets.UTF_8)));
        Mono<DataBuffer> payload = Mono.just(Objects.requireNonNull(buffer));
        return response.writeWith(Objects.requireNonNull(payload));
    }

    // ==================== IP ruleinternal====================

    /**
     * IP ruleinterface
     */
    private interface IpRule {
        boolean matches(InetAddress address);
    }

    /**
     * single IP rule
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
     * CIDR format IP rule
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
            
            // addresslengthnotsame（IPv4 vs IPv6
            if (networkAddress.length != targetAddress.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            // comparecompleteintegercharactersection
            for (int i = 0; i < fullBytes; i++) {
                if (networkAddress[i] != targetAddress[i]) {
                    return false;
                }
            }

            // compareremaining
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
