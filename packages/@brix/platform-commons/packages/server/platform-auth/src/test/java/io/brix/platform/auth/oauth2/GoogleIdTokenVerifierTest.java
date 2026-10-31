/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.sdk.capability.BrixExternalHttpException;
import io.runtime.sdk.capability.ExternalHttpCapability;
import io.runtime.sdk.capability.ExternalHttpRequest;
import io.runtime.sdk.capability.ExternalHttpResponse;

/**
 * Google ID tokens are checked against the keys read through platform-auth's declared external service (standard-link
 * plan L8.5).
 */
class GoogleIdTokenVerifierTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String CLIENT = "client-1.apps.googleusercontent.com";

    @Test
    void aTokenSignedWithAPublishedKeyIsAcceptedAndTheKeysComeFromTheDeclaredService() throws Exception {
        KeyPair keys = rsa();
        List<ExternalHttpRequest> sent = new ArrayList<>();
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(() -> service(sent, 200, jwks("k1", keys)), JSON);

        GoogleIdTokenVerifier.GoogleUserInfo user = verifier.verify(token("k1", keys, CLIENT), CLIENT);

        assertEquals("g-1", user.sub());
        assertEquals("person@example.test", user.email());
        assertEquals(1, sent.size());
        assertEquals("/oauth2/v3/certs", sent.get(0).path());
        assertEquals(List.of("application/json"), sent.get(0).headers().get("accept"));
        verifier.verify(token("k1", keys, CLIENT), CLIENT);
        assertEquals(1, sent.size(), "the keys are cached");
    }

    @Test
    void aTokenForAnotherClientOrWithoutTheKeysIsRefused() throws Exception {
        KeyPair keys = rsa();
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
            () -> service(new ArrayList<>(), 200, jwks("k1", keys)), JSON);
        assertThrows(OAuth2Exception.class, () -> verifier.verify(token("k1", keys, "other-client"), CLIENT));

        GoogleIdTokenVerifier unavailable = new GoogleIdTokenVerifier(() -> {
            throw new BrixExternalHttpException(BrixExternalHttpException.Code.UNDECLARED_SERVICE,
                GoogleIdTokenVerifier.JWKS_SERVICE, null);
        }, JSON);
        OAuth2Exception failure = assertThrows(OAuth2Exception.class,
            () -> unavailable.verify(token("k1", keys, CLIENT), CLIENT));
        assertTrue(failure.getMessage().contains("signing keys"), failure.getMessage());

        GoogleIdTokenVerifier refused = new GoogleIdTokenVerifier(
            () -> service(new ArrayList<>(), 503, "{}"), JSON);
        assertThrows(OAuth2Exception.class, () -> refused.verify(token("k1", keys, CLIENT), CLIENT));
    }

    private static ExternalHttpCapability.ExternalService service(List<ExternalHttpRequest> sent, int status,
            String body) {
        return new ExternalHttpCapability.ExternalService() {
            @Override
            public String id() {
                return GoogleIdTokenVerifier.JWKS_SERVICE;
            }

            @Override
            public ExternalHttpResponse send(ExternalHttpRequest request) {
                sent.add(request);
                return new ExternalHttpResponse(status, Map.of(), body.getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    private static KeyPair rsa() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String jwks(String kid, KeyPair keys) {
        RSAPublicKey key = (RSAPublicKey) keys.getPublic();
        return "{\"keys\":[{\"kid\":\"" + kid + "\",\"kty\":\"RSA\",\"alg\":\"RS256\",\"n\":\"" + unsigned(key.getModulus())
            + "\",\"e\":\"" + unsigned(key.getPublicExponent()) + "\"}]}";
    }

    private static String token(String kid, KeyPair keys, String audience) throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        String header = base64("{\"alg\":\"RS256\",\"kid\":\"" + kid + "\"}");
        String payload = base64("{\"iss\":\"accounts.google.com\",\"aud\":\"" + audience + "\",\"exp\":" + (now + 600)
            + ",\"iat\":" + now + ",\"sub\":\"g-1\",\"email\":\"person@example.test\",\"email_verified\":true}");
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keys.getPrivate());
        signature.update((header + "." + payload).getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
