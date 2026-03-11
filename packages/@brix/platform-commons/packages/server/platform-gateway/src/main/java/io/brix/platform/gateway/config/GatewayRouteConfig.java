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
package io.brix.platform.gateway.config;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import io.brix.platform.gateway.dto.PluginRouteDTO;

/**
 * Gateway Route Configuration
 * <p>
 * Implements dynamic route loading mechanism, reads route definitions published by Plugin Engine from Redis,
 * and supports dynamic refresh.
 * </p>
 * 
 * <p><b>[v3.1 Brand Name Isolation]</b></p>
 * <p>
 * Redis key prefix now supports customization via configuration file, defaults to "brix:gateway:routes".
 * Configuration: {@code brix.gateway.routes.key-prefix}
 * </p>
 *
 * @author Brix Team
 * @version 3.1.0
 * @see GatewayRoutesProperties
 */
@Configuration
@EnableConfigurationProperties(GatewayRoutesProperties.class)
public class GatewayRouteConfig {

    private static final Logger logger = LoggerFactory.getLogger(GatewayRouteConfig.class);

    /**
     * Configure ReactiveRedisTemplate for dynamic routing
     * <p>
     * Uses StringRedisSerializer to ensure compatibility with data written by Plugin Engine.
     * Plugin Engine uses Jackson2JsonRedisSerializer to serialize, outputting JSON strings,
     * here we use StringRedisSerializer to read those strings and parse manually.
     * </p>
     * <p>
     * Note: This Bean is specifically for dynamic routing, not using @Primary to avoid overriding
     * the default ReactiveStringRedisTemplate (needed by OAuth2UserService).
     * </p>
     */
    @Bean("routeRedisTemplate")
    public ReactiveRedisTemplate<String, String> routeRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        
        RedisSerializationContext<String, String> serializationContext = 
            RedisSerializationContext.<String, String>newSerializationContext(stringSerializer)
                .key(stringSerializer)
                .value(stringSerializer)
                .hashKey(stringSerializer)
                .hashValue(stringSerializer)
                .build();
        
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }

    @Bean
    public RouteDefinitionRepository redisRouteDefinitionRepository(
            @org.springframework.beans.factory.annotation.Qualifier("routeRedisTemplate")
            ReactiveRedisTemplate<String, String> redisTemplate, 
            ObjectMapper objectMapper,
            GatewayRoutesProperties properties) {
        final String routesKey = properties.getKeyPrefix();
        final String logPrefix = properties.getLogPrefix();
        
        logger.info("{} Initializing Redis-based RouteDefinitionRepository for dynamic routes", logPrefix);
        
        return new RouteDefinitionRepository() {
            @Override
            public Flux<RouteDefinition> getRouteDefinitions() {
                logger.debug("{} Loading dynamic routes from Redis key: {}", logPrefix, routesKey);
                
                return redisTemplate.<String, String>opsForHash().values(routesKey)
                        .doOnSubscribe(s -> logger.debug("{} Subscribing to Redis hash values for routes", logPrefix))
                        .filter(value -> value != null)
                        .flatMap(value -> {
                            try {
                                logger.debug("{} Parsing route data: {}", logPrefix, value);
                                RouteDefinition route = parseRouteData(value, objectMapper);
                                if (route != null) {
                                    logger.info("{} Loaded dynamic route: {} -> {} (path={})", 
                                            logPrefix, route.getId(), route.getUri(), 
                                            route.getPredicates().isEmpty() ? "?" : route.getPredicates().get(0).getArgs());
                                    return Mono.just(route);
                                } else {
                                    return Mono.empty();
                                }
                            } catch (Exception e) {
                                logger.error("{} Failed to parse route definition: {}", logPrefix, value, e);
                                return Mono.empty();
                            }
                        })
                        .doOnComplete(() -> logger.debug("{} Completed loading dynamic routes from Redis", logPrefix));
            }

            @Override
            public Mono<Void> save(Mono<RouteDefinition> route) {
                return Mono.error(new UnsupportedOperationException("Save not supported from Gateway"));
            }

            @Override
            public Mono<Void> delete(Mono<String> routeId) {
                return Mono.error(new UnsupportedOperationException("Delete not supported from Gateway"));
            }
        };
    }
    
    /**
     * Parse route data
     * Supports multiple data formats:
     * 1. JSON string
     * 2. Jackson JSON array with type info ["className", {...}]
     * 3. LinkedHashMap (object deserialized from Redis)
     */
    private RouteDefinition parseRouteData(Object value, ObjectMapper objectMapper) throws Exception {
        PluginRouteDTO pluginRoute = null;
        
        if (value instanceof String json) {
            // Check if Jackson type array format ["className", {...}]
            if (json.startsWith("[\"")) {
                // Jackson format with type info, need to extract actual data
                Object[] typeArray = objectMapper.readValue(json, Object[].class);
                if (typeArray.length >= 2 && typeArray[1] instanceof Map) {
                    pluginRoute = objectMapper.convertValue(typeArray[1], PluginRouteDTO.class);
                }
            } else {
                // Plain JSON format
                pluginRoute = objectMapper.readValue(json, PluginRouteDTO.class);
            }
        } else if (value instanceof Map) {
            // Already a Map object
            pluginRoute = objectMapper.convertValue(value, PluginRouteDTO.class);
        } else if (value instanceof List) {
            // Jackson type array [className, data]
            List<?> typeList = (List<?>) value;
            if (typeList.size() >= 2) {
                pluginRoute = objectMapper.convertValue(typeList.get(1), PluginRouteDTO.class);
            }
        }
        
        return convertToRouteDefinition(pluginRoute);
    }

    private RouteDefinition convertToRouteDefinition(PluginRouteDTO pluginRoute) {
        if (pluginRoute == null) return null;
        
        RouteDefinition definition = new RouteDefinition();
        definition.setId(pluginRoute.getId());
        
        // set URI
        try {
            definition.setUri(new java.net.URI(pluginRoute.getTargetUri()));
        } catch (java.net.URISyntaxException e) {
            logger.error("Invalid URI: {}", pluginRoute.getTargetUri());
            return null;
        }

        // set Predicates (Path)
        org.springframework.cloud.gateway.handler.predicate.PredicateDefinition predicate = new org.springframework.cloud.gateway.handler.predicate.PredicateDefinition();
        predicate.setName("Path");
        predicate.addArg("pattern", pluginRoute.getPath());
        definition.setPredicates(List.of(predicate));

        // extract basePath beforefixandcreateRewritePath filter
        // routepathformat: /api/platform/api/v1/users/register/**
        // needstrip/api/platform beforefix，retainafteraspectofpathconvertsendgiveafter
        String path = pluginRoute.getPath();
        java.util.List<org.springframework.cloud.gateway.filter.FilterDefinition> filters = new java.util.ArrayList<>();
        
        // checktestwhethercontainservicebeforefix（like /api/platform, /api/medical, /api/case
        // thesebeforefixbyserviceof api-base-path configurationdetermine
        java.util.regex.Pattern basePathPattern = java.util.regex.Pattern.compile("^(/api/[^/]+)(/.*)?$");
        java.util.regex.Matcher matcher = basePathPattern.matcher(path.replace("/**", ""));
        
        if (matcher.matches()) {
            String basePath = matcher.group(1);  //  /api/platform
            // use RewritePath /api/platform/xxx rewrite/xxx
            // regexexpressionneedconvertmeaning，Spring Cloud Gateway RewritePath use Java regex
            org.springframework.cloud.gateway.filter.FilterDefinition rewriteFilter = new org.springframework.cloud.gateway.filter.FilterDefinition();
            rewriteFilter.setName("RewritePath");
            // /api/platform(?<segment>.*) rewrite$\{segment}
            String escapedBasePath = basePath.replace("/", "\\/");
            rewriteFilter.addArg("regexp", escapedBasePath + "(?<segment>.*)");
            rewriteFilter.addArg("replacement", "${segment}");
            filters.add(rewriteFilter);
            logger.debug("Added RewritePath filter: {} -> ${{segment}}", basePath);
        } else {
            // nomethodidentify basePath beforefix，useStripPrefix=0 retaincompleteintegerpath
            org.springframework.cloud.gateway.filter.FilterDefinition stripPrefixFilter = new org.springframework.cloud.gateway.filter.FilterDefinition();
            stripPrefixFilter.setName("StripPrefix");
            stripPrefixFilter.addArg("parts", "0");
            filters.add(stripPrefixFilter);
        }
        
        definition.setFilters(filters);

        logger.debug("Loaded dynamic route: {} -> {}", pluginRoute.getPath(), pluginRoute.getTargetUri());
        return definition;
    }
}
