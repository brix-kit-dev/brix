package io.infra.adapter.identity.provider.support;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.StateStoreCapability;
import tools.jackson.databind.json.JsonMapper;

/**
 * Server-side storage for protocol data: values are serialized to JSON and encrypted before
 * they reach the state store (root {@code BRX-RS-SECURITY-012}: tokens are stored encrypted).
 * A value that no longer decrypts, for example after a key change, reads as absent.
 *
 * @since 3.2.0
 */
public final class EncryptedStateStore {

    private static final Logger log = LoggerFactory.getLogger(EncryptedStateStore.class);
    private static final String PREFIX = "idp:";

    private final StateStoreCapability store;
    private final SecretEncryptionCapability encryption;
    private final JsonMapper json;

    public EncryptedStateStore(StateStoreCapability store, SecretEncryptionCapability encryption, JsonMapper json) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.encryption = Objects.requireNonNull(encryption, "encryption must not be null");
        this.json = Objects.requireNonNull(json, "json must not be null");
    }

    public void put(String key, Object value, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            remove(key);
            return;
        }
        store.put(PREFIX + key, encryption.encryptSecret(json.writeValueAsString(value)), ttl);
    }

    /** Stores the value only when the key is absent; returns whether it was stored. */
    public boolean putIfAbsent(String key, Object value, Duration ttl) {
        return store.putIfAbsent(PREFIX + key, encryption.encryptSecret(json.writeValueAsString(value)), ttl);
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        return store.get(PREFIX + key, String.class).flatMap(value -> decode(key, value, type));
    }

    /** Reads and removes the value in one step, so a one-time value is used at most once. */
    public <T> Optional<T> take(String key, Class<T> type) {
        return store.getAndRemove(PREFIX + key, String.class).flatMap(value -> decode(key, value, type));
    }

    public void remove(String key) {
        store.remove(PREFIX + key);
    }

    private <T> Optional<T> decode(String key, String value, Class<T> type) {
        try {
            return Optional.of(json.readValue(encryption.decryptSecret(value), type));
        } catch (RuntimeException e) {
            log.warn("[IdentityProvider] stored value could not be read and is ignored: key={}, reason={}",
                    key.replaceAll(":[^:]+$", ":<hash>"), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
