/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Regression coverage for {@link RemovalReadyDeprecationScanner}.
 */
public class RemovalReadyDeprecationScannerTest {

    @TempDir
    private Path tempDir;

    @Test
    public void testMatchingSnapshotDetection() throws IOException {
        writePom("<version>0.24.0-SNAPSHOT</version>");
        writeJava("ta4j-core/src/main/java/org/ta4j/core/legacy/LegacyBridge.java", """
                package org.ta4j.core.legacy;

                /**
                 * @deprecated Scheduled for removal in 0.24.0.
                 */
                @Deprecated(since = "0.20.0", forRemoval = true)
                public class LegacyBridge {

                    @Deprecated(since = "0.20.0", forRemoval = true)
                    public static void bridge() {
                    }
                }
                """);
        writeJava("ta4j-examples/src/main/java/ta4jexamples/legacy/FutureBridge.java", """
                package ta4jexamples.legacy;

                /**
                 * @deprecated Scheduled for removal in 0.25.0.
                 */
                @Deprecated(since = "0.20.0", forRemoval = true)
                public class FutureBridge {
                }
                """);

        JsonObject report = runScanner();
        assertEquals("0.24.0-SNAPSHOT", report.get("snapshotVersion").getAsString());
        assertEquals(2, report.get("findingCount").getAsInt());
        assertEquals(1, report.get("issuePlanCount").getAsInt());

        JsonObject plan = report.getAsJsonArray("issuePlans").get(0).getAsJsonObject();
        assertEquals("ta4j-core", plan.get("module").getAsString());
        assertEquals("ta4j-core/src/main/java/org/ta4j/core/legacy/LegacyBridge.java",
                plan.get("filePath").getAsString());
        assertEquals("Remove 0.24.0-ready deprecations in LegacyBridge.java", plan.get("issueTitle").getAsString());
        assertSymbolNames(plan.getAsJsonArray("symbols"), "LegacyBridge", "bridge");
        assertTrue(Files.readString(tempDir.resolve("report.md")).contains("LegacyBridge"));
    }

    @Test
    public void testNotifierVersionDetection() throws IOException {
        writePom("<version>0.24.0-SNAPSHOT</version>");
        writeJava("ta4j-core/src/main/java/org/ta4j/core/legacy/NotifierBridge.java",
                """
                        package org.ta4j.core.legacy;

                        import org.ta4j.core.utils.DeprecationNotifier;

                        @Deprecated(since = "0.20.0", forRemoval = true)
                        public class NotifierBridge {

                            {
                                DeprecationNotifier.warnOnce(NotifierBridge.class, "org.ta4j.core.modern.NotifierBridge", "0.24.0");
                            }
                        }
                        """);

        JsonObject report = runScanner();
        assertEquals(1, report.get("findingCount").getAsInt());
        JsonObject plan = report.getAsJsonArray("issuePlans").get(0).getAsJsonObject();
        assertSymbolNames(plan.getAsJsonArray("symbols"), "NotifierBridge");
    }

    @Test
    public void testMixedVersionsInOneFile() throws IOException {
        writePom("<version>0.24.0-SNAPSHOT</version>");
        writeJava("ta4j-core/src/main/java/org/ta4j/core/legacy/MixedRemovalBridge.java", """
                package org.ta4j.core.legacy;

                public class MixedRemovalBridge {

                    /**
                     * @deprecated Scheduled for removal in 0.24.0.
                     */
                    @Deprecated(since = "0.20.0", forRemoval = true)
                    public void removeNow() {
                    }

                    /**
                     * @deprecated Scheduled for removal in 0.25.0.
                     */
                    @Deprecated(since = "0.21.0", forRemoval = true)
                    public void removeLater() {
                    }
                }
                """);

        JsonObject report = runScanner();
        assertEquals(1, report.get("findingCount").getAsInt());
        JsonObject plan = report.getAsJsonArray("issuePlans").get(0).getAsJsonObject();
        assertSymbolNames(plan.getAsJsonArray("symbols"), "removeNow");
        assertFalse(Files.readString(tempDir.resolve("report.md")).contains("removeLater"));
    }

