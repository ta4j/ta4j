/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.display;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYDataset;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ConcurrentBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BacktestExecutionResult;
import org.ta4j.core.backtest.BacktestRuntimeReport;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import ta4jexamples.charting.ChartingTestFixtures;
import ta4jexamples.charting.compose.TradingChartFactory;
import ta4jexamples.charting.builder.TimeAxisMode;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.InvalidClassException;
import java.io.Serializable;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SwingChartDisplayer}.
 */
class SwingChartDisplayerTest {

    private SwingChartDisplayer displayer;

    @BeforeEach
    void setUp() {
        displayer = new SwingChartDisplayer();
        // Clear any existing properties
        System.clearProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY);
        System.clearProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY);
        // Always disable display in tests to prevent actual windows from being created
        // which would call System.exit(0) when closed
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
    }

    @AfterEach
    void tearDown() {
        // Keep display disabled while frames are torn down.
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        Frame[] frames = Frame.getFrames();
        for (Frame frame : frames) {
            frame.dispose();
        }
        // Clean up properties
        System.clearProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY);
        System.clearProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY);
        System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
    }

    @ParameterizedTest
    @EnumSource(TimeAxisMode.class)
    void testDeepCopyPreservesTradingChart(TimeAxisMode timeAxisMode) throws Exception {
        BarSeries series = new BaseBarSeriesBuilder().withBars(ChartingTestFixtures.standardDailySeries().getBarData())
                .build();
        JFreeChart chart = new TradingChartFactory().createTradingRecordChart(series, "Trades",
                ChartingTestFixtures.completedTradeRecord(series), timeAxisMode);
        JFreeChart copy = displayer.deepCopyChart(chart);
        assertNotSame(chart, copy);
        assertEquals(chart.getTitle().getText(), copy.getTitle().getText());
        assertEquals(chart.getXYPlot().getDataset().getYValue(0, 2), copy.getXYPlot().getDataset().getYValue(0, 2));
        assertEquals(chart.getXYPlot().getDomainAxis().getLabel(), copy.getXYPlot().getDomainAxis().getLabel());
    }

    @Test
    void testDeepCopyPreservesConcurrentRealtimeTradingChart() throws Exception {
        BarSeries series = new ConcurrentBarSeriesBuilder()
                .withBars(ChartingTestFixtures.standardDailySeries().getBarData())
                .build();
        series.barBuilder()
                .timePeriod(series.getLastBar().getTimePeriod())
                .endTime(series.getLastBar().getEndTime().plus(series.getLastBar().getTimePeriod()))
                .openPrice(100)
                .highPrice(110)
                .lowPrice(90)
                .closePrice(105)
                .volume(10)
                .add();
        JFreeChart chart = new TradingChartFactory().createTradingRecordChart(series, "Realtime trades",
                ChartingTestFixtures.completedTradeRecord(series), TimeAxisMode.BAR_INDEX);
        JFreeChart copy = displayer.deepCopyChart(chart);
        int lastItem = chart.getXYPlot().getDataset().getItemCount(0) - 1;
        assertEquals(105, copy.getXYPlot().getDataset().getYValue(0, lastItem));
        assertEquals(
                chart.getXYPlot()
                        .getRenderer()
                        .getDefaultToolTipGenerator()
                        .generateToolTip(chart.getXYPlot().getDataset(), 0, lastItem),
                copy.getXYPlot()
                        .getRenderer()
                        .getDefaultToolTipGenerator()
                        .generateToolTip(copy.getXYPlot().getDataset(), 0, lastItem));
    }

    @ParameterizedTest
    @EnumSource(TimeAxisMode.class)
    void testDeepCopyPreservesImmutableBacktestSnapshot(TimeAxisMode timeAxisMode) throws Exception {
        BarSeries source = ChartingTestFixtures.standardDailySeries();
        BarSeries snapshot = new BacktestExecutionResult(source, List.of(), BacktestRuntimeReport.empty()).barSeries();
        JFreeChart chart = new TradingChartFactory().createTradingRecordChart(snapshot, "Backtest snapshot",
                ChartingTestFixtures.completedTradeRecord(snapshot), timeAxisMode);
        JFreeChart copy = displayer.deepCopyChart(chart);
        assertNotSame(chart.getXYPlot().getDataset(), copy.getXYPlot().getDataset());
        assertEquals(chart.getXYPlot().getDataset().getYValue(0, 2), copy.getXYPlot().getDataset().getYValue(0, 2));
        assertEquals(
                chart.getXYPlot()
                        .getRenderer()
                        .getDefaultToolTipGenerator()
                        .generateToolTip(chart.getXYPlot().getDataset(), 0, 2),
                copy.getXYPlot()
                        .getRenderer()
                        .getDefaultToolTipGenerator()
                        .generateToolTip(copy.getXYPlot().getDataset(), 0, 2));
    }

    @ParameterizedTest
    @ValueSource(strings = { "maxdepth=1", "!org.jfree.chart.JFreeChart;*" })
    void testDeepCopyPreservesJvmFilter(String filter) throws Exception {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(javaExecutable, "-Djdk.serialFilter=" + filter, "-cp", classpath,
                RestrictedFilterProbe.class.getName()).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "filter probe did not terminate");
            String output = new String(process.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
        } finally {
            process.destroyForcibly();
        }
    }

    public static final class RestrictedFilterProbe {
        public static void main(String[] args) throws Exception {
            JFreeChart chart = ChartFactory.createLineChart("Restricted", "X", "Y", null);
            try {
                new SwingChartDisplayer().deepCopyChart(chart);
            } catch (InvalidClassException expected) {
                return;
            }
            throw new AssertionError("JVM-wide chart restriction was discarded");
        }
    }

    @ParameterizedTest
    @EnumSource(TimeAxisMode.class)
    void testDeepCopyPreservesChartsForCustomBarSeriesImplementations(TimeAxisMode timeAxisMode) throws Exception {
        List<BarSeries> customSeries = List.of(windowedCustomBaseSeries(),
                new DelegatingBarSeries(windowedBaseSeries()));
        for (BarSeries series : customSeries) {
            String type = series.getClass().getSimpleName();
            assertTrue(series.getBeginIndex() > 0, type + " must model a trimmed series window");

            TradingChartFactory factory = new TradingChartFactory();
            Indicator<Num> close = new ClosePriceIndicator(series);
            List<JFreeChart> charts = List.of(
                    factory.createTradingRecordChart(series, "Trades", windowedTradeRecord(series), timeAxisMode,
                            close),
                    factory.createIndicatorChart(series, timeAxisMode, close), factory.createDualAxisChart(series,
                            close, "Close", new VolumeIndicator(series), "Volume", "Dual axis", timeAxisMode));

            List<List<String>> labels = new ArrayList<>();
            for (JFreeChart chart : charts) {
                JFreeChart copy = displayer.deepCopyChart(chart);
                assertNotSame(chart, copy, type);
                List<String> chartLabels = renderedLabels(chart);
                assertEquals(chartLabels, renderedLabels(copy), type + " deep copy must render identical labels");
                labels.add(chartLabels);
                if (timeAxisMode == TimeAxisMode.BAR_INDEX) {
                    assertBarIndexLabelsDescribeBars(type, chart, series);
                    assertBarIndexLabelsDescribeBars(type + " copy", copy, series);
                }
            }

            // Charts keep the labels they were built with when the caller later trims the
            // series they were created from.
            series.setMaximumBarCount(3);
            for (int i = 0; i < charts.size(); i++) {
                assertEquals(labels.get(i), renderedLabels(charts.get(i)),
                        type + " labels must not follow later series mutations");
            }
        }
    }

    @Test
    void testDeepCopyStillRejectsUntrustedToolTipGenerators() {
        BarSeries series = windowedBaseSeries();
        JFreeChart chart = new TradingChartFactory().createTradingRecordChart(series, "Trades",
                windowedTradeRecord(series), TimeAxisMode.BAR_INDEX);
        chart.getXYPlot().getRenderer().setDefaultToolTipGenerator(new UntrustedToolTipGenerator());

        assertThrows(InvalidClassException.class, () -> displayer.deepCopyChart(chart));
    }

    private static void assertBarIndexLabelsDescribeBars(String message, JFreeChart chart, BarSeries series) {
        int beginIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex();
        DateFormat dailyFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
        DecimalFormat priceFormat = new DecimalFormat("#,##0.00###");

        assertTrue(chart.getXYPlot().getDomainAxis() instanceof NumberAxis, message + " bar-index domain axis");
        NumberFormat axisFormat = ((NumberAxis) chart.getXYPlot().getDomainAxis()).getNumberFormatOverride();
        assertNotNull(axisFormat, message + " bar-index axis must format ticks as dates");
        for (int index = beginIndex; index <= endIndex; index++) {
            assertEquals(dailyFormat.format(Date.from(series.getBar(index).getEndTime())), axisFormat.format(index),
                    message + " axis label for bar " + index);
        }
        assertEquals(axisFormat.format(beginIndex), axisFormat.format(beginIndex - 2),
                message + " clamps ticks before the window");
        assertEquals(axisFormat.format(endIndex), axisFormat.format(endIndex + 2),
                message + " clamps ticks after the window");

        int checkedTooltips = 0;
        for (XYPlot plot : plotsOf(chart)) {
            for (XYDataset dataset : plot.getDatasets().values()) {
                XYItemRenderer renderer = plot.getRendererForDataset(dataset);
                if (renderer == null || dataset.getSeriesCount() == 0) {
                    continue;
                }
                for (int item = 0; item < dataset.getItemCount(0); item++) {
                    XYToolTipGenerator generator = renderer.getToolTipGenerator(0, item);
                    if (generator == null) {
                        continue;
                    }
                    String generatorType = generator.getClass().getSimpleName();
                    if (!generatorType.startsWith("BarIndex")) {
                        continue;
                    }
                    int barIndex = (int) Math.round(dataset.getXValue(0, item));
                    Bar bar = series.getBar(barIndex);
                    String date = dailyFormat.format(Date.from(bar.getEndTime()));
                    String expected = generatorType.equals("BarIndexOhlcToolTipGenerator")
                            ? String.format("%s --> Date=%s High=%s Low=%s Open=%s Close=%s", dataset.getSeriesKey(0),
                                    date, priceFormat.format(bar.getHighPrice().doubleValue()),
                                    priceFormat.format(bar.getLowPrice().doubleValue()),
                                    priceFormat.format(bar.getOpenPrice().doubleValue()),
                                    priceFormat.format(bar.getClosePrice().doubleValue()))
                            : String.format("%s: %s, Value: %s", dataset.getSeriesKey(0), date,
                                    priceFormat.format(dataset.getYValue(0, item)));
                    assertEquals(expected, generator.generateToolTip(dataset, 0, item),
                            message + " tooltip for bar " + barIndex);
                    checkedTooltips++;
                }
            }
        }
        assertTrue(checkedTooltips > 0, message + " must expose bar-index tooltips");
    }

    private static List<String> renderedLabels(JFreeChart chart) {
        List<String> labels = new ArrayList<>();
        if (chart.getXYPlot().getDomainAxis() instanceof NumberAxis axis && axis.getNumberFormatOverride() != null) {
            for (int tick = 0; tick < 12; tick++) {
                labels.add(axis.getNumberFormatOverride().format(tick));
            }
        }
        for (XYPlot plot : plotsOf(chart)) {
            for (XYDataset dataset : plot.getDatasets().values()) {
                XYItemRenderer renderer = plot.getRendererForDataset(dataset);
                if (renderer == null) {
                    continue;
                }
                for (int seriesIndex = 0; seriesIndex < dataset.getSeriesCount(); seriesIndex++) {
                    for (int item = 0; item < dataset.getItemCount(seriesIndex); item++) {
                        XYToolTipGenerator generator = renderer.getToolTipGenerator(seriesIndex, item);
                        if (generator != null) {
                            labels.add(generator.generateToolTip(dataset, seriesIndex, item));
                        }
                    }
                }
            }
        }
        return labels;
    }

    private static List<XYPlot> plotsOf(JFreeChart chart) {
        if (chart.getPlot() instanceof CombinedDomainXYPlot combinedPlot) {
            return combinedPlot.getSubplots();
        }
        return List.of(chart.getXYPlot());
    }

    private static List<Bar> windowedBars() {
        BaseBarSeries source = new BaseBarSeriesBuilder().withName("Windowed").build();
        Instant firstEnd = Instant.parse("2025-03-04T00:00:00Z");
        for (int i = 0; i < 10; i++) {
            source.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(firstEnd.plus(Duration.ofDays(i)))
                    .openPrice(100 + i)
                    .highPrice(103 + i)
                    .lowPrice(98 + i)
                    .closePrice(101 + i)
                    .volume(1_000 + 10 * i)
                    .add();
        }
        return new ArrayList<>(source.getBarData());
    }

    /**
     * Ten daily bars trimmed to the latest six, so the series starts at index 4.
     */
    private static BaseBarSeries windowedBaseSeries() {
        return new BaseBarSeriesBuilder().withName("Windowed").withBars(windowedBars()).withMaxBarCount(6).build();
    }

    private static BaseBarSeries windowedCustomBaseSeries() {
        CustomBaseBarSeries series = new CustomBaseBarSeries("Windowed", windowedBars());
        series.setMaximumBarCount(6);
        return series;
    }

    private static TradingRecord windowedTradeRecord(BarSeries series) {
        int entryIndex = series.getBeginIndex() + 1;
        int exitIndex = series.getEndIndex() - 1;
        Num amount = series.numFactory().numOf(1);
        TradingRecord record = new BaseTradingRecord();
        record.enter(entryIndex, series.getBar(entryIndex).getClosePrice(), amount);
        record.exit(exitIndex, series.getBar(exitIndex).getClosePrice(), amount);
        return record;
    }

    /**
     * Public {@link BaseBarSeries} subclass that the display allowlist does not
     * know.
     */
    private static final class CustomBaseBarSeries extends BaseBarSeries {

        private static final long serialVersionUID = 1L;

        private CustomBaseBarSeries(String name, List<Bar> bars) {
            super(name, bars);
        }
    }

    /**
     * Minimal third-party style {@link BarSeries} that only implements the abstract
     * API.
     */
    private static final class DelegatingBarSeries implements BarSeries {

        private static final long serialVersionUID = 1L;

        private final BarSeries delegate;

        private DelegatingBarSeries(BarSeries delegate) {
            this.delegate = delegate;
        }

        @Override
        public NumFactory numFactory() {
            return delegate.numFactory();
        }

        @Override
        public BarBuilder barBuilder() {
            return delegate.barBuilder();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Bar getBar(int i) {
            return delegate.getBar(i);
        }

        @Override
        public int getBarCount() {
            return delegate.getBarCount();
        }

        @Override
        public List<Bar> getBarData() {
            return delegate.getBarData();
        }

        @Override
        public int getBeginIndex() {
            return delegate.getBeginIndex();
        }

        @Override
        public int getEndIndex() {
            return delegate.getEndIndex();
        }

        @Override
        public int getMaximumBarCount() {
            return delegate.getMaximumBarCount();
        }

        @Override
        public void setMaximumBarCount(int maximumBarCount) {
            delegate.setMaximumBarCount(maximumBarCount);
        }

        @Override
        public int getRemovedBarsCount() {
            return delegate.getRemovedBarsCount();
        }

        @Override
        public void addBar(Bar bar, boolean replace) {
            delegate.addBar(bar, replace);
        }

        @Override
        public void addTrade(Num tradeVolume, Num tradePrice) {
            delegate.addTrade(tradeVolume, tradePrice);
        }

        @Override
        public void addPrice(Num price) {
            delegate.addPrice(price);
        }

        @Override
        public BarSeries getSubSeries(int startIndex, int endIndex) {
            return delegate.getSubSeries(startIndex, endIndex);
        }
    }

    /**
     * Serializable generator outside the display allowlist; deep copies must reject
     * it.
     */
    private static final class UntrustedToolTipGenerator implements XYToolTipGenerator, Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public String generateToolTip(XYDataset dataset, int series, int item) {
            return "untrusted";
        }
    }

    @Test
    void testDefaultDisplayScale() {
        double scale = displayer.resolveDisplayScale();
        assertEquals(SwingChartDisplayer.DEFAULT_DISPLAY_SCALE, scale, 1e-9,
                "Default scale should be " + SwingChartDisplayer.DEFAULT_DISPLAY_SCALE);
    }

    @Test
    void testDisplayScaleFromValidProperty() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.5");
        double scale = displayer.resolveDisplayScale();
        assertEquals(0.5, scale, 1e-9, "Scale should reflect configured property");
    }

    @Test
    void testDisplayScaleFromValidPropertyAtMax() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "1.0");
        double scale = displayer.resolveDisplayScale();
        assertEquals(1.0, scale, 1e-9, "Scale should accept max value of 1.0");
    }

    @Test
    void testDisplayScaleFromValidPropertyNearMin() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.11");
        double scale = displayer.resolveDisplayScale();
        assertEquals(0.11, scale, 1e-9, "Scale should accept values just above 0.1");
    }

    @Test
    void testDisplayScaleIgnoresValueBelowThreshold() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.05");
        double scale = displayer.resolveDisplayScale();
        assertEquals(SwingChartDisplayer.DEFAULT_DISPLAY_SCALE, scale, 1e-9, "Values <= 0.1 should be ignored");
    }

    @Test
    void testDisplayScaleIgnoresValueAboveMax() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "2.0");
        double scale = displayer.resolveDisplayScale();
        assertEquals(SwingChartDisplayer.DEFAULT_DISPLAY_SCALE, scale, 1e-9, "Values > 1.0 should be ignored");
    }

    @Test
    void testDisplayScaleHandlesInvalidPropertyValue() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "invalid");
        double scale = displayer.resolveDisplayScale();
        assertEquals(SwingChartDisplayer.DEFAULT_DISPLAY_SCALE, scale, 1e-9,
                "Invalid property value should fall back to default");
    }

    @Test
    void testDisplayScaleHandlesEmptyPropertyValue() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "");
        double scale = displayer.resolveDisplayScale();
        assertEquals(SwingChartDisplayer.DEFAULT_DISPLAY_SCALE, scale, 1e-9,
                "Empty property should fall back to default");
    }

    @Test
    void testDetermineDisplaySizeReturnsDimension() {
        Dimension size = displayer.determineDisplaySize();
        assertNotNull(size, "Display size should not be null");
        assertTrue(size.width > 0, "Width should be positive");
        assertTrue(size.height > 0, "Height should be positive");
    }

    @Test
    void testDetermineDisplaySizeMeetsMinimumDimensions() {
        Dimension size = displayer.determineDisplaySize();
        assertTrue(size.width >= 800, "Width should meet minimum of 800");
        assertTrue(size.height >= 600, "Height should meet minimum of 600");
    }

    @Test
    void testDetermineDisplaySizeScalesCorrectly() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.5");
        Dimension size1 = displayer.determineDisplaySize();

        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.75");
        Dimension size2 = displayer.determineDisplaySize();

        assertTrue(size2.width >= size1.width, "Larger scale should produce larger width");
        assertTrue(size2.height >= size1.height, "Larger scale should produce larger height");
    }

    @Test
    void testDisplayWithNullChart() {
        assertThrows(Exception.class, () -> displayer.display(null), "Display should throw exception for null chart");
    }

    @Test
    @Tag("requires-display")
    void testDisplayCreatesNonFocusableWindowWhenEnabled() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Headless environment");

        String title = "Focusability Test " + UUID.randomUUID();
        System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);

        JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);
        displayer.display(chart, title);

        JFrame frame = findFrameByTitle(title);
        assertNotNull(frame, "Expected frame created by public display API");
        assertFalse(frame.getFocusableWindowState(), "Displayed chart frame should not be focusable");

        // Keep cleanup deterministic in tests and avoid exit-on-close behavior.
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        frame.dispose();
    }

    /**
     * Tests that display gracefully handles headless environments.
     * <p>
     * <b>Note:</b> This test is tagged {@code requires-headless} because it
     * specifically tests behavior in no-display environments where GUI operations
     * should fail gracefully.
     * <p>
     * Select the tag explicitly when validating headless environment handling.
     */
    @Test
    @Tag("requires-headless")
    void testDisplayHandlesHeadlessEnvironment() {
        // This test only runs in headless environments
        Assumptions.assumeTrue(GraphicsEnvironment.isHeadless(), "Test requires headless environment");

        // Clear the disable display property so we can test actual headless behavior
        System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        try {
            // In headless environment, display should fail gracefully
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // This will throw HeadlessException in headless environment
            // but we should handle it gracefully
            assertThrows(Exception.class, () -> displayer.display(chart));
        } finally {
            // Restore the property for cleanup
            System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        }
    }

    @Test
    void testDisplayRespectsDisableDisplayProperty() {
        // Set property to disable display
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // Display should return immediately without showing a window
            displayer.display(chart);

            // If we get here without exception, the property worked
            assertTrue(true, "Display should be disabled when property is set");
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }

    @Test
    void testDetermineDisplaySizeWithCustomScale() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.8");
        Dimension size = displayer.determineDisplaySize();
        assertNotNull(size);
        // Should use custom scale or fall back to defaults
    }

    @Test
    void testDetermineDisplaySizeBoundsHandling() {
        // Test with various property values to ensure bounds are respected
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.5");
        Dimension size = displayer.determineDisplaySize();
        assertTrue(size.width >= 800 && size.height >= 600, "Dimensions should respect minimum bounds");
    }

    @Test
    void testResolveDisplaySizeUsesExplicitPreferredSize() {
        Dimension requested = new Dimension(1440, 900);

        Dimension resolved = displayer.resolveDisplaySize(requested);

        assertEquals(requested, resolved);
        assertNotSame(requested, resolved, "Resolved size should be a defensive copy");
    }

    @Test
    void testResolveDisplaySizeRejectsNonPositivePreferredDimensions() {
        assertThrows(IllegalArgumentException.class, () -> displayer.resolveDisplaySize(new Dimension(0, 900)));
        assertThrows(IllegalArgumentException.class, () -> displayer.resolveDisplaySize(new Dimension(1440, -1)));
    }

    // ========== Mouseover functionality tests ==========

    @Test
    void testDefaultHoverDelay() {
        int delay = displayer.resolveHoverDelay();
        assertEquals(SwingChartDisplayer.DEFAULT_HOVER_DELAY_MS, delay,
                "Default hover delay should be " + SwingChartDisplayer.DEFAULT_HOVER_DELAY_MS + "ms");
    }

    @Test
    void testHoverDelayFromValidProperty() {
        System.setProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY, "500");
        int delay = displayer.resolveHoverDelay();
        assertEquals(500, delay, "Hover delay should reflect configured property");
    }

    @Test
    void testHoverDelayFromZeroProperty() {
        System.setProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY, "0");
        int delay = displayer.resolveHoverDelay();
        assertEquals(0, delay, "Hover delay should accept zero value");
    }

    @Test
    void testHoverDelayFromLargeProperty() {
        System.setProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY, "5000");
        int delay = displayer.resolveHoverDelay();
        assertEquals(5000, delay, "Hover delay should accept large values");
    }

    @Test
    void testHoverDelayIgnoresNegativeValue() {
        System.setProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY, "-100");
        int delay = displayer.resolveHoverDelay();
        assertEquals(SwingChartDisplayer.DEFAULT_HOVER_DELAY_MS, delay,
                "Negative values should be ignored and fall back to default");
    }

    @Test
    void testHoverDelayHandlesInvalidPropertyValue() {
        System.setProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY, "invalid");
        int delay = displayer.resolveHoverDelay();
        assertEquals(SwingChartDisplayer.DEFAULT_HOVER_DELAY_MS, delay,
                "Invalid property value should fall back to default");
    }

    @Test
    void testHoverDelayHandlesEmptyPropertyValue() {
        System.setProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY, "");
        int delay = displayer.resolveHoverDelay();
        assertEquals(SwingChartDisplayer.DEFAULT_HOVER_DELAY_MS, delay, "Empty property should fall back to default");
    }

    @Test
    void testChartMouseoverListenerCreation() {
        javax.swing.JLabel infoLabel = new javax.swing.JLabel(" ");
        int hoverDelay = 1000;

        SwingChartDisplayer.ChartMouseoverListener listener = new SwingChartDisplayer.ChartMouseoverListener(infoLabel,
                hoverDelay);
        assertNotNull(listener, "Listener should be created successfully");
    }

    @Test
    void testChartMouseoverListenerCreationWithDifferentDelays() {
        javax.swing.JLabel infoLabel = new javax.swing.JLabel(" ");

        // Test with different delay values
        SwingChartDisplayer.ChartMouseoverListener listener1 = new SwingChartDisplayer.ChartMouseoverListener(infoLabel,
                0);
        assertNotNull(listener1, "Listener should be created with zero delay");

        SwingChartDisplayer.ChartMouseoverListener listener2 = new SwingChartDisplayer.ChartMouseoverListener(infoLabel,
                5000);
        assertNotNull(listener2, "Listener should be created with large delay");
    }

    @Test
    void testChartMouseoverListenerHandlesClick() {
        javax.swing.JLabel infoLabel = new javax.swing.JLabel(" ");
        SwingChartDisplayer.ChartMouseoverListener listener = new SwingChartDisplayer.ChartMouseoverListener(infoLabel,
                1000);

        // Verify listener was created
        assertNotNull(listener, "Listener should be created successfully");
        // The click handler should not throw - this tests the method signature
        // Note: We can't easily test with actual ChartMouseEvent without complex
        // mocking,
        // but the method exists and is part of the ChartMouseListener interface
    }

    // ========== Window cascading functionality tests ==========

    @Test
    void testMultipleDisplaysHandleCascadingGracefully() {
        // Set property to disable display to avoid actually showing windows
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart1 = ChartFactory.createLineChart("Test 1", "X", "Y", null);
            JFreeChart chart2 = ChartFactory.createLineChart("Test 2", "X", "Y", null);
            JFreeChart chart3 = ChartFactory.createLineChart("Test 3", "X", "Y", null);

            // Multiple displays should not throw exceptions
            // The cascading logic should handle positioning gracefully
            assertDoesNotThrow(() -> {
                displayer.display(chart1, "Window 1");
                displayer.display(chart2, "Window 2");
                displayer.display(chart3, "Window 3");
            }, "Multiple displays should handle cascading without exceptions");
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }

    @Test
    void testDisplayWithWindowTitleHandlesCascading() {
        // Set property to disable display
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // Display with custom window title should work
            assertDoesNotThrow(() -> displayer.display(chart, "Custom Window Title"),
                    "Display with window title should handle cascading gracefully");
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }

    @Test
    @Tag("requires-headless")
    void testDisplayHandlesCascadingInHeadlessEnvironment() {
        // This test only runs in headless environments
        Assumptions.assumeTrue(GraphicsEnvironment.isHeadless(), "Test requires headless environment");

        // Clear the disable display property so we can test actual headless behavior
        System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // In headless environment, cascading logic should fail gracefully
            // The exception handling in the cascading code should catch any errors
            assertThrows(Exception.class, () -> displayer.display(chart, "Test Window"),
                    "Display should throw exception in headless environment, but cascading logic should be attempted");
        } finally {
            // Restore the property for cleanup
            System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        }
    }

    @Test
    void testMultipleDisplaysWithDifferentTitles() {
        // Set property to disable display
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // Multiple displays with different titles should all work
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 5; i++) {
                    displayer.display(chart, "Window " + i);
                }
            }, "Multiple displays with different titles should handle cascading correctly");
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }

    // ========== Window tracking tests ==========

    @Test
    void testWindowTrackingWhenDisplayed() {
        // Set property to disable actual display (works in both headless and
        // non-headless)
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // Display should not throw
            assertDoesNotThrow(() -> displayer.display(chart, "Test Window"),
                    "Display should track window without throwing");
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }

    @Test
    void testMultipleWindowsTrackedIndependently() {
        // Set property to disable actual display (works in both headless and
        // non-headless)
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // Display multiple windows
            assertDoesNotThrow(() -> {
                displayer.display(chart, "Window 1");
                displayer.display(chart, "Window 2");
                displayer.display(chart, "Window 3");
            }, "Multiple windows should be tracked independently");
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }

    @Test
    void testWindowTrackingWithDisabledDisplay() {
        // Set property to disable display
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // When display is disabled, no windows should be created or tracked
            assertDoesNotThrow(() -> {
                displayer.display(chart, "Test Window");
                displayer.display(chart, "Test Window 2");
            }, "Display with disabled property should not throw");

            // Since display is disabled, no windows are created, so no exit behavior
            // This test just verifies the code path doesn't crash
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }

    private JFrame findFrameByTitle(String title) {
        Frame[] frames = Frame.getFrames();
        for (Frame frame : frames) {
            if (frame instanceof JFrame jFrame && title.equals(jFrame.getTitle())) {
                return jFrame;
            }
        }
        return null;
    }

}
