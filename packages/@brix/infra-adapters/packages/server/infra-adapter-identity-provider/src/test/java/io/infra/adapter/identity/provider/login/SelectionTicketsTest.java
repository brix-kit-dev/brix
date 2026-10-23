package io.infra.adapter.identity.provider.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import io.infra.adapter.identity.provider.support.EncryptedStateStore;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.StateStoreCapability;
import tools.jackson.databind.json.JsonMapper;

/** multi-tenant 5.1 §9.3 contextSelectionTicket hard constraints. */
class SelectionTicketsTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-23T09:00:00Z"));
    private final SelectionTickets tickets = new SelectionTickets(new EncryptedStateStore(
            new MapStateStore(), new PlainEncryption(), JsonMapper.builder().build()), clock);

    @Test
    void aTicketIsHighEntropyAndWorksOnceThenCountsAsReplay() {
        LoginTransaction login = login(7L);
        String ticket = tickets.issue(login, "ctx-a");

        assertThat(ticket).hasSizeGreaterThanOrEqualTo(43).doesNotContain("ctx-a");
        assertThat(tickets.redeem(ticket, login).contextId()).isEqualTo("ctx-a");
        SelectionTickets.Redemption again = tickets.redeem(ticket, login);
        assertThat(again.valid()).isFalse();
        assertThat(again.replayed()).isTrue();
    }

    @Test
    void aTicketIsBoundToItsLoginItsGenerationAndFiveMinutes() {
        LoginTransaction login = login(7L);
        LoginTransaction otherLoginOfTheSameIdentity = login(7L);

        String foreign = tickets.issue(login, "ctx-a");
        assertThat(tickets.redeem(foreign, otherLoginOfTheSameIdentity).valid()).isFalse();

        String stale = tickets.issue(login, "ctx-a");
        login.nextTicketGeneration();
        assertThat(tickets.redeem(stale, login).valid()).isFalse();

        String late = tickets.issue(login, "ctx-a");
        clock.now = clock.now.plus(SelectionTickets.TTL).plusSeconds(1);
        assertThat(tickets.redeem(late, login).valid()).isFalse();
    }

    private LoginTransaction login(Long identityId) {
        LoginTransaction transaction = new LoginTransaction("staff@example.test", identityId, clock.instant(), "select-tenant");
        transaction.nextTicketGeneration();
        return transaction;
    }

    private static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class PlainEncryption implements SecretEncryptionCapability {
        @Override
        public String encryptSecret(String plaintextSecret) {
            return plaintextSecret;
        }

        @Override
        public String decryptSecret(String encryptedSecret) {
            return encryptedSecret;
        }
    }

    /** Ignores TTLs: expiry is asserted through the ticket's own deadline. */
    private static final class MapStateStore implements StateStoreCapability {
        private final java.util.Map<String, String> values = new java.util.concurrent.ConcurrentHashMap<>();
        private final JsonMapper json = JsonMapper.builder().build();

        @Override
        public <T> java.util.Optional<T> get(String key, Class<T> type) {
            String value = values.get(key);
            return value == null ? java.util.Optional.empty() : java.util.Optional.of(json.readValue(value, type));
        }

        @Override
        public void put(String key, Object value) {
            values.put(key, json.writeValueAsString(value));
        }

        @Override
        public void put(String key, Object value, java.time.Duration ttl) {
            put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }

        @Override
        public boolean exists(String key) {
            return values.containsKey(key);
        }
    }
}
