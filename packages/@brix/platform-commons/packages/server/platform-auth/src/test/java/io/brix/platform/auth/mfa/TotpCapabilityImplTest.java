package io.brix.platform.auth.mfa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class TotpCapabilityImplTest {

    @Test
    void matchesRfc6238Sha1Vectors() {
        MfaProperties properties = new MfaProperties();
        properties.setTotpDigits(8);
        properties.setTotpPeriodSeconds(30);
        TotpCapabilityImpl totp = new TotpCapabilityImpl(
                properties, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), new SecureRandom());

        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

        assertEquals("94287082", totp.generateCode(secret, Instant.ofEpochSecond(59), 8));
        assertEquals("07081804", totp.generateCode(secret, Instant.ofEpochSecond(1111111109), 8));
        assertEquals("14050471", totp.generateCode(secret, Instant.ofEpochSecond(1111111111), 8));
        assertEquals("89005924", totp.generateCode(secret, Instant.ofEpochSecond(1234567890), 8));
        assertEquals("69279037", totp.generateCode(secret, Instant.ofEpochSecond(2000000000), 8));
    }

    @Test
    void validatesOnlySixDigitCodesByDefault() {
        MfaProperties properties = new MfaProperties();
        properties.setTotpWindow(0);
        TotpCapabilityImpl totp = new TotpCapabilityImpl(
                properties, Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC), new SecureRandom());

        assertTrue(totp.validateCode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "287082"));
        assertFalse(totp.validateCode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "94287082"));
        assertFalse(totp.validateCode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "abcdef"));
    }
}