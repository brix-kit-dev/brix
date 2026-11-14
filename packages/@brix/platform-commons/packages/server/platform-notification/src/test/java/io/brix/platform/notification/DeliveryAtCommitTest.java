package io.brix.platform.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.brix.platform.notification.delivery.EmailDeliveryAdapter;
import io.brix.platform.notification.delivery.EmailDeliveryException;
import io.brix.platform.notification.delivery.EmailMessage;
import io.brix.platform.notification.template.StrictTemplateRenderer;
import io.brix.platform.notification.template.TemplateRepository;
import io.runtime.sdk.capability.NotificationException;
import io.runtime.sdk.capability.NotificationRequest;
import io.runtime.sdk.capability.NotificationTemplateKeys;

/**
 * When a notification goes out (root BRX-RS-ERROR-017 items 1, 3 and 4; RFC-012 §4; standard-link plan queue item
 * 11). A transaction manager that only records its steps stands for the owner's: Spring's synchronization, suspension
 * and commit order are the real ones, and a synchronization standing for the owner's transaction scope records the
 * flush of its writes.
 */
class DeliveryAtCommitTest {

    private static final String KEY = NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL;

    private final List<String> steps = new ArrayList<>();
    private final RecordingAdapter adapter = new RecordingAdapter(steps);
    private final List<String> deliveries = new ArrayList<>();
    private final NotificationApplicationService service = new NotificationApplicationService(
            new TemplateRepository(new ObjectMapper(), getClass().getClassLoader()),
            new StrictTemplateRenderer(),
            adapter,
            "en-US",
            new NotificationMetrics() {
                @Override
                public void delivered(String templateKey, String outcome, Duration took) {
                    deliveries.add(templateKey + "|" + outcome);
                }
            });
    private final StepTransactions transactions = new StepTransactions(steps);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger serviceLog = (Logger) LoggerFactory.getLogger(NotificationApplicationService.class);

    @BeforeEach
    void captureLogs() {
        logs.start();
        serviceLog.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        serviceLog.detachAppender(logs);
        MDC.clear();
    }

    @Test
    void inAUnitOfWorkTheMessageGoesOutAfterItsLastWriteAndBeforeItsCommit() {
        inUnitOfWork("owner", () -> {
            service.send(link("a"));
            steps.add("write");
        });

        assertEquals(List.of("begin owner", "write", "flush owner", "deliver a", "commit owner"), steps);
    }

    @Test
    void messagesGoOutInTheOrderTheUnitOfWorkSentThem() {
        inUnitOfWork("owner", () -> {
            service.send(link("a"));
            service.send(link("b"));
        });

        assertEquals(List.of("begin owner", "flush owner", "deliver a", "deliver b", "commit owner"), steps);
    }

    @Test
    void theFirstMessageRefusedStopsTheRestAndRollsTheUnitOfWorkBack() {
        adapter.refused.add("a");

        NotificationException failure = assertThrows(NotificationException.class, () -> inUnitOfWork("owner", () -> {
            service.send(link("a"));
            service.send(link("b"));
            steps.add("write");
        }));

        assertEquals(NotificationException.Code.DELIVERY_FAILED, failure.errorCode());
        assertEquals(List.of("begin owner", "write", "flush owner", "deliver a", "rollback owner"), steps);
        // Tried once: the capability does not retry.
        assertEquals(List.of(KEY + "|rejected"), deliveries);
    }

    @Test
    void aUnitOfWorkThatRollsBackForAnotherReasonSendsNothing() {
        assertThrows(IllegalStateException.class, () -> inUnitOfWork("owner", () -> {
            service.send(link("a"));
            throw new IllegalStateException("a later write failed");
        }));
        new TransactionTemplate(transactions, named("marked")).executeWithoutResult(status -> {
            TransactionSynchronizationManager.registerSynchronization(new OwnerScope("marked"));
            service.send(link("b"));
            status.setRollbackOnly();
        });
        inUnitOfWork("next", () -> service.send(link("c")));

        assertEquals(List.of("begin owner", "rollback owner", "begin marked", "rollback marked",
            "begin next", "flush next", "deliver c", "commit next"), steps);
        assertEquals(List.of(KEY + "|sent"), deliveries);
    }

    @Test
    void outsideAUnitOfWorkTheMessageGoesOutAtOnceAndItsFailureIsThrownThere() {
        adapter.refused.add("b");

        service.send(link("a"));
        assertEquals(List.of("deliver a"), steps);

        assertThrows(NotificationException.class, () -> service.send(link("b")));
        assertEquals(List.of("deliver a", "deliver b"), steps);
    }

    @Test
    void aRequestOrTemplateThatIsNotRightFailsAtTheSendInsideAUnitOfWorkToo() {
        inUnitOfWork("owner", () -> {
            NotificationException missing = assertThrows(NotificationException.class, () -> service.send(
                new NotificationRequest(null, "a@example.invalid", "platform.admin.unknown", "en-US",
                    Map.of("setupUrl", "https://setup.example.invalid/#token=raw-secret"))));
            assertEquals(NotificationException.Code.TEMPLATE_NOT_FOUND, missing.errorCode());
            NotificationException extra = assertThrows(NotificationException.class, () -> service.send(
                new NotificationRequest(null, "a@example.invalid", KEY, "en-US",
                    Map.of("setupUrl", "https://setup.example.invalid/#token=raw-secret", "extra", "value"))));
            assertEquals(NotificationException.Code.TEMPLATE_INVALID, extra.errorCode());
            steps.add("the caller carries on");
        });

        // Nothing waits for the commit, so nothing is flushed for it either.
        assertEquals(List.of("begin owner", "the caller carries on", "commit owner"), steps);
        assertEquals(List.of(), deliveries);
    }

