/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ta4jexamples.charting.builder;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.awt.BasicStroke;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

import ta4jexamples.charting.ChartingTestFixtures;
import ta4jexamples.charting.workflow.ChartWorkflow;

class ChartBuilderTest {

    private ChartWorkflow chartWorkflow;
    private BarSeries series;
    private TradingRecord tradingRecord;

    @BeforeEach
    void setUp() {
        chartWorkflow = new ChartWorkflow();
        series = ChartingTestFixtures.standardDailySeries();
        tradingRecord = ChartingTestFixtures.completedTradeRecord(series);
    }

    @Test
    void buildsCandlestickChartWithIndicatorOverlay() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(closePrice)
                .withLineColor(Color.CYAN)
                .toChart();

        assertNotNull(chart);
        assertInstanceOf(CombinedDomainXYPlot.class, chart.getPlot());
        CombinedDomainXYPlot combined = (CombinedDomainXYPlot) chart.getPlot();
        assertEquals(1, combined.getSubplots().size());
        XYPlot basePlot = combined.getSubplots().get(0);
        // Candle dataset + overlay dataset
        assertEquals(2, basePlot.getDatasetCount());
    }

    @Test
    void supportsIndicatorBaseChartsWithSubplot() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        JFreeChart chart = chartWorkflow.builder().withIndicator(closePrice).withSubChart(rsiIndicator).toChart();

        CombinedDomainXYPlot combined = (CombinedDomainXYPlot) chart.getPlot();
        assertEquals(2, combined.getSubplots().size(), "Indicator chart should contain base plot plus sub-plot");
    }

    @Test
    void tradingRecordOverlayAddsMarkers() {
        JFreeChart chart = chartWorkflow.builder().withSeries(series).withTradingRecordOverlay(tradingRecord).toChart();

        CombinedDomainXYPlot combined = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot basePlot = combined.getSubplots().get(0);
        assertTrue(basePlot.getRendererCount() >= 2,
                "Trading record overlay should install an additional renderer for markers");
    }

    @Test
    void overlayStylingAppliesCustomColorAndStroke() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(closePrice)
                .withLineColor(Color.ORANGE)
                .withLineWidth(3f)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        StandardXYItemRenderer renderer = (StandardXYItemRenderer) basePlot.getRenderer(1);
        assertEquals(Color.ORANGE, renderer.getSeriesPaint(0));
        BasicStroke stroke = (BasicStroke) renderer.getSeriesStroke(0);
        assertEquals(3f, stroke.getLineWidth());
    }

    @Test
    void withSubChartInvalidatesPreviousStage() {
        ChartBuilder.ChartStage baseStage = chartWorkflow.builder().withSeries(series);
        ChartBuilder.ChartStage subStage = baseStage.withSubChart(new ClosePriceIndicator(series));
        assertThrows(IllegalStateException.class, baseStage::toChart,
                "Parent stage should be unusable after withSubChart is invoked");

        assertNotNull(subStage.toChart(), "Sub stage should still be able to build the chart");
    }

    @Test
    void terminalOperationConsumesBuilder() {
        ChartBuilder.ChartStage stage = chartWorkflow.builder()
                .withSeries(series)
                .withTradingRecordOverlay(tradingRecord);
        stage.toChart();
        assertThrows(IllegalStateException.class, stage::toChart,
                "All terminal operations should fail after the builder is consumed");
    }

    @Test
    void exposesChartPlanForManualExecution() {
        ChartPlan plan = chartWorkflow.builder().withSeries(series).toPlan();
        JFreeChart chart = chartWorkflow.render(plan);
        assertNotNull(chart, "Rendered chart should not be null");
    }

    @Test
    void toPlanAlsoConsumesBuilder() {
        ChartBuilder.ChartStage stage = chartWorkflow.builder().withSeries(series);
        stage.toPlan();
        assertThrows(IllegalStateException.class, stage::toChart,
                "Invoking toPlan should consume the builder like other terminal operations");
    }

    @Test
    void analysisCriterionOverlayCreatesSecondaryAxis() {
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withTradingRecordOverlay(tradingRecord)
                .withAnalysisCriterionOverlay(new NetProfitCriterion(), tradingRecord)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        assertNotNull(basePlot.getRangeAxis(1), "Analysis overlays should create a secondary axis");
        assertEquals("NetProfit", basePlot.getRangeAxis(1).getLabel(), "Secondary axis should reflect criterion label");
        assertTrue(plotContainsSeries(basePlot, "NetProfit"),
                "Criterion overlay should contribute a dataset with its label");
    }

    @Test
    void tradingRecordOverlayIgnoredForIndicatorCharts() {
        ConstantIndicator baseIndicator = constantIndicator(1, "base-indicator");
        JFreeChart chart = chartWorkflow.builder()
                .withIndicator(baseIndicator)
                .withTradingRecordOverlay(tradingRecord)
                .toChart();

        XYPlot plot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        assertEquals(1, plot.getDatasetCount(),
                "Indicator charts should ignore trading record overlays due to incompatible axes");
    }

    @Test
    void rejectsOverlaysWhenBothAxesOccupied() {
        ConstantIndicator baseIndicator = constantIndicator(5, "base");
        ConstantIndicator near = constantIndicator(6, "near");
        ConstantIndicator far = constantIndicator(500, "far");
        ConstantIndicator rejected = constantIndicator(-500, "rejected");

        ChartBuilder.ChartStage dualAxisStage = chartWorkflow.builder()
                .withIndicator(baseIndicator)
                .withIndicatorOverlay(near)
                .withIndicatorOverlay(far);
        JFreeChart chartBefore = dualAxisStage.toChart();
        XYPlot plotBefore = ((CombinedDomainXYPlot) chartBefore.getPlot()).getSubplots().get(0);
        int datasetCountBefore = plotBefore.getDatasetCount();
        assertNotNull(plotBefore.getRangeAxis(1), "Secondary axis should be created for the far overlay");

        ChartBuilder.StyledOverlayStage rejectedStage = chartWorkflow.builder()
                .withIndicator(baseIndicator)
                .withIndicatorOverlay(near)
                .withIndicatorOverlay(far)
                .withIndicatorOverlay(rejected);

        assertThrows(IllegalStateException.class, () -> rejectedStage.withLineColor(Color.RED),
                "Styling an invalid overlay should fail fast");

        JFreeChart chart = rejectedStage.toChart();
        XYPlot plot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        assertNotNull(plot.getRangeAxis(1), "Secondary axis should persist after rejecting the overlay");
        assertEquals(datasetCountBefore, plot.getDatasetCount(),
                "Rejected overlay must not mutate the dataset collection");
    }

    @Test
    void overlayLineWidthValidation() {
        ChartBuilder.StyledOverlayStage stage = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(new ClosePriceIndicator(series));

        assertThrows(IllegalArgumentException.class, () -> stage.withLineWidth(0.01f),
                "Line width must be positive and above the minimum threshold");
        assertDoesNotThrow(stage::toChart);
    }

    private ConstantIndicator constantIndicator(double value, String name) {
        return new ConstantIndicator(series, value, name);
    }

    private boolean plotContainsSeries(XYPlot plot, String seriesName) {
        for (int i = 0; i < plot.getDatasetCount(); i++) {
            XYDataset dataset = plot.getDataset(i);
            if (dataset instanceof TimeSeriesCollection collection) {
                for (int j = 0; j < collection.getSeriesCount(); j++) {
                    if (seriesName.equals(collection.getSeriesKey(j))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static final class ConstantIndicator implements Indicator<Num> {
        private final Num value;
        private final String name;
        private final BarSeries series;

        private ConstantIndicator(BarSeries series, double value, String name) {
            this.series = series;
            this.value = series.numFactory().numOf(value);
            this.name = name;
        }

        @Override
        public Num getValue(int index) {
            return value;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }
    }
}
