package io.brix.platform.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

import io.brix.platform.auth.context.AuthenticatedUser;
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
        TestKeys keys = writeKeys(tempDir);
        properties.setPrivateKeyPath(keys.privateKeyPath().toUri().toString());
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

    @Test
    void issuedPlatformAdminTokenCanBeValidatedByRuntimeJwtValidator(@TempDir Path tempDir) throws Exception {
        JwtProperties properties = new JwtProperties();
        TestKeys keys = writeKeys(tempDir);
        properties.setPrivateKeyPath(keys.privateKeyPath().toUri().toString());
        properties.setPublicKeyPath(keys.publicKeyPath().toUri().toString());

        JwtIssuerCapabilityImpl issuer = new JwtIssuerCapabilityImpl(properties, objectMapper);
        JwtValidator validator = new JwtValidator(properties);

        String token = issuer.issuePlatformAdminToken(new PlatformAdminTokenRequest(
                11L,
                42L,
                "admin@example.invalid",
                "platform-admin",
                "PLATFORM_SUPER_ADMIN",
                List.of("platform:admin:read", "platform:audit:read"),
                7L));

        AuthenticatedUser user = validator.validate(token);

        assertNotNull(user);
        assertEquals("42", user.getUserId());
        assertEquals("PLATFORM", user.getScope());
        assertEquals("PLATFORM_SUPER_ADMIN", user.getPlatformRole());
        assertEquals(List.of("PLATFORM_SUPER_ADMIN"), user.getRoles());
        assertEquals(List.of("platform:admin:read", "platform:audit:read"), user.getPermissions());
    }

    private TestKeys writeKeys(Path tempDir) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        String privateBody = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(keyPair.getPrivate().getEncoded());
        Path privateKeyPath = tempDir.resolve("jwt-private.pem");
        Files.writeString(
                privateKeyPath,
                "-----BEGIN PRIVATE KEY-----\n" + privateBody + "\n-----END PRIVATE KEY-----\n",
                StandardCharsets.US_ASCII);

        String publicBody = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(keyPair.getPublic().getEncoded());
        Path publicKeyPath = tempDir.resolve("jwt-public.pem");
        Files.writeString(
                publicKeyPath,
                "-----BEGIN PUBLIC KEY-----\n" + publicBody + "\n-----END PUBLIC KEY-----\n",
                StandardCharsets.US_ASCII);

        return new TestKeys(privateKeyPath, publicKeyPath);
    }

    private Map<String, Object> decodePayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    private record TestKeys(Path privateKeyPath, Path publicKeyPath) {
    }
}
