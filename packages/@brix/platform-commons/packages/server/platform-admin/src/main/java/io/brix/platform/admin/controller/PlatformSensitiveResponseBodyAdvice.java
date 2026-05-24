package io.brix.platform.admin.controller;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Profile-scoped guard that fails fast when platform API responses expose forbidden field names.
 */
@Profile({"test", "prod"})
@ControllerAdvice(basePackages = "io.brix.platform.admin.controller")
public class PlatformSensitiveResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final Set<String> FORBIDDEN_FIELD_NAMES = Set.of(
            "password",
            "temppassword",
            "setup_token",
            "setuptoken",
            "setupurl",
            "setupurlmasked",
            "mfa_secret",
            "mfasecret",
            "totpcode",
            "otpcode",
            "token"
    );

    private final ObjectMapper objectMapper;

    public PlatformSensitiveResponseBodyAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(@Nullable Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {
        if (!request.getURI().getPath().startsWith("/api/platform/")) {
            return body;
        }
        JsonNode tree = objectMapper.valueToTree(body);
        assertNoForbiddenFields(tree);
        return body;
    }

    private void assertNoForbiddenFields(@Nullable JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String fieldName = fields.next();
                if (FORBIDDEN_FIELD_NAMES.contains(fieldName.toLowerCase(Locale.ROOT))) {
                    throw new IllegalStateException(
                            "Platform API response contains forbidden sensitive field: " + fieldName);
                }
                assertNoForbiddenFields(node.get(fieldName));
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                assertNoForbiddenFields(child);
            }
        }
    }
}