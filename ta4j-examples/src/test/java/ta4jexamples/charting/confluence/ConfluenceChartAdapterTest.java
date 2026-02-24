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

import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfluenceChartAdapterTest {

    @Test
    public void buildsPlanWithSupportResistanceMarkersAndSignalSubcharts() {
        BarSeries series = buildSeries(380, 4300.0d, 2.0d);
        ConfluenceReport report = new ConfluenceReportGenerator().generate("^GSPC", series);

        ConfluenceChartAdapter adapter = new ConfluenceChartAdapter(new ChartWorkflow());
        ChartPlan plan = adapter.buildPlan(series, report, "Confluence Plan Test");

        int expectedLevelMarkers = report.topSupports(3).size() + report.topResistances(3).size();
        assertEquals(expectedLevelMarkers, plan.definition().basePlot().horizontalMarkers().size());

        assertEquals(2, plan.definition().subplots().size(), "RSI and MACD subcharts are expected");
        assertEquals(3, plan.definition().subplots().get(0).horizontalMarkers().size(), "RSI guide markers expected");
        assertEquals(1, plan.definition().subplots().get(1).horizontalMarkers().size(), "MACD zero marker expected");
        assertTrue(plan.metadata().title().contains("Confluence"));
    }

    private static BarSeries buildSeries(int bars, double basePrice, double driftPerBar) {
        BarSeries series = new MockBarSeriesBuilder().withName("chart-adapter-series").build();
        Instant start = Instant.parse("2023-01-01T00:00:00Z");
        Duration period = Duration.ofDays(1);
        for (int i = 0; i < bars; i++) {
            double drift = driftPerBar * i;
            double seasonal = Math.sin(i / 13.0d) * 17.0d;
            double close = basePrice + drift + seasonal;
            double open = close - 2.5d + Math.cos(i / 8.0d);
            double high = Math.max(open, close) + 6.0d;
            double low = Math.min(open, close) - 6.0d;
            double volume = 850_000.0d + (i * 1_500.0d) + Math.abs(Math.cos(i / 6.0d)) * 120_000.0d;

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
