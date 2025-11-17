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
                .withIndicators(closePrice, sma)
                .build();

        assertNotNull(handle);
        JFreeChart chart = handle.getChart();
        assertNotNull(chart);
        assertInstanceOf(CombinedDomainXYPlot.class, chart.getPlot(),
                "Chart with indicators should have CombinedDomainXYPlot");
    }

    @Test
    void testWithTradingRecordAndAnalysis() {
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .addAnalysis(AnalysisType.MOVING_AVERAGE_20)
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());
    }

    @Test
    void testWithTradingRecordIndicatorsAndAnalysis() {
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .withIndicators(closePrice, sma)
                .addAnalysis(AnalysisType.MOVING_AVERAGE_20, AnalysisType.MOVING_AVERAGE_50)
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());
    }

    @Test
    void testWithAnalysis() {
        ChartHandle handle = chartMaker.builder().withAnalysis(barSeries, AnalysisType.MOVING_AVERAGE_20).build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());
        assertEquals(barSeries, handle.getSeries());
    }

    @Test
    void testWithAnalysisNullSeries() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(NullPointerException.class, () -> builder.withAnalysis(null, AnalysisType.MOVING_AVERAGE_20),
                "Should throw exception for null series");
    }

    @Test
    void testWithAnalysisNullAnalysisTypes() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.withAnalysis(barSeries, (AnalysisType[]) null),
                "Should throw exception for null analysis types");
    }

    @Test
    void testWithAnalysisNullAnalysisTypeInArray() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder.withAnalysis(barSeries, AnalysisType.MOVING_AVERAGE_20, null),
                "Should throw exception for null analysis type in array");
    }

    @Test
    void testWithAnalysisAndIndicators() {
        ChartHandle handle = chartMaker.builder()
                .withAnalysis(barSeries, AnalysisType.MOVING_AVERAGE_20)
                .withIndicators(closePrice, sma)
                .build();

        assertNotNull(handle);
        JFreeChart chart = handle.getChart();
        assertNotNull(chart);
        assertInstanceOf(CombinedDomainXYPlot.class, chart.getPlot(),
                "Chart with indicators should have CombinedDomainXYPlot");
    }

    @Test
    void testWithDualAxis() {
        ChartHandle handle = chartMaker.builder().withDualAxis(barSeries, closePrice, "Close", sma, "SMA").build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());
        assertEquals(barSeries, handle.getSeries());
    }

    @Test
    void testWithDualAxisNullSeries() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(NullPointerException.class, () -> builder.withDualAxis(null, closePrice, "Close", sma, "SMA"),
                "Should throw exception for null series");
    }

    @Test
    void testWithDualAxisNullPrimaryIndicator() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(NullPointerException.class, () -> builder.withDualAxis(barSeries, null, "Close", sma, "SMA"),
                "Should throw exception for null primary indicator");
    }

    @Test
    void testWithDualAxisNullPrimaryLabel() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(NullPointerException.class, () -> builder.withDualAxis(barSeries, closePrice, null, sma, "SMA"),
                "Should throw exception for null primary label");
    }

    @Test
    void testWithDualAxisNullSecondaryIndicator() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(NullPointerException.class,
                () -> builder.withDualAxis(barSeries, closePrice, "Close", null, "SMA"),
                "Should throw exception for null secondary indicator");
    }

    @Test
    void testWithDualAxisNullSecondaryLabel() {
        ChartBuilder builder = chartMaker.builder();
        assertThrows(NullPointerException.class, () -> builder.withDualAxis(barSeries, closePrice, "Close", sma, null),
                "Should throw exception for null secondary label");
    }

    @Test
    void testWithDualAxisAndIndicators() {
        SMAIndicator sma2 = new SMAIndicator(closePrice, 10);
        ChartHandle handle = chartMaker.builder()
                .withDualAxis(barSeries, closePrice, "Close", sma, "SMA")
                .withIndicators(sma2)
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());
    }

    @Test
    void testWithIndicatorsNull() {
        ChartBuilder builder = chartMaker.builder().withTradingRecord(barSeries, "Strategy", tradingRecord);
        @SuppressWarnings("unchecked")
        Indicator<Num>[] nullIndicators = null;
        assertThrows(IllegalArgumentException.class, () -> builder.withIndicators(nullIndicators),
                "Should throw exception for null indicators");
    }

    @Test
    void testWithIndicatorsNullInArray() {
        ChartBuilder builder = chartMaker.builder().withTradingRecord(barSeries, "Strategy", tradingRecord);
        assertThrows(IllegalArgumentException.class, () -> builder.withIndicators(closePrice, null, sma),
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
        assertThrows(IllegalStateException.class, () -> builder.withAnalysis(barSeries, AnalysisType.MOVING_AVERAGE_20),
                "Should throw exception when trying to set second chart type");
    }

    @Test
    void testMultipleChartTypesThrowsExceptionReverseOrder() {
        ChartBuilder builder = chartMaker.builder().withAnalysis(barSeries, AnalysisType.MOVING_AVERAGE_20);
        assertThrows(IllegalStateException.class, () -> builder.withTradingRecord(barSeries, "Strategy", tradingRecord),
                "Should throw exception when trying to set second chart type");
    }

    @Test
    void testMultipleChartTypesThrowsExceptionDualAxis() {
        ChartBuilder builder = chartMaker.builder().withDualAxis(barSeries, closePrice, "Close", sma, "SMA");
        assertThrows(IllegalStateException.class, () -> builder.withTradingRecord(barSeries, "Strategy", tradingRecord),
                "Should throw exception when trying to set second chart type");
    }

    @Test
    void testFluentChaining() {
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .withIndicators(closePrice, sma)
                .addAnalysis(AnalysisType.MOVING_AVERAGE_20)
                .withTitle("Fluent Test")
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());
        assertEquals("Fluent Test", handle.getChart().getTitle().getText());
    }

    @Test
    void testAddAnalysis() {
        ChartHandle handle = chartMaker.builder()
                .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                .addAnalysis(AnalysisType.MOVING_AVERAGE_20, AnalysisType.MOVING_AVERAGE_50)
                .build();

        assertNotNull(handle);
        assertNotNull(handle.getChart());
    }

    @Test
    void testAddAnalysisNull() {
        ChartBuilder builder = chartMaker.builder().withTradingRecord(barSeries, "Strategy", tradingRecord);
        assertThrows(IllegalArgumentException.class, () -> builder.addAnalysis((AnalysisType[]) null),
                "Should throw exception for null analysis types");
    }

    @Test
    void testAddAnalysisNullInArray() {
        ChartBuilder builder = chartMaker.builder().withTradingRecord(barSeries, "Strategy", tradingRecord);
        assertThrows(IllegalArgumentException.class, () -> builder.addAnalysis(AnalysisType.MOVING_AVERAGE_20, null),
                "Should throw exception for null analysis type in array");
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
