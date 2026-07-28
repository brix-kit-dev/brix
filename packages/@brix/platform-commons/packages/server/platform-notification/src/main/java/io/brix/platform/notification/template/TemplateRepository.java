package io.brix.platform.notification.template;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.runtime.sdk.capability.NotificationException;

/**
 * Loads packaged notification templates from classpath resources.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class TemplateRepository {

    private static final String TEMPLATE_ROOT = "notification-templates/";

    private final ObjectMapper objectMapper;
    private final ClassLoader classLoader;

    /**
     * Creates a template repository.
     *
     * @param objectMapper JSON parser
     * @param classLoader resource class loader
     */
    public TemplateRepository(ObjectMapper objectMapper, ClassLoader classLoader) {
        this.objectMapper = objectMapper;
        this.classLoader = classLoader;
    }

    /**
     * Loads one template by key and locale.
     *
     * @param templateKey stable template key
     * @param locale locale tag
     * @return parsed template
     */
    public NotificationTemplate load(String templateKey, String locale) {
        String path = TEMPLATE_ROOT + templateKey + "/" + locale + ".json";
        try (InputStream input = classLoader.getResourceAsStream(path)) {
            if (input == null) {
                throw new NotificationException(
                        NotificationException.Code.TEMPLATE_NOT_FOUND,
                        Map.of("templateKey", templateKey, "locale", locale));
            }
            return objectMapper.readValue(input, NotificationTemplate.class);
        } catch (NotificationException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new NotificationException(
                    NotificationException.Code.TEMPLATE_INVALID,
                    Map.of("templateKey", templateKey, "locale", locale),
                    ex);
        }
    }
}
