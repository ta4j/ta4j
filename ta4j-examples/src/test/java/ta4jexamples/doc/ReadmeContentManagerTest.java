/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
        assertTrue(counts.lf > 0);
        assertEquals(0, counts.crlf);
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
        assertTrue(counts.crlf > 0);
        assertEquals(0, counts.lf);
        assertTrue(updatedContent.contains(snippetBlock("ema-crossover", "ema_crossover", 1, lineSeparator)));
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

    private static final class LineEndingCounts {
        private final int crlf;
        private final int lf;

        private LineEndingCounts(int crlf, int lf) {
            this.crlf = crlf;
            this.lf = lf;
        }
    }
}
