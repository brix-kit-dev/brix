package io.brix.platform.gateway.config;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * CORS 配置属性单元测试
 * <p>
 * MVP 红线 M014：核心路径单元测试覆盖
 * </p>
 * <p>
 * MVP 红线要求：
 * <ul>
 *   <li>CORS 显式白名单配置</li>
 *   <li>生产环境禁止通配符</li>
 * </ul>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@DisplayName("CorsProperties CORS 配置属性测试")
@SuppressWarnings("unused") // JUnit nested classes, setUp used by JUnit
class CorsPropertiesTest {

    private CorsProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CorsProperties();
    }

    // ========== 默认值测试 ==========

    @Nested
    @DisplayName("默认值验证")
    class DefaultValueTests {

        @Test
        @DisplayName("默认应允许所有来源（开发模式）")
        void shouldHaveDefaultAllowedOrigins() {
            List<String> patterns = properties.getAllowedOriginPatterns();
            
            assertNotNull(patterns);
            assertTrue(patterns.contains("*"));
        }

        @Test
        @DisplayName("默认应允许常用 HTTP 方法")
        void shouldHaveDefaultAllowedMethods() {
            List<String> methods = properties.getAllowedMethods();
            
            assertNotNull(methods);
            assertTrue(methods.contains("GET"));
            assertTrue(methods.contains("POST"));
            assertTrue(methods.contains("PUT"));
            assertTrue(methods.contains("DELETE"));
            assertTrue(methods.contains("OPTIONS"));
        }

        @Test
        @DisplayName("默认应允许所有请求头")
        void shouldHaveDefaultAllowedHeaders() {
            List<String> headers = properties.getAllowedHeaders();
            
            assertNotNull(headers);
            assertTrue(headers.contains("*"));
        }

        @Test
        @DisplayName("默认应允许凭证")
        void shouldAllowCredentialsByDefault() {
            assertTrue(properties.isAllowCredentials());
        }

        @Test
        @DisplayName("默认缓存时间应为 3600 秒")
        void shouldHaveDefaultMaxAge() {
            assertEquals(3600L, properties.getMaxAge());
        }

        @Test
        @DisplayName("默认应启用通配符警告")
        void shouldWarnOnWildcardByDefault() {
            assertTrue(properties.isWarnOnWildcard());
        }

        @Test
        @DisplayName("默认应不在生产环境阻止通配符")
        void shouldBlockWildcardInProductionByDefault() {
            assertFalse(properties.isBlockWildcardInProduction());
        }
    }

    // ========== 自定义配置测试 ==========

    @Nested
    @DisplayName("自定义配置")
    class CustomConfigurationTests {

        @Test
        @DisplayName("应支持自定义白名单域名")
        void shouldAllowCustomWhitelist() {
            properties.setAllowedOriginPatterns(List.of(
                "https://www.example.com",
                "https://*.example.com"
            ));
            
            List<String> patterns = properties.getAllowedOriginPatterns();
            
            assertEquals(2, patterns.size());
            assertTrue(patterns.contains("https://www.example.com"));
            assertTrue(patterns.contains("https://*.example.com"));
        }

        @Test
        @DisplayName("应支持限制 HTTP 方法")
        void shouldAllowRestrictedMethods() {
            properties.setAllowedMethods(List.of("GET", "POST"));
            
            List<String> methods = properties.getAllowedMethods();
            
            assertEquals(2, methods.size());
            assertTrue(methods.contains("GET"));
            assertTrue(methods.contains("POST"));
            assertFalse(methods.contains("DELETE"));
        }

        @Test
        @DisplayName("应支持自定义请求头白名单")
        void shouldAllowCustomHeaders() {
            properties.setAllowedHeaders(List.of(
                "Content-Type",
                "Authorization",
                "X-Custom-Header"
            ));
            
            List<String> headers = properties.getAllowedHeaders();
            
            assertEquals(3, headers.size());
        }

        @Test
        @DisplayName("应支持禁用凭证")
        void shouldAllowDisablingCredentials() {
            properties.setAllowCredentials(false);
            
            assertFalse(properties.isAllowCredentials());
        }

        @Test
        @DisplayName("应支持自定义缓存时间")
        void shouldAllowCustomMaxAge() {
            properties.setMaxAge(7200L);
            
            assertEquals(7200L, properties.getMaxAge());
        }
    }

    // ========== 通配符检测测试 ==========

    @Nested
    @DisplayName("通配符检测")
    class WildcardDetectionTests {

        @Test
        @DisplayName("应检测到通配符配置")
        void shouldDetectWildcard() {
            properties.setAllowedOriginPatterns(List.of("*"));
            
            assertTrue(properties.hasWildcardOrigin());
        }

        @Test
        @DisplayName("应检测到无通配符配置")
        void shouldDetectNoWildcard() {
            properties.setAllowedOriginPatterns(List.of(
                "https://www.example.com"
            ));
            
            assertFalse(properties.hasWildcardOrigin());
        }

        @Test
        @DisplayName("应检测到部分通配符配置")
        void shouldDetectPartialWildcard() {
            properties.setAllowedOriginPatterns(List.of(
                "https://www.example.com",
                "*"
            ));
            
            assertTrue(properties.hasWildcardOrigin());
        }
    }

    // ========== 暴露头配置测试 ==========

    @Nested
    @DisplayName("暴露头配置")
    class ExposedHeadersTests {

        @Test
        @DisplayName("默认不暴露额外头")
        void shouldNotExposeHeadersByDefault() {
            List<String> headers = properties.getExposedHeaders();
            
            assertTrue(headers == null || headers.isEmpty());
        }

        @Test
        @DisplayName("应支持配置暴露头")
        void shouldAllowConfiguringExposedHeaders() {
            properties.setExposedHeaders(List.of(
                "X-Total-Count",
                "X-Page-Size"
            ));
            
            List<String> headers = properties.getExposedHeaders();
            
            assertEquals(2, headers.size());
            assertTrue(headers.contains("X-Total-Count"));
        }
    }
}