    @Test
    void anOwnerCalledByAnotherSendsItsOwnBeforeItCommitsWhileTheCallersWait() {
        // Root BRX-RS-DATA-029: the called owner's unit of work suspends the caller's and commits first.
        inUnitOfWork("tenant", () -> {
            service.send(link("invitee"));
            inUnitOfWork("identity", () -> service.send(link("setup")));
            steps.add("tenant write");
        });

        assertEquals(List.of("begin tenant", "begin identity", "flush identity", "deliver setup", "commit identity",
            "tenant write", "flush tenant", "deliver invitee", "commit tenant"), steps);
    }

    @Test
    void aCallerThatRollsBackAfterTheCalledOwnerCommittedCannotCallItsMessageBack() {
        // Root BRX-RS-ERROR-017 item 3: the called owner's message went out with its commit; the caller's did not.
        assertThrows(IllegalStateException.class, () -> inUnitOfWork("tenant", () -> {
            service.send(link("invitee"));
            inUnitOfWork("identity", () -> service.send(link("setup")));
            throw new IllegalStateException("the caller's last write failed");
        }));

        assertEquals(List.of("begin tenant", "begin identity", "flush identity", "deliver setup", "commit identity",
            "rollback tenant"), steps);
    }

    @Test
    void aMessageSentOnceTheCommitHasBegunGoesOutAtOnce() {
        inUnitOfWork("owner", () -> {
            service.send(link("a"));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCompletion() {
                    service.send(link("b"));
                }
            });
        });

        assertEquals(List.of("begin owner", "flush owner", "deliver a", "deliver b", "commit owner"), steps);
    }

    @Test
    void aMessageSentFromACommitCallbackOfAnotherSynchronizationGoesOutAfterTheCommitAndIsNotLost() {
        adapter.refused.add("refused");

        inUnitOfWork("owner", () -> TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    service.send(link("late"));
                    service.send(link("refused"));
                }
            }));

        // The commit has happened, so the refusal is reported in the log and the meters, not to the committer.
        assertEquals(List.of("begin owner", "commit owner", "deliver late", "deliver refused"), steps);
        assertEquals(List.of(KEY + "|sent", KEY + "|rejected"), deliveries);
        assertEquals(1, warnings().size());
        assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
    }

    @Test
    void aFailureAtTheCommitIsLoggedWithTheTraceOfTheSendThatQueuedIt() {
        adapter.refused.add("a");

        assertThrows(NotificationException.class, () -> inUnitOfWork("owner", () -> {
            MDC.put("traceId", "0af7651916cd43dd8448eb211c80319c");
            service.send(link("a"));
            MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        }));

        List<String> warnings = warnings();
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("traceId=0af7651916cd43dd8448eb211c80319c"), warnings.get(0));
    }

    private void inUnitOfWork(String owner, Runnable work) {
        new TransactionTemplate(transactions, named(owner)).executeWithoutResult(status -> {
            TransactionSynchronizationManager.registerSynchronization(new OwnerScope(owner));
            work.run();
        });
    }

    private static TransactionDefinition named(String name) {
        org.springframework.transaction.support.DefaultTransactionDefinition definition =
            new org.springframework.transaction.support.DefaultTransactionDefinition(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        definition.setName(name);
        return definition;
    }

    private static NotificationRequest link(String recipient) {
        return new NotificationRequest(null, recipient + "@example.invalid", KEY, "en-US",
            Map.of("setupUrl", "https://setup.example.invalid/#token=raw-secret"));
    }

    private List<String> warnings() {
        return logs.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    /** Stands for the owner's transaction scope, which writes the owner's pending changes on Spring's flush callback. */
    private final class OwnerScope implements TransactionSynchronization {

        private final String owner;

        OwnerScope(String owner) {
            this.owner = owner;
        }

        @Override
        public void flush() {
            steps.add("flush " + owner);
        }
    }

    private static final class RecordingAdapter implements EmailDeliveryAdapter {

        private final List<String> steps;
        private final Set<String> refused = new HashSet<>();

        RecordingAdapter(List<String> steps) {
            this.steps = steps;
        }

        @Override
        public void deliver(EmailMessage message) {
            String recipient = message.recipientEmail().substring(0, message.recipientEmail().indexOf('@'));
            steps.add("deliver " + recipient);
            if (refused.contains(recipient)) {
                throw new EmailDeliveryException(EmailDeliveryException.Reason.REJECTED, "reply 550", null);
            }
        }
    }

    /** A transaction manager that records its steps, with Spring's own synchronization and suspension. */
    private static final class StepTransactions extends AbstractPlatformTransactionManager {

        private final List<String> steps;
        private final ThreadLocal<String> active = new ThreadLocal<>();

        StepTransactions(List<String> steps) {
            this.steps = steps;
        }

        @Override
        protected Object doGetTransaction() {
            return new Transaction(active.get());
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((Transaction) transaction).existing != null;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            ((Transaction) transaction).name = definition.getName();
            active.set(definition.getName());
            steps.add("begin " + definition.getName());
        }

        @Override
        protected Object doSuspend(Object transaction) {
            String suspended = active.get();
            active.remove();
            return suspended;
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
            active.set((String) suspendedResources);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            steps.add("commit " + ((Transaction) status.getTransaction()).name);
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            steps.add("rollback " + ((Transaction) status.getTransaction()).name);
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            // The template marks the status itself; nothing else holds the transaction.
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            active.remove();
        }
    }

    private static final class Transaction {

        private final String existing;
        private String name;

        Transaction(String existing) {
            this.existing = existing;
        }
    }
}
