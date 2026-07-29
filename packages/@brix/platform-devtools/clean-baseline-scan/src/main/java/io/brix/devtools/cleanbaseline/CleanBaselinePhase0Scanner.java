/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.devtools.cleanbaseline;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans worktrees for v3.0.10 clean-baseline Phase 0 legacy markers and
 * classifies each finding for migration triage.
 */
public final class CleanBaselinePhase0Scanner {

    private static final String CLEAN_INITIALIZATION_SCOPE =
        "pre-release clean initialization: not online, no shared environment, no retained data";
    private static final String TARGET_HOST_COMPOSITION =
        "Runtime Shell 3.0.10 + platform-admin + platform-tenant + app-tenant template";
    private static final Set<String> SCANNABLE_EXTENSIONS = Set.of(
        ".java", ".kt", ".ts", ".tsx", ".js", ".jsx", ".cjs", ".mjs",
        ".json", ".yaml", ".yml", ".xml", ".md", ".sql", ".properties",
        ".toml", ".txt", ".ejs");
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
        ".git", "target", "node_modules", "dist", "build", ".idea", ".vscode",
        ".pnpm-store", "coverage");
    private static final List<LegacyRule> RULES = List.of(
        LegacyRule.review("legacy-version-3.0.9", "3.0.9", Pattern.compile("3\\.0\\.9")),
        LegacyRule.review("legacy-runtime-context", "RuntimeContext", Pattern.compile("\\bRuntimeContext\\b")),
        LegacyRule.review("legacy-abstract-module", "AbstractModule", Pattern.compile("\\bAbstractModule\\b")),
        LegacyRule.blocking("spring-application-runner", "ApplicationRunner", Pattern.compile("\\bApplicationRunner\\b")),
        LegacyRule.blocking("spring-command-line-runner", "CommandLineRunner", Pattern.compile("\\bCommandLineRunner\\b")),
        LegacyRule.blocking("legacy-owner-identity-id", "ownerIdentityId", Pattern.compile("\\bownerIdentityId\\b")),
        LegacyRule.blocking("legacy-tenant-api-v1", "/api/v1/tenants", Pattern.compile("/api/v1/tenants")),
        LegacyRule.blocking(
            "legacy-kafka-outbox-adapter",
            "infra-adapter-kafka-outbox",
            Pattern.compile("\\binfra-adapter-kafka-outbox\\b")));

    private CleanBaselinePhase0Scanner() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Runs the scanner from the command line.
     *
     * @param args scanner arguments
     * @throws IOException when a configured root cannot be scanned
     */
    public static void main(String[] args) throws IOException {
        ScanOptions options = ScanOptions.parse(args);
        if (options.help()) {
            printUsage(System.out);
            return;
        }
        ScanReport report = scan(options.roots());
        if (options.format() == OutputFormat.JSON) {
            System.out.println(toJson(report));
        } else {
            printText(report, System.out);
        }
        if (options.failOnBlocking() && report.count(FindingDisposition.BLOCKING) > 0) {
            System.exit(2);
        }
    }

    /**
     * Scans all configured repository roots.
     *
     * @param roots repository roots
     * @return immutable scan report
     * @throws IOException when a root cannot be traversed
     */
    public static ScanReport scan(List<Path> roots) throws IOException {
        List<Path> normalizedRoots = normalizeRoots(roots);
        List<LegacyFinding> findings = new ArrayList<>();
        for (Path root : normalizedRoots) {
            Files.walkFileTree(root, new SourceVisitor(root, findings));
        }
        findings.sort(Comparator
            .comparing((LegacyFinding finding) -> finding.repositoryRoot().toString())
            .thenComparing(finding -> finding.relativePath().toString())
            .thenComparingInt(LegacyFinding::line)
            .thenComparing(finding -> finding.rule().id()));
        return new ScanReport(
            Instant.now(),
            CLEAN_INITIALIZATION_SCOPE,
            TARGET_HOST_COMPOSITION,
            normalizedRoots,
            List.copyOf(findings));
    }

    private static List<Path> normalizeRoots(List<Path> roots) {
        List<Path> effectiveRoots = roots.isEmpty() ? defaultRoots() : roots;
        Set<Path> unique = new HashSet<>();
        List<Path> normalized = new ArrayList<>();
        for (Path root : effectiveRoots) {
            Path absolute = root.toAbsolutePath().normalize();
            if (Files.isDirectory(absolute) && unique.add(absolute)) {
                normalized.add(absolute);
            }
        }
        return List.copyOf(normalized);
    }

    private static List<Path> defaultRoots() {
        Path current = Path.of(".").toAbsolutePath().normalize();
        Path enterprise = current.resolveSibling("brix-enterprise").normalize();
        List<Path> roots = new ArrayList<>();
        roots.add(current);
        if (Files.isDirectory(enterprise)) {
            roots.add(enterprise);
        }
        return roots;
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: java io.brix.devtools.cleanbaseline.CleanBaselinePhase0Scanner [options]");
        out.println("Options:");
        out.println("  --root <path>          Repository root to scan. May be repeated.");
        out.println("  --format text|json     Output format. Defaults to text.");
        out.println("  --fail-on-blocking     Exit with code 2 when BLOCKING findings exist.");
        out.println("  --help                 Show this help.");
    }

    private static void printText(ScanReport report, PrintStream out) {
        out.println("Brix Clean Baseline Phase 0 Scan");
        out.println("generatedAt: " + report.generatedAt());
        out.println("cleanInitializationScope: " + report.cleanInitializationScope());
        out.println("targetHostComposition: " + report.targetHostComposition());
        out.println("roots:");
        for (Path root : report.roots()) {
            out.println("  - " + root);
        }
        out.println("summary:");
        for (FindingDisposition disposition : FindingDisposition.values()) {
            out.println("  " + disposition + ": " + report.count(disposition));
        }
        out.println("findings:");
        for (LegacyFinding finding : report.findings()) {
            out.println("  - " + finding.disposition() + " "
                + finding.relativePath()
                + ":" + finding.line()
                + " [" + finding.rule().id() + "] "
                + finding.reason());
            out.println("    text: " + finding.lineText().trim());
        }
    }

    private static String toJson(ScanReport report) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        appendJsonField(json, "generatedAt", report.generatedAt().toString()).append(",");
        appendJsonField(json, "cleanInitializationScope", report.cleanInitializationScope()).append(",");
        appendJsonField(json, "targetHostComposition", report.targetHostComposition()).append(",");
        json.append("\"roots\":[");
        for (int i = 0; i < report.roots().size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("\"").append(escapeJson(report.roots().get(i).toString())).append("\"");
        }
        json.append("],");
        json.append("\"summary\":{");
        int summaryIndex = 0;
        for (FindingDisposition disposition : FindingDisposition.values()) {
            if (summaryIndex++ > 0) {
                json.append(",");
            }
            json.append("\"").append(disposition).append("\":").append(report.count(disposition));
        }
        json.append("},");
        json.append("\"findings\":[");
        for (int i = 0; i < report.findings().size(); i++) {
            LegacyFinding finding = report.findings().get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{");
            appendJsonField(json, "root", finding.repositoryRoot().toString()).append(",");
            appendJsonField(json, "path", finding.relativePath().toString()).append(",");
            json.append("\"line\":").append(finding.line()).append(",");
            appendJsonField(json, "ruleId", finding.rule().id()).append(",");
            appendJsonField(json, "token", finding.rule().token()).append(",");
            appendJsonField(json, "disposition", finding.disposition().name()).append(",");
            appendJsonField(json, "reason", finding.reason()).append(",");
            appendJsonField(json, "lineText", finding.lineText().trim());
            json.append("}");
        }
        json.append("]}");
        return json.toString();
    }

    private static StringBuilder appendJsonField(StringBuilder json, String key, String value) {
        return json.append("\"").append(key).append("\":\"").append(escapeJson(value)).append("\"");
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static boolean isScannable(Path file) {
        String name = file.getFileName().toString();
        if (name.equals("pom.xml") || name.equals("package.json")) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return SCANNABLE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static FindingDisposition classify(Path relativePath, LegacyRule rule) {
        if (isDocumentation(relativePath)) {
            return FindingDisposition.RETAIN;
        }
        if (isScannerImplementation(relativePath)) {
            return FindingDisposition.RETAIN;
        }
        if (isFrontendRuntimeContext(relativePath, rule)) {
            return FindingDisposition.RETAIN;
        }
        if (isRuntimeSdkCompatibilitySource(relativePath, rule)) {
            return FindingDisposition.RETAIN;
        }
        if (isTestSource(relativePath)) {
            return FindingDisposition.REVIEW;
        }
        if (isTemplateSource(relativePath)) {
            return FindingDisposition.BLOCKING;
        }
        return rule.defaultDisposition();
    }

    private static String reason(Path relativePath, LegacyRule rule, FindingDisposition disposition) {
        if (isDocumentation(relativePath)) {
            return "historical or guiding documentation reference retained as evidence";
        }
        if (isScannerImplementation(relativePath)) {
            return "clean-baseline scanner rule declaration retained as governance code";
        }
        if (isFrontendRuntimeContext(relativePath, rule)) {
            return "frontend runtime context naming is retained under the active frontend runtime boundary";
        }
        if (isRuntimeSdkCompatibilitySource(relativePath, rule)) {
            return "runtime-sdk-api compatibility surface may retain deprecated symbols during the migration window";
        }
        if (isTestSource(relativePath)) {
            return "test or contract fixture requires owner review before deletion or rewrite";
        }
        if (isTemplateSource(relativePath)) {
            return "active scaffolding template must not generate 3.0.9 or legacy Runtime Shell code";
        }
        if (disposition == FindingDisposition.BLOCKING) {
            return "active code or configuration is on the clean-baseline blocking path";
        }
        return "legacy reference requires migration-owner review";
    }

    private static boolean isDocumentation(Path relativePath) {
        String path = normalize(relativePath);
        return path.startsWith("docs/")
            || path.startsWith("docs-dev/")
            || path.startsWith("website/docs/")
            || path.endsWith("/README.md")
            || path.endsWith("/CHANGELOG.md")
            || path.equals("README.md")
            || path.equals("CHANGELOG.md")
            || path.endsWith("-status.md");
    }

    private static boolean isScannerImplementation(Path relativePath) {
        String path = normalize(relativePath);
        return path.contains("packages/@brix/platform-devtools/clean-baseline-scan/src/main/");
    }

    private static boolean isFrontendRuntimeContext(Path relativePath, LegacyRule rule) {
        if (!rule.id().equals("legacy-runtime-context")) {
            return false;
        }
        String path = normalize(relativePath);
        return path.endsWith(".ts")
            || path.endsWith(".tsx")
            || path.contains("runtime-sdk-api-web/")
            || path.contains("shared-runtime")
            || path.contains("-ui-web/")
            || path.contains("-ui-mobile/")
            || path.contains("host-shell-standalone-web/");
    }

    private static boolean isRuntimeSdkCompatibilitySource(Path relativePath, LegacyRule rule) {
        String path = normalize(relativePath);
        return (rule.id().equals("legacy-runtime-context") || rule.id().equals("legacy-abstract-module"))
            && path.contains("packages/@brix/runtime-sdk/runtime-sdk-api/");
    }

    private static boolean isTestSource(Path relativePath) {
        String path = normalize(relativePath);
        return path.contains("/src/test/")
            || path.contains("/__tests__/")
            || path.contains("/testfixtures/")
            || path.contains("host-contract-tests/");
    }

    private static boolean isTemplateSource(Path relativePath) {
        return normalize(relativePath).contains("packages/@brix/platform-devtools/@brix/create-brix/templates/");
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    /**
     * Supported finding dispositions.
     */
    public enum FindingDisposition {
        RETAIN,
        REVIEW,
        BLOCKING
    }

    /**
     * Supported output formats.
     */
    public enum OutputFormat {
        TEXT,
        JSON
    }

    /**
     * Legacy search rule.
     *
     * @param id stable rule identifier
     * @param token displayed legacy marker
     * @param pattern source pattern
     * @param defaultDisposition default disposition outside path allowlists
     */
    public record LegacyRule(
        String id,
        String token,
        Pattern pattern,
        FindingDisposition defaultDisposition) {

        private static LegacyRule review(String id, String token, Pattern pattern) {
            return new LegacyRule(id, token, pattern, FindingDisposition.REVIEW);
        }

        private static LegacyRule blocking(String id, String token, Pattern pattern) {
            return new LegacyRule(id, token, pattern, FindingDisposition.BLOCKING);
        }
    }

    /**
     * One legacy marker occurrence.
     *
     * @param repositoryRoot root where the finding was discovered
     * @param relativePath file path relative to the root
     * @param line one-based line number
     * @param rule matched rule
     * @param disposition migration disposition
     * @param reason disposition reason
     * @param lineText source line containing the match
     */
    public record LegacyFinding(
        Path repositoryRoot,
        Path relativePath,
        int line,
        LegacyRule rule,
        FindingDisposition disposition,
        String reason,
        String lineText) {
    }

    /**
     * Complete scan report.
     *
     * @param generatedAt report generation time
     * @param cleanInitializationScope frozen clean initialization scope
     * @param targetHostComposition target host composition for this migration track
     * @param roots scanned roots
     * @param findings classified findings
     */
    public record ScanReport(
        Instant generatedAt,
        String cleanInitializationScope,
        String targetHostComposition,
        List<Path> roots,
        List<LegacyFinding> findings) {

        /**
         * Counts findings by disposition.
         *
         * @param disposition disposition to count
         * @return finding count
         */
        public long count(FindingDisposition disposition) {
            return findings.stream()
                .filter(finding -> finding.disposition() == disposition)
                .count();
        }

        /**
         * Returns summary counts for all dispositions.
         *
         * @return summary count map
         */
        public Map<FindingDisposition, Long> summary() {
            Map<FindingDisposition, Long> summary = new EnumMap<>(FindingDisposition.class);
            for (FindingDisposition disposition : FindingDisposition.values()) {
                summary.put(disposition, count(disposition));
            }
            return Map.copyOf(summary);
        }
    }

    /**
     * Parsed command line options.
     *
     * @param roots roots to scan
     * @param format output format
     * @param failOnBlocking whether blocking findings should fail the process
     * @param help whether help was requested
     */
    public record ScanOptions(
        List<Path> roots,
        OutputFormat format,
        boolean failOnBlocking,
        boolean help) {

        private static ScanOptions parse(String[] args) {
            Objects.requireNonNull(args, "args");
            List<Path> roots = new ArrayList<>();
            OutputFormat format = OutputFormat.TEXT;
            boolean failOnBlocking = false;
            boolean help = false;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--root" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("--root requires a path");
                        }
                        roots.add(Path.of(args[++i]));
                    }
                    case "--format" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("--format requires text or json");
                        }
                        format = OutputFormat.valueOf(args[++i].toUpperCase(Locale.ROOT));
                    }
                    case "--fail-on-blocking" -> failOnBlocking = true;
                    case "--help", "-h" -> help = true;
                    default -> throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }
            return new ScanOptions(List.copyOf(roots), format, failOnBlocking, help);
        }
    }

    private static final class SourceVisitor extends SimpleFileVisitor<Path> {
        private final Path root;
        private final List<LegacyFinding> findings;

        private SourceVisitor(Path root, List<LegacyFinding> findings) {
            this.root = root;
            this.findings = findings;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (!dir.equals(root) && EXCLUDED_DIRECTORIES.contains(dir.getFileName().toString())) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (attrs.isRegularFile() && isScannable(file)) {
                scanFile(file);
            }
            return FileVisitResult.CONTINUE;
        }

        private void scanFile(Path file) throws IOException {
            Path relativePath = root.relativize(file);
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String[] lines = content.split("\\R", -1);
            for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                String line = lines[lineIndex];
                for (LegacyRule rule : RULES) {
                    Matcher matcher = rule.pattern().matcher(line);
                    if (matcher.find()) {
                        FindingDisposition disposition = classify(relativePath, rule);
                        findings.add(new LegacyFinding(
                            root,
                            relativePath,
                            lineIndex + 1,
                            rule,
                            disposition,
                            reason(relativePath, rule, disposition),
                            line));
                    }
                }
            }
        }
    }
}
