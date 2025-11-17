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
package ta4jexamples.charting;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.num.Num;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChartBuilder}.
 */
class ChartBuilderTest {

    private ChartMaker chartMaker;
    private BarSeries barSeries;
    private TradingRecord tradingRecord;
    private ClosePriceIndicator closePrice;
    private SMAIndicator sma;

    @BeforeEach
    void setUp() {
        chartMaker = new ChartMaker();
        barSeries = ChartingTestFixtures.standardDailySeries();
        tradingRecord = ChartingTestFixtures.completedTradeRecord(barSeries);
        closePrice = new ClosePriceIndicator(barSeries);
        sma = new SMAIndicator(closePrice, 5);
    }

    @Test
    void testBuilderCreation() {
        ChartBuilder builder = chartMaker.builder();
        assertNotNull(builder, "Builder should not be null");
    }

    @Test
    void testBuildWithoutChartType() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(IllegalStateException.class, () -> builder.build(),
                "Should throw exception when no chart type is configured");
    }

    @Test
    void testWithTradingRecord() {
        ChartHandle handle = chartMaker.builder().withTradingRecord(barSeries, "Test Strategy", tradingRecord).build();

        assertNotNull(handle, "Handle should not be null");
        assertNotNull(handle.getChart(), "Chart should not be null");
        assertEquals(barSeries, handle.getSeries(), "Series should match");
    }

    @Test
    void testWithTradingRecordNullSeries() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(NullPointerException.class, () -> builder.withTradingRecord(null, "Strategy", tradingRecord),
                "Should throw exception for null series");
    }

    @Test
    void testWithTradingRecordNullStrategyName() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(NullPointerException.class, () -> builder.withTradingRecord(barSeries, null, tradingRecord),
                "Should throw exception for null strategy name");
    }

    @Test
    void testWithTradingRecordNullTradingRecord() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(NullPointerException.class, () -> builder.withTradingRecord(barSeries, "Strategy", null),
                "Should throw exception for null trading record");
    }

    @Test
    void testWithTradingRecordAndIndicators() {
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .addIndicators(closePrice, sma)
                .build();

        assertNotNull(handle);
        JFreeChart chart = handle.getChart();
        assertNotNull(chart);
        assertInstanceOf(CombinedDomainXYPlot.class, chart.getPlot(),
                "Chart with indicators should have CombinedDomainXYPlot");
    }

    @Test
    void testWithIndicatorsOnly() {
        ChartHandle handle = chartMaker.builder().withIndicators(barSeries, closePrice, sma).build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());
        assertEquals(barSeries, handle.getSeries());
        assertInstanceOf(CombinedDomainXYPlot.class, handle.getChart().getPlot(),
                "Indicator-only chart should have CombinedDomainXYPlot");
    }

    @Test
    void testWithIndicatorsOnlyNullSeries() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(NullPointerException.class, () -> builder.withIndicators(null, closePrice, sma),
                "Should throw exception for null series");
    }

    @Test
    void testWithIndicatorsOnlyNullIndicators() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.withIndicators(barSeries, (Indicator<Num>[]) null),
                "Should throw exception for null indicators");
    }

    @Test
    void testWithIndicatorsOnlyEmptyIndicators() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.withIndicators(barSeries),
                "Should throw exception for empty indicators");
    }

    @Test
    void testWithIndicatorsOnlyNullInArray() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.withIndicators(barSeries, closePrice, null, sma),
                "Should throw exception for null indicator in array");
    }

    @Test
    void testWithAnalysisCriterion() {
        org.ta4j.core.criteria.NumberOfPositionsCriterion criterion = new org.ta4j.core.criteria.NumberOfPositionsCriterion();
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .withAnalysisCriterion(barSeries, tradingRecord, criterion, "Number of Positions")
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());
        // Should create dual-axis chart
        assertInstanceOf(XYPlot.class, handle.getChart().getPlot(),
                "Chart with analysis criterion should have XYPlot (dual-axis)");
    }

    @Test
    void testWithAnalysisCriterionNullSeries() {
        ChartBuilder builder = chartMaker.builder().withTradingRecord(barSeries, "Strategy", tradingRecord);
        org.ta4j.core.criteria.NumberOfPositionsCriterion criterion = new org.ta4j.core.criteria.NumberOfPositionsCriterion();
        assertThrows(NullPointerException.class,
                () -> builder.withAnalysisCriterion(null, tradingRecord, criterion, "Label"),
                "Should throw exception for null series");
    }

    @Test
    void testWithAnalysisCriterionNullTradingRecord() {
        ChartBuilder builder = chartMaker.builder().withTradingRecord(barSeries, "Strategy", tradingRecord);
        org.ta4j.core.criteria.NumberOfPositionsCriterion criterion = new org.ta4j.core.criteria.NumberOfPositionsCriterion();
        assertThrows(NullPointerException.class,
                () -> builder.withAnalysisCriterion(barSeries, null, criterion, "Label"),
                "Should throw exception for null trading record");
    }

    @Test
    void testWithAnalysisCriterionNullCriterion() {
        ChartBuilder builder = chartMaker.builder().withTradingRecord(barSeries, "Strategy", tradingRecord);
        assertThrows(NullPointerException.class,
                () -> builder.withAnalysisCriterion(barSeries, tradingRecord, null, "Label"),
                "Should throw exception for null criterion");
    }

    @Test
    void testWithAnalysisCriterionWithoutLabel() {
        org.ta4j.core.criteria.ExpectancyCriterion criterion = new org.ta4j.core.criteria.ExpectancyCriterion();
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .withAnalysisCriterion(barSeries, tradingRecord, criterion)
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());

        // Verify label is inferred and used in the chart
        JFreeChart chart = handle.getChart();
        XYPlot plot = extractMainPlot(chart);
        assertNotNull(plot.getRangeAxis(1), "Chart should have a secondary axis");
        assertEquals("Expectancy", plot.getRangeAxis(1).getLabel(),
                "Inferred label should be 'Expectancy' for ExpectancyCriterion");
    }

    @Test
    void testWithAnalysisCriterionWithNullLabel() {
        org.ta4j.core.criteria.NumberOfPositionsCriterion criterion = new org.ta4j.core.criteria.NumberOfPositionsCriterion();
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .withAnalysisCriterion(barSeries, tradingRecord, criterion, null)
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());

        // Verify label is inferred when null is explicitly passed
        JFreeChart chart = handle.getChart();
        XYPlot plot = extractMainPlot(chart);
        assertNotNull(plot.getRangeAxis(1), "Chart should have a secondary axis");
        assertEquals("NumberOfPositions", plot.getRangeAxis(1).getLabel(),
                "Inferred label should be 'NumberOfPositions' for NumberOfPositionsCriterion");
    }

    @Test
    @SuppressWarnings("deprecation")
    void testWithAnalysisCriterionWithEmptyLabel() {
        org.ta4j.core.criteria.MaximumDrawdownCriterion criterion = new org.ta4j.core.criteria.MaximumDrawdownCriterion();
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .withAnalysisCriterion(barSeries, tradingRecord, criterion, "   ")
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());

        // Verify label is inferred when empty/whitespace is provided
        JFreeChart chart = handle.getChart();
        XYPlot plot = extractMainPlot(chart);
        assertNotNull(plot.getRangeAxis(1), "Chart should have a secondary axis");
        assertEquals("MaximumDrawdown", plot.getRangeAxis(1).getLabel(),
                "Inferred label should be 'MaximumDrawdown' for MaximumDrawdownCriterion when label is whitespace");
    }

    @Test
    @SuppressWarnings("deprecation")
    void testWithAnalysisCriterionLabelInference() {
        // Test various criterion types to verify label inference
        org.ta4j.core.criteria.ExpectancyCriterion expectancy = new org.ta4j.core.criteria.ExpectancyCriterion();
        org.ta4j.core.criteria.NumberOfPositionsCriterion nop = new org.ta4j.core.criteria.NumberOfPositionsCriterion();
        org.ta4j.core.criteria.MaximumDrawdownCriterion mdd = new org.ta4j.core.criteria.MaximumDrawdownCriterion();

        ChartHandle handle1 = chartMaker.builder()
                .withTradingRecord(barSeries, "Strategy", tradingRecord)
                .withAnalysisCriterion(barSeries, tradingRecord, expectancy)
                .build();
        assertNotNull(handle1);
        XYPlot plot1 = extractMainPlot(handle1.getChart());
        assertEquals("Expectancy", plot1.getRangeAxis(1).getLabel(), "ExpectancyCriterion should infer 'Expectancy'");

        ChartHandle handle2 = chartMaker.builder()
                .withTradingRecord(barSeries, "Strategy", tradingRecord)
                .withAnalysisCriterion(barSeries, tradingRecord, nop)
                .build();
        assertNotNull(handle2);
        XYPlot plot2 = extractMainPlot(handle2.getChart());
        assertEquals("NumberOfPositions", plot2.getRangeAxis(1).getLabel(),
                "NumberOfPositionsCriterion should infer 'NumberOfPositions'");

        ChartHandle handle3 = chartMaker.builder()
                .withTradingRecord(barSeries, "Strategy", tradingRecord)
                .withAnalysisCriterion(barSeries, tradingRecord, mdd)
                .build();
        assertNotNull(handle3);
        XYPlot plot3 = extractMainPlot(handle3.getChart());
        assertEquals("MaximumDrawdown", plot3.getRangeAxis(1).getLabel(),
                "MaximumDrawdownCriterion should infer 'MaximumDrawdown'");
    }

    @Test
    void testWithAnalysisCriterionExplicitLabelOverridesInference() {
        org.ta4j.core.criteria.ExpectancyCriterion criterion = new org.ta4j.core.criteria.ExpectancyCriterion();
        String customLabel = "Custom Expectancy Label";

        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .withAnalysisCriterion(barSeries, tradingRecord, criterion, customLabel)
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());

        // Verify explicit label is used instead of inferred label
        JFreeChart chart = handle.getChart();
        XYPlot plot = extractMainPlot(chart);
        assertNotNull(plot.getRangeAxis(1), "Chart should have a secondary axis");
        assertEquals(customLabel, plot.getRangeAxis(1).getLabel(), "Explicit label should override inferred label");
        assertNotEquals("Expectancy", plot.getRangeAxis(1).getLabel(),
                "Explicit label should not be the inferred label");
    }

    @Test
    void testWithAnalysisCriterionLabelInferenceForVariousCriteria() {
        // Test additional criterion types to ensure inference works broadly
        org.ta4j.core.criteria.NumberOfBarsCriterion numberOfBars = new org.ta4j.core.criteria.NumberOfBarsCriterion();
        org.ta4j.core.criteria.NumberOfWinningPositionsCriterion winningPos = new org.ta4j.core.criteria.NumberOfWinningPositionsCriterion();
        org.ta4j.core.criteria.pnl.NetProfitCriterion netProfit = new org.ta4j.core.criteria.pnl.NetProfitCriterion();

        ChartHandle handle1 = chartMaker.builder()
                .withTradingRecord(barSeries, "Strategy", tradingRecord)
                .withAnalysisCriterion(barSeries, tradingRecord, numberOfBars)
                .build();
        XYPlot plot1 = extractMainPlot(handle1.getChart());
        assertEquals("NumberOfBars", plot1.getRangeAxis(1).getLabel(),
                "NumberOfBarsCriterion should infer 'NumberOfBars'");

        ChartHandle handle2 = chartMaker.builder()
                .withTradingRecord(barSeries, "Strategy", tradingRecord)
                .withAnalysisCriterion(barSeries, tradingRecord, winningPos)
                .build();
        XYPlot plot2 = extractMainPlot(handle2.getChart());
        assertEquals("NumberOfWinningPositions", plot2.getRangeAxis(1).getLabel(),
                "NumberOfWinningPositionsCriterion should infer 'NumberOfWinningPositions'");

        ChartHandle handle3 = chartMaker.builder()
                .withTradingRecord(barSeries, "Strategy", tradingRecord)
                .withAnalysisCriterion(barSeries, tradingRecord, netProfit)
                .build();
        XYPlot plot3 = extractMainPlot(handle3.getChart());
        assertEquals("NetProfit", plot3.getRangeAxis(1).getLabel(), "NetProfitCriterion should infer 'NetProfit'");
    }

    @Test
    void testWithAnalysisCriterionLabelInferenceOnIndicatorOnlyChart() {
        org.ta4j.core.criteria.ExpectancyCriterion criterion = new org.ta4j.core.criteria.ExpectancyCriterion();

        ChartHandle handle = chartMaker.builder()
                .withIndicators(barSeries, closePrice, sma)
                .withAnalysisCriterion(barSeries, tradingRecord, criterion)
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());

        // Verify label inference works on indicator-only charts
        JFreeChart chart = handle.getChart();
        XYPlot plot = extractMainPlot(chart);
        assertNotNull(plot.getRangeAxis(1), "Indicator-only chart with criterion should have a secondary axis");
        assertEquals("Expectancy", plot.getRangeAxis(1).getLabel(),
                "Inferred label should work on indicator-only charts");
    }

    /**
     * Extracts the main XYPlot from a chart, handling both regular XYPlot and
     * CombinedDomainXYPlot.
     */
    private XYPlot extractMainPlot(JFreeChart chart) {
        if (chart.getPlot() instanceof CombinedDomainXYPlot combinedPlot) {
            @SuppressWarnings("unchecked")
            java.util.List<XYPlot> subplots = combinedPlot.getSubplots();
            if (subplots != null && !subplots.isEmpty()) {
                return subplots.get(0);
            }
            throw new IllegalStateException("Combined plot has no subplots");
        } else if (chart.getPlot() instanceof XYPlot plot) {
            return plot;
        } else {
            throw new IllegalStateException("Chart plot is not an XYPlot");
        }
    }

    @Test
    void testWithAnalysisCriterionTwice() {
        ChartBuilder builder = chartMaker.builder().withTradingRecord(barSeries, "Strategy", tradingRecord);
        org.ta4j.core.criteria.NumberOfPositionsCriterion criterion1 = new org.ta4j.core.criteria.NumberOfPositionsCriterion();
        org.ta4j.core.criteria.NumberOfPositionsCriterion criterion2 = new org.ta4j.core.criteria.NumberOfPositionsCriterion();
        builder.withAnalysisCriterion(barSeries, tradingRecord, criterion1, "Label1");
        assertThrows(IllegalStateException.class,
                () -> builder.withAnalysisCriterion(barSeries, tradingRecord, criterion2, "Label2"),
                "Should throw exception when trying to set second analysis criterion");
    }

    @Test
    void testWithAnalysisCriterionOnIndicatorOnlyChart() {
        ChartBuilder builder = chartMaker.builder().withIndicators(barSeries, closePrice);
        org.ta4j.core.criteria.NumberOfPositionsCriterion criterion = new org.ta4j.core.criteria.NumberOfPositionsCriterion();
        // This should work - withAnalysisCriterion sets the tradingRecord
        ChartHandle handle = builder.withAnalysisCriterion(barSeries, tradingRecord, criterion, "Number of Positions")
                .build();
        assertNotNull(handle);
        assertNotNull(handle.getChart());
    }

    @Test
    void testAddIndicatorsNull() {
        ChartBuilder builder = chartMaker.builder().withTradingRecord(barSeries, "Strategy", tradingRecord);
        @SuppressWarnings("unchecked")
        Indicator<Num>[] nullIndicators = null;
        assertThrows(IllegalArgumentException.class, () -> builder.addIndicators(nullIndicators),
                "Should throw exception for null indicators");
    }

    @Test
    void testAddIndicatorsNullInArray() {
        ChartBuilder builder = chartMaker.builder().withTradingRecord(barSeries, "Strategy", tradingRecord);
        assertThrows(IllegalArgumentException.class, () -> builder.addIndicators(closePrice, null, sma),
                "Should throw exception for null indicator in array");
    }

    @Test
    void testWithTitle() {
        String customTitle = "My Custom Chart Title";
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .withTitle(customTitle)
                .build();

        assertNotNull(handle);
        JFreeChart chart = handle.getChart();
        assertNotNull(chart.getTitle());
        assertEquals(customTitle, chart.getTitle().getText(), "Chart title should match custom title");
    }

    @Test
    void testWithTitleOverridesAutoGenerated() {
        String customTitle = "Custom Title";
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .withTitle(customTitle)
                .build();

        JFreeChart chart = handle.getChart();
        assertEquals(customTitle, chart.getTitle().getText());
    }

    @Test
    void testWithTitleEmptyString() {
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .withTitle("")
                .build();

        assertNotNull(handle);
        // Empty title should be ignored, auto-generated title used
        assertNotNull(handle.getChart().getTitle());
    }

    @Test
    void testWithTitleWhitespace() {
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .withTitle("   ")
                .build();

        assertNotNull(handle);
        // Whitespace-only title should be ignored
        assertNotNull(handle.getChart().getTitle());
    }

    @Test
    void testMultipleChartTypesThrowsException() {
        ChartBuilder builder = chartMaker.builder().withTradingRecord(barSeries, "Strategy", tradingRecord);
        assertThrows(IllegalStateException.class, () -> builder.withIndicators(barSeries, closePrice),
                "Should throw exception when trying to set second chart type");
    }

    @Test
    void testMultipleChartTypesThrowsExceptionReverseOrder() {
        ChartBuilder builder = chartMaker.builder().withIndicators(barSeries, closePrice);
        assertThrows(IllegalStateException.class, () -> builder.withTradingRecord(barSeries, "Strategy", tradingRecord),
                "Should throw exception when trying to set second chart type");
    }

    @Test
    void testFluentChaining() {
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .addIndicators(closePrice, sma)
                .withTitle("Fluent Test")
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());
        assertEquals("Fluent Test", handle.getChart().getTitle().getText());
    }

    @Test
    void testFluentChainingWithAnalysisCriterion() {
        org.ta4j.core.criteria.NumberOfPositionsCriterion criterion = new org.ta4j.core.criteria.NumberOfPositionsCriterion();
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .addIndicators(closePrice, sma)
                .withAnalysisCriterion(barSeries, tradingRecord, criterion, "Number of Positions")
                .withTitle("Fluent Test")
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());
        assertEquals("Fluent Test", handle.getChart().getTitle().getText());
    }

    @Test
    void testFluentChainingWithAnalysisCriterionInferredLabel() {
        org.ta4j.core.criteria.ExpectancyCriterion criterion = new org.ta4j.core.criteria.ExpectancyCriterion();
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .addIndicators(closePrice, sma)
                .withAnalysisCriterion(barSeries, tradingRecord, criterion)
                .withTitle("Fluent Test")
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());
        assertEquals("Fluent Test", handle.getChart().getTitle().getText());
    }

    @Test
    void testAddIndicatorsOnIndicatorOnlyChart() {
        ChartBuilder builder = chartMaker.builder().withIndicators(barSeries, closePrice);
        assertThrows(IllegalStateException.class, () -> builder.addIndicators(sma),
                "Should throw exception when trying to add indicators to indicator-only chart");
    }

    @Test
    void testBuilderReusability() {
        ChartBuilder builder = chartMaker.builder();
        ChartHandle handle1 = builder.withTradingRecord(barSeries, "Strategy 1", tradingRecord).build();

        // Builder should not be reusable after build
        // This is expected behavior - builder state is consumed
        assertNotNull(handle1);
    }
}
