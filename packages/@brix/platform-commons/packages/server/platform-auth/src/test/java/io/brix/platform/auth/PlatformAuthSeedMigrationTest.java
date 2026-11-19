package io.brix.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.brix.platform.auth.api.PermissionCode;

/**
 * The platform permission seed is the catalog and nothing else (super-admin §3.3, BRX-SA-AUTH-006; standard-link plan
 * queue item 18, 18f, D158, D160): after platform-auth's migrations, {@code auth_permission} holds exactly the IDs of
 * {@link PermissionCode} (read by reflection), the super administrator is granted exactly them, and the only platform
 * role is {@code PLATFORM_SUPER_ADMIN}. Runs on a throwaway PostgreSQL named by {@code BRIX_P0_POSTGRES_URL}, in a schema
 * of its own.
 */
class PlatformAuthSeedMigrationTest {

    private static final String URL = System.getenv("BRIX_P0_POSTGRES_URL");
    private static final String USER = System.getenv("BRIX_P0_POSTGRES_USER");
    private static final String PASSWORD = System.getenv("BRIX_P0_POSTGRES_PASSWORD");

    private String schema;
    private Connection connection;

    @BeforeEach
    void connect() throws SQLException {
        assumeTrue(URL != null && !URL.isBlank(), "BRIX_P0_POSTGRES_URL names no throwaway PostgreSQL database");
        schema = "platform_auth_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        connection = DriverManager.getConnection(URL, USER, PASSWORD);
    }

    @AfterEach
    void dropSchema() throws SQLException {
        if (connection != null) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
            connection.close();
        }
    }

    @Test
    void aFreshInstallSeedsExactlyTheCatalog() throws Exception {
        migrate(null);

        assertSeedIsTheCatalog();
        // Flyway also records creating the schema, with no version.
        assertEquals(List.of("1", "2", "3", "4", "5"), strings("SELECT version FROM flyway_schema_history_platform_auth"
            + " WHERE success AND version IS NOT NULL ORDER BY installed_rank"));
    }

    @Test
    void anUpgradeFromV4RemovesTheRetiredRowsAndALinkToTheBootstrapRole() throws Exception {
        migrate("4");
        // Where every installation stands before 18f: twenty codes, a BOOTSTRAP role and its two grants.
        assertEquals(20, strings("SELECT code FROM auth_permission WHERE tenant_id = 'PLATFORM'").size());
        assertEquals(Set.of("BOOTSTRAP", "PLATFORM_SUPER_ADMIN"), platformRoles());
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO auth_user (id, username, tenant_id) VALUES ('u-18f', 'linked', 'PLATFORM')");
            statement.execute("INSERT INTO auth_user_role (id, user_id, role_id, tenant_id)"
                + " VALUES ('ur-18f', 'u-18f', 'role-platform-bootstrap', 'PLATFORM')");
        }

        migrate(null);

        assertSeedIsTheCatalog();
        assertEquals(List.of(), strings("SELECT id FROM auth_user_role WHERE role_id = 'role-platform-bootstrap'"));
        assertEquals(List.of("linked"), strings("SELECT username FROM auth_user WHERE id = 'u-18f'"));
        // The tenant roles are not this migration's: they leave with super-admin's role assignment (RFC-003 Q10).
        assertEquals(List.of("TENANT_ADMIN", "USER"), strings("SELECT code FROM auth_role WHERE scope = 'TENANT' ORDER BY code"));
    }

    private void assertSeedIsTheCatalog() throws Exception {
        Set<String> catalog = catalog();
        assertEquals(catalog, new TreeSet<>(strings(
            "SELECT code FROM auth_permission WHERE tenant_id = 'PLATFORM' AND NOT deleted")), "the seeded catalog");
        assertEquals(catalog, new TreeSet<>(strings("SELECT p.code FROM auth_role_permission rp"
            + " JOIN auth_permission p ON p.id = rp.permission_id"
            + " WHERE rp.role_id = 'role-platform-super-admin' AND NOT rp.deleted")), "the super administrator's grants");
        assertEquals(List.of(), strings("SELECT p.code FROM auth_role_permission rp"
            + " JOIN auth_permission p ON p.id = rp.permission_id WHERE rp.tenant_id = 'PLATFORM'"
            + " AND rp.role_id <> 'role-platform-super-admin'"), "grants to any other platform role");
        assertEquals(Set.of("PLATFORM_SUPER_ADMIN"), platformRoles());
    }

    private Set<String> platformRoles() throws SQLException {
        return new TreeSet<>(strings("SELECT code FROM auth_role WHERE scope = 'PLATFORM' AND NOT deleted"));
    }

    private void migrate(String target) {
        var configuration = Flyway.configure()
            .dataSource(URL, USER, PASSWORD)
            .schemas(schema)
            .locations("classpath:db/migration/platform-auth")
            .table("flyway_schema_history_platform_auth");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private List<String> strings(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            try (ResultSet rows = statement.executeQuery(sql)) {
                List<String> values = new ArrayList<>();
                while (rows.next()) {
                    values.add(rows.getString(1));
                }
                return values;
            }
        }
    }

    /** The IDs {@link PermissionCode} declares: its static String fields (a coverage agent's synthetic field aside). */
    private static Set<String> catalog() throws IllegalAccessException {
        Set<String> ids = new TreeSet<>();
        for (Field field : PermissionCode.class.getDeclaredFields()) {
            if (!field.isSynthetic() && Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                ids.add((String) field.get(null));
            }
        }
        return ids;
    }
}
