package io.brix.platform.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.enums.TokenRole;
import io.brix.platform.auth.enums.TokenType;
import io.runtime.sdk.capability.IdentityAccountCapability;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.JwtIssuerCapability.ActorTokenRequest;
import io.runtime.sdk.capability.JwtIssuerCapability.AuthenticationContext;
import io.runtime.sdk.capability.JwtIssuerCapability.IssuedToken;
import io.runtime.sdk.capability.JwtIssuerCapability.OidcIdTokenRequest;
import io.runtime.sdk.capability.JwtIssuerCapability.PlatformAdminTokenRequest;
import io.runtime.sdk.capability.JwtIssuerCapability.SubjectTokenRequest;

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
    void tenantAccessTokensCarryNoPermissionList(@TempDir Path tempDir) throws Exception {
        JwtProperties properties = new JwtProperties();
        TestKeys keys = writeKeys(tempDir);
        properties.setPrivateKeyPath(keys.privateKeyPath().toUri().toString());
        JwtIssuerCapabilityImpl issuer = new JwtIssuerCapabilityImpl(properties, objectMapper);

        Map<String, Object> actor = decodePayload(issuer.issueActorAccessToken(new ActorTokenRequest(
                21L, "staff@example.invalid", "staff", 42L, 7L, "MEMBER",
                List.of("MEMBER"), List.of("tenant:member:read"), 1L, "ctx-actor", 3L)));
        Map<String, Object> subject = decodePayload(issuer.issueSubjectAccessToken(new SubjectTokenRequest(
                31L, "patient@example.invalid", "patient", 42L, 9L, "CUSTOMER",
                "Patient", 1L, "ctx-subject", 2L)));

        assertEquals("MEMBER", actor.get("mtype"));
        assertEquals(3, ((Number) actor.get("mver")).intValue());
        assertFalse(actor.containsKey("permissions"), "actor tokens must not carry a permission list (T-30)");
        assertEquals("CUSTOMER", subject.get("ptype"));
        assertEquals(2, ((Number) subject.get("pver")).intValue());
        assertFalse(subject.containsKey("roles"), "subject tokens have no roles claim (multi-tenant §8)");
        assertFalse(subject.containsKey("permissions"), "subject tokens must not carry a permission list (T-30)");
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

    @Test
    void sessionAccessTokensCarryTheAuthenticationEventForAtMostFifteenMinutes(@TempDir Path tempDir)
            throws Exception {
        JwtProperties properties = new JwtProperties();
        TestKeys keys = writeKeys(tempDir);
        properties.setPrivateKeyPath(keys.privateKeyPath().toUri().toString());
        properties.setPublicKeyPath(keys.publicKeyPath().toUri().toString());
        properties.setAccessTokenExpirationSeconds(3600L);
        JwtIssuerCapabilityImpl issuer = new JwtIssuerCapabilityImpl(properties, objectMapper);
        Instant authTime = Instant.parse("2026-10-23T09:00:00Z");

        IssuedToken token = issuer.issueActorAccessToken(new ActorTokenRequest(
                21L, "staff@example.invalid", "staff", 42L, 7L, "MEMBER",
                List.of("MEMBER"), List.of(), 1L, "ctx-actor", 3L),
                new AuthenticationContext("brix:aal2", List.of("pwd", "otp"), authTime, "sid-1"));

        Map<String, Object> claims = decodePayload(token.value());
        assertEquals("brix:aal2", claims.get("acr"));
        assertEquals(List.of("pwd", "otp"), claims.get("amr"));
        assertEquals(authTime.getEpochSecond(), ((Number) claims.get("auth_time")).longValue());
        assertEquals("sid-1", claims.get("sid"));
        assertEquals("brix-actor", claims.get("aud"));
        assertFalse(claims.containsKey("permissions"));
        long lifetime = ((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue();
        assertEquals(JwtIssuerCapability.MAX_SESSION_ACCESS_TOKEN_SECONDS, lifetime);
        assertEquals(Duration.ofSeconds(lifetime), Duration.between(token.issuedAt(), token.expiresAt()));
        assertEquals("21", new JwtValidator(properties).validate(token.value()).getUserId());
        // The legacy method keeps the configured lifetime for exempted callers.
        Map<String, Object> legacy = decodePayload(issuer.issueActorAccessToken(new ActorTokenRequest(
                21L, "staff@example.invalid", "staff", 42L, 7L, "MEMBER",
                List.of("MEMBER"), List.of(), 1L, "ctx-actor", 3L)));
        assertFalse(legacy.containsKey("acr"));
        assertEquals(3600L, ((Number) legacy.get("exp")).longValue() - ((Number) legacy.get("iat")).longValue());
    }

    @Test
    void everyTokenNamesThePublishedPublicKey(@TempDir Path tempDir) throws Exception {
        JwtProperties properties = new JwtProperties();
        TestKeys keys = writeKeys(tempDir);
        properties.setPrivateKeyPath(keys.privateKeyPath().toUri().toString());
        JwtIssuerCapabilityImpl issuer = new JwtIssuerCapabilityImpl(properties, objectMapper);

        String token = issuer.issueSubjectAccessToken(new SubjectTokenRequest(
                31L, "patient@example.invalid", "patient", 42L, 9L, "CUSTOMER",
                "Patient", 1L, "ctx-subject", 2L));
        List<?> jwks = (List<?>) issuer.publicJwkSet().get("keys");
        Map<?, ?> jwk = (Map<?, ?>) jwks.get(0);
        Map<String, Object> header = decodePart(token, 0);

        assertEquals(1, jwks.size());
        assertEquals(jwk.get("kid"), header.get("kid"));
        assertEquals("RS256", header.get("alg"));
        assertEquals("RSA", jwk.get("kty"));
        assertEquals("sig", jwk.get("use"));
        for (String privateMember : List.of("d", "p", "q", "dp", "dq", "qi")) {
            assertFalse(jwk.containsKey(privateMember), "JWKS must not publish " + privateMember);
        }
        RSAPublicKey published = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                new BigInteger(1, Base64.getUrlDecoder().decode((String) jwk.get("n"))),
                new BigInteger(1, Base64.getUrlDecoder().decode((String) jwk.get("e")))));
        String[] parts = token.split("\\.");
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(published);
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(signature.verify(Base64.getUrlDecoder().decode(parts[2])));
    }

    @Test
    void idTokensCarryTheClientNonceAndAuthenticationEvent(@TempDir Path tempDir) throws Exception {
        JwtProperties properties = new JwtProperties();
        TestKeys keys = writeKeys(tempDir);
        properties.setPrivateKeyPath(keys.privateKeyPath().toUri().toString());
        JwtIssuerCapabilityImpl issuer = new JwtIssuerCapabilityImpl(properties, objectMapper);
        AuthenticationContext authentication = new AuthenticationContext(
                "brix:aal2", List.of("pwd", "otp"), Instant.parse("2026-10-23T09:00:00Z"), "sid-1");

        IssuedToken idToken = issuer.issueOidcIdToken(new OidcIdTokenRequest(
                "https://login.example.invalid", "21", "tenant-console", "nonce-1", authentication,
                Map.of("cid", "ctx-actor")));

        Map<String, Object> claims = decodePayload(idToken.value());
        assertEquals("https://login.example.invalid", claims.get("iss"));
        assertEquals("21", claims.get("sub"));
        assertEquals("tenant-console", claims.get("aud"));
        assertEquals("tenant-console", claims.get("azp"));
        assertEquals("nonce-1", claims.get("nonce"));
        assertEquals("sid-1", claims.get("sid"));
        assertEquals("ctx-actor", claims.get("cid"));
        assertFalse(claims.containsKey("token_type"), "ID tokens are not access tokens");
        assertThrows(IllegalArgumentException.class, () -> new OidcIdTokenRequest(
                "https://login.example.invalid", "21", "tenant-console", null, authentication,
                Map.of("sub", "someone-else")));
    }

    @Test
    void idTokensAreNeverAcceptedAsAccessTokens(@TempDir Path tempDir) throws Exception {
        JwtProperties properties = new JwtProperties();
        TestKeys keys = writeKeys(tempDir);
        properties.setPrivateKeyPath(keys.privateKeyPath().toUri().toString());
        properties.setPublicKeyPath(keys.publicKeyPath().toUri().toString());
        JwtIssuerCapabilityImpl issuer = new JwtIssuerCapabilityImpl(properties, objectMapper);
        JwtValidator validator = new JwtValidator(properties);
        AuthenticationContext authentication = new AuthenticationContext(
                "brix:aal2", List.of("pwd", "otp"), Instant.now(), "sid-1");

        // identity-auth IA-6: an ID token is meant for one client and is no access token, even
        // when it names the access-token issuer.
        for (String idTokenIssuer : List.of(properties.getIssuer(), "https://login.example.invalid")) {
            IssuedToken idToken = issuer.issueOidcIdToken(new OidcIdTokenRequest(
                    idTokenIssuer, "21", "tenant-console", "nonce-1", authentication, Map.of("cid", "ctx-actor")));
            assertThrows(JwtValidator.JwtValidationException.class, () -> validator.validate(idToken.value()));
        }
    }

    @Test
    void aBootstrapSetupTokenIsTheSessionItNames(@TempDir Path tempDir) throws Exception {
        // Standard-link plan point 10b-7a (D96): the host verifies the token and names its session by the jti. No
        // identity stands behind it (super-admin 4.0.1 BRX-SA-BOOT-003), so the token-version check does not apply,
        // while it still applies to every other token.
        JwtProperties properties = new JwtProperties();
        TestKeys keys = writeKeys(tempDir);
        properties.setPrivateKeyPath(keys.privateKeyPath().toUri().toString());
        properties.setPublicKeyPath(keys.publicKeyPath().toUri().toString());
        JwtIssuerCapabilityImpl issuer = new JwtIssuerCapabilityImpl(properties, objectMapper);
        IdentityAccountCapability nobody = org.mockito.Mockito.mock(IdentityAccountCapability.class);
        org.mockito.Mockito.when(nobody.getTokenVersion(org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(new IllegalArgumentException("Identity not found"));
        JwtValidator validator = new JwtValidator(properties, nobody, null);

        String token = issuer.issueBootstrapSetupToken(new JwtIssuerCapability.BootstrapSetupTokenRequest(
                0L, 0L, "bootstrap@local.invalid", "Bootstrap Setup",
                List.of("bootstrap:status", "bootstrap:session", "bootstrap:create-first-admin"),
                1L, 300L, "session-jti"));
        AuthenticatedUser session = validator.validate(token);

        assertEquals("session-jti", session.getUserId());
        assertEquals(TokenType.BOOTSTRAP_SETUP, session.getTokenType());
        assertEquals(TokenRole.BOOTSTRAP, session.getTokenRole());
        assertEquals("BOOTSTRAP", session.getScope());
        // The role claims, written out: Bootstrap is no platform role, yet the token keeps carrying this value.
        assertEquals("BOOTSTRAP", session.getPlatformRole());
        assertEquals(List.of("BOOTSTRAP"), session.getRoles());
        assertEquals(List.of("bootstrap:status", "bootstrap:session", "bootstrap:create-first-admin"),
                session.getPermissions());
        assertEquals(null, session.getTenantId());
        org.mockito.Mockito.verify(nobody, org.mockito.Mockito.never()).getTokenVersion(org.mockito.ArgumentMatchers.anyLong());

        String platform = issuer.issuePlatformAdminToken(new PlatformAdminTokenRequest(
                11L, 42L, "admin@example.invalid", "platform-admin", "PLATFORM_SUPER_ADMIN",
                List.of("platform:admin:read"), 1L));
        assertThrows(JwtValidator.JwtValidationException.class, () -> validator.validate(platform));
    }

    @Test
    void aTokenThatOnlyClaimsToBeABootstrapSessionIsRefused(@TempDir Path tempDir) throws Exception {
        JwtProperties properties = new JwtProperties();
        TestKeys keys = writeKeys(tempDir);
        properties.setPublicKeyPath(keys.publicKeyPath().toUri().toString());
        JwtValidator validator = new JwtValidator(properties);

        assertEquals("session-jti", validator.validate(signed(keys, bootstrapClaims(properties))).getUserId());
        for (Map.Entry<String, Object> wrong : List.of(
                Map.entry("role", (Object) "platform-admin"),
                Map.entry("scope", (Object) "PLATFORM"),
                Map.entry("jti", (Object) " "),
                Map.entry("tenant_id", (Object) "42"),
                Map.entry("mid", (Object) "7"))) {
            Map<String, Object> claims = bootstrapClaims(properties);
            claims.put(wrong.getKey(), wrong.getValue());
            assertThrows(JwtValidator.JwtValidationException.class, () -> validator.validate(signed(keys, claims)),
                    wrong.getKey());
        }
        Map<String, Object> withoutJti = bootstrapClaims(properties);
        withoutJti.remove("jti");
        assertThrows(JwtValidator.JwtValidationException.class, () -> validator.validate(signed(keys, withoutJti)));
    }

    private static Map<String, Object> bootstrapClaims(JwtProperties properties) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "0");
        claims.put("iss", properties.getIssuer());
        claims.put("aud", properties.getAudience());
        claims.put("jti", "session-jti");
        claims.put("tv", 1L);
        claims.put("token_type", "BOOTSTRAP_SETUP");
        claims.put("role", "bootstrap");
        claims.put("scope", "BOOTSTRAP");
        claims.put("permissions", List.of("bootstrap:create-first-admin"));
        claims.put("iat", now);
        claims.put("nbf", now);
        claims.put("exp", now + 300);
        return claims;
    }

    private String signed(TestKeys keys, Map<String, Object> claims) throws Exception {
        String pem = Files.readString(keys.privateKeyPath(), StandardCharsets.US_ASCII)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        java.security.PrivateKey key = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        String input = url.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))
                + "." + url.encodeToString(objectMapper.writeValueAsBytes(claims));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + url.encodeToString(signature.sign());
    }

    private Map<String, Object> decodePart(String token, int index) throws Exception {
        String json = new String(Base64.getUrlDecoder().decode(token.split("\\.")[index]), StandardCharsets.UTF_8);
        return objectMapper.readValue(json, new TypeReference<>() {});
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
