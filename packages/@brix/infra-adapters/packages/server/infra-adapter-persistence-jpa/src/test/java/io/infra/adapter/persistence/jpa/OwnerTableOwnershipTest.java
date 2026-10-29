package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.infra.adapter.persistence.jpa.fixture.ForeignNote;
import io.infra.adapter.persistence.jpa.fixture.OwnedNote;
import io.infra.adapter.persistence.jpa.rejected.LinkedNote;
import io.runtime.sdk.capability.PersistenceCapabilityException;

/**
 * A table belongs to one owner (root BRX-RS-DATA-001, DATA-027): an owner's persistence unit is refused
 * when another owner's entity maps one of its tables, as platform-identity's audit entry did until P4e.
 */
class OwnerTableOwnershipTest {

    private static final Function<Class<?>, Optional<String>> OWNERS = type ->
        type == OwnedNote.class ? Optional.of("owner-a")
            : type == ForeignNote.class ? Optional.of("owner-b")
            : Optional.empty();

    @Test
    void aTableAnotherOwnerAlsoMapsIsRefused() {
        Map<Class<?>, String> tables = new LinkedHashMap<>();
        tables.put(OwnedNote.class, "sys_platform_audit_log");
        tables.put(ForeignNote.class, "sys_platform_audit_log");

        PersistenceCapabilityException refused = assertThrows(PersistenceCapabilityException.class,
            () -> OwnerPersistenceUnits.requireOneOwnerPerTable("owner-a", tables, OWNERS));

        String detail = refused.getCause().getMessage();
        assertTrue(detail.contains("sys_platform_audit_log is mapped by " + OwnedNote.class.getName() + " of 'owner-a'")
            && detail.contains(ForeignNote.class.getName() + " of 'owner-b'"), detail);
        assertThrows(PersistenceCapabilityException.class,
            () -> OwnerPersistenceUnits.requireOneOwnerPerTable("owner-b", tables, OWNERS),
            "the other owner is refused as well");
    }

    @Test
    void distinctTablesAndEntitiesWithoutAnOwnerAreAccepted() {
        Map<Class<?>, String> tables = new LinkedHashMap<>();
        tables.put(OwnedNote.class, "owned_note");
        tables.put(ForeignNote.class, "foreign_note");
        tables.put(LinkedNote.class, "owned_note");

        assertDoesNotThrow(() -> OwnerPersistenceUnits.requireOneOwnerPerTable("owner-a", tables, OWNERS),
            "an entity no plugin manifest packages belongs to no owner");
        assertDoesNotThrow(() -> OwnerPersistenceUnits.requireOneOwnerPerTable("owner-b", tables, OWNERS));
    }
}
