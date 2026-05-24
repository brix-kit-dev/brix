package io.brix.platform.tenant.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.repository.AuditLogRepository;

class AuditServiceImplTest {

    @Test
    void logRejectsSensitiveDescription() {
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        AuditServiceImpl auditService = new AuditServiceImpl(auditLogRepository, idGenerator);

        AuditEvent event = AuditEvent.builder()
                .action("TEST_ACTION")
                .resourceType("TEST")
                .description("password=Secret123")
                .success(false)
                .build();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class, () -> auditService.log(event));
        assertEquals("Audit field 'description' contains sensitive data and must not be persisted",
            exception.getMessage());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void logRejectsSixDigitOneTimeCodeInContext() {
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        AuditServiceImpl auditService = new AuditServiceImpl(auditLogRepository, idGenerator);

        AuditEvent event = AuditEvent.builder()
                .action("TEST_ACTION")
                .resourceType("TEST")
                .context("{\"attempt\":\"123456\"}")
                .success(false)
                .build();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class, () -> auditService.log(event));
        assertEquals("Audit field 'context' contains sensitive data and must not be persisted",
            exception.getMessage());
        verifyNoInteractions(auditLogRepository);
    }
}