/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.doc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Scans ta4j source trees for {@code @Deprecated(..., forRemoval = true)}
 * symbols, reports release lifecycle state, and emits issue plans for release
 * automation.
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
    private static final Pattern SEMVER_RE = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    private static final Pattern DECLARATION_VERSION_RE = Pattern.compile(
            "(?:scheduled\\s+for\\s+removal\\s+in|removal\\s+in)\\s+(\\d+\\.\\d+\\.\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOTIFIER_RE = Pattern.compile(
            "DeprecationNotifier\\.warnOnce\\s*\\([^,]+,\\s*\"([^\"]+)\"(?:\\s*,\\s*\"(\\d+\\.\\d+\\.\\d+)\")?",
            Pattern.DOTALL);
    private static final Pattern JAVADOC_REPLACEMENT_RE = Pattern.compile("(?i)\\buse\\s+\\{@link\\s+([^}]+)\\}");
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
            DeprecationReport report = generateReport(options);
            writeOutput(options.outputJson(), PRETTY_GSON.toJson(report));
            writeOutput(options.outputMarkdown(), report.summaryMarkdown());
            stdout.println(COMPACT_GSON.toJson(Map.of("snapshotVersion", report.snapshotVersion(), "removalVersion",
                    report.removalVersion(), "issuePlanCount", report.issuePlanCount(), "dueFindingCount",
                    report.dueFindingCount(), "overdueFindingCount", report.overdueFindingCount(),
                    "unscheduledFindingCount", report.unscheduledFindingCount())));
            if (options.failOnDue() && report.blockingFindingCount() > 0) {
                stderr.println("Removal-ready deprecation gate failed: " + report.blockingFindingCount()
                        + " symbol(s) are due or overdue for " + report.removalVersion() + ".");
                return 2;
            }
            return 0;
        } catch (IOException | IllegalArgumentException error) {
            stderr.println("Error: " + error.getMessage());
            return 1;
        }
    }

    private static DeprecationReport generateReport(ScanOptions options) throws IOException {
        ProjectVersion projectVersion = readProjectVersion(options.pomFile());
        String targetRemovalVersion = options.targetRemovalVersion();
        if (targetRemovalVersion == null) {
            if (!projectVersion.version().endsWith("-SNAPSHOT")) {
                throw new IllegalArgumentException("expected a SNAPSHOT version in " + options.pomFile() + ", found '"
                        + projectVersion.version() + "'");
            }
            targetRemovalVersion = projectVersion.version().substring(0, projectVersion.version().length() - 9);
        }
        validateVersion(targetRemovalVersion, "--target-removal-version");

        List<SourceFinding> allFindings = scanSources(options.repoRoot());
        List<IssuePlan> issuePlans = new ArrayList<>();
        List<SymbolFinding> selectedSymbols = new ArrayList<>();
        List<SymbolFinding> unscheduledSymbols = new ArrayList<>();
        int dueCount = 0;
        int overdueCount = 0;
        int futureCount = 0;
        int unscheduledCount = 0;

        Map<String, List<SymbolFinding>> symbolsByIssue = new LinkedHashMap<>();
        for (SourceFinding finding : allFindings) {
            if (finding.removalVersion() == null) {
                unscheduledCount++;
                unscheduledSymbols.add(finding.toSymbolFinding());
                continue;
            }

            int versionComparison = compareVersions(finding.removalVersion(), targetRemovalVersion);
            if (versionComparison < 0) {
                overdueCount++;
            } else if (versionComparison == 0) {
                dueCount++;
            } else {
                futureCount++;
            }

            boolean selected = versionComparison == 0 || options.includeOverdue() && versionComparison < 0;
            if (selected) {
                SymbolFinding symbol = finding.toSymbolFinding(status(finding.removalVersion(), targetRemovalVersion));
                selectedSymbols.add(symbol);
                String key = finding.removalVersion() + "|" + finding.filePath();
                symbolsByIssue.computeIfAbsent(key, ignored -> new ArrayList<>()).add(symbol);
            }
        }

        for (Map.Entry<String, List<SymbolFinding>> entry : symbolsByIssue.entrySet()) {
            List<SymbolFinding> symbols = entry.getValue();
            SourceFinding first = selectedSource(allFindings, symbols.get(0));
            issuePlans.add(buildIssuePlan(projectVersion.version(), first.removalVersion(), first.filePath(),
                    first.module(), symbols));
        }

        DeprecationReport partialReport = new DeprecationReport(OffsetDateTime.now(ZoneOffset.UTC).toString(),
                options.repoRoot().toString(), projectVersion.version(), targetRemovalVersion,
                options.targetRemovalVersion() == null ? "snapshot" : "target", options.includeOverdue(),
                options.failOnDue(), allFindings.size(), selectedSymbols.size(), dueCount, overdueCount, futureCount,
                unscheduledCount, dueCount + overdueCount, issuePlans.size(), List.copyOf(issuePlans),
                List.copyOf(unscheduledSymbols), "");
        return partialReport.withSummaryMarkdown(renderMarkdown(partialReport));
    }

    private static SourceFinding selectedSource(List<SourceFinding> allFindings, SymbolFinding selectedSymbol) {
        return allFindings.stream()
                .filter(finding -> Objects.equals(finding.filePath(), selectedSymbol.filePath())
                        && Objects.equals(finding.name(), selectedSymbol.name())
                        && Objects.equals(finding.kind(), selectedSymbol.kind())
                        && finding.line() == selectedSymbol.line())
                .findFirst()
                .orElseThrow();
    }

    private static List<SourceFinding> scanSources(Path repoRoot) throws IOException {
        List<Path> javaFiles = iterJavaFiles(repoRoot);
        if (javaFiles.isEmpty()) {
            return List.of();
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalArgumentException("unable to locate the JDK Java compiler");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null,
                StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> fileObjects = fileManager.getJavaFileObjectsFromPaths(javaFiles);
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics,
                    List.of("-proc:none", "-Xlint:none"), null, fileObjects);
            DocTrees docTrees = DocTrees.instance(task);
            SourcePositions sourcePositions = docTrees.getSourcePositions();
            List<SourceFinding> findings = new ArrayList<>();
            for (CompilationUnitTree unit : task.parse()) {
                Path sourcePath = sourcePath(unit);
                Path relativePath = repoRoot.relativize(sourcePath);
                String sourceText = Files.readString(sourcePath, StandardCharsets.UTF_8);
                SourceScanner scanner = new SourceScanner(docTrees, sourcePositions, unit, sourceText,
                        relativePath.toString().replace('\\', '/'), moduleName(relativePath));
                scanner.scan(unit, null);
                findings.addAll(scanner.findings());
            }

            List<Diagnostic<? extends JavaFileObject>> errors = diagnostics.getDiagnostics()
                    .stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .toList();
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("unable to parse Java source: " + errors.get(0).getMessage(null));
            }
            return findings;
        }
    }

    private static Path sourcePath(CompilationUnitTree unit) {
        URI uri = unit.getSourceFile().toUri();
        return Path.of(uri).toAbsolutePath().normalize();
    }

    private static String renderMarkdown(DeprecationReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("# Removal-ready deprecations for " + report.snapshotVersion());
        lines.add("");
        lines.add("- Target removal version: " + report.removalVersion());
        lines.add("- Scan mode: " + report.scanMode());
        lines.add("- Planned issues: " + report.issuePlanCount());
        lines.add("- Removal-ready symbols in planned issues: " + report.findingCount());
        lines.add("- Due symbols: " + report.dueFindingCount());
        lines.add("- Overdue symbols: " + report.overdueFindingCount());
        lines.add("- Future symbols: " + report.futureFindingCount());
        lines.add("- Unscheduled symbols: " + report.unscheduledFindingCount());
        lines.add("");

        if (report.issuePlans().isEmpty()) {
            lines.add("No removal-ready deprecations matched this scan's issue-planning criteria.");
        } else {
            for (IssuePlan plan : report.issuePlans()) {
                lines.add("## " + plan.issueTitle());
                lines.add("");
                lines.add("- Module: `" + plan.module() + "`");
                lines.add("- File: `" + plan.filePath() + "`");
                lines.add("- Removal version: `" + plan.removalVersion() + "`");
                lines.add("- Symbols:");
                for (SymbolFinding symbol : plan.symbols()) {
                    lines.add("  - " + renderSymbol(symbol));
                }
                lines.add("");
            }
        }

        if (!report.unscheduledSymbols().isEmpty()) {
            lines.add("");
            lines.add("## Unscheduled for-removal symbols");
            lines.add("");
            for (SymbolFinding symbol : report.unscheduledSymbols()) {
                lines.add("- `" + symbol.filePath() + "`: " + renderSymbol(symbol));
            }
        }
        return String.join("\n", lines);
    }

    private static String renderSymbol(SymbolFinding symbol) {
        List<String> parts = new ArrayList<>();
        parts.add("`" + symbol.name() + "`");
        parts.add(symbol.kind());
        parts.add("line " + symbol.line());
        parts.add(symbol.status());
        if (symbol.replacement() != null) {
            parts.add("use `" + symbol.replacement() + "`");
        }
        return parts.stream().reduce((left, right) -> left + ", " + right).orElse("");
    }

    private static ScanOptions parseArgs(String[] args) {
        Path repoRoot = Path.of(".").toAbsolutePath().normalize();
        String pomFile = "pom.xml";
        String outputJson = null;
        String outputMarkdown = null;
        String targetRemovalVersion = null;
        boolean includeOverdue = false;
        boolean failOnDue = false;

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
            } else if ("--target-removal-version".equals(argument)) {
                targetRemovalVersion = requireValue(argument, args, ++index);
                validateVersion(targetRemovalVersion, argument);
            } else if ("--include-overdue".equals(argument)) {
                includeOverdue = true;
            } else if ("--fail-on-due".equals(argument)) {
                failOnDue = true;
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
        return new ScanOptions(repoRoot, resolvedPom, Path.of(outputJson), Path.of(outputMarkdown),
                targetRemovalVersion, includeOverdue, failOnDue);
    }

    private static String requireValue(String option, String[] args, int valueIndex) {
        if (valueIndex >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[valueIndex];
    }

    private static ProjectVersion readProjectVersion(Path pomFile) throws IOException {
        String text = Files.readString(pomFile, StandardCharsets.UTF_8);
        Matcher matcher = VERSION_RE.matcher(text);
        if (!matcher.find()) {
            throw new IllegalArgumentException("unable to find a project version in " + pomFile);
        }

        return new ProjectVersion(matcher.group(1).trim());
    }

    private static void validateVersion(String version, String option) {
        if (!SEMVER_RE.matcher(version).matches()) {
            throw new IllegalArgumentException(option + " must be major.minor.patch, found '" + version + "'");
        }
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

    private static Set<String> removalVersions(String text) {
        Set<String> versions = new LinkedHashSet<>();
        Matcher declarationMatcher = DECLARATION_VERSION_RE.matcher(text);
        while (declarationMatcher.find()) {
            versions.add(declarationMatcher.group(1));
        }

        Matcher notifierMatcher = NOTIFIER_RE.matcher(text);
        while (notifierMatcher.find()) {
            String version = notifierMatcher.group(2);
            if (version != null) {
                versions.add(version);
            }
        }
        return versions;
    }

    private static String replacement(String text) {
        Matcher notifierMatcher = NOTIFIER_RE.matcher(text);
        if (notifierMatcher.find()) {
            return notifierMatcher.group(1);
        }

        Matcher javadocMatcher = JAVADOC_REPLACEMENT_RE.matcher(text);
        if (javadocMatcher.find()) {
            return javadocMatcher.group(1);
        }
        return null;
    }

    private static IssuePlan buildIssuePlan(String snapshotVersion, String removalVersion, String relativePath,
            String module, List<SymbolFinding> symbols) {
        int symbolCount = symbols.size();
        String issueTitle = symbolCount == 1
                ? "Remove " + removalVersion + "-ready deprecation: " + symbols.get(0).name()
                : "Remove " + removalVersion + "-ready deprecations in " + Path.of(relativePath).getFileName();
        String dedupeKey = removalVersion + ":" + relativePath;
        String issueMarker = "<!-- ta4j:deprecation-removal version=" + removalVersion + " dedupe=" + dedupeKey
                + " -->";
        String symbolLines = symbols.stream()
                .map(symbol -> "- " + renderSymbol(symbol))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        String issueBody = String.join("\n",
                "Release automation detected removal-ready deprecations for `" + snapshotVersion + "`.", "",
                "Module: `" + module + "`", "File: `" + relativePath + "`", "Removal version: `" + removalVersion + "`",
                "", "Symbols:", symbolLines, "", "Acceptance checks:",
                "- remove or migrate the compatibility symbols scheduled for removal in `" + removalVersion + "`",
                "- update callers, tests, and documentation as needed", "- keep the full build green", "", issueMarker);
        return new IssuePlan(dedupeKey, issueMarker, issueTitle, issueBody, removalVersion, module, relativePath,
                symbolCount, List.copyOf(symbols));
    }

    private static String status(String removalVersion, String targetRemovalVersion) {
        int comparison = compareVersions(removalVersion, targetRemovalVersion);
        if (comparison < 0) {
            return "overdue";
        }
        if (comparison == 0) {
            return "due";
        }
        return "future";
    }

    private static int compareVersions(String left, String right) {
        int[] leftParts = versionParts(left);
        int[] rightParts = versionParts(right);
        for (int index = 0; index < leftParts.length; index++) {
            int comparison = Integer.compare(leftParts[index], rightParts[index]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int[] versionParts(String version) {
        validateVersion(version, "version");
        String[] parts = version.split("\\.");
        try {
            return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]) };
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("version components must fit in an integer: '" + version + "'", error);
        }
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
        return "Usage: --output-json <path> --output-md <path> [--repo-root <path>] [--pom-file <path>] "
                + "[--target-removal-version <major.minor.patch>] [--include-overdue] [--fail-on-due]";
    }

    private static final class SourceScanner extends TreePathScanner<Void, Void> {

        private final DocTrees docTrees;
        private final SourcePositions sourcePositions;
        private final CompilationUnitTree unit;
        private final String sourceText;
        private final String filePath;
        private final String module;
        private final List<SourceFinding> findings = new ArrayList<>();
        private final Deque<TypeContext> typeContexts = new ArrayDeque<>();
        private int methodDepth;

        private SourceScanner(DocTrees docTrees, SourcePositions sourcePositions, CompilationUnitTree unit,
                String sourceText, String filePath, String module) {
            this.docTrees = docTrees;
            this.sourcePositions = sourcePositions;
            this.unit = unit;
            this.sourceText = sourceText;
            this.filePath = filePath;
            this.module = module;
        }

        private List<SourceFinding> findings() {
            return findings;
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            TreePath path = getCurrentPath();
            CandidateDetails details = candidateDetails(path, tree.getModifiers(), tree, className(tree),
                    kind(tree.getKind()), false);
            Set<String> inheritedVersions = details.forRemoval() ? details.effectiveVersions() : inheritedVersions();
            typeContexts.push(new TypeContext(className(tree), inheritedVersions));
            try {
                if (details.forRemoval()) {
                    findings.add(details.toFinding(filePath, module));
                }
                return super.visitClass(tree, unused);
            } finally {
                typeContexts.pop();
            }
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            TreePath path = getCurrentPath();
            String name = tree.getReturnType() == null ? currentTypeName() : tree.getName().toString();
            String kind = tree.getReturnType() == null ? "constructor" : "method";
            CandidateDetails details = candidateDetails(path, tree.getModifiers(), tree, name, kind, true);
            if (details.forRemoval()) {
                findings.add(details.toFinding(filePath, module));
            }

            methodDepth++;
            try {
                return super.visitMethod(tree, unused);
            } finally {
                methodDepth--;
            }
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            TreePath path = getCurrentPath();
            if (methodDepth == 0 && isField(path)) {
                CandidateDetails details = candidateDetails(path, tree.getModifiers(), tree, tree.getName().toString(),
                        "field", true);
                if (details.forRemoval()) {
                    findings.add(details.toFinding(filePath, module));
                }
            }
            return super.visitVariable(tree, unused);
        }

        private CandidateDetails candidateDetails(TreePath path, ModifiersTree modifiers, Tree tree, String name,
                String kind, boolean inheritMissingRemovalVersion) {
            boolean forRemoval = isDeprecatedForRemoval(modifiers);
            String context = context(path, tree);
            Set<String> explicitVersions = removalVersions(context);
            Set<String> effectiveVersions = explicitVersions.isEmpty() && inheritMissingRemovalVersion
                    ? inheritedVersions()
                    : explicitVersions;
            String removalVersion = effectiveVersions.stream().findFirst().orElse(null);
            String findingStatus = removalVersion == null ? "unscheduled" : "scheduled";
            int line = declarationLine(tree, name);
            return new CandidateDetails(forRemoval, name, kind, line, removalVersion, findingStatus,
                    replacement(context), Set.copyOf(effectiveVersions));
        }

        private String context(TreePath path, Tree tree) {
            StringBuilder context = new StringBuilder();
            DocCommentTree docComment = docTrees.getDocCommentTree(path);
            if (docComment != null) {
                context.append(docComment).append('\n');
            }
            context.append(sourceFragment(tree));
            return context.toString();
        }

        private String sourceFragment(Tree tree) {
            long start = sourcePositions.getStartPosition(unit, tree);
            long end = sourcePositions.getEndPosition(unit, tree);
            if (start < 0 || end < start || end > sourceText.length()) {
                return tree.toString();
            }
            return sourceText.substring((int) start, (int) end);
        }

        private int declarationLine(Tree tree, String name) {
            long start = sourcePositions.getStartPosition(unit, tree);
            if (start < 0) {
                return -1;
            }

            String fragment = sourceFragment(tree);
            int nameIndex = fragment.indexOf(name);
            if (nameIndex < 0) {
                nameIndex = 0;
            }
            return (int) unit.getLineMap().getLineNumber(start + nameIndex);
        }

        private boolean isDeprecatedForRemoval(ModifiersTree modifiers) {
            for (AnnotationTree annotation : modifiers.getAnnotations()) {
                if (!isDeprecatedAnnotation(annotation)) {
                    continue;
                }
                for (ExpressionTree argument : annotation.getArguments()) {
                    String normalized = argument.toString().replaceAll("\\s+", "");
                    if (normalized.equals("forRemoval=true")) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isDeprecatedAnnotation(AnnotationTree annotation) {
            String annotationType = annotation.getAnnotationType().toString();
            return "Deprecated".equals(annotationType) || "java.lang.Deprecated".equals(annotationType)
                    || annotationType.endsWith(".Deprecated");
        }

        private Set<String> inheritedVersions() {
            return typeContexts.isEmpty() ? Set.of() : typeContexts.peek().inheritedRemovalVersions();
        }

        private String currentTypeName() {
            return typeContexts.isEmpty() ? "" : typeContexts.peek().name();
        }

        private boolean isField(TreePath path) {
            TreePath parentPath = path.getParentPath();
            return parentPath != null && parentPath.getLeaf() instanceof ClassTree;
        }

        private String className(ClassTree tree) {
            return tree.getSimpleName().toString();
        }

        private String kind(Tree.Kind treeKind) {
            return switch (treeKind) {
            case ANNOTATION_TYPE -> "annotation";
            case ENUM -> "enum";
            case INTERFACE -> "interface";
            case RECORD -> "record";
            default -> "class";
            };
        }
    }

    private record ScanOptions(Path repoRoot, Path pomFile, Path outputJson, Path outputMarkdown,
            String targetRemovalVersion, boolean includeOverdue, boolean failOnDue) {
    }

    private record ProjectVersion(String version) {
    }

    private record TypeContext(String name, Set<String> inheritedRemovalVersions) {
    }

    private record CandidateDetails(boolean forRemoval, String name, String kind, int line, String removalVersion,
            String status, String replacement, Set<String> effectiveVersions) {

        private SourceFinding toFinding(String filePath, String module) {
            return new SourceFinding(name, kind, line, removalVersion, status, replacement, module, filePath);
        }
    }

    private record SourceFinding(String name, String kind, int line, String removalVersion, String status,
            String replacement, String module, String filePath) {

        private SymbolFinding toSymbolFinding() {
            return toSymbolFinding(status);
        }

        private SymbolFinding toSymbolFinding(String status) {
            return new SymbolFinding(name, kind, line, removalVersion, status, replacement, module, filePath);
        }
    }

    private record SymbolFinding(String name, String kind, int line, String removalVersion, String status,
            String replacement, String module, String filePath) {
    }

    private record IssuePlan(String dedupeKey, String issueMarker, String issueTitle, String issueBody,
            String removalVersion, String module, String filePath, int symbolCount, List<SymbolFinding> symbols) {
    }

    private record DeprecationReport(String generatedAt, String repoRoot, String snapshotVersion, String removalVersion,
            String scanMode, boolean includeOverdue, boolean failOnDue, int totalForRemovalCount, int findingCount,
            int dueFindingCount, int overdueFindingCount, int futureFindingCount, int unscheduledFindingCount,
            int blockingFindingCount, int issuePlanCount, List<IssuePlan> issuePlans,
            List<SymbolFinding> unscheduledSymbols, String summaryMarkdown) {

        private DeprecationReport withSummaryMarkdown(String summaryMarkdown) {
            return new DeprecationReport(generatedAt, repoRoot, snapshotVersion, removalVersion, scanMode,
                    includeOverdue, failOnDue, totalForRemovalCount, findingCount, dueFindingCount, overdueFindingCount,
                    futureFindingCount, unscheduledFindingCount, blockingFindingCount, issuePlanCount, issuePlans,
                    unscheduledSymbols, summaryMarkdown);
        }
    }
}
