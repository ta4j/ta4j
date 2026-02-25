/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.confluence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.confluence.ConfluenceReport;
import org.ta4j.core.analysis.confluence.ConfluenceScoringEngine;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfluenceReportGeneratorTest {

    @Test
    public void generatesReportWithRequiredConfluenceSections() {
        BarSeries series = buildSeries(420, 4200.0d, 2.5d);
        ConfluenceReportGenerator generator = new ConfluenceReportGenerator();

        ConfluenceReport report = generator.generate("^GSPC", series);

        assertEquals("^GSPC", report.snapshot().ticker());
        assertEquals(420, report.snapshot().barCount());
        assertEquals(6, report.pillarScores().size());
        assertFalse(report.levelConfidences().isEmpty(), "Expected support/resistance confidence levels");
        assertEquals(2, report.horizonProbabilities().size());
        assertFalse(report.outlookNarrative().isEmpty());
        assertTrue(report.extensions().containsKey("pillar.macro.status"));

        for (ConfluenceReport.HorizonProbability probability : report.horizonProbabilities()) {
            double sum = probability.upProbability() + probability.downProbability() + probability.rangeProbability();
            assertEquals(1.0d, sum, 1.0e-9d);
        }
    }

    @Test
    public void rejectsSeriesThatIsTooShortForDailyConfluence() {
        BarSeries shortSeries = buildSeries(120, 4100.0d, 1.8d);
        ConfluenceReportGenerator generator = new ConfluenceReportGenerator();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> generator.generate("^GSPC", shortSeries));
        assertTrue(exception.getMessage().contains("at least"));
    }

    @Test
    public void deterministicFixtureFingerprintRemainsStable() {
        BarSeries series = buildSeries(420, 4200.0d, 2.5d);
        Instant referenceNow = Instant.parse("2024-02-25T00:00:00Z");
        ConfluenceReportGenerator generator = new ConfluenceReportGenerator(Clock.fixed(referenceNow, ZoneOffset.UTC));
        ConfluenceReport report = generator.generate("^GSPC", series);

        String fingerprint = String.format("%.3f|%.3f|%.3f|%.3f|%.3f|%.3f", report.snapshot().rawConfluenceScore(),
                report.snapshot().decorrelatedConfluenceScore(), report.confidenceBreakdown().finalConfidence(),
                report.horizonProbabilities().get(0).upProbability(),
                report.horizonProbabilities().get(1).upProbability(), report.levelConfidences().get(0).confidence());

        assertEquals("68.082|68.387|52.481|0.598|0.691|40.696", fingerprint);
    }

    @Test
    public void validatesInputsAndConstructorDependencies() {
        BarSeries valid = buildSeries(320, 4200.0d, 1.5d);
        ConfluenceReportGenerator generator = new ConfluenceReportGenerator();

        assertThrows(IllegalArgumentException.class, () -> generator.generate(" ", valid));
        assertThrows(NullPointerException.class, () -> generator.generate("^GSPC", null));
        assertThrows(NullPointerException.class,
                () -> new ConfluenceReportGenerator(null, new LevelConfidenceCalculator()));
        assertThrows(NullPointerException.class,
                () -> new ConfluenceReportGenerator(new ConfluenceScoringEngine(), null));
        assertThrows(NullPointerException.class, () -> new ConfluenceReportGenerator((Clock) null));
    }

    @Test
    public void reportContainsExpectedValidationExtensionsAndBalancedLevels() {
        ConfluenceReport report = new ConfluenceReportGenerator().generate("^GSPC", buildSeries(420, 4200.0d, 2.5d));

        assertEquals("uncalibrated-v1", report.validationMetadata().calibrationMethod());
        assertFalse(report.validationMetadata().warnings().isEmpty());
        assertTrue(report.validationMetadata().warnings().get(0).contains("Calibration pipeline pending"));
        assertEquals(Set.of("regime.adx", "regime.rangeThreshold", "regime.atrPctPercentile", "regime.plusDI",
                "regime.minusDI", "pillar.macro.status"), report.extensions().keySet());

        assertTrue(report.levelConfidences()
                .stream()
                .anyMatch(level -> level.type() == ConfluenceReport.LevelType.SUPPORT));
        assertTrue(report.levelConfidences()
                .stream()
                .anyMatch(level -> level.type() == ConfluenceReport.LevelType.RESISTANCE));

        for (ConfluenceReport.HorizonProbability probability : report.horizonProbabilities()) {
            assertFalse(probability.calibrated());
            assertEquals("uncalibrated-v1", probability.calibrationMethod());
        }

        assertNotNull(report.confidenceBreakdown().notes());
        assertTrue(report.confidenceBreakdown().notes().size() >= 3);
        assertTrue(report.outlookNarrative().size() >= 5);
    }

    @Test
    public void staleSeriesProducesLowerDataConfidenceThanFreshSeries() {
        Instant freshStart = Instant.now().minus(Duration.ofDays(419));
        ConfluenceReport fresh = new ConfluenceReportGenerator().generate("^GSPC",
                buildSeries(420, 4200.0d, 2.5d, freshStart));
        ConfluenceReport stale = new ConfluenceReportGenerator().generate("^GSPC",
                buildSeries(420, 4200.0d, 2.5d, Instant.parse("2016-01-01T00:00:00Z")));

        assertTrue(fresh.confidenceBreakdown().dataConfidence() > stale.confidenceBreakdown().dataConfidence());
    }

    private static BarSeries buildSeries(int bars, double basePrice, double driftPerBar) {
        return buildSeries(bars, basePrice, driftPerBar, Instant.parse("2023-01-01T00:00:00Z"));
    }

    private static BarSeries buildSeries(int bars, double basePrice, double driftPerBar, Instant start) {
        BarSeries series = new MockBarSeriesBuilder().withName("confluence-series").build();
        Duration period = Duration.ofDays(1);
        for (int i = 0; i < bars; i++) {
            double drift = driftPerBar * i;
            double seasonal = Math.sin(i / 11.0d) * 22.0d;
            double close = basePrice + drift + seasonal;
            double open = close - 2.0d + Math.sin(i / 7.0d);
            double high = Math.max(open, close) + 5.0d + Math.cos(i / 9.0d);
            double low = Math.min(open, close) - 5.0d - Math.sin(i / 8.0d);
            double volume = 1_000_000.0d + (i * 1_300.0d) + Math.abs(Math.sin(i / 5.0d)) * 140_000.0d;

            series.barBuilder()
                    .timePeriod(period)
                    .endTime(start.plus(period.multipliedBy(i)))
                    .openPrice(open)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(close)
                    .volume(volume)
                    .add();
        }
        return series;
    }
}
