/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.confluence;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.confluence.ConfluenceReport;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static BarSeries buildSeries(int bars, double basePrice, double driftPerBar) {
        BarSeries series = new MockBarSeriesBuilder().withName("confluence-series").build();
        Instant start = Instant.parse("2023-01-01T00:00:00Z");
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
