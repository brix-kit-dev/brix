package io.brix.platform.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.runtime.sdk.capability.JwtIssuerCapability.PlatformAdminTokenRequest;

class JwtIssuerCapabilityImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void filtersPlatformBypassFromFrontendPermissions() {
        List<String> permissions = JwtIssuerCapabilityImpl.filterFrontendPermissions(List.of(
                "platform:admin:list",
                "platform:bypass",
                "platform:tenant:view"));

        assertEquals(List.of("platform:admin:list", "platform:tenant:view"), permissions);
        assertFalse(permissions.contains("platform:bypass"));
    }

    @Test
    void issuedPlatformAdminTokenDoesNotExposeBypassOrTenantClaim(@TempDir Path tempDir) throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setPrivateKeyPath(writePrivateKey(tempDir).toUri().toString());
        JwtIssuerCapabilityImpl issuer = new JwtIssuerCapabilityImpl(properties, objectMapper);

        String token = issuer.issuePlatformAdminToken(new PlatformAdminTokenRequest(
                11L,
                42L,
                "admin@example.invalid",
                "platform-admin",
                "PLATFORM_SUPER_ADMIN",
                List.of("platform:admin:read", "platform:bypass", "platform:audit:read"),
                7L));

        Map<String, Object> claims = decodePayload(token);
        List<?> permissions = (List<?>) claims.get("permissions");

        assertEquals("PLATFORM", claims.get("scope"));
        assertFalse(claims.containsKey("tenant_id"));
        assertEquals(List.of("platform:admin:read", "platform:audit:read"), permissions);
        assertFalse(permissions.contains("platform:bypass"));
    }

    private Path writePrivateKey(Path tempDir) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(keyPair.getPrivate().getEncoded());
        Path keyPath = tempDir.resolve("jwt-private.pem");
        Files.writeString(
                keyPath,
                "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n",
                StandardCharsets.US_ASCII);
        return keyPath;
    }

    private Map<String, Object> decodePayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return objectMapper.readValue(json, new TypeReference<>() {});
    }
}