package io.brix.platform.auth.mfa;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;

/**
 * Auto-configuration for platform MFA capabilities.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@AutoConfiguration
@EnableConfigurationProperties(MfaProperties.class)
public class MfaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TotpCapability.class)
    public TotpCapability totpCapability(MfaProperties properties) {
        return new TotpCapabilityImpl(properties);
    }

    @Bean
    @ConditionalOnMissingBean(SecretEncryptionCapability.class)
    @ConditionalOnProperty(prefix = "brix.platform.mfa", name = "encryption-key")
    public SecretEncryptionCapability secretEncryptionCapability(MfaProperties properties) {
        return new AesGcmSecretEncryptionCapability(properties.getEncryptionKey());
    }
}