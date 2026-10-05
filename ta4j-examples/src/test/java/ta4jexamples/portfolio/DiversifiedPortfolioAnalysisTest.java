/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import ta4jexamples.portfolio.DiversifiedPortfolioAnalysis.Arguments;

public class DiversifiedPortfolioAnalysisTest {

    @Test
    public void parsesDefaultsAndExplicitArguments() {
        Arguments defaults = Arguments.parse(new String[0]);
        assertEquals(Path.of("target", "portfolio-analysis"), defaults.outputDirectory());
        assertNull(defaults.aiAnalysisFile());

        Arguments explicit = Arguments.parse(new String[] { "--output=out/report", "--ai-analysis=notes.md" });
        assertEquals(Path.of("out/report"), explicit.outputDirectory());
        assertEquals(Path.of("notes.md"), explicit.aiAnalysisFile());

        assertThrows(IllegalArgumentException.class, () -> Arguments.parse(new String[] { "--out=x" }));
    }
}
