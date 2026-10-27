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
package io.infra.adapter.persistence.jpa;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.PersistenceCapabilityException;

/**
 * Last line of the adapter's tenant isolation: checks every SQL statement Hibernate renders and
 * refuses the ones it cannot prove stay inside the owner transaction's tenant scope. It never
 * rewrites SQL.
 *
 * <p>Inside an owner unit of work, each read of a tenant table must carry the tenant filter's
 * predicate on that table's alias, or, while a policy has bound no tenant yet, an equality on
 * the policy's key. Bulk updates and deletes need the tenant predicate too; entity writes by
 * primary key are checked by {@link TenantGuardIntegrator}; {@code insert ... select} into a
 * tenant table is refused. A tenant table the scope does not reach is refused outright.</p>
 *
 * <p>Statements outside owner units of work come from modules that still create Spring Data
 * repositories themselves. They keep the rules the platform-tenant guard applied before, until
 * the last such module moves to {@code PersistenceCapability}.</p>
 */
public final class TenantStatementVerifier implements StatementInspector {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger security = LoggerFactory.getLogger("brix.tenant.isolation.audit");
    private static final int CACHE_LIMIT = 20_000;
    private static final Pattern LITERAL = Pattern.compile("'(?:[^']|'')*'");
    private static final Pattern LEADING_COMMENT = Pattern.compile("^\\s*/\\*.*?\\*/\\s*", Pattern.DOTALL);
    private static final Pattern TABLE_REFERENCE = Pattern.compile(
        "\\b(from|join|update|into)\\s+([a-z_][a-z0-9_$]*(?:\\.[a-z_][a-z0-9_$]*)?)(?:\\s+(?:as\\s+)?([a-z_][a-z0-9_$]*))?");
    private static final Set<String> NOT_ALIASES = Set.of(
        "where", "set", "on", "join", "left", "right", "inner", "outer", "cross", "full", "group", "order",
        "having", "limit", "offset", "fetch", "for", "union", "values", "select", "natural", "using", "as",
        "of", "with", "returning", "default", "lateral", "except", "intersect", "window", "nowait", "skip");

    private final transient ConcurrentMap<String, Boolean> verified = new ConcurrentHashMap<>();

    @Override
    public String inspect(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        OwnerTransactionScope owner = OwnerTransactionScope.current();
        if (owner == null) {
            LegacyRules.check(sql);
            return sql;
        }
        TenantScopeState state = owner.tenancy();
        String cacheKey = signature(state) + '\n' + sql;
        if (verified.containsKey(cacheKey)) {
            return sql;
        }
        try {
            verify(sql, state);
        } catch (PersistenceCapabilityException violation) {
            security.warn("event=tenant.isolation.violation owner={} scope={} code={} sql={}",
                state.tables().ownerId(), state.safeParameters().get("scope"), violation.code().wireCode(),
                fingerprint(sql));
            throw violation;
        }
        if (verified.size() < CACHE_LIMIT) {
            verified.put(cacheKey, Boolean.TRUE);
        }
        return sql;
    }

    static void verify(String sql, TenantScopeState state) {
        String text = LEADING_COMMENT.matcher(LITERAL.matcher(sql.toLowerCase(Locale.ROOT)).replaceAll("''"))
            .replaceFirst("");
        String kind = firstKeyword(text);
        boolean insertSelect = "insert".equals(kind) && Pattern.compile("\\bselect\\b").matcher(text).find();
        List<Reference> references = references(text);
        boolean ownerStatement = references.stream()
            .anyMatch(reference -> state.tables().kindOf(OwnerTenantTables.normalize(reference.table())).isPresent());
        if (!ownerStatement && !references.isEmpty()) {
            // Another module's statement while this owner's transaction is bound, e.g. in an
            // after-commit callback: it keeps the rules it had outside owner units of work.
            LegacyRules.check(sql);
            return;
        }
        boolean targetSeen = false;
        for (Reference reference : references) {
            String table = OwnerTenantTables.normalize(reference.table());
            boolean target = !targetSeen && isTarget(kind, reference.keyword());
            targetSeen |= target;
            if (!state.tables().isTenantTable(table)) {
                continue;
            }
            if (target) {
                if (!state.mayWrite(table)) {
                    throw state.tenants().isEmpty()
                        ? state.required(kind + " of " + table + " without a bound tenant")
                        : state.violation(kind + " of " + table + " is outside the scope");
                }
                if (insertSelect) {
                    throw state.violation("insert ... select into tenant table " + table);
                }
                if (reference.alias() != null) {
                    requirePredicate(text, reference.alias(), table, state);
                }
                continue;
            }
            if (!state.mayRead(table)) {
                throw state.tenants().isEmpty() && state.policy().isEmpty()
                    ? state.required("read of " + table + " without a bound tenant")
                    : state.violation("read of " + table + " is outside the scope");
            }
            if (reference.alias() == null) {
                throw state.violation("read of " + table + " without an alias cannot be verified");
            }
            requirePredicate(text, reference.alias(), table, state);
        }
        if (references.isEmpty()) {
            for (String table : state.tables().tenantTables()) {
                if (Pattern.compile("\\b" + Pattern.quote(table) + "\\b").matcher(text).find()) {
                    throw state.violation("statement on " + table + " has an unrecognized shape");
                }
            }
        }
    }

