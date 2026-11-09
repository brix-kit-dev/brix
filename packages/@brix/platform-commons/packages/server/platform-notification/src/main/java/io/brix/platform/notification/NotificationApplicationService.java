package io.brix.platform.notification;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.brix.platform.notification.delivery.EmailDeliveryAdapter;
import io.brix.platform.notification.delivery.EmailDeliveryException;
import io.brix.platform.notification.delivery.EmailMessage;
import io.brix.platform.notification.template.NotificationTemplate;
import io.brix.platform.notification.template.RenderedTemplate;
import io.brix.platform.notification.template.StrictTemplateRenderer;
import io.brix.platform.notification.template.TemplateRepository;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.capability.NotificationException;
import io.runtime.sdk.capability.NotificationRequest;
import io.runtime.sdk.capability.NotificationTemplateKeys;

/**
 * Application service that implements the reusable notification capability.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class NotificationApplicationService implements NotificationCapability {

    private static final Logger log = LoggerFactory.getLogger(NotificationApplicationService.class);

    private final TemplateRepository templateRepository;
    private final StrictTemplateRenderer renderer;
    private final EmailDeliveryAdapter emailDeliveryAdapter;
    private final String defaultLocale;
    private final NotificationMetrics metrics;

    /**
     * Creates the notification application service.
     *
     * @param templateRepository template repository
     * @param renderer strict template renderer
     * @param emailDeliveryAdapter email delivery adapter
     * @param defaultLocale default locale tag
     * @param metrics the delivery meters
     */
    public NotificationApplicationService(
            TemplateRepository templateRepository,
            StrictTemplateRenderer renderer,
            EmailDeliveryAdapter emailDeliveryAdapter,
            String defaultLocale,
            NotificationMetrics metrics) {
        this.templateRepository = templateRepository;
        this.renderer = renderer;
        this.emailDeliveryAdapter = emailDeliveryAdapter;
        this.defaultLocale = normalizeDefaultLocale(defaultLocale);
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * Renders the template and hands the message to the delivery adapter.
     *
     * <p>Each delivery is counted and timed by template and outcome; one that fails is logged at WARN with the
     * masked recipient, the template, the outcome and the trace id, never with the content, a link or the server's
     * reply text (notification decision D107, standard-link point 14a). The caller gets the same error code as
     * before: {@code notification.delivery.failed}.</p>
     */
    @Override
    public void send(NotificationRequest request) {
        String locale = request.locale() == null ? defaultLocale : request.locale();
        NotificationTemplate template = templateRepository.load(request.templateKey(), locale);
        RenderedTemplate rendered = renderer.render(request.templateKey(), template, request.variables());
        EmailMessage message = new EmailMessage(request.recipientEmail(), rendered.subject(), rendered.body());
        long started = System.nanoTime();
        String outcome = EmailDeliveryException.Reason.FAILED.tag();
        try {
            emailDeliveryAdapter.deliver(message);
            outcome = NotificationMetrics.SENT;
            log.info("Notification accepted by delivery adapter: tenantId={}, recipient={}, templateKey={}, locale={}",
                    request.tenantId(), request.maskedRecipientEmail(), request.templateKey(), locale);
        } catch (EmailDeliveryException ex) {
            outcome = ex.reason().tag();
            failed(request, locale, outcome, ex.getMessage());
            throw new NotificationException(NotificationException.Code.DELIVERY_FAILED, Map.of(), ex);
        } catch (NotificationException ex) {
            failed(request, locale, outcome, ex.errorCode().wireCode());
            throw ex;
        } catch (RuntimeException ex) {
            failed(request, locale, outcome, ex.getClass().getSimpleName());
            throw new NotificationException(
                    NotificationException.Code.DELIVERY_FAILED,
                    Map.of("templateKey", request.templateKey()),
                    ex);
        } finally {
            metrics.delivered(request.templateKey(), outcome, Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private static void failed(NotificationRequest request, String locale, String outcome, String detail) {
        log.warn("event=notification.delivery_failed outcome={} templateKey={} locale={} tenantId={} recipient={} "
                        + "traceId={} detail={}",
                outcome, request.templateKey(), locale, request.tenantId(), request.maskedRecipientEmail(),
                traceId(), detail);
    }

    /** The trace id the runtime put on the thread for the call, or {@code -} outside one. */
    private static String traceId() {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        String traceparent = MDC.get("traceparent");
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 3 && !parts[1].isBlank()) {
                return parts[1];
            }
        }
        return "-";
    }

    @Override
    public void sendSetupLink(String email, String setupUrl, String purpose) {
        String templateKey = switch (purpose) {
            case "INITIAL_SETUP" -> NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL;
            case "PASSWORD_RESET" -> NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_PASSWORD_RESET;
            default -> throw new NotificationException(
                    NotificationException.Code.REQUEST_INVALID,
                    Map.of("field", "purpose"));
        };
        send(new NotificationRequest(null, email, templateKey, defaultLocale, Map.of("setupUrl", setupUrl)));
    }

    private static String normalizeDefaultLocale(String value) {
        String normalized = Locale.forLanguageTag(value == null || value.isBlank() ? "en-US" : value).toLanguageTag();
        if (!"zh-CN".equals(normalized) && !"en-US".equals(normalized)) {
            throw new NotificationException(
                    NotificationException.Code.REQUEST_INVALID,
                    Map.of("field", "defaultLocale"));
        }
        return normalized;
    }
}
