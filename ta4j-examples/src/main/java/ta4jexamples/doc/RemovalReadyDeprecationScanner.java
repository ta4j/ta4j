/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.doc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans ta4j source trees for {@code @Deprecated(..., forRemoval = true)}
 * symbols scheduled for removal in the current snapshot target and emits JSON +
 * Markdown reports for the release workflow.
 *
 * <p>
 * Typical usage:
 * {@code mvn -pl ta4j-examples -am compile exec:java -Dexec.mainClass=ta4jexamples.doc.RemovalReadyDeprecationScanner -Dexec.args="--output-json build/report.json --output-md build/report.md"}
 * </p>
 */
public final class RemovalReadyDeprecationScanner {

    private static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern VERSION_RE = Pattern.compile("<version>\\s*([^<]+?)\\s*</version>");
    private static final Pattern DEPRECATED_ANNOTATION_RE = Pattern
            .compile("@Deprecated\\s*\\(([^)]*forRemoval\\s*=\\s*true[^)]*)\\)", Pattern.DOTALL);
    private static final Pattern[] REMOVAL_VERSION_PATTERNS = {
            Pattern.compile("scheduled\\s+for\\s+removal\\s+in\\s+(\\d+\\.\\d+\\.\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("removal\\s+in\\s+(\\d+\\.\\d+\\.\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("DeprecationNotifier\\.warnOnce\\([^;]*?\"(\\d+\\.\\d+\\.\\d+)\"\\s*\\)", Pattern.DOTALL) };
    private static final Pattern CLASS_RE = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern INTERFACE_RE = Pattern.compile("\\binterface\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern ENUM_RE = Pattern.compile("\\benum\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern RECORD_RE = Pattern.compile("\\brecord\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern FIELD_RE = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*(?:=|;)");
    private static final Pattern CALLABLE_RE = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Set<String> SKIP_PARTS = Set.of(".agents", ".git", ".idea", "target");

    private RemovalReadyDeprecationScanner() {
    }

    /**
     * Launches the CLI scanner.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream stdout, PrintStream stderr) {
        ScanOptions options;
        try {
            options = parseArgs(args);
        } catch (IllegalArgumentException error) {
            stderr.println("Error: " + error.getMessage());
            stderr.println(usage());
            return 1;
        }

        try {
            DeprecationReport report = generateReport(options.repoRoot(), options.pomFile());
            writeOutput(options.outputJson(), PRETTY_GSON.toJson(report));
            writeOutput(options.outputMarkdown(), report.summaryMarkdown());
            stdout.println(COMPACT_GSON.toJson(
                    Map.of("snapshotVersion", report.snapshotVersion(), "issuePlanCount", report.issuePlanCount())));
            return 0;
        } catch (IOException | IllegalArgumentException error) {
            stderr.println("Error: " + error.getMessage());
            return 1;
        }
    }

    private static DeprecationReport generateReport(Path repoRoot, Path pomFile) throws IOException {
        SnapshotVersion snapshotVersion = readSnapshotVersion(pomFile);
        List<IssuePlan> issuePlans = new ArrayList<>();

        for (Path javaFile : iterJavaFiles(repoRoot)) {
            String text = Files.readString(javaFile, StandardCharsets.UTF_8);
            if (!text.contains("forRemoval = true") && !text.contains("forRemoval=true")) {
                continue;
            }

            Path relativePath = repoRoot.relativize(javaFile);
            List<SymbolFinding> symbols = findSymbols(text, javaFile.getFileName().toString().replace(".java", ""),
                    snapshotVersion.removalVersion());
            if (symbols.isEmpty()) {
                continue;
            }

            String relativePathText = relativePath.toString().replace('\\', '/');
            issuePlans.add(buildIssuePlan(snapshotVersion.snapshotVersion(), snapshotVersion.removalVersion(),
                    relativePathText, moduleName(relativePath), symbols));
        }

        int findingCount = issuePlans.stream().mapToInt(IssuePlan::symbolCount).sum();
        DeprecationReport partialReport = new DeprecationReport(OffsetDateTime.now(ZoneOffset.UTC).toString(),
                repoRoot.toString(), snapshotVersion.snapshotVersion(), snapshotVersion.removalVersion(), findingCount,
                issuePlans.size(), List.copyOf(issuePlans), "");
        return new DeprecationReport(partialReport.generatedAt(), partialReport.repoRoot(),
                partialReport.snapshotVersion(), partialReport.removalVersion(), partialReport.findingCount(),
                partialReport.issuePlanCount(), partialReport.issuePlans(), renderMarkdown(partialReport));
    }

    private static List<SymbolFinding> findSymbols(String text, String fileStem, String targetRemovalVersion) {
        List<String> lines = text.lines().toList();
        List<AnnotationRange> matches = deprecatedAnnotations(text);
        List<CandidateSymbol> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (int index = 0; index < matches.size(); index++) {
            AnnotationRange match = matches.get(index);
            Set<String> explicitVersions = removalVersions(symbolContext(text, matches, index, match));
            int annotationLine = lineNumberAtOffset(text, match.end());
            int declarationLine = annotationLine;
            List<String> declarationLines = new ArrayList<>();

            for (int lineIndex = annotationLine; lineIndex < lines.size(); lineIndex++) {
                declarationLine++;
                String stripped = lines.get(lineIndex).trim();
                if (stripped.isEmpty() || stripped.startsWith("@") || stripped.startsWith("*")
                        || stripped.startsWith("/*")) {
                    continue;
                }
                declarationLines.add(stripped);
                if (stripped.contains("{") || stripped.contains(";")) {
                    break;
                }
            }

            String declaration = String.join(" ", declarationLines);
            ParsedSymbol parsedSymbol = parseSymbol(declaration, fileStem);
            if (parsedSymbol == null) {
                continue;
            }

            String candidateKey = parsedSymbol.name() + "|" + parsedSymbol.kind() + "|" + declarationLine;
            if (!seen.add(candidateKey)) {
                continue;
            }

            candidates.add(new CandidateSymbol(parsedSymbol.name(), parsedSymbol.kind(), declarationLine,
                    Set.copyOf(explicitVersions)));
        }

        List<SymbolFinding> symbols = new ArrayList<>();
        Set<String> currentTypeVersions = Set.of();
        for (CandidateSymbol candidate : candidates) {
            Set<String> explicitVersions = candidate.explicitRemovalVersions();
            Set<String> candidateVersions;
            if (isTypeKind(candidate.kind())) {
                currentTypeVersions = explicitVersions;
                candidateVersions = explicitVersions;
            } else {
                candidateVersions = explicitVersions.isEmpty() ? currentTypeVersions : explicitVersions;
            }

            if (candidateVersions.contains(targetRemovalVersion)) {
                symbols.add(new SymbolFinding(candidate.name(), candidate.kind(), candidate.line()));
            }
        }

        return symbols;
    }

    private static String renderMarkdown(DeprecationReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("# Removal-ready deprecations for " + report.snapshotVersion());
        lines.add("");
        lines.add("- Planned issues: " + report.issuePlanCount());
        lines.add("- Removal-ready symbols: " + report.findingCount());
        lines.add("");

        if (report.issuePlans().isEmpty()) {
            lines.add("No removal-ready deprecations matched the current snapshot version.");
            return String.join("\n", lines);
        }

        for (IssuePlan plan : report.issuePlans()) {
            lines.add("## " + plan.issueTitle());
            lines.add("");
            lines.add("- Module: `" + plan.module() + "`");
            lines.add("- File: `" + plan.filePath() + "`");
            lines.add("- Symbols:");
            for (SymbolFinding symbol : plan.symbols()) {
                lines.add("  - `" + symbol.name() + "` (" + symbol.kind() + ", line " + symbol.line() + ")");
            }
            lines.add("");
        }
        return String.join("\n", lines);
    }

    private static ScanOptions parseArgs(String[] args) {
        Path repoRoot = Path.of(".").toAbsolutePath().normalize();
        String pomFile = "pom.xml";
        String outputJson = null;
        String outputMarkdown = null;

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if ("--repo-root".equals(argument)) {
                repoRoot = Path.of(requireValue(argument, args, ++index)).toAbsolutePath().normalize();
            } else if ("--pom-file".equals(argument)) {
                pomFile = requireValue(argument, args, ++index);
            } else if ("--output-json".equals(argument)) {
                outputJson = requireValue(argument, args, ++index);
            } else if ("--output-md".equals(argument)) {
                outputMarkdown = requireValue(argument, args, ++index);
            } else {
                throw new IllegalArgumentException("unknown argument " + argument);
            }
        }

        if (outputJson == null || outputMarkdown == null) {
            throw new IllegalArgumentException("--output-json and --output-md are required");
        }

        Path resolvedPom = Path.of(pomFile);
        if (!resolvedPom.isAbsolute()) {
            resolvedPom = repoRoot.resolve(resolvedPom).normalize();
        }
        return new ScanOptions(repoRoot, resolvedPom, Path.of(outputJson), Path.of(outputMarkdown));
    }

    private static String requireValue(String option, String[] args, int valueIndex) {
        if (valueIndex >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[valueIndex];
    }

    private static SnapshotVersion readSnapshotVersion(Path pomFile) throws IOException {
        String text = Files.readString(pomFile, StandardCharsets.UTF_8);
        Matcher matcher = VERSION_RE.matcher(text);
        if (!matcher.find()) {
            throw new IllegalArgumentException("unable to find a project version in " + pomFile);
        }

        String snapshotVersion = matcher.group(1).trim();
        if (!snapshotVersion.endsWith("-SNAPSHOT")) {
            throw new IllegalArgumentException(
                    "expected a SNAPSHOT version in " + pomFile + ", found '" + snapshotVersion + "'");
        }
        return new SnapshotVersion(snapshotVersion, snapshotVersion.substring(0, snapshotVersion.length() - 9));
    }

    private static List<Path> iterJavaFiles(Path repoRoot) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(repoRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> hasMainSourceMarker(repoRoot.relativize(path)))
                    .filter(path -> !containsSkipPart(repoRoot.relativize(path)))
                    .forEach(javaFiles::add);
        }
        javaFiles.sort(Comparator.naturalOrder());
        return javaFiles;
    }

    private static boolean containsSkipPart(Path relativePath) {
        for (Path part : relativePath) {
            if (SKIP_PARTS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMainSourceMarker(Path relativePath) {
        List<String> parts = pathParts(relativePath);
        for (int index = 0; index <= parts.size() - 3; index++) {
            if ("src".equals(parts.get(index)) && "main".equals(parts.get(index + 1))
                    && "java".equals(parts.get(index + 2))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> removalVersions(String text) {
        Set<String> versions = new LinkedHashSet<>();
        for (Pattern pattern : REMOVAL_VERSION_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                versions.add(matcher.group(1));
            }
        }
        return versions;
    }

    private static ParsedSymbol parseSymbol(String declaration, String fileStem) {
        String compact = declaration.trim().replaceAll("\\s+", " ");
        if (compact.isEmpty()) {
            return null;
        }

        Matcher classMatcher = CLASS_RE.matcher(compact);
        if (classMatcher.find()) {
            return new ParsedSymbol(classMatcher.group(1), "class");
        }

        Matcher interfaceMatcher = INTERFACE_RE.matcher(compact);
        if (interfaceMatcher.find()) {
            return new ParsedSymbol(interfaceMatcher.group(1), "interface");
        }

        Matcher enumMatcher = ENUM_RE.matcher(compact);
        if (enumMatcher.find()) {
            return new ParsedSymbol(enumMatcher.group(1), "enum");
        }

        Matcher recordMatcher = RECORD_RE.matcher(compact);
        if (recordMatcher.find()) {
            return new ParsedSymbol(recordMatcher.group(1), "record");
        }

        Matcher fieldMatcher = FIELD_RE.matcher(compact);
        if (fieldMatcher.find()) {
            return new ParsedSymbol(fieldMatcher.group(1), "field");
        }

        Matcher callableMatcher = CALLABLE_RE.matcher(compact);
        if (callableMatcher.find()) {
            String name = callableMatcher.group(1);
            String kind = name.equals(fileStem) ? "constructor" : "method";
            return new ParsedSymbol(name, kind);
        }

        return null;
    }

    private static boolean isTypeKind(String kind) {
        return "class".equals(kind) || "interface".equals(kind) || "enum".equals(kind) || "record".equals(kind);
    }

    private static String moduleName(Path relativePath) {
        List<String> parts = pathParts(relativePath);
        for (int index = 0; index <= parts.size() - 4; index++) {
            if ("src".equals(parts.get(index + 1)) && "main".equals(parts.get(index + 2))
                    && "java".equals(parts.get(index + 3))) {
                return parts.get(index);
            }
        }
        return parts.isEmpty() ? relativePath.toString() : parts.get(0);
    }

    private static String symbolContext(String text, List<AnnotationRange> matches, int index, AnnotationRange match) {
        int start = javadocStart(text, match.start());
        if (start == -1) {
            start = match.start();
        }

        int end = text.length();
        if (index + 1 < matches.size()) {
            int nextAnnotationStart = matches.get(index + 1).start();
            int nextJavadocStart = javadocStart(text, nextAnnotationStart);
            end = nextJavadocStart != -1 ? nextJavadocStart : nextAnnotationStart;
        }
        return text.substring(start, end);
    }

    private static int javadocStart(String text, int annotationStart) {
        int javadocOpen = text.lastIndexOf("/**", annotationStart);
        if (javadocOpen == -1) {
            return -1;
        }

        int javadocClose = text.indexOf("*/", javadocOpen);
        if (javadocClose == -1 || javadocClose >= annotationStart) {
            return -1;
        }

        String gap = text.substring(javadocClose + 2, annotationStart);
        return gap.isBlank() ? javadocOpen : -1;
    }

    private static List<AnnotationRange> deprecatedAnnotations(String text) {
        Matcher matcher = DEPRECATED_ANNOTATION_RE.matcher(text);
        List<AnnotationRange> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new AnnotationRange(matcher.start(), matcher.end()));
        }
        return matches;
    }

