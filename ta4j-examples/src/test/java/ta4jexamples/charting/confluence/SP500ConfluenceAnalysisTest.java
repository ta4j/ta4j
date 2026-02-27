/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.confluence;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jfree.chart.JFreeChart;
import org.junit.Assume;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.compose.TradingChartFactory;
import ta4jexamples.charting.display.ChartDisplayer;
import ta4jexamples.charting.storage.ChartStorage;
import ta4jexamples.charting.workflow.ChartWorkflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SP500ConfluenceAnalysisTest {

    @Test
    public void returnsEarlyWhenSeriesCannotBeLoaded(@TempDir Path tempDir) {
        AtomicBoolean workflowCalled = new AtomicBoolean(false);
        AtomicBoolean jsonCalled = new AtomicBoolean(false);

        SP500ConfluenceAnalysis.run(new String[] { "^GSPC", "420", tempDir.toString() }, (ticker, bars) -> null,
                new ConfluenceReportGenerator(), outputDir -> {
                    workflowCalled.set(true);
                    return new RecordingChartWorkflow();
                }, () -> true, (outputDir, safeTicker, report) -> {
                    jsonCalled.set(true);
                    return outputDir.resolve(safeTicker + ".json");
                });

        assertFalse(workflowCalled.get());
        assertFalse(jsonCalled.get());
    }

    @Test
    public void createsChartAndJsonArtifactsAndDisplaysWhenNotHeadless(@TempDir Path tempDir) {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        RecordingChartWorkflow workflow = new RecordingChartWorkflow();
        AtomicReference<Path> jsonPathRef = new AtomicReference<>();
        AtomicReference<String> safeTickerRef = new AtomicReference<>();

        SP500ConfluenceAnalysis.run(new String[] { "^GSPC", "420", tempDir.toString() },
                (ticker, bars) -> buildSeries(bars, 4200.0d, 2.5d), new ConfluenceReportGenerator(),
                outputDir -> workflow, () -> false, (outputDir, safeTicker, report) -> {
                    safeTickerRef.set(safeTicker);
                    Path jsonPath = SP500ConfluenceAnalysis.writeJsonReport(outputDir, safeTicker, report);
                    jsonPathRef.set(jsonPath);
                    return jsonPath;
                });

        assertEquals("_GSPC", safeTickerRef.get());
        assertEquals(tempDir.resolve("_GSPC-confluence.png"), workflow.lastSavedPath);
        assertEquals(1, workflow.displayCalls);
        assertNotNull(workflow.lastPlan);
        assertNotNull(jsonPathRef.get());
        assertTrue(Files.exists(jsonPathRef.get()));
    }

    @Test
    public void invalidBarsArgumentFallsBackToDefault(@TempDir Path tempDir) {
        AtomicInteger observedBars = new AtomicInteger(-1);
        SP500ConfluenceAnalysis.run(new String[] { "^GSPC", "invalid-bars", tempDir.toString() }, (ticker, bars) -> {
            observedBars.set(bars);
            return buildSeries(bars, 4200.0d, 2.5d);
        }, new ConfluenceReportGenerator(), outputDir -> new RecordingChartWorkflow(), () -> true,
                (outputDir, safeTicker, report) -> outputDir.resolve("report.json"));

        assertEquals(2520, observedBars.get());
    }

    @Test
    public void skipsDisplayWhenHeadless(@TempDir Path tempDir) {
        RecordingChartWorkflow workflow = new RecordingChartWorkflow();

        SP500ConfluenceAnalysis.run(new String[] { "^GSPC", "420", tempDir.toString() },
                (ticker, bars) -> buildSeries(bars, 4200.0d, 2.5d), new ConfluenceReportGenerator(),
                outputDir -> workflow, () -> true, (outputDir, safeTicker, report) -> outputDir.resolve("report.json"));

        assertEquals(0, workflow.displayCalls);
    }

    @Test
    public void validatesRunDependenciesAndTickerSanitization(@TempDir Path tempDir) {
        String[] args = new String[] { "^GSPC", "420", tempDir.toString() };
        ConfluenceReportGenerator generator = new ConfluenceReportGenerator();
        SP500ConfluenceAnalysis.WorkflowFactory workflowFactory = outputDir -> new RecordingChartWorkflow();
        SP500ConfluenceAnalysis.HeadlessProbe probe = () -> true;
        SP500ConfluenceAnalysis.JsonReportWriter writer = (outputDir, safeTicker, report) -> outputDir
                .resolve("x.json");
        SP500ConfluenceAnalysis.SeriesLoader loader = (ticker, bars) -> buildSeries(420, 4200.0d, 2.5d);

        assertThrows(NullPointerException.class,
                () -> SP500ConfluenceAnalysis.run(null, loader, generator, workflowFactory, probe, writer));
        assertThrows(NullPointerException.class,
                () -> SP500ConfluenceAnalysis.run(args, null, generator, workflowFactory, probe, writer));
        assertThrows(NullPointerException.class,
                () -> SP500ConfluenceAnalysis.run(args, loader, null, workflowFactory, probe, writer));
        assertThrows(NullPointerException.class,
                () -> SP500ConfluenceAnalysis.run(args, loader, generator, null, probe, writer));
        assertThrows(NullPointerException.class,
                () -> SP500ConfluenceAnalysis.run(args, loader, generator, workflowFactory, null, writer));
        assertThrows(NullPointerException.class,
                () -> SP500ConfluenceAnalysis.run(args, loader, generator, workflowFactory, probe, null));

        assertEquals("ABC_DEF", SP500ConfluenceAnalysis.sanitizeTicker("ABC^DEF"));
    }

    private static BarSeries buildSeries(int bars, double basePrice, double driftPerBar) {
        BarSeries series = new MockBarSeriesBuilder().withName("sp500-analysis-test-series").build();
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

    private static final class RecordingChartWorkflow extends ChartWorkflow {
        private int displayCalls;
        private ChartPlan lastPlan;
        private Path lastSavedPath;

        private RecordingChartWorkflow() {
            super(new TradingChartFactory(), new NoOpChartDisplayer(), ChartStorage.noOp());
        }

        @Override
        public Optional<Path> save(ChartPlan plan, Path directory, String filename) {
            this.lastPlan = plan;
            this.lastSavedPath = directory.resolve(filename);
            return Optional.of(lastSavedPath);
        }

        @Override
        public void display(ChartPlan plan, String windowTitle) {
            this.displayCalls++;
            this.lastPlan = plan;
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
