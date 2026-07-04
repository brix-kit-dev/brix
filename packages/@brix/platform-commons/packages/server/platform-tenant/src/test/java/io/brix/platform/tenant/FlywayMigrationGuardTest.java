package io.brix.platform.tenant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class FlywayMigrationGuardTest {

    private static final Path MIGRATION_DIR = Path.of(
            "src", "main", "resources", "db", "migration", "platform-tenant");

        private static final Pattern WEAK_PROFILE_REFERENCE_PATTERN = Pattern.compile("\\b(ref_type|ref_id)\\b");

        private static final Set<String> LEGACY_PROFILE_CLEANUP_FILES = Set.of(
            "V020__phase1_data_model_convergence.sql");

    @Test
    void newMigrationsMustNotIntroduceWeakProfileReferences() throws IOException {
        List<String> violations = scanMigrations(WEAK_PROFILE_REFERENCE_PATTERN, LEGACY_PROFILE_CLEANUP_FILES);

        assertTrue(violations.isEmpty(), () -> "Weak polymorphic profile references are forbidden in Flyway "
                + "migrations by v3.1.3 §5.3 and Phase 0. Violations: " + violations);
    }

    @Test
    void newMigrationsMustNotSilentlyIgnoreConflicts() throws IOException {
        List<String> violations = scanMigrations("on conflict do nothing", Set.of());

        assertTrue(violations.isEmpty(), () -> "Silent migration conflict handling is forbidden by v3.1.3 §5.7 "
                + "and Phase 0. Violations: " + violations);
    }

    private static List<String> scanMigrations(String token, Set<String> legacyFiles) throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.list(MIGRATION_DIR)) {
            for (Path path : paths.filter(FlywayMigrationGuardTest::isSqlFile).toList()) {
                if (legacyFiles.contains(path.getFileName().toString())) {
                    continue;
                }
                String normalized = Files.readString(path, StandardCharsets.UTF_8)
                        .replaceAll("\\s+", " ")
                        .toLowerCase(Locale.ROOT);
                if (normalized.contains(token)) {
                    violations.add(path.getFileName().toString());
                }
            }
        }
        return violations;
    }

    private static List<String> scanMigrations(Pattern pattern, Set<String> legacyFiles) throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.list(MIGRATION_DIR)) {
            for (Path path : paths.filter(FlywayMigrationGuardTest::isSqlFile).toList()) {
                if (legacyFiles.contains(path.getFileName().toString())) {
                    continue;
                }
                String normalized = Files.readString(path, StandardCharsets.UTF_8)
                        .toLowerCase(Locale.ROOT);
                if (pattern.matcher(normalized).find()) {
                    violations.add(path.getFileName().toString());
                }
            }
        }
        return violations;
    }

    private static boolean isSqlFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".sql");
    }
}
