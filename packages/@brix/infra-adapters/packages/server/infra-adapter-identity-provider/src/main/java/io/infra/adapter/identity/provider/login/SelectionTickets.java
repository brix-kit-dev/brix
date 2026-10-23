package io.infra.adapter.identity.provider.login;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import io.infra.adapter.identity.provider.support.EncryptedStateStore;
import io.infra.adapter.identity.provider.support.Secrets;

/**
 * One-time tickets for choosing an Actor context after login (multi-tenant 5.1 §9.3,
 * {@code contextSelectionTicket} hard constraints, T-20):
 *
 * <ol>
 *   <li>bound to the login transaction that received it, not only to the identity;</li>
 *   <li>single use, at most five minutes;</li>
 *   <li>256 random bits, never the stable contextId;</li>
 *   <li>the used state is kept in the state store, so a second use is recognized as replay;</li>
 *   <li>the stable contextId stays on the server until tokens are issued.</li>
 * </ol>
 *
 * <p>Reissuing tickets for a transaction (a page reload) starts a new generation and the
 * earlier tickets of that transaction stop working.</p>
 *
 * @since 3.2.0
 */
final class SelectionTickets {

    static final Duration TTL = Duration.ofMinutes(5);
    private static final String TICKET = "login:ticket:";
    private static final String USED = "login:ticket-used:";

    private final EncryptedStateStore store;
    private final Clock clock;

    SelectionTickets(EncryptedStateStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    String issue(LoginTransaction transaction, String contextId) {
        String ticket = Secrets.randomToken(32);
        store.put(TICKET + Secrets.sha256(ticket), new Ticket(transaction.id(), transaction.identityId(),
                transaction.ticketGeneration(), contextId, clock.instant().plus(TTL).getEpochSecond()), TTL);
        return ticket;
    }

    /** Uses a ticket once; the result says whether it was valid, unknown, or used before. */
    Redemption redeem(String ticket, LoginTransaction transaction) {
        if (ticket == null || ticket.isBlank() || ticket.length() > 128) {
            return Redemption.invalid();
        }
        String hash = Secrets.sha256(ticket);
        Optional<Ticket> found = store.take(TICKET + hash, Ticket.class);
        if (found.isEmpty()) {
            return store.get(USED + hash, Boolean.class).orElse(false) ? Redemption.replay() : Redemption.invalid();
        }
        store.put(USED + hash, Boolean.TRUE, TTL);
        Ticket value = found.get();
        boolean bound = value.transactionId().equals(transaction.id())
                && value.identityId().equals(transaction.identityId())
                && value.generation() == transaction.ticketGeneration()
                && clock.instant().getEpochSecond() < value.expiresAt();
        return bound ? Redemption.of(value.contextId()) : Redemption.invalid();
    }

    record Ticket(String transactionId, Long identityId, int generation, String contextId, long expiresAt) {
        @Override
        public String toString() {
            return "Ticket{generation=" + generation + ", expiresAt=" + expiresAt + "}";
        }
    }

    record Redemption(String contextId, boolean replayed) {

        static Redemption of(String contextId) {
            return new Redemption(contextId, false);
        }

        static Redemption invalid() {
            return new Redemption(null, false);
        }

        static Redemption replay() {
            return new Redemption(null, true);
        }

        boolean valid() {
            return contextId != null;
        }
    }
}
