package io.brix.platform.admin.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.server.ServerHttpResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("null")
class PlatformSensitiveResponseBodyAdviceTest {

    private final PlatformSensitiveResponseBodyAdvice advice =
            new PlatformSensitiveResponseBodyAdvice(new ObjectMapper());

    @Test
    void rejectsForbiddenPlatformResponseFieldName() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/platform/admins");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> advice.beforeBodyWrite(
                Map.of("setupToken", "sensitive"),
            mock(MethodParameter.class),
            MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(servletRequest),
            mock(ServerHttpResponse.class)));
        assertEquals("Platform API response contains forbidden sensitive field: setupToken", exception.getMessage());
    }

    @Test
    void allowsDocumentedLoginTokenFieldNames() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/platform/auth/login/totp");

        assertDoesNotThrow(() -> advice.beforeBodyWrite(
                Map.of("accessToken", "jwt", "refreshToken", "refresh"),
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(servletRequest),
                mock(ServerHttpResponse.class)));
    }
}