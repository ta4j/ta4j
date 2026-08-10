/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.doc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Locks the cross-file excluded-test-tags contract between the canonical full
 * build script and the README's documented Maven-only equivalents.
 *
 * <p>
 * {@code scripts/run-full-build-quiet.sh} defines the authoritative default
 * excluded test tags for local and hosted validation. The root README must
 * document the same tag set in both its repair and its CI-equivalent Maven
 * commands; a drift here silently changes which hardware-gated tests run on CI
 * versus what contributors are told to run locally.
 * </p>
 */
public class FullBuildExcludedTagsTest {

    private static final String EXCLUDED_TAGS = "analysis-demo,benchmark,requires-cuda,requires-metal,requires-opencl,requires-display,requires-headless";

    @Test
    public void readmeDocumentsTheSameExcludedTagsAsTheFullBuildScript() throws IOException {
        String readme = readUtf8(repositoryRoot().resolve("README.md"));
        String script = readUtf8(repositoryRoot().resolve("scripts/run-full-build-quiet.sh"));

        assertTrue(script.contains("DEFAULT_MAVEN_ARGS=(\"-Dta4j.excludedTestTags=" + EXCLUDED_TAGS + "\")"),
                "full-build script must define the canonical excluded test tag list");
        assertTrue(readme.contains("-Dta4j.excludedTestTags=" + EXCLUDED_TAGS),
                "README must document the canonical excluded test tag list");
        assertTrue(readme.contains(
                "./mvnw -B clean license:format spotless:apply verify -Dta4j.excludedTestTags=" + EXCLUDED_TAGS),
                "README must document the repair Maven command with the canonical tag list");
        assertTrue(readme.contains(
                "./mvnw -B clean license:check spotless:check verify -Dta4j.excludedTestTags=" + EXCLUDED_TAGS),
                "README must document the CI-equivalent Maven command with the canonical tag list");
    }

    private static Path repositoryRoot() {
        Path current = Path.of(".").toAbsolutePath().normalize();
        while (current != null
                && !(Files.exists(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("scripts")))) {
            current = current.getParent();
        }
        return current;
    }

    private static String readUtf8(Path path) throws IOException {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read " + path, exception);
        }
    }
}
