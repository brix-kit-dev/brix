package io.infra.adapter.identity.provider.login;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import io.infra.adapter.identity.provider.support.Secrets;
import io.runtime.sdk.capability.IdentityAccountCapability.IdentityRecord;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.JwtIssuerCapability.IdentityTokenRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls the tenant Owner's FIRST_OWNER acceptance entry through the Dispatcher of this process,
 * over the loopback interface like the BFF's other API calls, so the entry's own authentication,
 * tenant-context and acceptance rules decide.
 *
 * <p>The call authenticates with an Identity Token ({@code aud=brix-identity}, action
 * {@code first_owner_accept}) issued for the identity that has just completed both factors. It is
 * used once here and never reaches the browser (identity-auth {@code BRX-IA-SESSION-003}, root
 * S-20).</p>
 *
 * @since 3.2.0
 */
public final class DispatcherInvitationAcceptance implements InvitationAcceptance {

    private static final Logger log = LoggerFactory.getLogger(DispatcherInvitationAcceptance.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JwtIssuerCapability issuer;
    private final Supplier<String> internalBaseUrl;
    private final String path;
    private final RestClient client;

    public DispatcherInvitationAcceptance(JwtIssuerCapability issuer, Supplier<String> internalBaseUrl, String path) {
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
        this.internalBaseUrl = Objects.requireNonNull(internalBaseUrl, "internalBaseUrl must not be null");
        this.path = Objects.requireNonNull(path, "path must not be null");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public Outcome accept(IdentityRecord identity, String invitationToken) {
        String identityToken = issuer.issueIdentityToken(
                new IdentityTokenRequest(identity.id(), identity.email(), identity.username()));
        try {
            Map<String, Object> accepted = client.post()
                    .uri(internalBaseUrl.get() + path)
                    .headers(headers -> {
                        headers.setBearerAuth(identityToken);
                        headers.set("Idempotency-Key", Secrets.randomToken(16));
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("invitationToken", invitationToken))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            Object tenantId = accepted == null ? null : accepted.get("tenantId");
            return tenantId == null ? Outcome.refused("unavailable") : Outcome.accepted(Long.valueOf(String.valueOf(tenantId)));
        } catch (RestClientResponseException e) {
            String code = errorCode(e.getResponseBodyAsString());
            log.info("FIRST_OWNER acceptance refused: status={} code={}", e.getStatusCode().value(), code);
            return Outcome.refused(reason(e.getStatusCode().value(), code));
        } catch (RuntimeException e) {
            log.warn("FIRST_OWNER acceptance unavailable: {}", e.getClass().getSimpleName());
            return Outcome.refused("unavailable");
        }
    }

    /** The page's stable reason for the tenant Owner's answer. */
    static String reason(int status, String code) {
        if (code != null && code.contains("QUOTA")) {
            return "invitation_quota";
        }
        if ("TENANT_NOT_PENDING_ACTIVATION".equals(code) || "FIRST_OWNER_ALREADY_EXISTS".equals(code)) {
            return "invitation_taken";
        }
        if ("FIRST_OWNER_INVITATION_EMAIL_MISMATCH".equals(code)) {
            return "invitation_mismatch";
        }
        return status >= 500 ? "unavailable" : "invitation_invalid";
    }

    private static String errorCode(String body) {
        try {
            JsonNode node = JSON.readTree(body);
            JsonNode code = node == null ? null : node.get("errorCode");
            return code == null || !code.isString() ? null : code.asString();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
