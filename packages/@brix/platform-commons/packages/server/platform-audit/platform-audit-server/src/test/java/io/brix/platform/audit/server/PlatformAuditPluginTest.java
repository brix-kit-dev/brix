package io.brix.platform.audit.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

import io.brix.platform.audit.core.repository.PlatformAuditLogRepository;
import io.brix.platform.audit.server.event.IdentityAuditFactPayload;
import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.plugin.ClasspathPluginRuntimeDescriptorResolver;
import io.runtime.orchestrator.plugin.DefaultPluginBootstrapContext;
import io.runtime.orchestrator.plugin.DefaultPluginContext;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.PluginContext;

/**
 * The platform audit owner binds a handler for each event subscription it declares (root
 * BRX-RS-MANIFEST-013, MANIFEST-026), and the handler names the payload the runtime decodes.
 */
class PlatformAuditPluginTest {

    private final PluginRuntimeDescriptor descriptor = new ClasspathPluginRuntimeDescriptorResolver()
        .resolve(new PlatformAuditPlugin())
        .orElseThrow();

    @Test
    void everyManifestSubscriptionHasAHandlerFactory() {
        DefaultPluginBootstrapContext bootstrap = configured();

        assertThat(descriptor.eventSubscriptions()).containsOnlyKeys(PlatformAuditPlugin.HANDLER_IDENTITY_AUDIT_FACT);
        assertThat(bootstrap.eventHandlerFactories()).containsOnlyKeys(PlatformAuditPlugin.HANDLER_IDENTITY_AUDIT_FACT);
    }

    @Test
    void theIdentityFactHandlerRecordsThroughTheOwnersOwnRepository() {
        PersistenceCapability persistence = mock(PersistenceCapability.class);
        when(persistence.repository(PlatformAuditLogRepository.class)).thenReturn(mock(PlatformAuditLogRepository.class));
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry();
        capabilities.register(IdGeneratorCapability.class, mock(IdGeneratorCapability.class));
        PluginContext context = new DefaultPluginContext(descriptor, capabilities,
            owner -> "platform-audit".equals(owner) ? Optional.of(persistence) : Optional.empty());

        EventHandler<?> handler = configured().eventHandlerFactories()
            .get(PlatformAuditPlugin.HANDLER_IDENTITY_AUDIT_FACT).create(context);

        assertThat(ResolvableType.forClass(handler.getClass()).as(EventHandler.class).getGeneric(0).resolve())
            .isEqualTo(IdentityAuditFactPayload.class);
        verify(persistence).repository(PlatformAuditLogRepository.class);
    }

    private DefaultPluginBootstrapContext configured() {
        DefaultPluginBootstrapContext bootstrap = new DefaultPluginBootstrapContext(descriptor);
        new PlatformAuditPlugin().configure(bootstrap);
        return bootstrap;
    }
}