    @Test
    public void testNextJavadocDoesNotLeakBackwards() throws IOException {
        writePom("<version>0.24.0-SNAPSHOT</version>");
        writeJava("ta4j-core/src/main/java/org/ta4j/core/legacy/AdjacentJavadocBridge.java", """
                package org.ta4j.core.legacy;

                public class AdjacentJavadocBridge {

                    @Deprecated(since = "0.20.0", forRemoval = true)
                    public void unscheduled() {
                    }

                    /**
                     * @deprecated Scheduled for removal in 0.24.0.
                     */
                    @Deprecated(since = "0.21.0", forRemoval = true)
                    public void removeNow() {
                    }
                }
                """);

        JsonObject report = runScanner();
        JsonObject plan = firstPlan(report);
        assertSymbolNames(plan.getAsJsonArray("symbols"), "removeNow");
        assertSymbolNames(report.getAsJsonArray("unscheduledSymbols"), "unscheduled");
    }

    @Test
    public void testTypeInheritanceResetsBetweenTypes() throws IOException {
        writePom("<version>0.24.0-SNAPSHOT</version>");
        writeJava("ta4j-core/src/main/java/org/ta4j/core/legacy/LegacyBridge.java", """
                package org.ta4j.core.legacy;

                /**
                 * @deprecated Scheduled for removal in 0.24.0.
                 */
                @Deprecated(since = "0.20.0", forRemoval = true)
                public class LegacyBridge {

                    @Deprecated(since = "0.20.0", forRemoval = true)
                    public void removeNow() {
                    }
                }

                @Deprecated(since = "0.21.0", forRemoval = true)
                class UnscheduledBridge {

                    @Deprecated(since = "0.21.0", forRemoval = true)
                    void keepAround() {
                    }
                }
                """);

        JsonObject report = runScanner();
        assertEquals(2, report.get("findingCount").getAsInt());
        JsonObject plan = firstPlan(report);
        assertSymbolNames(plan.getAsJsonArray("symbols"), "LegacyBridge", "removeNow");
        assertSymbolNames(report.getAsJsonArray("unscheduledSymbols"), "UnscheduledBridge", "keepAround");
    }

    @Test
    public void testInitializedFieldIsClassifiedAsField() throws IOException {
        writePom("<version>0.24.0-SNAPSHOT</version>");
        writeJava("ta4j-core/src/main/java/org/ta4j/core/legacy/LegacyFieldHolder.java", """
                package org.ta4j.core.legacy;

                /**
                 * @deprecated Scheduled for removal in 0.24.0.
                 */
                @Deprecated(since = "0.20.0", forRemoval = true)
                public class LegacyFieldHolder {

                    @Deprecated(since = "0.20.0", forRemoval = true)
                    public static final LegacyFieldHolder LEGACY = createLegacy();

                    private static LegacyFieldHolder createLegacy() {
                        return new LegacyFieldHolder();
                    }
                }
                """);

        JsonObject plan = firstPlan(runScanner());
        JsonArray symbols = plan.getAsJsonArray("symbols");
        JsonObject field = symbols.get(1).getAsJsonObject();
        assertEquals("LEGACY", field.get("name").getAsString());
        assertEquals("field", field.get("kind").getAsString());
        assertFalse(Files.readString(tempDir.resolve("report.md")).contains("createLegacy"));
    }

    @Test
    public void testTargetVersionIncludesOverdueFindingsWhenRequested() throws IOException {
        writePom("<version>0.24.1-SNAPSHOT</version>");
        writeJava("ta4j-core/src/main/java/org/ta4j/core/legacy/OverdueBridge.java", """
                package org.ta4j.core.legacy;

                /**
                 * @deprecated Scheduled for removal in 0.24.0.
                 */
                @Deprecated(since = "0.20.0", forRemoval = true)
                public class OverdueBridge {
                }
                """);

        JsonObject report = runScanner("--target-removal-version", "0.24.1", "--include-overdue");
        assertEquals("0.24.1", report.get("removalVersion").getAsString());
        assertEquals(1, report.get("overdueFindingCount").getAsInt());
        assertEquals(1, report.get("findingCount").getAsInt());

        JsonObject symbol = firstPlan(report).getAsJsonArray("symbols").get(0).getAsJsonObject();
        assertEquals("overdue", symbol.get("status").getAsString());
    }