    private static int lineNumberAtOffset(String text, int offset) {
        int count = 1;
        for (int index = 0; index < offset; index++) {
            if (text.charAt(index) == '\n') {
                count++;
            }
        }
        return count;
    }

    private static IssuePlan buildIssuePlan(String snapshotVersion, String removalVersion, String relativePath,
            String module, List<SymbolFinding> symbols) {
        int symbolCount = symbols.size();
        String issueTitle = symbolCount == 1
                ? "Remove " + removalVersion + "-ready deprecation: " + symbols.get(0).name()
                : "Remove " + removalVersion + "-ready deprecations in " + Path.of(relativePath).getFileName();
        String dedupeKey = removalVersion + ":" + relativePath;
        String issueMarker = "<!-- ta4j:deprecation-removal dedupe=" + dedupeKey + " -->";
        String symbolLines = symbols.stream()
                .map(symbol -> "- `" + symbol.name() + "` (" + symbol.kind() + ", line " + symbol.line() + ")")
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        String issueBody = String.join("\n",
                "Snapshot bump automation detected removal-ready deprecations for `" + snapshotVersion + "`.", "",
                "Module: `" + module + "`", "File: `" + relativePath + "`", "", "Symbols:", symbolLines, "",
                "Acceptance checks:",
                "- remove or migrate the compatibility symbols scheduled for removal in `" + removalVersion + "`",
                "- update callers, tests, and documentation as needed", "- keep the full build green", "", issueMarker);
        return new IssuePlan(dedupeKey, issueMarker, issueTitle, issueBody, module, relativePath, symbolCount,
                List.copyOf(symbols));
    }

    private static void writeOutput(Path path, String content) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static List<String> pathParts(Path path) {
        List<String> parts = new ArrayList<>();
        for (Path part : path) {
            parts.add(part.toString());
        }
        return parts;
    }

    private static String usage() {
        return "Usage: --output-json <path> --output-md <path> [--repo-root <path>] [--pom-file <path>]";
    }

    private record ScanOptions(Path repoRoot, Path pomFile, Path outputJson, Path outputMarkdown) {
    }

    private record SnapshotVersion(String snapshotVersion, String removalVersion) {
    }

    private record ParsedSymbol(String name, String kind) {
    }

    private record AnnotationRange(int start, int end) {
    }

    private record CandidateSymbol(String name, String kind, int line, Set<String> explicitRemovalVersions) {
    }

    private record SymbolFinding(String name, String kind, int line) {
    }

    private record IssuePlan(String dedupeKey, String issueMarker, String issueTitle, String issueBody, String module,
            String filePath, int symbolCount, List<SymbolFinding> symbols) {
    }

    private record DeprecationReport(String generatedAt, String repoRoot, String snapshotVersion, String removalVersion,
            int findingCount, int issuePlanCount, List<IssuePlan> issuePlans, String summaryMarkdown) {
    }
}
