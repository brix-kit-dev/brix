package io.brix.platform.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.core.Ordered;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

/**
 * The deliveries a unit of work holds until the last moment before its commit (root BRX-RS-ERROR-017 item 1,
 * RFC-012; standard-link plan queue item 11).
 *
 * <p>A delivery submitted inside a transaction waits there. When the transaction commits, Spring's flush callback
 * first writes every pending change of the unit of work to the database, the answer, inbox and audit records the
 * runtime writes for its owner included, and then the deliveries run in the order they were submitted. The first one
 * that fails stops the rest and fails the commit, so the unit of work rolls back. A transaction that rolls back for
 * any other reason drops what it holds. Outside a transaction, a delivery runs at once.</p>
 *
 * <p>The deliveries belong to one physical transaction: a unit of work that suspends it, as another data owner's
 * does, holds its own and sends them when it commits, before the suspended one does. A delivery submitted once its
 * transaction has begun to commit runs at once, since the writes are in the database by then. One that Spring could
 * no longer hold for the commit, because it was submitted from another synchronization's commit callback, runs
 * after the commit, and its failure is only logged and counted.</p>
 *
 * @author Brix Platform Team
 * @since 3.4.0
 */
final class PendingDeliveries implements TransactionSynchronization {

    private static final Object RESOURCE_KEY = new Object();

    private final List<Runnable> deliveries = new ArrayList<>();
    private boolean committing;

    private PendingDeliveries() {
    }

    /**
     * Runs {@code delivery} at the last moment before the current transaction commits, or at once outside one.
     *
     * @param delivery hands one message to the delivery channel; throws when the channel did not accept it
     */
    static void submit(Runnable delivery) {
        Objects.requireNonNull(delivery, "delivery must not be null");
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            delivery.run();
            return;
        }
        PendingDeliveries pending;
        if (TransactionSynchronizationManager.getResource(RESOURCE_KEY) instanceof PendingDeliveries bound) {
            pending = bound;
        } else {
            pending = new PendingDeliveries();
            TransactionSynchronizationManager.bindResource(RESOURCE_KEY, pending);
            TransactionSynchronizationManager.registerSynchronization(pending);
        }
        if (pending.committing) {
            delivery.run();
            return;
        }
        pending.deliveries.add(delivery);
    }

    /** After every other synchronization registered before it, the owner's transaction scope included. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void suspend() {
        TransactionSynchronizationManager.unbindResourceIfPossible(RESOURCE_KEY);
    }

    @Override
    public void resume() {
        TransactionSynchronizationManager.bindResource(RESOURCE_KEY, this);
    }

    @Override
    public void beforeCommit(boolean readOnly) {
        committing = true;
        TransactionSynchronizationUtils.triggerFlush();
        deliverAll();
    }

    @Override
    public void afterCompletion(int status) {
        if (TransactionSynchronizationManager.getResource(RESOURCE_KEY) == this) {
            TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
        }
        if (status == STATUS_COMMITTED && !committing) {
            committing = true;
            try {
                deliverAll();
            } catch (RuntimeException alreadyReported) {
                // Logged and counted where it failed; the transaction has committed, so there is nothing to undo.
            }
        }
        deliveries.clear();
    }

    /** In submission order; the first failure stops the rest. */
    private void deliverAll() {
        while (!deliveries.isEmpty()) {
            deliveries.remove(0).run();
        }
    }
}