    @Test
    public void testFailOnDueReturnsNonZeroAfterWritingReport() throws IOException {
        writePom("<version>0.24.0-SNAPSHOT</version>");
        writeJava("ta4j-core/src/main/java/org/ta4j/core/legacy/DueBridge.java", """
                package org.ta4j.core.legacy;

                /**
                 * @deprecated Scheduled for removal in 0.24.0.
                 */
                @Deprecated(since = "0.20.0", forRemoval = true)
                public class DueBridge {
                }
                """);

        Path outputJson = tempDir.resolve("report.json");
        Path outputMarkdown = tempDir.resolve("report.md");
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = RemovalReadyDeprecationScanner.run(
                scannerArgs(outputJson, outputMarkdown, "--target-removal-version", "0.24.0", "--fail-on-due"),
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(stderr));

        assertEquals(2, exitCode);
        assertTrue(Files.exists(outputJson));
        assertTrue(Files.exists(outputMarkdown));
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("deprecation gate failed"));
    }

    @Test
    public void testUnscheduledForRemovalIsReportedButNotPlanned() throws IOException {
        writePom("<version>0.24.0-SNAPSHOT</version>");
        writeJava("ta4j-core/src/main/java/org/ta4j/core/legacy/UnscheduledBridge.java", """
                package org.ta4j.core.legacy;

                @Deprecated(
                        since = "0.20.0",
                        forRemoval = true
                )
                public class UnscheduledBridge {
                }
                """);

        JsonObject report = runScanner();
        assertEquals(0, report.get("issuePlanCount").getAsInt());
        assertEquals(1, report.get("unscheduledFindingCount").getAsInt());
        assertSymbolNames(report.getAsJsonArray("unscheduledSymbols"), "UnscheduledBridge");
    }

    @Test
    public void testMultilineAnnotationAndCommentFalsePositives() throws IOException {
        writePom("<version>0.24.0-SNAPSHOT</version>");
        writeJava("ta4j-core/src/main/java/org/ta4j/core/legacy/MultilineBridge.java", """
                package org.ta4j.core.legacy;

                public class MultilineBridge {
                    // @Deprecated(since = "0.20.0", forRemoval = true)
                    // Scheduled for removal in 0.24.0.

                    /**
                     * @deprecated Scheduled for removal in 0.24.0.
                     */
                    @Deprecated(
                            since = "0.20.0",
                            forRemoval =
                                    true
                    )
                    public void removeNow() {
                    }
                }
                """);

        JsonObject report = runScanner();
        assertEquals(1, report.get("findingCount").getAsInt());
        assertSymbolNames(firstPlan(report).getAsJsonArray("symbols"), "removeNow");
    }

    @Test
    public void testNestedDeprecatedTypeResetsInheritedVersions() throws IOException {
        writePom("<version>0.24.0-SNAPSHOT</version>");
        writeJava("ta4j-core/src/main/java/org/ta4j/core/legacy/NestedBridge.java", """
                package org.ta4j.core.legacy;

                /**
                 * @deprecated Scheduled for removal in 0.24.0.
                 */
                @Deprecated(since = "0.20.0", forRemoval = true)
                public class NestedBridge {

                    @Deprecated(since = "0.20.0", forRemoval = true)
                    public void removeOuterMember() {
                    }

                    @Deprecated(since = "0.20.0", forRemoval = true)
                    static class UnscheduledNestedBridge {

                        @Deprecated(since = "0.20.0", forRemoval = true)
                        void keepNestedMember() {
                        }
                    }
                }
                """);

        JsonObject report = runScanner();
        assertEquals(2, report.get("findingCount").getAsInt());
        assertEquals(2, report.get("unscheduledFindingCount").getAsInt());
        assertSymbolNames(firstPlan(report).getAsJsonArray("symbols"), "NestedBridge", "removeOuterMember");
    }

    @Test
    public void testIssuePlanIncludesVersionMarkerAndReplacementHint() throws IOException {
        writePom("<version>0.24.0-SNAPSHOT</version>");
        writeJava("ta4j-core/src/main/java/org/ta4j/core/legacy/ReplacementBridge.java",
                """
                        package org.ta4j.core.legacy;

                        import org.ta4j.core.utils.DeprecationNotifier;

                        /**
                         * @deprecated Use {@link ModernBridge}. Scheduled for removal in 0.24.0.
                         */
                        @Deprecated(since = "0.20.0", forRemoval = true)
                        public class ReplacementBridge {
                            {
                                DeprecationNotifier.warnOnce(ReplacementBridge.class, "org.ta4j.core.legacy.ModernBridge", "0.24.0");
                            }
                        }
                        """);

        JsonObject plan = firstPlan(runScanner());
        assertTrue(plan.get("issueMarker").getAsString().contains("version=0.24.0"));
        assertTrue(plan.get("issueBody").getAsString().contains("org.ta4j.core.legacy.ModernBridge"));
    }

    @Test
    public void testWorktreePathPrefixDoesNotHideRepoSources() throws IOException {
        Path repoRoot = tempDir.resolve(".agents/worktrees/scanner-checkout");
        Files.createDirectories(repoRoot);
        Files.writeString(repoRoot.resolve("pom.xml"), "<project><version>0.24.0-SNAPSHOT</version></project>",
                StandardCharsets.UTF_8);
        Path javaFile = repoRoot.resolve("ta4j-core/src/main/java/org/ta4j/core/legacy/WorktreeBridge.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, """
                package org.ta4j.core.legacy;

                /**
                 * @deprecated Scheduled for removal in 0.24.0.
                 */
                @Deprecated(since = "0.20.0", forRemoval = true)
                public class WorktreeBridge {
                }
                """, StandardCharsets.UTF_8);

        Path outputJson = repoRoot.resolve("report.json");
        Path outputMarkdown = repoRoot.resolve("report.md");
        int exitCode = RemovalReadyDeprecationScanner.run(
                new String[] { "--repo-root", repoRoot.toString(), "--output-json", outputJson.toString(),
                        "--output-md", outputMarkdown.toString() },
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));

        assertEquals(0, exitCode);
        JsonObject report = JsonParser.parseString(Files.readString(outputJson)).getAsJsonObject();
        assertEquals(1, report.get("findingCount").getAsInt());
        assertTrue(Files.readString(outputMarkdown).contains("WorktreeBridge"));
    }

    @Test
    public void testRejectsNonSnapshotVersion() throws IOException {
        writePom("<version>0.24.0</version>");

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = RemovalReadyDeprecationScanner.run(
                new String[] { "--repo-root", tempDir.toString(), "--output-json",
                        tempDir.resolve("report.json").toString(), "--output-md",
                        tempDir.resolve("report.md").toString() },
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(stderr));

        assertEquals(1, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("expected a SNAPSHOT version"));
    }

    private JsonObject runScanner(String... extraArgs) throws IOException {
        Path outputJson = tempDir.resolve("report.json");
        Path outputMarkdown = tempDir.resolve("report.md");
        int exitCode = RemovalReadyDeprecationScanner.run(scannerArgs(outputJson, outputMarkdown, extraArgs),
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));
        assertEquals(0, exitCode);
        return JsonParser.parseString(Files.readString(outputJson, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private String[] scannerArgs(Path outputJson, Path outputMarkdown, String... extraArgs) {
        String[] baseArgs = { "--repo-root", tempDir.toString(), "--output-json", outputJson.toString(), "--output-md",
                outputMarkdown.toString() };
        String[] args = new String[baseArgs.length + extraArgs.length];
        System.arraycopy(baseArgs, 0, args, 0, baseArgs.length);
        System.arraycopy(extraArgs, 0, args, baseArgs.length, extraArgs.length);
        return args;
    }

    private JsonObject firstPlan(JsonObject report) {
        return report.getAsJsonArray("issuePlans").get(0).getAsJsonObject();
    }

    private void assertSymbolNames(JsonArray symbols, String... expectedNames) {
        assertEquals(expectedNames.length, symbols.size());
        for (int index = 0; index < expectedNames.length; index++) {
            assertEquals(expectedNames[index], symbols.get(index).getAsJsonObject().get("name").getAsString());
        }
    }

    private void writePom(String versionLine) throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project>" + System.lineSeparator() + "  " + versionLine
                + System.lineSeparator() + "</project>" + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private void writeJava(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
