/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for {@link ReadmeContentManager} line-ending behavior.
 *
 * <p>
 * The README updater should preserve the dominant line separator already used
 * in the target README file, regardless of source snippet line endings.
 * </p>
 */
public class ReadmeContentManagerTest {

    private static final String[] SNIPPET_IDS = { "ema-crossover", "rsi-strategy", "strategy-performance",
            "advanced-strategy", "serialize-indicator", "serialize-rule", "serialize-strategy" };

    @TempDir
    private Path tempDir;

    @Test
    public void testUpdateReadmeSnippetsPreservesLfLineEndings() throws IOException {
        String lineSeparator = "\n";
        Path sourceFile = tempDir.resolve("ReadmeContentManager.java");
        Path readmeFile = tempDir.resolve("README.md");

        Files.writeString(sourceFile, buildSourceSnippets(lineSeparator), StandardCharsets.UTF_8);
        Files.writeString(readmeFile, buildReadmeTemplate(lineSeparator), StandardCharsets.UTF_8);

        boolean updated = ReadmeContentManager.updateReadmeSnippets(readmeFile, sourceFile);
        assertTrue(updated);

        String updatedContent = Files.readString(readmeFile, StandardCharsets.UTF_8);
        LineEndingCounts counts = countLineEndings(updatedContent);
        assertTrue(counts.lf() > 0);
        assertEquals(0, counts.crlf());
        assertTrue(updatedContent.contains(snippetBlock("ema-crossover", "ema_crossover", 1, lineSeparator)));
    }

    @Test
    public void testUpdateReadmeSnippetsPreservesCrLfLineEndings() throws IOException {
        String lineSeparator = "\r\n";
        Path sourceFile = tempDir.resolve("ReadmeContentManager.java");
        Path readmeFile = tempDir.resolve("README.md");

        Files.writeString(sourceFile, buildSourceSnippets("\n"), StandardCharsets.UTF_8);
        Files.writeString(readmeFile, buildReadmeTemplate(lineSeparator), StandardCharsets.UTF_8);

        boolean updated = ReadmeContentManager.updateReadmeSnippets(readmeFile, sourceFile);
        assertTrue(updated);

        String updatedContent = Files.readString(readmeFile, StandardCharsets.UTF_8);
        LineEndingCounts counts = countLineEndings(updatedContent);
        assertTrue(counts.crlf() > 0);
        assertEquals(0, counts.lf());
        assertTrue(updatedContent.contains(snippetBlock("ema-crossover", "ema_crossover", 1, lineSeparator)));
    }

    @Test
    public void testRepositoryJavaBaselineDocumentationAndWorkflowsStayAligned() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        String pom = readString(repositoryRoot.resolve("pom.xml"));
        String readme = readString(repositoryRoot.resolve("README.md"));
        String contributing = readString(repositoryRoot.resolve(".github").resolve("CONTRIBUTING.md"));
        List<Path> setupJavaWorkflows = new ArrayList<>();

        assertTrue(pom.contains("<maven.compiler.release>25</maven.compiler.release>"));
        assertTrue(pom.contains("<requireJavaVersion>"));
        assertTrue(pom.contains("<version>[25,)</version>"));
        assertTrue(readme.contains("JDK-25%2B"));
        assertTrue(readme.contains("Java 25+"));
        assertFalse(readme.contains("./mvnw"));
        assertFalse(readme.contains("mvnw.cmd"));
        assertTrue(contributing.contains("Java 25+"));

        try (Stream<Path> workflowPaths = Files.list(repositoryRoot.resolve(".github").resolve("workflows"))) {
            workflowPaths.filter(path -> path.getFileName().toString().endsWith(".yml")).forEach(path -> {
                String workflow = readString(path);
                if (workflow.contains("actions/setup-java")) {
                    setupJavaWorkflows.add(path);
                    assertTrue(workflow.contains("java-version: 25"), path + " should set up Java 25");
                    assertFalse(workflow.contains("java-version: 21"), path + " should not set up Java 21");
                    assertTrue(workflow.contains("actions/setup-java@v5"), path + " should keep setup-java on v5");
                }
                if (workflow.contains("actions/checkout@")) {
                    assertTrue(workflow.contains("actions/checkout@v6"), path + " should use checkout@v6");
                }
                if (workflow.contains("actions/cache@")) {
                    assertTrue(workflow.contains("actions/cache@v5"), path + " should use cache@v5");
                }
                if (workflow.contains("actions/upload-artifact@")) {
                    assertTrue(workflow.contains("actions/upload-artifact@v7"),
                            path + " should use upload-artifact@v7");
                }
                assertFalse(workflow.contains("actions/checkout@v5"), path + " should not pin checkout@v5");
                assertFalse(workflow.contains("actions/cache@v4"), path + " should not pin cache@v4");
                assertFalse(workflow.contains("actions/upload-artifact@v4"),
                        path + " should not pin upload-artifact@v4");
            });
        }

        assertFalse(setupJavaWorkflows.isEmpty());
    }

    private static String buildSourceSnippets(String lineSeparator) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < SNIPPET_IDS.length; i++) {
            String snippetId = SNIPPET_IDS[i];
            String variableName = snippetId.replace('-', '_');
            builder.append("// START_SNIPPET: ").append(snippetId).append(lineSeparator);
            builder.append("    int ")
                    .append(variableName)
                    .append(" = ")
                    .append(i + 1)
                    .append(";")
                    .append(lineSeparator);
            builder.append("// END_SNIPPET: ").append(snippetId).append(lineSeparator).append(lineSeparator);
        }
        return builder.toString();
    }

    private static String buildReadmeTemplate(String lineSeparator) {
        StringBuilder builder = new StringBuilder();
        builder.append("# README").append(lineSeparator).append(lineSeparator);
        for (String snippetId : SNIPPET_IDS) {
            builder.append("<!-- START_SNIPPET: ").append(snippetId).append(" -->").append(lineSeparator);
            builder.append("```java").append(lineSeparator);
            builder.append("old").append(lineSeparator);
            builder.append("```").append(lineSeparator);
            builder.append("<!-- END_SNIPPET: ")
                    .append(snippetId)
                    .append(" -->")
                    .append(lineSeparator)
                    .append(lineSeparator);
        }
        return builder.toString();
    }

    private static String snippetBlock(String snippetId, String variableName, int value, String lineSeparator) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!-- START_SNIPPET: ").append(snippetId).append(" -->").append(lineSeparator);
        builder.append("```java").append(lineSeparator);
        builder.append("int ").append(variableName).append(" = ").append(value).append(";").append(lineSeparator);
        builder.append("```").append(lineSeparator);
        builder.append("<!-- END_SNIPPET: ").append(snippetId).append(" -->");
        return builder.toString();
    }

    private static LineEndingCounts countLineEndings(String content) {
        int crlf = 0;
        int lf = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                if (i > 0 && content.charAt(i - 1) == '\r') {
                    crlf++;
                } else {
                    lf++;
                }
            }
        }
        return new LineEndingCounts(crlf, lf);
    }

    private static Path findRepositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        Path candidate = current;
        while (candidate != null) {
            if (Files.exists(candidate.resolve("pom.xml")) && Files.exists(candidate.resolve("README.md"))
                    && Files.isDirectory(candidate.resolve(".github"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IOException("Unable to locate repository root from " + current);
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record LineEndingCounts(int crlf, int lf) {
    }
}
