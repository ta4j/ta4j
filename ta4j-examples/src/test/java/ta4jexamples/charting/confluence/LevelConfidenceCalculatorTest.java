/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.confluence;

import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.confluence.ConfluenceReport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LevelConfidenceCalculatorTest {

    @Test
    public void scoresLevelWithinExpectedBounds() {
        LevelConfidenceCalculator calculator = new LevelConfidenceCalculator();
        LevelConfidenceCalculator.LevelSample sample = new LevelConfidenceCalculator.LevelSample(
                ConfluenceReport.LevelType.SUPPORT, "trendline-support", 5100.0d, 5200.0d, 45.0d, 0.80d, 8, 397, 400,
                252, 0.65d, "Trendline with repeated touches");

        ConfluenceReport.LevelConfidence level = calculator.score(sample);

        assertEquals("trendline-support", level.name());
        assertTrue(level.confidence() >= 0.0d && level.confidence() <= 95.0d);
        assertTrue(level.structural() >= 0.0d && level.structural() <= 1.0d);
        assertTrue(level.touches() >= 0.0d && level.touches() <= 1.0d);
        assertTrue(level.recency() >= 0.0d && level.recency() <= 1.0d);
        assertTrue(level.volatilityContext() >= 0.0d && level.volatilityContext() <= 1.0d);
    }
}
