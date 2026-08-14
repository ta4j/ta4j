/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class WalkForwardPublicSincePolicyTest {

    private static final String EXPECTED_SINCE = "@since 0.24.2";
    private static final String SOURCE_RELATIVE_PATH = "ta4j-core/src/main/java/org/ta4j/core/walkforward/WalkForwardRunResult.java";

    @Test
    void newFoldFailureMembersCarryReleaseSinceTags() throws IOException {
        String source = Files.readString(sourcePath());

        assertThat(javadocBefore(source, "public FoldFailure {")).contains(EXPECTED_SINCE);
        assertThat(javadocBefore(source, "public Throwable cause()")).contains(EXPECTED_SINCE);

        // Existing API metadata remains on its original release boundary.
        assertThat(javadocBefore(source, "public Map<String, Num> globalMetricsForHorizon(int horizonBars)"))
                .contains("@since 0.22.4")
                .doesNotContain(EXPECTED_SINCE);
    }

    private static Path sourcePath() {
        String worktreeRoot = System.getProperty("review.worktree");
        return worktreeRoot == null
                ? Path.of("src", "main", "java", "org/ta4j/core/walkforward/WalkForwardRunResult.java")
                : Path.of(worktreeRoot, SOURCE_RELATIVE_PATH);
    }

    private static String javadocBefore(String source, String declaration) {
        int declarationIndex = source.indexOf(declaration);
        assertThat(declarationIndex).as("declaration %s", declaration).isGreaterThanOrEqualTo(0);
        int javadocStart = source.lastIndexOf("/**", declarationIndex);
        int javadocEnd = source.indexOf("*/", javadocStart);
        assertThat(javadocStart).as("Javadoc for %s", declaration).isGreaterThanOrEqualTo(0);
        assertThat(javadocEnd).as("Javadoc for %s", declaration).isLessThan(declarationIndex);
        return source.substring(javadocStart, javadocEnd);
    }
}
