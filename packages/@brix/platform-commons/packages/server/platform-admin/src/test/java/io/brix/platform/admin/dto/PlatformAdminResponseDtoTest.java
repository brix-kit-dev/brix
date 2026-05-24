package io.brix.platform.admin.dto;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class PlatformAdminResponseDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void resetPasswordResponseContainsOnlySetupLinkStatus() throws Exception {
        ResetPasswordResponse response = new ResetPasswordResponse(true);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertTrue(json.get("setupLinkSent").asBoolean());
        assertFalse(json.has("password"));
        assertFalse(json.has("setupUrl"));
        assertFalse(json.has("setupToken"));
    }

    @Test
    void createPlatformAdminResponseContainsOnlyIdsAndSetupLinkStatus() throws Exception {
        CreatePlatformAdminResponse response = new CreatePlatformAdminResponse(
                976369206184382464L,
                974180454301175808L,
                true);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertEquals("976369206184382464", json.get("id").asText());
        assertEquals("974180454301175808", json.get("identityId").asText());
        assertTrue(json.get("setupLinkSent").asBoolean());
        assertFalse(json.get("id").isNumber());
        assertFalse(json.has("password"));
        assertFalse(json.has("setupUrl"));
        assertFalse(json.has("setupToken"));
    }

    @Test
    void setupValidateResponseCarriesExplicitValidFlagAndNoSetupToken() throws Exception {
        PlatformSetupValidateResponse response = new PlatformSetupValidateResponse(
                true,
                974180454301175808L,
                "root-admin@example.invalid",
                "root-admin",
                "INITIAL_SETUP",
                OffsetDateTime.parse("2026-05-23T08:58:15.200589Z"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertTrue(json.get("valid").asBoolean());
        assertEquals("974180454301175808", json.get("identityId").asText());
        assertFalse(json.get("identityId").isNumber());
        assertFalse(json.has("setupToken"));
        assertFalse(json.has("setupUrl"));
        assertFalse(json.has("token"));
    }

    @Test
    void setupCompleteResponseContainsOnlyActivationStatus() throws Exception {
        PlatformSetupCompleteResponse response = new PlatformSetupCompleteResponse(true);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertTrue(json.get("activated").asBoolean());
        assertFalse(json.has("setupToken"));
        assertFalse(json.has("setupUrl"));
        assertFalse(json.has("token"));
    }

    @Test
    void setupRequestDtosUseNeutralTokenJsonField() throws Exception {
        PlatformSetupTotpInitRequest initRequest = objectMapper.readValue(
                "{\"token\":\"setup-token\"}",
                PlatformSetupTotpInitRequest.class);
        PlatformSetupCompleteRequest completeRequest = objectMapper.readValue(
                "{\"token\":\"setup-token\",\"challengeId\":\"challenge-id\","
                        + "\"password\":\"StrongPassword!2026\",\"totpCode\":\"123456\"}",
                PlatformSetupCompleteRequest.class);

        JsonNode initJson = objectMapper.readTree(objectMapper.writeValueAsString(initRequest));
        JsonNode completeJson = objectMapper.readTree(objectMapper.writeValueAsString(completeRequest));

        assertEquals("setup-token", initRequest.setupToken());
        assertEquals("setup-token", completeRequest.setupToken());
        assertEquals("setup-token", initJson.get("token").asText());
        assertEquals("setup-token", completeJson.get("token").asText());
        assertFalse(initJson.has("setupToken"));
        assertFalse(completeJson.has("setupToken"));
    }

    @Test
    void platformAdminDtoSerializesSnowflakeIdsAsStrings() throws Exception {
        PlatformAdminDto response = new PlatformAdminDto(
                976369206184382464L,
                974180454301175808L,
                "platform-super-admin",
                "super-admin@example.invalid",
                "PLATFORM_SUPER_ADMIN",
                "ACTIVE",
                false,
                null,
                OffsetDateTime.parse("2026-05-18T12:00:00Z"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertEquals("976369206184382464", json.get("adminId").asText());
        assertEquals("974180454301175808", json.get("identityId").asText());
        assertFalse(json.get("adminId").isNumber());
        assertFalse(json.get("identityId").isNumber());
    }
}
