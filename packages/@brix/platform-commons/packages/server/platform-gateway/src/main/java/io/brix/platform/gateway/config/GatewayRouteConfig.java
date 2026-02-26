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
 * 网关路由配置
 * <p>
 * 实现动态路由加载机制，从 Redis 中读取由插件引擎发布的路由定义，并支持动态刷新。
 * </p>
 * 
 * <p><b>【v3.1 品牌名隔离】</b></p>
 * <p>
 * Redis key 前缀现在支持通过配置文件自定义，默认为 &quot;brix:gateway:routes&quot;。
 * 配置项：{@code brix.gateway.routes.key-prefix}
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
     * 配置用于动态路由的 ReactiveRedisTemplate
     * <p>
     * 使用 StringRedisSerializer 确保与插件引擎写入的数据兼容
     * 插件引擎使用 Jackson2JsonRedisSerializer 序列化，输出 JSON 字符串，
     * 这里StringRedisSerializer 读取这些字符串后手动解析
     * </p>
     * <p>
     * 注意：这Bean 专门用于动态路由，不使用 @Primary 以避免覆盖默认的
     * ReactiveStringRedisTemplate（OAuth2UserService 需要）
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
     * 解析路由数据
     * 支持多种数据格式
     * 1. JSON 字符
     * 2. Jackson 带类型信息的 JSON 数组 ["className", {...}]
     * 3. LinkedHashMap（从 Redis 反序列化后的对象
     */
    private RouteDefinition parseRouteData(Object value, ObjectMapper objectMapper) throws Exception {
        PluginRouteDTO pluginRoute = null;
        
        if (value instanceof String json) {
            // 检查是否是 Jackson 类型数组格式 ["className", {...}]
            if (json.startsWith("[\"")) {
                // Jackson 带类型信息的格式，需要提取实际数
                Object[] typeArray = objectMapper.readValue(json, Object[].class);
                if (typeArray.length >= 2 && typeArray[1] instanceof Map) {
                    pluginRoute = objectMapper.convertValue(typeArray[1], PluginRouteDTO.class);
                }
            } else {
                // 普JSON 格式
                pluginRoute = objectMapper.readValue(json, PluginRouteDTO.class);
            }
        } else if (value instanceof Map) {
            // 已经Map 对象
            pluginRoute = objectMapper.convertValue(value, PluginRouteDTO.class);
        } else if (value instanceof List) {
            // Jackson 类型数组 [className, data]
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
        
        // 设置 URI
        try {
            definition.setUri(new java.net.URI(pluginRoute.getTargetUri()));
        } catch (java.net.URISyntaxException e) {
            logger.error("Invalid URI: {}", pluginRoute.getTargetUri());
            return null;
        }

        // 设置 Predicates (Path)
        org.springframework.cloud.gateway.handler.predicate.PredicateDefinition predicate = new org.springframework.cloud.gateway.handler.predicate.PredicateDefinition();
        predicate.setName("Path");
        predicate.addArg("pattern", pluginRoute.getPath());
        definition.setPredicates(List.of(predicate));

        // 提取 basePath 前缀并创RewritePath 过滤
        // 路由路径格式: /api/platform/api/v1/users/register/**
        // 需要剥/api/platform 前缀，保留后面的路径转发给后
        String path = pluginRoute.getPath();
        java.util.List<org.springframework.cloud.gateway.filter.FilterDefinition> filters = new java.util.ArrayList<>();
        
        // 检测是否包含服务前缀（如 /api/platform, /api/medical, /api/case
        // 这些前缀由服务的 api-base-path 配置决定
        java.util.regex.Pattern basePathPattern = java.util.regex.Pattern.compile("^(/api/[^/]+)(/.*)?$");
        java.util.regex.Matcher matcher = basePathPattern.matcher(path.replace("/**", ""));
        
        if (matcher.matches()) {
            String basePath = matcher.group(1);  // 渚嬪 /api/platform
            // 使用 RewritePath /api/platform/xxx 重写/xxx
            // 正则表达式需要转义，Spring Cloud Gateway RewritePath 使用 Java 正则
            org.springframework.cloud.gateway.filter.FilterDefinition rewriteFilter = new org.springframework.cloud.gateway.filter.FilterDefinition();
            rewriteFilter.setName("RewritePath");
            // /api/platform(?<segment>.*) 重写$\{segment}
            String escapedBasePath = basePath.replace("/", "\\/");
            rewriteFilter.addArg("regexp", escapedBasePath + "(?<segment>.*)");
            rewriteFilter.addArg("replacement", "${segment}");
            filters.add(rewriteFilter);
            logger.debug("Added RewritePath filter: {} -> ${{segment}}", basePath);
        } else {
            // 无法识别 basePath 前缀，使StripPrefix=0 保留完整路径
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
