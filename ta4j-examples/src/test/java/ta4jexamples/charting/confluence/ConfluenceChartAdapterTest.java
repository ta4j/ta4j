/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.confluence;

import java.time.Duration;
import java.time.Instant;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.confluence.ConfluenceReport;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.compose.TradingChartFactory;
import ta4jexamples.charting.display.ChartDisplayer;
import ta4jexamples.charting.storage.ChartStorage;
import ta4jexamples.charting.workflow.ChartWorkflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    public void constructorAndBuildPlanRejectInvalidInputs() {
        BarSeries series = buildSeries(320, 4300.0d, 1.8d);
        ConfluenceReport report = new ConfluenceReportGenerator().generate("^GSPC", series);
        ConfluenceChartAdapter adapter = new ConfluenceChartAdapter(new ChartWorkflow());

        assertThrows(NullPointerException.class, () -> new ConfluenceChartAdapter(null));
        assertThrows(NullPointerException.class, () -> adapter.buildPlan(null, report, "title"));
        assertThrows(NullPointerException.class, () -> adapter.buildPlan(series, null, "title"));
        assertThrows(IllegalArgumentException.class, () -> adapter.buildPlan(series, report, "   "));
    }

    @Test
    public void saveDelegatesToWorkflowAndReturnsRecordedPath() {
        BarSeries series = buildSeries(340, 4300.0d, 2.1d);
        ConfluenceReport report = new ConfluenceReportGenerator().generate("^GSPC", series);
        RecordingChartWorkflow workflow = new RecordingChartWorkflow();
        ConfluenceChartAdapter adapter = new ConfluenceChartAdapter(workflow);
        Path directory = Path.of("temp", "charts", "confluence-tests");

        Optional<Path> saved = adapter.save(series, report, "Confluence Save Test", directory, "chart.png");

        assertTrue(saved.isPresent());
        assertEquals(directory.resolve("chart.png"), saved.get());
        assertNotNull(workflow.lastPlan);
        assertEquals(directory, workflow.lastDirectory);
        assertEquals("chart.png", workflow.lastFilename);
    }

    @Test
    public void saveRejectsInvalidDirectoryAndFilename() {
        BarSeries series = buildSeries(340, 4300.0d, 2.1d);
        ConfluenceReport report = new ConfluenceReportGenerator().generate("^GSPC", series);
        ConfluenceChartAdapter adapter = new ConfluenceChartAdapter(new ChartWorkflow());
        Path directory = Path.of("temp", "charts", "confluence-tests");

        assertThrows(NullPointerException.class, () -> adapter.save(series, report, "x", null, "chart.png"));
        assertThrows(IllegalArgumentException.class, () -> adapter.save(series, report, "x", directory, " "));
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

    private static final class RecordingChartWorkflow extends ChartWorkflow {
        private ChartPlan lastPlan;
        private Path lastDirectory;
        private String lastFilename;

        private RecordingChartWorkflow() {
            super(new TradingChartFactory(), new NoOpChartDisplayer(), ChartStorage.noOp());
        }

        @Override
        public Optional<Path> save(ChartPlan plan, Path directory, String filename) {
            this.lastPlan = plan;
            this.lastDirectory = directory;
            this.lastFilename = filename;
            return Optional.of(directory.resolve(filename));
        }
    }

    private static final class NoOpChartDisplayer implements ChartDisplayer {
        @Override
        public void display(JFreeChart chart) {
        }

        @Override
        public void display(JFreeChart chart, String windowTitle) {
        }
    }
}
