package io.brix.platform.notification;

import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.brix.platform.notification.delivery.EmailDeliveryAdapter;
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

    /**
     * Creates the notification application service.
     *
     * @param templateRepository template repository
     * @param renderer strict template renderer
     * @param emailDeliveryAdapter email delivery adapter
     * @param defaultLocale default locale tag
     */
    public NotificationApplicationService(
            TemplateRepository templateRepository,
            StrictTemplateRenderer renderer,
            EmailDeliveryAdapter emailDeliveryAdapter,
            String defaultLocale) {
        this.templateRepository = templateRepository;
        this.renderer = renderer;
        this.emailDeliveryAdapter = emailDeliveryAdapter;
        this.defaultLocale = normalizeDefaultLocale(defaultLocale);
    }

    @Override
    public void send(NotificationRequest request) {
        String locale = request.locale() == null ? defaultLocale : request.locale();
        NotificationTemplate template = templateRepository.load(request.templateKey(), locale);
        RenderedTemplate rendered = renderer.render(request.templateKey(), template, request.variables());
        try {
            emailDeliveryAdapter.deliver(
                    new EmailMessage(request.recipientEmail(), rendered.subject(), rendered.body()));
            log.info("Notification accepted by delivery adapter: tenantId={}, recipient={}, templateKey={}, locale={}",
                    request.tenantId(), request.maskedRecipientEmail(), request.templateKey(), locale);
        } catch (NotificationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new NotificationException(
                    NotificationException.Code.DELIVERY_FAILED,
                    Map.of("templateKey", request.templateKey()),
                    ex);
        }
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
