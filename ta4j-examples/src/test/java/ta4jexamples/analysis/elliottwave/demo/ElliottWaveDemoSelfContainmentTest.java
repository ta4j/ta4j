/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the demo self-containment refactor.
 * <p>
 * Demo classes must be independently runnable and copyable; none of them may
 * reference the sibling suite demo's public surface. Degree selection is shared
 * through {@link ElliottWaveDemoSupport} instead.
 */
class ElliottWaveDemoSelfContainmentTest {

    private static final String FORBIDDEN_REFERENCE = "ElliottWaveIndicatorSuiteDemo";

    @Test
    void demoClassesDoNotReferenceElliottWaveIndicatorSuiteDemo() throws IOException {
        Path demoSourceDir = findDemoSourceDir();
        try (var stream = Files.walk(demoSourceDir)) {
            List<Path> offending = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains(FORBIDDEN_REFERENCE);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .toList();
            assertEquals(List.of(), offending,
                    "demo classes must not reference " + FORBIDDEN_REFERENCE + ", found: " + offending);
        }
    }

    private static Path findDemoSourceDir() {
        Path current = Path.of("").toAbsolutePath();
        Path candidate = current.resolve("src/main/java/ta4jexamples/analysis/elliottwave/demo");
        while (!Files.isDirectory(candidate) && current.getParent() != null) {
            current = current.getParent();
            candidate = current.resolve("src/main/java/ta4jexamples/analysis/elliottwave/demo");
        }
        if (!Files.isDirectory(candidate)) {
            throw new IllegalStateException("Cannot locate demo source directory from " + Path.of("").toAbsolutePath());
        }
        return candidate;
    }
}
