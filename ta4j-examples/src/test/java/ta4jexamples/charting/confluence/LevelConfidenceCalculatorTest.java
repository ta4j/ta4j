/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.confluence;

import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.confluence.ConfluenceReport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    public void scoreIsDeterministicAndMatchesFormulaComponents() {
        LevelConfidenceCalculator calculator = new LevelConfidenceCalculator();
        LevelConfidenceCalculator.LevelSample sample = new LevelConfidenceCalculator.LevelSample(
                ConfluenceReport.LevelType.SUPPORT, "trendline-support", 5100.0d, 5200.0d, 45.0d, 0.80d, 8, 397, 400,
                252, 0.65d, "Trendline with repeated touches");

        ConfluenceReport.LevelConfidence level = calculator.score(sample);

        assertEquals(79.61772486772487d, level.confidence(), 1.0e-12d);
        assertEquals(0.8d, level.touches(), 1.0e-12d);
        assertEquals(0.9880952380952381d, level.recency(), 1.0e-12d);
        assertEquals(0.6296296296296297d, level.volatilityContext(), 1.0e-12d);
        assertEquals(-1.9230769230769231d, level.distanceToPricePct(), 1.0e-12d);
    }

    @Test
    public void capsConfidenceAtNinetyFiveWhenAllInputsAreStrong() {
        LevelConfidenceCalculator calculator = new LevelConfidenceCalculator();
        LevelConfidenceCalculator.LevelSample sample = new LevelConfidenceCalculator.LevelSample(
                ConfluenceReport.LevelType.RESISTANCE, "perfect", 5200.0d, 5200.0d, 30.0d, 1.0d, 20, 400, 400, 252,
                1.0d, "perfect confluence");

        ConfluenceReport.LevelConfidence level = calculator.score(sample);

        assertEquals(95.0d, level.confidence(), 1.0e-12d);
    }

    @Test
    public void usesRecencyFallbackWhenInteractionIndexIsInvalid() {
        LevelConfidenceCalculator calculator = new LevelConfidenceCalculator();
        LevelConfidenceCalculator.LevelSample sample = new LevelConfidenceCalculator.LevelSample(
                ConfluenceReport.LevelType.SUPPORT, "late", 5000.0d, 5200.0d, 40.0d, 0.6d, 5, 999, 400, 252, 0.7d,
                "invalid interaction index");

        ConfluenceReport.LevelConfidence level = calculator.score(sample);

        assertEquals(0.20d, level.recency(), 1.0e-12d);
    }

    @Test
    public void rejectsInvalidSamplesAndNullInput() {
        LevelConfidenceCalculator calculator = new LevelConfidenceCalculator();
        assertThrows(NullPointerException.class, () -> calculator.score(null));
        assertThrows(IllegalArgumentException.class,
                () -> new LevelConfidenceCalculator.LevelSample(ConfluenceReport.LevelType.SUPPORT, "", 5100.0d,
                        5200.0d, 10.0d, 0.5d, 2, 100, 120, 60, 0.5d, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new LevelConfidenceCalculator.LevelSample(ConfluenceReport.LevelType.SUPPORT, "x", -1.0d, 5200.0d,
                        10.0d, 0.5d, 2, 100, 120, 60, 0.5d, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new LevelConfidenceCalculator.LevelSample(ConfluenceReport.LevelType.SUPPORT, "x", 5100.0d, 0.0d,
                        10.0d, 0.5d, 2, 100, 120, 60, 0.5d, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new LevelConfidenceCalculator.LevelSample(ConfluenceReport.LevelType.SUPPORT, "x", 5100.0d,
                        5200.0d, -1.0d, 0.5d, 2, 100, 120, 60, 0.5d, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new LevelConfidenceCalculator.LevelSample(ConfluenceReport.LevelType.SUPPORT, "x", 5100.0d,
                        5200.0d, 10.0d, 1.5d, 2, 100, 120, 60, 0.5d, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new LevelConfidenceCalculator.LevelSample(ConfluenceReport.LevelType.SUPPORT, "x", 5100.0d,
                        5200.0d, 10.0d, 0.5d, -1, 100, 120, 60, 0.5d, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new LevelConfidenceCalculator.LevelSample(ConfluenceReport.LevelType.SUPPORT, "x", 5100.0d,
                        5200.0d, 10.0d, 0.5d, 2, 100, -1, 60, 0.5d, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new LevelConfidenceCalculator.LevelSample(ConfluenceReport.LevelType.SUPPORT, "x", 5100.0d,
                        5200.0d, 10.0d, 0.5d, 2, 100, 120, 0, 0.5d, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new LevelConfidenceCalculator.LevelSample(ConfluenceReport.LevelType.SUPPORT, "x", 5100.0d,
                        5200.0d, 10.0d, 0.5d, 2, 100, 120, 60, -0.1d, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new LevelConfidenceCalculator.LevelSample(ConfluenceReport.LevelType.SUPPORT, "x", 5100.0d,
                        5200.0d, 10.0d, 0.5d, 2, 100, 120, 60, 0.5d, " "));
    }
}
