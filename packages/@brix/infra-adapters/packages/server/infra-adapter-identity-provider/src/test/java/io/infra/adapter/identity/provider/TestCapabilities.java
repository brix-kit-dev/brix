package io.infra.adapter.identity.provider;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.runtime.sdk.capability.IdentityAccountCapability;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.StateStoreCapability;
import io.runtime.sdk.capability.TenantAccessCapability;
import tools.jackson.databind.json.JsonMapper;

/** In-memory Layer 2A capabilities for the identity provider tests. */
@Configuration(proxyBeanMethods = false)
class TestCapabilities {

    static final long STAFF_ID = 21L;
    static final String STAFF_EMAIL = "staff@example.test";
    static final String STAFF_PASSWORD = "correct horse battery";
    static final long TENANT_ID = 42L;
    static final String CONTEXT_ID = context("ctx-staff");
    /** Staff of two tenants (multi-tenant §9.3 selection, T-26 switching). */
    static final long MULTI_ID = 22L;
    static final String MULTI_EMAIL = "multi@example.test";
    static final long OTHER_TENANT_ID = 43L;
    static final String MULTI_CONTEXT_A = context("ctx-multi-a");
    static final String MULTI_CONTEXT_B = context("ctx-multi-b");
    /** Another person's Actor context, never reachable by the staff above. */
    static final String FOREIGN_CONTEXT = context("ctx-foreign");

