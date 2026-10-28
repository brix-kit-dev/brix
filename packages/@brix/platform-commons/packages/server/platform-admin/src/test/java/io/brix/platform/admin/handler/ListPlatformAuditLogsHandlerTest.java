/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.platform.admin.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.brix.platform.admin.dto.PlatformAuditLogDto;
import io.brix.platform.admin.dto.PlatformPageDto;
import io.brix.platform.audit.internal.PlatformAuditAdministration;
import io.brix.platform.audit.internal.PlatformAuditEntryView;
import io.brix.platform.audit.internal.PlatformAuditPage;
import io.brix.platform.audit.internal.PlatformAuditQuery;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * The console's audit query goes to the platform audit owner and comes back in the fields and page
 * format the console already shows (super-admin §6; platform audit design §14).
 */
class ListPlatformAuditLogsHandlerTest {

    private final PlatformAuditAdministration audit = mock(PlatformAuditAdministration.class);
    private final ListPlatformAuditLogsHandler handler = new ListPlatformAuditLogsHandler(audit);

    @Test
    void theConsolesFiltersBecomeTheOwnersQuery() {
        when(audit.query(any())).thenReturn(new PlatformAuditPage(List.of(), 0, 1, 50));

        handler.handle(invocation(Map.of(
            "page", List.of("1"), "size", List.of("50"), "sort", List.of("createdAt,desc"),
            "action", List.of("TENANT_CREATED"), "actorIdentityId", List.of("1001"), "result", List.of("FAILURE"),
            "fromTime", List.of("2026-10-27T00:00:00Z"), "toTime", List.of("2026-10-29T00:00:00+09:00"))));

        ArgumentCaptor<PlatformAuditQuery> query = ArgumentCaptor.forClass(PlatformAuditQuery.class);
        verify(audit).query(query.capture());
        assertEquals(new PlatformAuditQuery(1, 50, "TENANT_CREATED", 1001L,
            Instant.parse("2026-10-27T00:00:00Z"), Instant.parse("2026-10-28T15:00:00Z"), false), query.getValue());
    }

    @Test
    void withoutFiltersTheNewestTwentyRecordsOfEitherResultAreAskedFor() {
        when(audit.query(any())).thenReturn(new PlatformAuditPage(List.of(), 0, 0, 20));

        handler.handle(invocation(Map.of()));

        ArgumentCaptor<PlatformAuditQuery> query = ArgumentCaptor.forClass(PlatformAuditQuery.class);
        verify(audit).query(query.capture());
        assertEquals(new PlatformAuditQuery(0, 20, null, null, null, null, null), query.getValue());
    }

    @Test
    void recordsComeBackInTheConsolesFieldsAndPageFormat() {
        PlatformAuditEntryView discovery = new PlatformAuditEntryView(373732940393353216L, "LOGIN_TENANT_DISCOVERY",
            1034986928673394688L, "runtime:policy:login-tenant-discovery", "SELF", null, List.of(910001L, 910002L),
            true, "login-tenant-discovery", null, "93b93985-4115-4406-b6b4-71706bb8bef4", "172.18.0.0/24", null,
            "runtime.tenant-policy-audit", Instant.parse("2026-10-28T07:21:16Z"));
        PlatformAuditEntryView refused = new PlatformAuditEntryView(373732940393353217L, "FIRST_OWNER_INVITATION_CREATED",
            1001L, "platform-admin", "INVITATION", null, List.of(42L), false, "platform-operation", "tenant_not_found",
            "0af7651916cd43dd8448eb211c80319c", null, null, "platform-audit", Instant.parse("2026-10-28T07:22:00Z"));
        when(audit.query(any())).thenReturn(new PlatformAuditPage(List.of(discovery, refused), 45, 2, 20));

        PlatformPageDto<PlatformAuditLogDto> page = handler.handle(invocation(Map.of("page", List.of("2"))));

        assertEquals(3, page.totalPages());
        assertFalse(page.first());
        assertTrue(page.last());
        PlatformAuditLogDto first = page.content().get(0);
        assertEquals("910001,910002", first.tenantId());
        assertEquals("SUCCESS", first.result());
        assertEquals("login-tenant-discovery", first.reason());
        assertEquals("172.18.0.0/24", first.ip());
        assertEquals("93b93985-4115-4406-b6b4-71706bb8bef4", first.requestId());
        assertNull(first.actorUsername(), "the actor is shown by identity id only (D8)");
        assertEquals("runtime.tenant-policy-audit", first.writer());
        PlatformAuditLogDto second = page.content().get(1);
        assertEquals("FAILURE", second.result());
        assertEquals("tenant_not_found", second.errorCode());
        assertEquals("42", second.tenantId());
        assertEquals("2026-10-28T07:22Z", second.createdAt().toString());
    }

    @Test
    void queriesTheOwnerCannotAnswerAreRefusedWithAStableCode() {
        for (Map<String, List<String>> parameters : List.of(
                Map.of("tenantId", List.of("42")),
                Map.of("result", List.of("MAYBE")),
                Map.of("fromTime", List.of("yesterday")),
                Map.of("fromTime", List.of("2026-10-29T00:00:00Z"), "toTime", List.of("2026-10-28T00:00:00Z")),
                Map.of("size", List.of("0")),
                Map.<String, List<String>>of("actorIdentityId", List.of("someone")))) {
            EndpointHandlingException refused = assertThrows(EndpointHandlingException.class,
                () -> handler.handle(invocation(parameters)), parameters.toString());
            assertEquals(400, refused.status());
            assertEquals("PLATFORM_AUDIT_QUERY_INVALID", refused.errorCode());
        }
        verifyNoInteractions(audit);
    }

    @Test
    void aTenantContextIsRefusedBeforeTheOwnerIsAsked() {
        EndpointInvocation<Void> withTenant = new EndpointInvocation<>(
            null, Map.of(), Map.of(), Map.of(), Optional.of("910001"), Optional.of("1001"), Optional.empty(),
            Instant.now().plusSeconds(30));

        assertEquals(403, assertThrows(EndpointHandlingException.class, () -> handler.handle(withTenant)).status());
        verifyNoInteractions(audit);
    }

    private static EndpointInvocation<Void> invocation(Map<String, List<String>> queryParameters) {
        return new EndpointInvocation<>(
            null, Map.of(), queryParameters, Map.of(), Optional.empty(), Optional.of("1001"), Optional.empty(),
            Instant.now().plusSeconds(30));
    }
}