    private static void requirePredicate(String text, String alias, String table, TenantScopeState state) {
        Optional<String> key = state.requiredKeyColumn(table);
        String quoted = Pattern.quote(alias);
        Pattern predicate = key
            .map(column -> Pattern.compile("\\b" + quoted + "\\." + Pattern.quote(column) + "\\s*=\\s*\\?"))
            .orElseGet(() -> Pattern.compile("\\b" + quoted + "\\.tenant_id\\s+in\\s*\\("));
        if (!predicate.matcher(text).find()) {
            throw state.violation(key.isPresent()
                ? "read of " + table + " does not match the policy key"
                : "statement on " + table + " lacks the tenant predicate");
        }
    }

    private static boolean isTarget(String kind, String keyword) {
        return ("update".equals(kind) && "update".equals(keyword))
            || ("delete".equals(kind) && "from".equals(keyword))
            || ("insert".equals(kind) && "into".equals(keyword));
    }

    private static String firstKeyword(String text) {
        Matcher matcher = Pattern.compile("^\\s*([a-z]+)").matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static List<Reference> references(String text) {
        List<Reference> references = new ArrayList<>();
        Matcher matcher = TABLE_REFERENCE.matcher(text);
        while (matcher.find()) {
            String alias = matcher.group(3);
            references.add(new Reference(
                matcher.group(1), matcher.group(2), alias == null || NOT_ALIASES.contains(alias) ? null : alias));
        }
        return references;
    }

    private static String signature(TenantScopeState state) {
        Map<String, String> parameters = state.safeParameters();
        return parameters.get("owner") + '|' + parameters.get("scope") + '|' + (state.tenants().isEmpty() ? '0' : '1');
    }

    private static String fingerprint(String sql) {
        String compact = sql.replaceAll("\\s+", " ").trim();
        return compact.length() > 160 ? compact.substring(0, 160) + "..." : compact;
    }

    private record Reference(String keyword, String table, String alias) {
    }

    /** The platform-tenant guard's rules, for statements outside owner units of work. */
    static final class LegacyRules {

        private static final Set<String> WHITELIST = Set.of(
            "sys_tenant", "sys_identity", "sys_platform_admin", "sys_platform_config", "sys_installation_quota",
            "sys_platform_audit_log", "auth_refresh_token", "flyway_schema_history", "hibernate_sequence",
            "hibernate_sequences");
        private static final Pattern SELECT_TABLE = Pattern.compile(
            "\\bFROM\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)", Pattern.CASE_INSENSITIVE);
        private static final Pattern UPDATE_TABLE = Pattern.compile(
            "\\bUPDATE\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)", Pattern.CASE_INSENSITIVE);
        private static final Pattern DELETE_TABLE = Pattern.compile(
            "\\bDELETE\\s+FROM\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)", Pattern.CASE_INSENSITIVE);
        private static final Pattern TENANT_CONDITION = Pattern.compile(
            "\\bWHERE\\b[^;]*\\btenant_id\\s*=", Pattern.CASE_INSENSITIVE);

        private LegacyRules() {
        }

        static void check(String sql) {
            String statement = sql.trim();
            String upper = statement.toUpperCase(Locale.ROOT);
            Pattern tablePattern;
            if (upper.startsWith("SELECT")) {
                tablePattern = SELECT_TABLE;
            } else if (upper.startsWith("UPDATE")) {
                tablePattern = UPDATE_TABLE;
            } else if (upper.startsWith("DELETE")) {
                tablePattern = DELETE_TABLE;
            } else {
                return;
            }
            Matcher matcher = tablePattern.matcher(statement);
            if (!matcher.find()) {
                return;
            }
            String table = OwnerTenantTables.normalize(matcher.group(1));
            if (WHITELIST.contains(table) || !requiresTenantFilter(table)
                    || TENANT_CONDITION.matcher(statement).find()) {
                return;
            }
            security.warn("event=tenant.isolation.violation owner=legacy table={} sql={}", table, fingerprint(sql));
            throw new PersistenceCapabilityException(
                PersistenceCapabilityException.Code.TENANT_SCOPE_VIOLATION,
                Map.of("owner", "legacy"),
                new IllegalStateException("statement on " + table + " lacks a tenant_id condition"));
        }

        private static boolean requiresTenantFilter(String table) {
            if (table.startsWith("biz_") || "sys_tenant_member".equals(table) || "sys_tenant_principal".equals(table)) {
                return true;
            }
            return !table.startsWith("sys_") && !table.startsWith("flyway_") && !table.startsWith("hibernate_");
        }
    }
}