    private static String context(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Bean
    InMemoryStateStore stateStore() {
        return new InMemoryStateStore();
    }

    @Bean
    SecretEncryptionCapability secretEncryption() {
        return new AesGcmEncryption();
    }

    @Bean
    SigningIssuer jwtIssuer() {
        return new SigningIssuer();
    }

    @Bean
    FakeIdentities identities() {
        return new FakeIdentities();
    }

    @Bean
    FakePasswords passwords() {
        return new FakePasswords();
    }

    @Bean
    FakeTenants tenants() {
        return new FakeTenants();
    }

    /** State store with expiry, like the Redis adapter. */
    static final class InMemoryStateStore implements StateStoreCapability {

        private static final JsonMapper JSON = JsonMapper.builder().build();
        final Map<String, Entry> entries = new ConcurrentHashMap<>();

        record Entry(String json, Instant expiresAt) {
        }

        @Override
        public <T> Optional<T> get(String key, Class<T> type) {
            Entry entry = entries.get(key);
            if (entry == null || (entry.expiresAt() != null && Instant.now().isAfter(entry.expiresAt()))) {
                entries.remove(key);
                return Optional.empty();
            }
            return Optional.of(JSON.readValue(entry.json(), type));
        }

        @Override
        public void put(String key, Object value) {
            entries.put(key, new Entry(JSON.writeValueAsString(value), null));
        }

        @Override
        public void put(String key, Object value, Duration ttl) {
            entries.put(key, new Entry(JSON.writeValueAsString(value), Instant.now().plus(ttl)));
        }

        @Override
        public void remove(String key) {
            entries.remove(key);
        }

        @Override
        public boolean exists(String key) {
            return get(key, Object.class).isPresent();
        }

        @Override
        public <T> Optional<T> getAndRemove(String key, Class<T> type) {
            Optional<T> value = get(key, type);
            entries.remove(key);
            return value;
        }

        @Override
        public boolean putIfAbsent(String key, Object value, Duration ttl) {
            return entries.putIfAbsent(key, new Entry(JSON.writeValueAsString(value), Instant.now().plus(ttl))) == null;
        }

        /** Every stored value, as the store holds it. */
        String dump() {
            StringBuilder all = new StringBuilder();
            entries.forEach((key, entry) -> all.append(key).append('=').append(entry.json()).append('\n'));
            return all.toString();
        }
    }

    static final class AesGcmEncryption implements SecretEncryptionCapability {

        private final SecretKey key;
        private final SecureRandom random = new SecureRandom();

        AesGcmEncryption() {
            try {
                KeyGenerator generator = KeyGenerator.getInstance("AES");
                generator.init(256);
                key = generator.generateKey();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String encryptSecret(String plaintextSecret) {
            try {
                byte[] iv = new byte[12];
                random.nextBytes(iv);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
                byte[] encrypted = cipher.doFinal(plaintextSecret.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(encrypted);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String decryptSecret(String encryptedSecret) {
            try {
                String[] parts = encryptedSecret.split("\\.");
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
                return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Signs like the platform issuer: RS256 with a {@code kid}, actor claims plus the authentication event. */
    static final class SigningIssuer implements JwtIssuerCapability {

        final RSAKey key;

        SigningIssuer() {
            try {
                key = new RSAKeyGenerator(2048).keyID("test-key").generate();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public IssuedToken issueActorAccessToken(ActorTokenRequest request, AuthenticationContext authentication) {
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", "brix-auth-center");
            claims.put("sub", String.valueOf(request.identityId()));
            claims.put("aud", AUDIENCE_ACTOR);
            claims.put("token_type", "access");
            claims.put("role", "actor");
            claims.put("scope", "actor");
            claims.put("tenant_id", String.valueOf(request.tenantId()));
            claims.put("cid", request.contextId());
            claims.put("mid", String.valueOf(request.memberId()));
            claims.put("mtype", request.memberType());
            claims.put("mver", request.authzVersion());
            claims.put("tv", request.tokenVersion());
            putAuthentication(claims, authentication);
            return sign(claims);
        }

        @Override
        public IssuedToken issueOidcIdToken(OidcIdTokenRequest request) {
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", request.issuer());
            claims.put("sub", request.subject());
            claims.put("aud", request.audience());
            claims.put("azp", request.audience());
            if (request.nonce() != null) {
                claims.put("nonce", request.nonce());
            }
            putAuthentication(claims, request.authentication());
            claims.putAll(request.additionalClaims());
            return sign(claims);
        }

        @Override
        public Map<String, Object> publicJwkSet() {
            return Map.of("keys", List.of(key.toPublicJWK().toJSONObject()));
        }

        private static void putAuthentication(Map<String, Object> claims, AuthenticationContext authentication) {
            claims.put("acr", authentication.acr());
            claims.put("amr", authentication.amr());
            claims.put("auth_time", authentication.authTime().getEpochSecond());
            claims.put("sid", authentication.sid());
        }

        private IssuedToken sign(Map<String, Object> claims) {
            try {
                Instant now = Instant.now();
                Instant expiresAt = now.plusSeconds(MAX_SESSION_ACCESS_TOKEN_SECONDS);
                JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
                claims.forEach(builder::claim);
                builder.issueTime(java.util.Date.from(now)).expirationTime(java.util.Date.from(expiresAt))
                        .jwtID(UUID.randomUUID().toString());
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(), builder.build());
                jwt.sign(new RSASSASigner(key));
                return new IssuedToken(jwt.serialize(), Instant.ofEpochSecond(now.getEpochSecond()),
                        Instant.ofEpochSecond(expiresAt.getEpochSecond()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String issueActorAccessToken(ActorTokenRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String issueSubjectAccessToken(SubjectTokenRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String issueIdentityToken(IdentityTokenRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String issuePlatformAdminToken(PlatformAdminTokenRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String issuePlatformMfaChallengeToken(PlatformMfaChallengeTokenRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String issueBootstrapSetupToken(BootstrapSetupTokenRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getAccessTokenExpirationSeconds() {
            return MAX_SESSION_ACCESS_TOKEN_SECONDS;
        }

        @Override
        public long getIdentityTokenExpirationSeconds() {
            return 300;
        }

        @Override
        public String issuePlatformAdminViewToken(PlatformAdminViewTokenRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    /** One active staff identity with a bound TOTP factor; each code works once. */
    static final class FakeIdentities implements IdentityAccountCapability {

        final Set<String> usedCodes = ConcurrentHashMap.newKeySet();
        volatile String status = "ACTIVE";

        @Override
        public Optional<IdentityRecord> findIdentityByEmail(String email) {
            if (STAFF_EMAIL.equals(email)) {
                return findIdentityById(STAFF_ID);
            }
            return MULTI_EMAIL.equals(email) ? findIdentityById(MULTI_ID) : Optional.empty();
        }

        @Override
        public Optional<IdentityRecord> findIdentityById(Long id) {
            if (Long.valueOf(STAFF_ID).equals(id)) {
                return Optional.of(new IdentityRecord(STAFF_ID, STAFF_EMAIL, "staff", "h:" + STAFF_PASSWORD,
                        status, false, 3L));
            }
            if (Long.valueOf(MULTI_ID).equals(id)) {
                return Optional.of(new IdentityRecord(MULTI_ID, MULTI_EMAIL, "multi", "h:" + STAFF_PASSWORD,
                        "ACTIVE", false, 1L));
            }
            return Optional.empty();
        }

        @Override
        public boolean isTotpEnabled(Long identityId) {
            return findIdentityById(identityId).isPresent();
        }

        @Override
        public boolean verifyTotp(Long identityId, String code) {
            // Any code "1xxxxx" is valid once; tests use a fresh code per login.
            return isTotpEnabled(identityId) && code != null && code.matches("1\\d{5}") && usedCodes.add(code);
        }

        @Override
        public void updatePasswordHash(Long identityId, String newPasswordHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void incrementTokenVersion(Long identityId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getTokenVersion(Long identityId) {
            return 3L;
        }

        @Override
        public LoginFailureRecord recordFailedLogin(Long identityId, int maxAttempts, int lockMinutes,
                                                    String clientIp) {
            throw new AssertionError("hosted login must not use the global account lock");
        }

        @Override
        public void recordSuccessfulLogin(Long identityId, String clientIp) {
        }

        @Override
        public boolean unlockExpiredLoginLock(Long identityId, Instant now) {
            return false;
        }

        @Override
        public Optional<PlatformAdminRecord> findActivePlatformAdmin(Long identityId) {
            return Optional.empty();
        }
    }

    /** Hashes are {@code h:<password>}; counts every verification, known account or not. */
    static final class FakePasswords implements PasswordCapability {

        final AtomicInteger verifications = new AtomicInteger();

        @Override
        public String hash(String rawPassword) {
            return "h:" + rawPassword;
        }

        @Override
        public boolean verify(String rawPassword, String encodedPassword) {
            verifications.incrementAndGet();
            return encodedPassword != null && encodedPassword.equals("h:" + rawPassword);
        }
    }

    /**
     * The staff identity is an active MEMBER of one tenant; the multi-tenant staff is a MEMBER of
     * tenant 42 and the OWNER of tenant 43; another person is a member of tenant 44.
     */
    static final class FakeTenants implements TenantAccessCapability {

        /** Switches the single-tenant staff membership off. */
        volatile boolean active = true;

        TenantMembershipRecord membership() {
            return new TenantMembershipRecord(7L, TENANT_ID, "clinic", "Clinic", STAFF_ID, "MEMBER", "ACTIVE",
                    Instant.parse("2026-10-01T00:00:00Z"), CONTEXT_ID, 3L);
        }

        private List<TenantMembershipRecord> all() {
            Instant joined = Instant.parse("2026-10-01T00:00:00Z");
            List<TenantMembershipRecord> records = new java.util.ArrayList<>(List.of(
                    new TenantMembershipRecord(8L, TENANT_ID, "clinic", "Clinic", MULTI_ID, "MEMBER", "ACTIVE",
                            joined, MULTI_CONTEXT_A, 1L),
                    new TenantMembershipRecord(9L, OTHER_TENANT_ID, "hospital", "Hospital", MULTI_ID, "OWNER",
                            "ACTIVE", joined, MULTI_CONTEXT_B, 1L),
                    new TenantMembershipRecord(10L, 44L, "foreign", "Foreign", 23L, "OWNER", "ACTIVE", joined,
                            FOREIGN_CONTEXT, 1L)));
            if (active) {
                records.add(membership());
            }
            return records;
        }

        @Override
        public List<TenantMembershipRecord> getActiveMemberships(Long identityId) {
            return all().stream().filter(m -> m.identityId().equals(identityId)).toList();
        }

        @Override
        public List<TenantPrincipalRecord> getActivePrincipalships(Long identityId) {
            return List.of();
        }

        @Override
        public Optional<TenantMembershipRecord> findMembership(Long identityId, Long tenantId) {
            return getActiveMemberships(identityId).stream().filter(m -> m.tenantId().equals(tenantId)).findFirst();
        }

        @Override
        public Optional<TenantMembershipRecord> findMembershipByContextId(String contextId) {
            return all().stream().filter(m -> m.contextId().equals(contextId)).findFirst();
        }

        @Override
        public Optional<TenantPrincipalRecord> findPrincipalship(Long identityId, Long tenantId) {
            return Optional.empty();
        }

        @Override
        public Optional<TenantPrincipalRecord> findPrincipalshipByContextId(String contextId) {
            return Optional.empty();
        }

        @Override
        public void touchMemberAccess(Long memberId) {
        }

        @Override
        public void touchPrincipalAccess(Long principalId) {
        }
    }
}
