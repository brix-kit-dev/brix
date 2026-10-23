package io.brix.platform.starter.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import io.brix.platform.starter.config.CorsProperties;

class CorsAutoConfigurationTest {

    private final CorsAutoConfiguration configuration = new CorsAutoConfiguration();

    @Test
    void otherOriginsGetNoCorsHeadersByDefault() throws Exception {
        CorsFilter filter = configuration.corsFilter(new CorsProperties());

        MockHttpServletResponse response = send(filter, "https://attacker.example");

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isNull();
    }

    @Test
    void onlyExplicitOriginsAreAllowed() throws Exception {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("https://partner.example"));
        CorsFilter filter = configuration.corsFilter(properties);

        assertThat(send(filter, "https://partner.example").getHeader("Access-Control-Allow-Origin"))
                .isEqualTo("https://partner.example");
        assertThat(send(filter, "https://attacker.example").getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(send(filter, "https://partner.example").getHeader("Access-Control-Allow-Credentials")).isNull();
    }

    @Test
    void credentialsAreNeverAllowedForWildcardOrigins() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOriginPatterns(List.of("*"));
        properties.setAllowCredentials(true);

        assertThatThrownBy(() -> configuration.corsFilter(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("explicit origins");
    }

    private static MockHttpServletResponse send(CorsFilter filter, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/platform/tenants");
        request.addHeader("Origin", origin);
        request.setServerName("console.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
