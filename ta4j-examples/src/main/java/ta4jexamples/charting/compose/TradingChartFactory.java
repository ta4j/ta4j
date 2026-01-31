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
package ta4jexamples.charting.compose;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYDifferenceRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.Layer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Minute;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.OHLCDataItem;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.ta4j.core.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.SwingPointMarkerIndicator;
import org.ta4j.core.num.Num;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.builder.ChartBuilder;
import ta4jexamples.charting.builder.TimeAxisMode;
import ta4jexamples.charting.renderer.BaseCandleStickRenderer;

import java.awt.*;
import java.io.Serializable;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.time.Duration;
import java.util.Locale;
import java.time.Instant;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Builds {@link JFreeChart} instances with different overlays for TA4J trading
 * data.
 *
 * @since 0.19
 */
public final class TradingChartFactory {

    private static final Logger LOG = LogManager.getLogger(TradingChartFactory.class);

    private static final Color CHART_BACKGROUND_COLOR = Color.BLACK;
    private static final float CHART_BACKGROUND_ALPHA = 0.85f;
    private static final Color GRIDLINE_COLOR = new Color(0x232323);
    private static final Color BUY_MARKER_COLOR = new Color(0x26A69A);
    private static final Color SELL_MARKER_COLOR = new Color(0xEF5350);
    private static final float SWING_MARKER_DEFAULT_OPACITY = 0.9f;
    private static final Color TRADE_LABEL_BACKGROUND = new Color(0, 0, 0, 170);
    private static final float POSITION_BAND_ALPHA = 0.14f;
    private static final int TRADE_MARKER_SIZE = 7;
    private static final Color[] POSITION_BAND_COLORS = { new Color(33, 150, 243), new Color(156, 39, 176),
            new Color(76, 175, 80) };
    private static final Font TRADE_LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private static final Font POSITION_LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font OVERLAY_LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final int LABEL_EDGE_BARS = 5;
    private static final int MAX_AXIS_LABEL_LENGTH = 30;
    private static final String DATE_AXIS_LABEL = "Date";
    private static final String BAR_INDEX_AXIS_LABEL = "Bar Index";
    private static final AxisFactory AXIS_FACTORY = new AxisFactory();
    private static final ThreadLocal<DecimalFormat> PRICE_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00###");
        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);
        return decimalFormat;
    });

    private final DatasetFactory datasetFactory = new DatasetFactory();
    private final OverlayRendererFactory overlayRendererFactory = new OverlayRendererFactory();

    /**
     * Returns a locale-aware date formatter for daily charts. Uses the default
     * locale's date format preferences.
     *
     * @return DateFormat instance for daily date formatting
     */
    private static DateFormat getDailyDateFormat() {
        return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
    }

    /**
     * Returns a locale-aware date-time formatter for intraday charts. Uses the
     * default locale's date and time format preferences.
     *
     * @return DateFormat instance for intraday date-time formatting
     */
    private static DateFormat getIntradayDateFormat() {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault());
    }

    private static DateFormat resolveDateFormat(Duration duration) {
        return duration.toDays() >= 1 ? getDailyDateFormat() : getIntradayDateFormat();
    }

    private static String resolveDomainAxisLabel(TimeAxisMode timeAxisMode) {
        return AXIS_FACTORY.axisLabel(timeAxisMode);
    }

    private static double toDomainValue(BarSeries series, int index, TimeAxisMode timeAxisMode) {
        return AXIS_FACTORY.domainValue(series, index, timeAxisMode);
    }

    /**
     * Truncates a label to fit within the maximum axis label length, adding
     * ellipsis if needed. This prevents axis labels from overlapping in
     * multi-subplot charts.
     *
     * @param label the original label text
     * @return truncated label with ellipsis if needed, or original label if it fits
     */
    private static String truncateAxisLabel(String label) {
        if (label == null || label.length() <= MAX_AXIS_LABEL_LENGTH) {
            return label;
        }
        return label.substring(0, MAX_AXIS_LABEL_LENGTH - 3) + "...";
    }

    private ValueAxis createDomainAxis(BarSeries series, Duration duration, TimeAxisMode timeAxisMode, String label,
            double lowerMargin, double upperMargin) {
        return AXIS_FACTORY.createDomainAxis(series, duration, timeAxisMode, label, lowerMargin, upperMargin);
    }

    private static final class AxisFactory {

        private static final TimeAxisModeStrategy REAL_TIME_AXIS_STRATEGY = new RealTimeAxisModeStrategy();
        private static final TimeAxisModeStrategy BAR_INDEX_AXIS_STRATEGY = new BarIndexAxisModeStrategy();

        String axisLabel(TimeAxisMode timeAxisMode) {
            return resolveTimeAxisModeStrategy(timeAxisMode).axisLabel();
        }

        double domainValue(BarSeries series, int index, TimeAxisMode timeAxisMode) {
            return resolveTimeAxisModeStrategy(timeAxisMode).domainValue(series, index);
        }

        ValueAxis createDomainAxis(BarSeries series, Duration duration, TimeAxisMode timeAxisMode, String label,
                double lowerMargin, double upperMargin) {
            return resolveTimeAxisModeStrategy(timeAxisMode).createAxis(series, duration, label, lowerMargin,
                    upperMargin);
        }

        private static TimeAxisModeStrategy resolveTimeAxisModeStrategy(TimeAxisMode timeAxisMode) {
            return timeAxisMode == TimeAxisMode.BAR_INDEX ? BAR_INDEX_AXIS_STRATEGY : REAL_TIME_AXIS_STRATEGY;
        }

        private static void applyDomainAxisStyle(ValueAxis axis, double lowerMargin, double upperMargin) {
            axis.setAutoRange(true);
            axis.setLowerMargin(lowerMargin);
            axis.setUpperMargin(upperMargin);
            axis.setTickLabelPaint(Color.LIGHT_GRAY);
            axis.setLabelPaint(Color.LIGHT_GRAY);
        }

        private interface TimeAxisModeStrategy {

            String axisLabel();

            double domainValue(BarSeries series, int index);

            ValueAxis createAxis(BarSeries series, Duration duration, String label, double lowerMargin,
                    double upperMargin);
        }

        private static final class RealTimeAxisModeStrategy implements TimeAxisModeStrategy {

            @Override
            public String axisLabel() {
                return DATE_AXIS_LABEL;
            }

            @Override
            public double domainValue(BarSeries series, int index) {
                return series.getBar(index).getEndTime().toEpochMilli();
            }

            @Override
            public ValueAxis createAxis(BarSeries series, Duration duration, String label, double lowerMargin,
                    double upperMargin) {
                DateAxis axis = new DateAxis(label);
                axis.setDateFormatOverride(resolveDateFormat(duration));
                applyDomainAxisStyle(axis, lowerMargin, upperMargin);
                return axis;
            }
        }

        private static final class BarIndexAxisModeStrategy implements TimeAxisModeStrategy {

            @Override
            public String axisLabel() {
                return BAR_INDEX_AXIS_LABEL;
            }

            @Override
            public double domainValue(BarSeries series, int index) {
                return index;
            }

            @Override
            public ValueAxis createAxis(BarSeries series, Duration duration, String label, double lowerMargin,
                    double upperMargin) {
                NumberAxis axis = new NumberAxis(label);
                axis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
                axis.setNumberFormatOverride(new BarIndexDateFormat(series, resolveDateFormat(duration)));
                applyDomainAxisStyle(axis, lowerMargin, upperMargin);
                return axis;
            }
        }
    }

    private TimeAxisMode requireTimeAxisMode(TimeAxisMode timeAxisMode) {
        return Objects.requireNonNull(timeAxisMode, "Time axis mode cannot be null");
    }

    public JFreeChart createTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        return createTradingRecordChart(series, strategyName, tradingRecord, TimeAxisMode.REAL_TIME);
    }

    public JFreeChart createTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord,
            TimeAxisMode timeAxisMode) {
        TimeAxisMode resolvedTimeAxisMode = requireTimeAxisMode(timeAxisMode);
        DefaultOHLCDataset data = datasetFactory.createChartDataset(series, resolvedTimeAxisMode);
        String chartTitle = buildChartTitle(series.getName(), strategyName);
        JFreeChart chart = buildChart(chartTitle, series, series.getFirstBar().getTimePeriod(), data,
                resolvedTimeAxisMode);
        addTradingRecordToChart((XYPlot) chart.getPlot(), series, tradingRecord, resolvedTimeAxisMode);
        return chart;
    }

    @SafeVarargs
    public final JFreeChart createTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord,
            Indicator<Num>... indicators) {
        return createTradingRecordChart(series, strategyName, tradingRecord, TimeAxisMode.REAL_TIME, indicators);
    }

    @SafeVarargs
    public final JFreeChart createTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord,
            TimeAxisMode timeAxisMode, Indicator<Num>... indicators) {
        TimeAxisMode resolvedTimeAxisMode = requireTimeAxisMode(timeAxisMode);
        if (indicators == null || indicators.length == 0) {
            return createTradingRecordChart(series, strategyName, tradingRecord, resolvedTimeAxisMode);
        }

        JFreeChart chart = createIndicatorChart(series, resolvedTimeAxisMode, indicators);
        String chartTitle = buildChartTitle(series.getName(), strategyName);
        if (chart.getTitle() != null) {
            chart.getTitle().setText(chartTitle);
        } else {
            chart.setTitle(chartTitle);
            if (chart.getTitle() != null) {
                chart.getTitle().setPaint(Color.LIGHT_GRAY);
            }
        }

        if (chart.getPlot() instanceof CombinedDomainXYPlot combinedPlot) {
            List<XYPlot> subplots = combinedPlot.getSubplots();
            if (subplots != null && !subplots.isEmpty()) {
                addTradingRecordToChart(subplots.get(0), series, tradingRecord, resolvedTimeAxisMode);
            }
        } else if (chart.getPlot() instanceof XYPlot plot) {
            addTradingRecordToChart(plot, series, tradingRecord, resolvedTimeAxisMode);
        }

        return chart;
    }

    @SafeVarargs
    public final JFreeChart createIndicatorChart(BarSeries series, Indicator<Num>... indicators) {
        return createIndicatorChart(series, TimeAxisMode.REAL_TIME, indicators);
    }

    @SafeVarargs
    public final JFreeChart createIndicatorChart(BarSeries series, TimeAxisMode timeAxisMode,
            Indicator<Num>... indicators) {
        TimeAxisMode resolvedTimeAxisMode = requireTimeAxisMode(timeAxisMode);
        if (indicators == null || indicators.length == 0) {
            // No indicators, return simple OHLC chart
            DefaultOHLCDataset data = datasetFactory.createChartDataset(series, resolvedTimeAxisMode);
            String chartTitle = series.toString();
            if (series.getName() != null) {
                chartTitle = series.getName();
            }
            return buildChart(chartTitle, series, series.getFirstBar().getTimePeriod(), data, resolvedTimeAxisMode);
        }

        // Create shared domain axis (X-axis)
        Duration duration = series.getFirstBar().getTimePeriod();
        ValueAxis domainAxis = createDomainAxis(series, duration, resolvedTimeAxisMode,
                resolveDomainAxisLabel(resolvedTimeAxisMode), 0.03, 0.07);

        // Create combined plot
        CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(domainAxis);
        combinedPlot.setGap(10.0);
        combinedPlot.setOrientation(PlotOrientation.VERTICAL);
        combinedPlot.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        combinedPlot.setBackgroundAlpha(CHART_BACKGROUND_ALPHA);
        combinedPlot.setDomainGridlinePaint(GRIDLINE_COLOR);
        combinedPlot.setRangeGridlinePaint(GRIDLINE_COLOR);

        // Create main OHLC candlestick plot
        DefaultOHLCDataset ohlcData = datasetFactory.createChartDataset(series, resolvedTimeAxisMode);
        XYPlot mainPlot = createOHLCPlot(series, duration, ohlcData, resolvedTimeAxisMode);
        combinedPlot.add(mainPlot, calculateMainPlotWeight(indicators.length));

        // Create a separate subplot for each indicator
        for (Indicator<Num> indicator : indicators) {
            XYPlot indicatorPlot = createIndicatorSubplot(series, indicator, resolvedTimeAxisMode);
            combinedPlot.add(indicatorPlot, calculateIndicatorPlotWeight(indicators.length));
        }

        // Create chart with combined plot
        String chartTitle = series.toString();
        if (series.getName() != null) {
            chartTitle = series.getName();
        }
        JFreeChart chart = new JFreeChart(chartTitle, null, combinedPlot, true);
        applyChartStyling(chart);

        return chart;
    }

    public JFreeChart compose(ChartBuilder.ChartDefinition definition) {
        Objects.requireNonNull(definition, "Chart definition cannot be null");
        ChartBuilder.PlotDefinition baseDefinition = Objects.requireNonNull(definition.basePlot(),
                "Base plot cannot be null");

        TimeAxisMode timeAxisMode = requireTimeAxisMode(definition.timeAxisMode());
        CombinedDomainXYPlot combinedPlot = createCombinedPlot(baseDefinition.series(), timeAxisMode);
        XYPlot basePlot = buildPlotFromDefinition(baseDefinition, timeAxisMode);
        combinedPlot.add(basePlot, calculateMainPlotWeight(definition.subplots().size()));

        if (!definition.subplots().isEmpty()) {
            int subplotWeight = calculateIndicatorPlotWeight(definition.subplots().size());
            for (ChartBuilder.PlotDefinition subplot : definition.subplots()) {
                combinedPlot.add(buildPlotFromDefinition(subplot, timeAxisMode), subplotWeight);
            }
        }

        String resolvedTitle = definition.title() != null && !definition.title().trim().isEmpty() ? definition.title()
                : buildChartTitle(baseDefinition.series() != null ? baseDefinition.series().getName() : "", "");
        JFreeChart chart = new JFreeChart(resolvedTitle, JFreeChart.DEFAULT_TITLE_FONT, combinedPlot, true);
        applyChartStyling(chart);
        return chart;
    }

    public JFreeChart createDualAxisChart(BarSeries series, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> secondaryIndicator, String secondaryLabel) {
        return createDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator, secondaryLabel, null,
                TimeAxisMode.REAL_TIME);
    }

    public JFreeChart createDualAxisChart(BarSeries series, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> secondaryIndicator, String secondaryLabel, String chartTitle) {
        return createDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator, secondaryLabel,
                chartTitle, TimeAxisMode.REAL_TIME);
    }

    public JFreeChart createDualAxisChart(BarSeries series, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> secondaryIndicator, String secondaryLabel, String chartTitle, TimeAxisMode timeAxisMode) {
        TimeAxisMode resolvedTimeAxisMode = requireTimeAxisMode(timeAxisMode);
        String effectiveTitle = chartTitle != null && !chartTitle.trim().isEmpty() ? chartTitle
                : (series.getName() != null ? series.getName() : series.toString());
        Duration duration = series.getFirstBar().getTimePeriod();

        if (resolvedTimeAxisMode == TimeAxisMode.REAL_TIME) {
            TimeSeriesCollection primaryDataset = datasetFactory.createTimeSeriesDataset(series, primaryIndicator,
                    primaryLabel, false);
            TimeSeriesCollection secondaryDataset = datasetFactory.createTimeSeriesDataset(series, secondaryIndicator,
                    secondaryLabel, false);

            JFreeChart chart = ChartFactory.createTimeSeriesChart(effectiveTitle,
                    resolveDomainAxisLabel(resolvedTimeAxisMode), primaryLabel, primaryDataset, true, true, false);
            applyChartStyling(chart);

            XYPlot plot = (XYPlot) chart.getPlot();
            configureDualAxisPlot(plot, series, duration, resolvedTimeAxisMode);
            addSecondaryAxis(plot, secondaryDataset, secondaryLabel, resolvedTimeAxisMode, series, duration);

            return chart;
        }

        XYSeriesCollection primaryDataset = datasetFactory.createIndexSeriesDataset(series, primaryIndicator,
                primaryLabel, false);
        XYSeriesCollection secondaryDataset = datasetFactory.createIndexSeriesDataset(series, secondaryIndicator,
                secondaryLabel, false);

        StandardXYItemRenderer primaryRenderer = new StandardXYItemRenderer();
        DateFormat dateFormat = resolveDateFormat(duration);
        primaryRenderer.setDefaultToolTipGenerator(new BarIndexToolTipGenerator(series, dateFormat));

        XYPlot plot = new XYPlot(primaryDataset, null, new NumberAxis(primaryLabel), primaryRenderer);
        configureDualAxisPlot(plot, series, duration, resolvedTimeAxisMode);
        addSecondaryAxis(plot, secondaryDataset, secondaryLabel, resolvedTimeAxisMode, series, duration);

        JFreeChart chart = new JFreeChart(effectiveTitle, JFreeChart.DEFAULT_TITLE_FONT, plot, true);
        applyChartStyling(chart);

        return chart;
    }

    /**
     * Adds an analysis criterion to an existing chart, converting it to a dual-axis
     * chart if necessary.
     *
     * @param chart              the chart to modify
     * @param series             the bar series
     * @param criterionIndicator the analysis criterion indicator
     * @param criterionLabel     the label for the criterion axis
     * @return the modified chart (may be a new instance if conversion was needed)
     */
    JFreeChart addAnalysisCriterionToChart(JFreeChart chart, BarSeries series, Indicator<Num> criterionIndicator,
            String criterionLabel) {
        if (chart == null || series == null || criterionIndicator == null) {
            return chart;
        }

        // Get the main plot
        XYPlot mainPlot;
        if (chart.getPlot() instanceof CombinedDomainXYPlot combinedPlot) {
            List<XYPlot> subplots = combinedPlot.getSubplots();
            if (subplots != null && !subplots.isEmpty()) {
                mainPlot = subplots.get(0);
            } else {
                return chart;
            }
        } else if (chart.getPlot() instanceof XYPlot plot) {
            mainPlot = plot;
        } else {
            return chart;
        }

        TimeAxisMode timeAxisMode = mainPlot.getDomainAxis() instanceof NumberAxis ? TimeAxisMode.BAR_INDEX
                : TimeAxisMode.REAL_TIME;

        XYDataset criterionDataset = timeAxisMode == TimeAxisMode.BAR_INDEX
                ? datasetFactory.createIndexSeriesDataset(series, criterionIndicator, criterionLabel, false)
                : datasetFactory.createTimeSeriesDataset(series, criterionIndicator, criterionLabel, false);

        addSecondaryAxis(mainPlot, criterionDataset, criterionLabel, timeAxisMode, series,
                series.getFirstBar().getTimePeriod());

        return chart;
    }

    public String buildChartTitle(String barSeriesName, String strategyName) {
        if (barSeriesName == null || barSeriesName.trim().isEmpty()) {
            return strategyName;
        }
        String[] shortenedBarSeriesName = barSeriesName.split(" ");
        if (strategyName == null || strategyName.trim().isEmpty()) {
            return shortenedBarSeriesName[0];
        }
        return strategyName + "@" + shortenedBarSeriesName[0];
    }

    private JFreeChart buildChart(String chartTitle, BarSeries series, Duration duration, DefaultOHLCDataset dataSet,
            TimeAxisMode timeAxisMode) {
        String domainLabel = resolveDomainAxisLabel(timeAxisMode);
        JFreeChart chart = ChartFactory.createCandlestickChart(chartTitle, domainLabel, "Price (USD)", dataSet, true);
        applyChartStyling(chart);

        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setDomainAxis(createDomainAxis(series, duration, timeAxisMode, domainLabel, 0.03, 0.07));
        configureRangeAxis(plot);
        configureCandlestickRenderer(plot, series, timeAxisMode);
        configurePlotAppearance(plot);

        return chart;
    }

    private void applyChartStyling(JFreeChart chart) {
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        chart.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        chart.setBackgroundImageAlpha(CHART_BACKGROUND_ALPHA);
        if (chart.getTitle() != null) {
            chart.getTitle().setPaint(Color.LIGHT_GRAY);
        }
    }

    private void configureRangeAxis(XYPlot plot) {
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        rangeAxis.setLabelPaint(Color.LIGHT_GRAY);
    }

    private void configureCandlestickRenderer(XYPlot plot, BarSeries series, TimeAxisMode timeAxisMode) {
        BaseCandleStickRenderer candleStickRenderer = new BaseCandleStickRenderer();
        candleStickRenderer.setDownPaint(BaseCandleStickRenderer.DEFAULT_DOWN_COLOR);
        candleStickRenderer.setUpPaint(BaseCandleStickRenderer.DEFAULT_UP_COLOR);
        candleStickRenderer.setAutoWidthMethod(BaseCandleStickRenderer.WIDTHMETHOD_SMALLEST);
        candleStickRenderer.setAutoWidthGap(0.35);
        candleStickRenderer.setAutoWidthFactor(0.65);
        candleStickRenderer.setUseOutlinePaint(true);
        candleStickRenderer.setDrawVolume(false);
        if (timeAxisMode == TimeAxisMode.BAR_INDEX) {
            DateFormat dateFormat = resolveDateFormat(series.getFirstBar().getTimePeriod());
            candleStickRenderer.setDefaultToolTipGenerator(new BarIndexOhlcToolTipGenerator(series, dateFormat));
        }
        plot.setRenderer(candleStickRenderer);
    }

    private void configurePlotAppearance(XYPlot plot) {
        plot.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        plot.setBackgroundAlpha(CHART_BACKGROUND_ALPHA);
        plot.setDomainGridlinePaint(GRIDLINE_COLOR);
        plot.setRangeGridlinePaint(GRIDLINE_COLOR);
    }

    private CombinedDomainXYPlot createCombinedPlot(BarSeries series, TimeAxisMode timeAxisMode) {
        Objects.requireNonNull(series, "BarSeries cannot be null");
        Duration duration = series.getFirstBar().getTimePeriod();
        ValueAxis domainAxis = createDomainAxis(series, duration, timeAxisMode, resolveDomainAxisLabel(timeAxisMode),
                0.02, 0.02);

        CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(domainAxis);
        combinedPlot.setGap(10.0);
        combinedPlot.setOrientation(PlotOrientation.VERTICAL);
        combinedPlot.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        combinedPlot.setBackgroundAlpha(CHART_BACKGROUND_ALPHA);
        combinedPlot.setDomainGridlinePaint(GRIDLINE_COLOR);
        combinedPlot.setRangeGridlinePaint(GRIDLINE_COLOR);
        return combinedPlot;
    }

    private XYPlot buildPlotFromDefinition(ChartBuilder.PlotDefinition definition, TimeAxisMode timeAxisMode) {
        XYPlot plot = switch (definition.type()) {
        case CANDLESTICK -> buildCandlestickPlot(definition, timeAxisMode);
        case INDICATOR -> buildIndicatorPlot(definition, timeAxisMode);
        case TRADING_RECORD -> buildTradingRecordPlot(definition, timeAxisMode);
        };
        attachChannelFills(plot, definition, timeAxisMode);
        attachOverlays(plot, definition, timeAxisMode);
        attachHorizontalMarkers(plot, definition);
        return plot;
    }

    private XYPlot buildCandlestickPlot(ChartBuilder.PlotDefinition definition, TimeAxisMode timeAxisMode) {
        DefaultOHLCDataset dataset = datasetFactory.createChartDataset(definition.series(), timeAxisMode);
        return createOHLCPlot(definition.series(), definition.series().getFirstBar().getTimePeriod(), dataset,
                timeAxisMode);
    }

    private XYPlot buildIndicatorPlot(ChartBuilder.PlotDefinition definition, TimeAxisMode timeAxisMode) {
        Indicator<Num> indicator = Objects.requireNonNull(definition.baseIndicator(),
                "Indicator plot requires a base indicator");
        return createIndicatorSubplot(definition.series(), indicator, timeAxisMode);
    }

    private XYPlot buildTradingRecordPlot(ChartBuilder.PlotDefinition definition, TimeAxisMode timeAxisMode) {
        TradingRecord tradingRecord = Objects.requireNonNull(definition.tradingRecord(),
                "Trading record plot requires trading data");
        BarSeries series = Objects.requireNonNull(definition.series(),
                "Trading record plots require a BarSeries for context");

        XYPlot plot = new XYPlot();
        XYSeriesCollection dataset = datasetFactory.createDataSeriesForIndicator(new ClosePriceIndicator(series),
                timeAxisMode);
        plot.setDataset(0, dataset);

        // Create and configure domain axis
        Duration duration = series.getFirstBar().getTimePeriod();
        plot.setDomainAxis(
                createDomainAxis(series, duration, timeAxisMode, resolveDomainAxisLabel(timeAxisMode), 0.02, 0.02));

        NumberAxis rangeAxis = new NumberAxis("Trade price");
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        rangeAxis.setLabelPaint(Color.LIGHT_GRAY);
        plot.setRangeAxis(rangeAxis);

        StandardXYItemRenderer renderer = new StandardXYItemRenderer();
        renderer.setSeriesPaint(0, Color.LIGHT_GRAY);
        renderer.setSeriesStroke(0, new BasicStroke(1.1f));
        plot.setRenderer(0, renderer);
        configurePlotAppearance(plot);
        addTradingRecordToChart(plot, series, tradingRecord, timeAxisMode);
        return plot;
    }

    private void attachChannelFills(XYPlot plot, ChartBuilder.PlotDefinition definition, TimeAxisMode timeAxisMode) {
        for (ChartBuilder.ChannelOverlayDefinition channel : definition.channelOverlays()) {
            Indicator<Num> upper = channel.upper();
            Indicator<Num> lower = channel.lower();
            if (upper == null || lower == null) {
                continue;
            }
            BarSeries series = upper.getBarSeries() != null ? upper.getBarSeries() : definition.series();
            if (series == null || series.getBarCount() == 0) {
                continue;
            }
            XYDataset dataset = datasetFactory.createChannelFillDataset(series, upper, lower, timeAxisMode);
            if (dataset.getSeriesCount() == 0) {
                continue;
            }

            int datasetIndex = plot.getDatasetCount();
            plot.setDataset(datasetIndex, dataset);
            plot.setRenderer(datasetIndex, overlayRendererFactory.createChannelFillRenderer(channel));

            int axisIndex = channel.axisSlot() == ChartBuilder.AxisSlot.SECONDARY ? 1 : 0;
            if (axisIndex == 1) {
                ensureSecondaryAxisExists(plot, upper.toString());
            }
            plot.mapDatasetToRangeAxis(datasetIndex, axisIndex);
        }
    }

    private void attachOverlays(XYPlot plot, ChartBuilder.PlotDefinition definition, TimeAxisMode timeAxisMode) {
        for (ChartBuilder.OverlayDefinition overlay : definition.overlays()) {
            switch (overlay.type()) {
            case INDICATOR:
            case ANALYSIS_CRITERION:
                attachIndicatorOverlay(plot, definition, overlay, timeAxisMode);
                break;
            case TRADING_RECORD:
                addTradingRecordToChart(plot, definition.series(), overlay.tradingRecord(), timeAxisMode);
                break;
            default:
                break;
            }
        }
    }

    private void attachIndicatorOverlay(XYPlot plot, ChartBuilder.PlotDefinition definition,
            ChartBuilder.OverlayDefinition overlay, TimeAxisMode timeAxisMode) {
        Indicator<Num> indicator = overlay.indicator();
        if (indicator == null) {
            return;
        }
        BarSeries series = indicator.getBarSeries() != null ? indicator.getBarSeries() : definition.series();
        if (series != null && series.getBarCount() > 0) {
            indicator.getValue(series.getEndIndex()); // warm caches so retrospective swing markers render
        }
        boolean connectGaps = overlay.style().connectGaps();
        String label = overlay.label() != null ? overlay.label() : indicator.toString();
        XYDataset dataset;
        if (indicator instanceof BarSeriesLabelIndicator labelIndicator) {
            dataset = timeAxisMode == TimeAxisMode.BAR_INDEX
                    ? datasetFactory.createIndexBarSeriesLabelDataset(series, labelIndicator, label)
                    : datasetFactory.createBarSeriesLabelDataset(series, labelIndicator, label);
        } else if (indicator instanceof SwingPointMarkerIndicator swingMarker) {
            dataset = timeAxisMode == TimeAxisMode.BAR_INDEX
                    ? datasetFactory.createIndexSwingMarkerDataset(series, swingMarker, label)
                    : datasetFactory.createSwingMarkerDataset(series, swingMarker, label);
        } else {
            dataset = timeAxisMode == TimeAxisMode.BAR_INDEX
                    ? datasetFactory.createIndexSeriesDataset(series, indicator, label, connectGaps)
                    : datasetFactory.createTimeSeriesDataset(series, indicator, label, connectGaps);
        }
        // Add dataset at the next available index (after base OHLC dataset)
        // This ensures overlays (including swing markers) are rendered on top of
        // candles
        int datasetIndex = plot.getDatasetCount();
        plot.setDataset(datasetIndex, dataset);

        if (indicator instanceof BarSeriesLabelIndicator labelIndicator) {
            plot.setRenderer(datasetIndex,
                    overlayRendererFactory.createBarSeriesLabelRenderer(dataset, overlay, series, timeAxisMode));
            attachBarSeriesLabelAnnotations(plot, series, labelIndicator, overlay, timeAxisMode);
        } else if (indicator instanceof SwingPointMarkerIndicator) {
            // Swing markers are rendered after the base dataset, ensuring they appear in
            // front of candles
            plot.setRenderer(datasetIndex,
                    overlayRendererFactory.createSwingMarkerRenderer(dataset, overlay, series, timeAxisMode));
        } else {
            plot.setRenderer(datasetIndex,
                    overlayRendererFactory.createStandardOverlayRenderer(dataset, overlay, series, timeAxisMode));
        }

        int axisIndex = overlay.axisSlot() == ChartBuilder.AxisSlot.SECONDARY ? 1 : 0;
        if (axisIndex == 1) {
            ensureSecondaryAxisExists(plot, label);
        }
        plot.mapDatasetToRangeAxis(datasetIndex, axisIndex);
    }

    private static final class OverlayRendererFactory {

        private XYDifferenceRenderer createChannelFillRenderer(ChartBuilder.ChannelOverlayDefinition channel) {
            XYDifferenceRenderer renderer = new XYDifferenceRenderer();
            Color baseColor = channel.fillColor();
            float opacity = channel.fillOpacity();
            Color fillColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                    Math.round(opacity * 255));
            Color transparent = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 0);
            renderer.setPositivePaint(fillColor);
            renderer.setNegativePaint(fillColor);
            renderer.setSeriesPaint(0, transparent);
            renderer.setSeriesPaint(1, transparent);
            renderer.setSeriesVisibleInLegend(0, false);
            renderer.setSeriesVisibleInLegend(1, false);
            return renderer;
        }

        private StandardXYItemRenderer createStandardOverlayRenderer(XYDataset dataset,
                ChartBuilder.OverlayDefinition overlay, BarSeries series, TimeAxisMode timeAxisMode) {
            StandardXYItemRenderer renderer = new StandardXYItemRenderer();
            Color baseColor = overlay.style().color();
            float opacity = overlay.style().opacity();
            Color colorWithOpacity = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                    Math.round(opacity * 255));
            for (int i = 0; i < dataset.getSeriesCount(); i++) {
                renderer.setSeriesPaint(i, colorWithOpacity);
                renderer.setSeriesStroke(i, createStroke(overlay.style()));
            }
            renderer.setDefaultToolTipGenerator(createOverlayToolTipGenerator(series, timeAxisMode));
            return renderer;
        }

        private XYLineAndShapeRenderer createSwingMarkerRenderer(XYDataset dataset,
                ChartBuilder.OverlayDefinition overlay, BarSeries series, TimeAxisMode timeAxisMode) {
            boolean connectLines = overlay.style().connectGaps();
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(connectLines, true);
            Color baseColor = overlay.style().color();
            // Swing markers default to 90% opacity (0.9) if using default opacity (1.0)
            float opacity = overlay.style().opacity();
            if (opacity == 1.0f) {
                opacity = SWING_MARKER_DEFAULT_OPACITY; // Default to 90% opacity for swing markers
            }
            Color colorWithOpacity = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                    Math.round(opacity * 255));
            double diameter = Math.max(3.0, overlay.style().lineWidth() * 2.4);
            Ellipse2D.Double shape = new Ellipse2D.Double(-diameter / 2.0, -diameter / 2.0, diameter, diameter);

            for (int i = 0; i < dataset.getSeriesCount(); i++) {
                renderer.setSeriesPaint(i, colorWithOpacity);
                renderer.setSeriesFillPaint(i, colorWithOpacity);
                renderer.setSeriesShape(i, shape);
                renderer.setSeriesStroke(i, createStroke(overlay.style()));
                renderer.setSeriesLinesVisible(i, connectLines);
            }
            renderer.setDefaultToolTipGenerator(createOverlayToolTipGenerator(series, timeAxisMode));
            renderer.setDefaultShapesFilled(true);
            renderer.setUseFillPaint(true);
            renderer.setUseOutlinePaint(false);
            return renderer;
        }

        private XYLineAndShapeRenderer createBarSeriesLabelRenderer(XYDataset dataset,
                ChartBuilder.OverlayDefinition overlay, BarSeries series, TimeAxisMode timeAxisMode) {
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
            Color baseColor = overlay.style().color();
            float opacity = overlay.style().opacity();
            Color colorWithOpacity = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                    Math.round(opacity * 255));
            double diameter = Math.max(4.0, overlay.style().lineWidth() * 2.4);
            Ellipse2D.Double shape = new Ellipse2D.Double(-diameter / 2.0, -diameter / 2.0, diameter, diameter);

            for (int i = 0; i < dataset.getSeriesCount(); i++) {
                renderer.setSeriesPaint(i, colorWithOpacity);
                renderer.setSeriesFillPaint(i, colorWithOpacity);
                renderer.setSeriesShape(i, shape);
                renderer.setSeriesStroke(i, createStroke(overlay.style()));
                renderer.setSeriesShapesVisible(i, true);
                renderer.setSeriesShapesFilled(i, true);
                renderer.setSeriesLinesVisible(i, true);
            }

            renderer.setDefaultToolTipGenerator(createOverlayToolTipGenerator(series, timeAxisMode));
            renderer.setDefaultShapesFilled(true);
            renderer.setUseFillPaint(true);
            renderer.setUseOutlinePaint(false);
            return renderer;
        }

        private BasicStroke createStroke(ChartBuilder.OverlayStyle style) {
            float[] dash = style.dashPattern();
            if (dash != null && dash.length > 0) {
                return new BasicStroke(style.lineWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f, dash,
                        0.0f);
            }
            return new BasicStroke(style.lineWidth());
        }

        private XYToolTipGenerator createOverlayToolTipGenerator(BarSeries series, TimeAxisMode timeAxisMode) {
            if (timeAxisMode == TimeAxisMode.BAR_INDEX) {
                DateFormat dateFormat = resolveDateFormat(series.getFirstBar().getTimePeriod());
                return new BarIndexToolTipGenerator(series, dateFormat);
            }
            return new TimeSeriesToolTipGenerator();
        }
    }

    private XYToolTipGenerator createSeriesToolTipGenerator(BarSeries series, Duration duration,
            TimeAxisMode timeAxisMode) {
        DateFormat dateFormat = resolveDateFormat(duration);
        if (timeAxisMode == TimeAxisMode.BAR_INDEX) {
            return new BarIndexToolTipGenerator(series, dateFormat);
        }
        return new XYSeriesToolTipGenerator(dateFormat);
    }

    private void ensureSecondaryAxisExists(XYPlot plot, String label) {
        if (plot.getRangeAxisCount() > 1 && plot.getRangeAxis(1) != null) {
            return;
        }
        String axisLabel = label != null ? truncateAxisLabel(label) : "Value";
        NumberAxis secondaryAxis = new NumberAxis(axisLabel);
        secondaryAxis.setAutoRangeIncludesZero(false);
        secondaryAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        secondaryAxis.setLabelPaint(Color.LIGHT_GRAY);
        plot.setRangeAxis(1, secondaryAxis);
    }

    private void addTradingRecordToChart(XYPlot plot, BarSeries series, TradingRecord tradingRecord,
            TimeAxisMode timeAxisMode) {
        try {
            XYSeries buyMarkers = createTradeSeries("Buy trades");
            XYSeries sellMarkers = createTradeSeries("Sell trades");
            int positionIndex = 1;

            for (Position position : tradingRecord.getPositions()) {
                addTradeMarker(buyMarkers, sellMarkers, plot, series, position.getEntry(), positionIndex, timeAxisMode);
                addTradeMarker(buyMarkers, sellMarkers, plot, series, position.getExit(), positionIndex, timeAxisMode);
                addPositionBand(plot, series, positionIndex, position.getEntry(), position.getExit(), timeAxisMode);
                positionIndex++;
            }

            if (tradingRecord.getCurrentPosition().isOpened()) {
                Trade lastTrade = tradingRecord.getLastTrade();
                if (lastTrade != null) {
                    addTradeMarker(buyMarkers, sellMarkers, plot, series, lastTrade, positionIndex, timeAxisMode);
                }
                addPositionBand(plot, series, positionIndex, tradingRecord.getCurrentPosition().getEntry(), null,
                        timeAxisMode);
            }

            attachTradeDataset(plot, buyMarkers, sellMarkers);
        } catch (Exception ex) {
            LOG.error("Failed to add trading record to chart", ex);
        }
    }

    private void addTradeMarker(XYSeries buyMarkers, XYSeries sellMarkers, XYPlot plot, BarSeries series, Trade trade,
            int positionIndex, TimeAxisMode timeAxisMode) {
        if (trade == null) {
            return;
        }

        Instant seriesStartTime = series.getFirstBar().getEndTime();
        Instant seriesEndTime = series.getLastBar().getEndTime();
        Instant tradeTime = series.getBar(trade.getIndex()).getEndTime();

        if (!isTradeTimeInRange(tradeTime, seriesStartTime, seriesEndTime)) {
            LOG.debug("Trade at {} not added to chart - outside range [{}, {}]", tradeTime, seriesStartTime,
                    seriesEndTime);
            return;
        }

        double orderDateTime = toDomainValue(series, trade.getIndex(), timeAxisMode);
        double price = trade.getPricePerAsset().doubleValue();

        if (trade.isBuy()) {
            buyMarkers.add(orderDateTime, price);
        } else {
            sellMarkers.add(orderDateTime, price);
        }

        annotateTrade(plot, series, trade, positionIndex, orderDateTime, price);
    }

    private void annotateTrade(XYPlot plot, BarSeries series, Trade trade, int positionIndex, double orderDateTime,
            double price) {
        String labelPrefix = trade.isBuy() ? "B" : "S";
        String label = labelPrefix + positionIndex + " @" + PRICE_FORMAT.get().format(price);

        XYTextAnnotation annotation = new XYTextAnnotation(label, orderDateTime, price);
        annotation.setFont(TRADE_LABEL_FONT);
        annotation.setPaint(trade.isBuy() ? BUY_MARKER_COLOR : SELL_MARKER_COLOR);
        annotation.setBackgroundPaint(TRADE_LABEL_BACKGROUND);
        annotation.setTextAnchor(resolveTradeLabelAnchor(trade, series));
        plot.addAnnotation(annotation);
    }

    private TextAnchor resolveTradeLabelAnchor(Trade trade, BarSeries series) {
        int index = trade.getIndex();
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        boolean nearStart = index - begin <= LABEL_EDGE_BARS;
        boolean nearEnd = end - index <= LABEL_EDGE_BARS;

        if (nearEnd && !nearStart) {
            return trade.isBuy() ? TextAnchor.BOTTOM_RIGHT : TextAnchor.TOP_RIGHT;
        }
        if (nearStart && !nearEnd) {
            return trade.isBuy() ? TextAnchor.BOTTOM_LEFT : TextAnchor.TOP_LEFT;
        }
        return trade.isBuy() ? TextAnchor.BOTTOM_CENTER : TextAnchor.TOP_CENTER;
    }

    private void attachTradeDataset(XYPlot plot, XYSeries buyMarkers, XYSeries sellMarkers) {
        if (buyMarkers.isEmpty() && sellMarkers.isEmpty()) {
            return;
        }

        XYSeriesCollection tradeData = new XYSeriesCollection();
        tradeData.addSeries(buyMarkers);
        tradeData.addSeries(sellMarkers);

        XYLineAndShapeRenderer markerRenderer = new XYLineAndShapeRenderer(false, true);
        markerRenderer.setSeriesShape(0, createTriangleShape(true));
        markerRenderer.setSeriesShape(1, createTriangleShape(false));
        markerRenderer.setSeriesPaint(0, BUY_MARKER_COLOR);
        markerRenderer.setSeriesPaint(1, SELL_MARKER_COLOR);
        markerRenderer.setSeriesFillPaint(0, BUY_MARKER_COLOR);
        markerRenderer.setSeriesFillPaint(1, SELL_MARKER_COLOR);
        markerRenderer.setSeriesOutlinePaint(0, Color.DARK_GRAY);
        markerRenderer.setSeriesOutlinePaint(1, Color.DARK_GRAY);
        markerRenderer.setSeriesOutlineStroke(0, new BasicStroke(1.2f));
        markerRenderer.setSeriesOutlineStroke(1, new BasicStroke(1.2f));
        markerRenderer.setSeriesShapesVisible(0, true);
        markerRenderer.setSeriesShapesVisible(1, true);
        markerRenderer.setSeriesShapesFilled(0, true);
        markerRenderer.setSeriesShapesFilled(1, true);
        markerRenderer.setUseFillPaint(true);

        int datasetIndex = plot.getDatasetCount();
        plot.setDataset(datasetIndex, tradeData);
        plot.setRenderer(datasetIndex, markerRenderer);
        plot.mapDatasetToRangeAxis(datasetIndex, 0);
    }

    private XYSeries createTradeSeries(String key) {
        return new XYSeries(key, false, true);
    }

    private void addPositionBand(XYPlot plot, BarSeries series, int positionIndex, Trade entry, Trade exit,
            TimeAxisMode timeAxisMode) {
        if (entry == null) {
            return;
        }

        long start = Math.round(toDomainValue(series, entry.getIndex(), timeAxisMode));
        long end;
        if (exit != null) {
            end = Math.round(toDomainValue(series, exit.getIndex(), timeAxisMode));
        } else {
            end = Math.round(toDomainValue(series, series.getEndIndex(), timeAxisMode));
        }
        if (end <= start) {
            end = start + 1;
        }

        IntervalMarker marker = new IntervalMarker(start, end);
        Color baseColor = POSITION_BAND_COLORS[(positionIndex - 1) % POSITION_BAND_COLORS.length];
        marker.setPaint(withAlpha(baseColor, POSITION_BAND_ALPHA));
        marker.setLabel("Position " + positionIndex);
        marker.setLabelFont(POSITION_LABEL_FONT);
        marker.setLabelPaint(Color.LIGHT_GRAY);
        setPositionLabelAnchors(marker, entry.getIndex(), series);
        plot.addDomainMarker(marker, Layer.BACKGROUND);
    }

    private void setPositionLabelAnchors(IntervalMarker marker, int entryIndex, BarSeries series) {
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        boolean nearEnd = end - entryIndex <= LABEL_EDGE_BARS;
        boolean nearStart = entryIndex - begin <= LABEL_EDGE_BARS;
        if (nearEnd && !nearStart) {
            marker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
            marker.setLabelTextAnchor(TextAnchor.TOP_RIGHT);
        } else {
            marker.setLabelAnchor(RectangleAnchor.TOP_LEFT);
            marker.setLabelTextAnchor(TextAnchor.TOP_LEFT);
        }
    }

    private Shape createTriangleShape(boolean pointingUp) {
        Polygon polygon = new Polygon();
        if (pointingUp) {
            polygon.addPoint(0, -TRADE_MARKER_SIZE);
            polygon.addPoint(TRADE_MARKER_SIZE, TRADE_MARKER_SIZE);
            polygon.addPoint(-TRADE_MARKER_SIZE, TRADE_MARKER_SIZE);
        } else {
            polygon.addPoint(0, TRADE_MARKER_SIZE);
            polygon.addPoint(TRADE_MARKER_SIZE, -TRADE_MARKER_SIZE);
            polygon.addPoint(-TRADE_MARKER_SIZE, -TRADE_MARKER_SIZE);
        }
        return polygon;
    }

    private Color withAlpha(Color color, float alpha) {
        int alphaChannel = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alphaChannel);
    }

    private boolean isTradeTimeInRange(Instant tradeTime, Instant startTime, Instant endTime) {
        return (tradeTime.equals(startTime) || tradeTime.isAfter(startTime))
                && (tradeTime.equals(endTime) || tradeTime.isBefore(endTime));
    }

    private XYPlot createOHLCPlot(BarSeries series, Duration duration, DefaultOHLCDataset data,
            TimeAxisMode timeAxisMode) {
        XYPlot plot = new XYPlot();
        plot.setDataset(0, data);

        // Configure domain axis
        plot.setDomainAxis(
                createDomainAxis(series, duration, timeAxisMode, resolveDomainAxisLabel(timeAxisMode), 0.02, 0.02));

        // Configure range axis
        NumberAxis rangeAxis = new NumberAxis("Price (USD)");
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        rangeAxis.setLabelPaint(Color.LIGHT_GRAY);
        plot.setRangeAxis(rangeAxis);

        // Configure candlestick renderer
        configureCandlestickRenderer(plot, series, timeAxisMode);
        configurePlotAppearance(plot);

        return plot;
    }

    private XYPlot createIndicatorSubplot(BarSeries series, Indicator<Num> indicator, TimeAxisMode timeAxisMode) {
        XYSeriesCollection dataset = datasetFactory.createDataSeriesForIndicator(indicator, timeAxisMode);

        XYPlot plot = new XYPlot();
        plot.setDataset(0, dataset);

        // Configure domain axis (will be shared by CombinedDomainXYPlot, but we set it
        // for consistency)
        Duration duration = series.getFirstBar().getTimePeriod();
        plot.setDomainAxis(createDomainAxis(series, duration, timeAxisMode, null, 0.02, 0.02));

        // Configure range axis with indicator label (truncated to prevent overlap)
        String indicatorLabel = truncateAxisLabel(indicator.toString());
        NumberAxis rangeAxis = new NumberAxis(indicatorLabel);
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        rangeAxis.setLabelPaint(Color.LIGHT_GRAY);
        plot.setRangeAxis(rangeAxis);

        // Configure renderer
        StandardXYItemRenderer renderer = new StandardXYItemRenderer();
        // Set tooltip generator to show series name, date, and value
        // For XYSeriesCollection with epoch milliseconds, use a custom format
        renderer.setDefaultToolTipGenerator(createSeriesToolTipGenerator(series, duration, timeAxisMode));
        plot.setRenderer(0, renderer);
        configurePlotAppearance(plot);

        return plot;
    }

    /**
     * Creates an appropriate RegularTimePeriod for a given date and bar duration.
     * <p>
     * Selects the time unit based on the bar duration to avoid duplicate time
     * periods:
     * <ul>
     * <li>Daily or longer: uses {@link Day}</li>
     * <li>Hourly (1-23 hours): uses {@link Hour}</li>
     * <li>Minute-level (1-59 minutes): uses {@link Minute}</li>
     * <li>Second-level or shorter: uses {@link Second}</li>
     * </ul>
     *
     * @param barDate  the date/time for the time period
     * @param duration the bar duration
     * @return an appropriate RegularTimePeriod instance
     */
    private RegularTimePeriod createTimePeriod(Date barDate, Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours();
        long minutes = duration.toMinutes();

        if (days >= 1) {
            return new Day(barDate);
        } else if (hours >= 1) {
            return new Hour(barDate);
        } else if (minutes >= 1) {
            return new Minute(barDate);
        } else {
            return new Second(barDate);
        }
    }

    private int calculateMainPlotWeight(int indicatorCount) {
        // Give main plot proportionally more space when there are more indicators
        // Base weight of 50, scale down slightly with more indicators
        return Math.max(30, 60 - (indicatorCount * 5));
    }

    private int calculateIndicatorPlotWeight(int indicatorCount) {
        // Distribute remaining space evenly among indicators
        // Each indicator gets equal weight
        return Math.max(10, 40 / indicatorCount);
    }

    private final class DatasetFactory {

        private DefaultOHLCDataset createChartDataset(BarSeries series, TimeAxisMode timeAxisMode) {
            List<OHLCDataItem> dataItems = new ArrayList<>();

            for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
                Bar bar = series.getBar(i);
                Date barDate = timeAxisMode == TimeAxisMode.BAR_INDEX ? new Date((long) i)
                        : Date.from(bar.getEndTime());
                OHLCDataItem item = new OHLCDataItem(barDate, bar.getOpenPrice().doubleValue(),
                        bar.getHighPrice().doubleValue(), bar.getLowPrice().doubleValue(),
                        bar.getClosePrice().doubleValue(), bar.getVolume().doubleValue());
                dataItems.add(item);
            }

            String seriesName = series.getName() != null ? series.getName().split(" ")[0] : "Unknown";
            return new DefaultOHLCDataset(seriesName, dataItems.toArray(new OHLCDataItem[0]));
        }

        /**
         * Creates an XYSeriesCollection from an indicator, treating NaN values as gaps.
         * When NaN values are encountered, the series is split into multiple segments
         * to create visual gaps in the chart.
         *
         * @param indicator the indicator to convert
         * @return XYSeriesCollection containing one or more series segments
         */
        private XYSeriesCollection createDataSeriesForIndicator(Indicator<Num> indicator, TimeAxisMode timeAxisMode) {
            XYSeriesCollection collection = new XYSeriesCollection();
            BarSeries series = indicator.getBarSeries();
            String baseName = indicator.toString();

            XYSeries currentSegment = null;
            int segmentIndex = 0;

            for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
                Num value = indicator.getValue(i);
                boolean isValid = Num.isValid(value);

                if (isValid) {
                    // Start a new segment if we don't have one
                    if (currentSegment == null) {
                        String segmentName = segmentIndex == 0 ? baseName
                                : baseName + " (segment " + segmentIndex + ")";
                        currentSegment = new XYSeries(segmentName);
                        segmentIndex++;
                    }
                    double orderDateTime = toDomainValue(series, i, timeAxisMode);
                    currentSegment.add(orderDateTime, value.doubleValue());
                } else {
                    // NaN encountered - finish current segment if it exists
                    if (currentSegment != null) {
                        collection.addSeries(currentSegment);
                        currentSegment = null;
                    }
                }
            }

            // Add the last segment if it exists
            if (currentSegment != null) {
                collection.addSeries(currentSegment);
            }

            return collection;
        }

        /**
         * Creates a TimeSeriesCollection from an indicator. When connectGaps is false,
         * NaN values are treated as gaps and the series is split into multiple
         * segments. When connectGaps is true, NaN values are skipped but non-NaN values
         * are connected in a single series.
         *
         * @param series      the bar series
         * @param indicator   the indicator to convert
         * @param seriesName  the base name for the series
         * @param connectGaps if true, connect non-NaN values across NaN gaps; if false,
         *                    split on NaN
         * @return TimeSeriesCollection containing one or more series segments
         */
        private TimeSeriesCollection createTimeSeriesDataset(BarSeries series, Indicator<Num> indicator,
                String seriesName, boolean connectGaps) {
            TimeSeriesCollection collection = new TimeSeriesCollection();
            TimeSeries currentSegment = null;
            int segmentIndex = 0;
            Duration barDuration = series.getFirstBar().getTimePeriod();

            for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
                Bar bar = series.getBar(i);
                Date barDate = Date.from(bar.getEndTime());
                Num value = indicator.getValue(i);
                boolean isValid = Num.isValid(value);

                if (isValid) {
                    // Start a new segment if we don't have one
                    if (currentSegment == null) {
                        String segmentName = segmentIndex == 0 ? seriesName
                                : seriesName + " (segment " + segmentIndex + ")";
                        currentSegment = new TimeSeries(segmentName);
                        segmentIndex++;
                    }
                    RegularTimePeriod timePeriod = createTimePeriod(barDate, barDuration);
                    currentSegment.addOrUpdate(timePeriod, value.doubleValue());
                } else {
                    // NaN encountered
                    if (connectGaps) {
                        // When connecting gaps, just skip NaN values and continue with current segment
                        // No action needed - the segment continues
                    } else {
                        // When not connecting gaps, finish current segment if it exists
                        if (currentSegment != null) {
                            collection.addSeries(currentSegment);
                            currentSegment = null;
                        }
                    }
                }
            }

            // Add the last segment if it exists
            if (currentSegment != null) {
                collection.addSeries(currentSegment);
            }

            return collection;
        }

        private XYSeriesCollection createIndexSeriesDataset(BarSeries series, Indicator<Num> indicator,
                String seriesName, boolean connectGaps) {
            XYSeriesCollection collection = new XYSeriesCollection();
            XYSeries currentSegment = null;
            int segmentIndex = 0;

            for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
                Num value = indicator.getValue(i);
                boolean isValid = Num.isValid(value);

                if (isValid) {
                    if (currentSegment == null) {
                        String segmentName = segmentIndex == 0 ? seriesName
                                : seriesName + " (segment " + segmentIndex + ")";
                        currentSegment = new XYSeries(segmentName);
                        segmentIndex++;
                    }
                    currentSegment.add(toDomainValue(series, i, TimeAxisMode.BAR_INDEX), value.doubleValue());
                } else if (!connectGaps && currentSegment != null) {
                    collection.addSeries(currentSegment);
                    currentSegment = null;
                }
            }

            if (currentSegment != null) {
                collection.addSeries(currentSegment);
            }

            return collection;
        }

        private XYDataset createChannelFillDataset(BarSeries series, Indicator<Num> upper, Indicator<Num> lower,
                TimeAxisMode timeAxisMode) {
            if (timeAxisMode == TimeAxisMode.BAR_INDEX) {
                XYSeriesCollection collection = new XYSeriesCollection();
                XYSeries upperSeries = new XYSeries("Channel upper");
                XYSeries lowerSeries = new XYSeries("Channel lower");

                for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
                    Num upperValue = upper.getValue(i);
                    Num lowerValue = lower.getValue(i);
                    if (Num.isValid(upperValue) && Num.isValid(lowerValue)) {
                        double x = toDomainValue(series, i, timeAxisMode);
                        upperSeries.add(x, upperValue.doubleValue());
                        lowerSeries.add(x, lowerValue.doubleValue());
                    }
                }

                if (upperSeries.getItemCount() > 0 && lowerSeries.getItemCount() > 0) {
                    collection.addSeries(upperSeries);
                    collection.addSeries(lowerSeries);
                }
                return collection;
            }

            TimeSeriesCollection collection = new TimeSeriesCollection();
            TimeSeries upperSeries = new TimeSeries("Channel upper");
            TimeSeries lowerSeries = new TimeSeries("Channel lower");
            Duration barDuration = series.getFirstBar().getTimePeriod();

            for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
                Bar bar = series.getBar(i);
                Date barDate = Date.from(bar.getEndTime());
                Num upperValue = upper.getValue(i);
                Num lowerValue = lower.getValue(i);
                if (Num.isValid(upperValue) && Num.isValid(lowerValue)) {
                    RegularTimePeriod timePeriod = createTimePeriod(barDate, barDuration);
                    upperSeries.addOrUpdate(timePeriod, upperValue.doubleValue());
                    lowerSeries.addOrUpdate(timePeriod, lowerValue.doubleValue());
                }
            }

            if (upperSeries.getItemCount() > 0 && lowerSeries.getItemCount() > 0) {
                collection.addSeries(upperSeries);
                collection.addSeries(lowerSeries);
            }
            return collection;
        }

        private TimeSeriesCollection createSwingMarkerDataset(BarSeries series, SwingPointMarkerIndicator marker,
                String seriesName) {
            TimeSeriesCollection collection = new TimeSeriesCollection();
            if (series == null || series.isEmpty()) {
                return collection;
            }

            List<Integer> swingIndexes = new ArrayList<>(marker.getSwingPointIndexes());
            Collections.sort(swingIndexes);
            TimeSeries swingSeries = new TimeSeries(seriesName);
            Duration barDuration = series.getFirstBar().getTimePeriod();
            for (Integer index : swingIndexes) {
                Num value = marker.getPriceIndicator().getValue(index);
                if (Num.isNaNOrNull(value)) {
                    value = series.getBar(index).getClosePrice();
                }
                if (Num.isValid(value)) {
                    Date barDate = Date.from(series.getBar(index).getEndTime());
                    RegularTimePeriod timePeriod = createTimePeriod(barDate, barDuration);
                    swingSeries.addOrUpdate(timePeriod, value.doubleValue());
                }
            }
            if (swingSeries.getItemCount() > 0) {
                collection.addSeries(swingSeries);
            }
            return collection;
        }

        private XYSeriesCollection createIndexSwingMarkerDataset(BarSeries series, SwingPointMarkerIndicator marker,
                String seriesName) {
            XYSeriesCollection collection = new XYSeriesCollection();
            if (series == null || series.isEmpty()) {
                return collection;
            }

            List<Integer> swingIndexes = new ArrayList<>(marker.getSwingPointIndexes());
            Collections.sort(swingIndexes);
            XYSeries swingSeries = new XYSeries(seriesName);
            for (Integer index : swingIndexes) {
                Num value = marker.getPriceIndicator().getValue(index);
                if (Num.isNaNOrNull(value)) {
                    value = series.getBar(index).getClosePrice();
                }
                if (Num.isValid(value)) {
                    double x = toDomainValue(series, index, TimeAxisMode.BAR_INDEX);
                    swingSeries.add(x, value.doubleValue());
                }
            }
            if (swingSeries.getItemCount() > 0) {
                collection.addSeries(swingSeries);
            }
            return collection;
        }

        private TimeSeriesCollection createBarSeriesLabelDataset(BarSeries series,
                BarSeriesLabelIndicator labelIndicator, String seriesName) {
            TimeSeriesCollection collection = new TimeSeriesCollection();
            if (series == null || series.isEmpty()) {
                return collection;
            }

            List<BarLabel> labels = labelIndicator.labels();
            if (labels.isEmpty()) {
                return collection;
            }

            TimeSeries labelSeries = new TimeSeries(seriesName);
            Duration barDuration = series.getFirstBar().getTimePeriod();
            for (BarLabel barLabel : labels) {
                int index = barLabel.barIndex();
                if (index < series.getBeginIndex() || index > series.getEndIndex()) {
                    continue;
                }
                Num value = barLabel.yValue();
                if (Num.isNaNOrNull(value)) {
                    continue;
                }
                Date barDate = Date.from(series.getBar(index).getEndTime());
                RegularTimePeriod timePeriod = createTimePeriod(barDate, barDuration);
                labelSeries.addOrUpdate(timePeriod, value.doubleValue());
            }

            if (labelSeries.getItemCount() > 0) {
                collection.addSeries(labelSeries);
            }

            return collection;
        }

        private XYSeriesCollection createIndexBarSeriesLabelDataset(BarSeries series,
                BarSeriesLabelIndicator labelIndicator, String seriesName) {
            XYSeriesCollection collection = new XYSeriesCollection();
            if (series == null || series.isEmpty()) {
                return collection;
            }

            List<BarLabel> labels = labelIndicator.labels();
            if (labels.isEmpty()) {
                return collection;
            }

            XYSeries labelSeries = new XYSeries(seriesName);
            for (BarLabel barLabel : labels) {
                int index = barLabel.barIndex();
                if (index < series.getBeginIndex() || index > series.getEndIndex()) {
                    continue;
                }
                Num value = barLabel.yValue();
                if (Num.isNaNOrNull(value)) {
                    continue;
                }
                double x = toDomainValue(series, index, TimeAxisMode.BAR_INDEX);
                labelSeries.add(x, value.doubleValue());
            }

            if (labelSeries.getItemCount() > 0) {
                collection.addSeries(labelSeries);
            }

            return collection;
        }
    }

    private void attachBarSeriesLabelAnnotations(XYPlot plot, BarSeries series, BarSeriesLabelIndicator labelIndicator,
            ChartBuilder.OverlayDefinition overlay, TimeAxisMode timeAxisMode) {
        if (plot == null || series == null || labelIndicator == null) {
            return;
        }

        Color baseColor = overlay.style().color();
        float opacity = overlay.style().opacity();
        Color colorWithOpacity = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                Math.round(opacity * 255));

        for (BarLabel label : labelIndicator.labels()) {
            if (label == null || label.text().isBlank()) {
                continue;
            }
            int index = label.barIndex();
            if (index < series.getBeginIndex() || index > series.getEndIndex()) {
                continue;
            }
            Num yValue = label.yValue();
            if (Num.isNaNOrNull(yValue)) {
                continue;
            }
            double x = toDomainValue(series, index, timeAxisMode);
            double y = yValue.doubleValue();

            XYTextAnnotation annotation = new XYTextAnnotation(label.text(), x, y);
            annotation.setFont(OVERLAY_LABEL_FONT);
            annotation.setPaint(colorWithOpacity);
            annotation.setBackgroundPaint(TRADE_LABEL_BACKGROUND);
            annotation.setTextAnchor(resolveBarLabelAnchor(label, series));
            // Annotations are automatically rendered on top of data series in JFreeChart
            // Adding them after the data series ensures they appear in front
            plot.addAnnotation(annotation);
        }
    }

    private TextAnchor resolveBarLabelAnchor(BarLabel label, BarSeries series) {
        int index = label.barIndex();
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        boolean nearStart = index - begin <= LABEL_EDGE_BARS;
        boolean nearEnd = end - index <= LABEL_EDGE_BARS;

        return switch (label.placement()) {
        case ABOVE -> {
            if (nearEnd && !nearStart) {
                yield TextAnchor.BOTTOM_RIGHT;
            }
            if (nearStart && !nearEnd) {
                yield TextAnchor.BOTTOM_LEFT;
            }
            yield TextAnchor.BOTTOM_CENTER;
        }
        case BELOW -> {
            if (nearEnd && !nearStart) {
                yield TextAnchor.TOP_RIGHT;
            }
            if (nearStart && !nearEnd) {
                yield TextAnchor.TOP_LEFT;
            }
            yield TextAnchor.TOP_CENTER;
        }
        case CENTER -> {
            if (nearEnd && !nearStart) {
                yield TextAnchor.CENTER_RIGHT;
            }
            if (nearStart && !nearEnd) {
                yield TextAnchor.CENTER_LEFT;
            }
            yield TextAnchor.CENTER;
        }
        };
    }

    private void configureDualAxisPlot(XYPlot plot, BarSeries series, Duration duration, TimeAxisMode timeAxisMode) {
        plot.setDomainAxis(
                createDomainAxis(series, duration, timeAxisMode, resolveDomainAxisLabel(timeAxisMode), 0.03, 0.07));
        configureRangeAxis(plot);
        configurePlotAppearance(plot);
    }

    private void addSecondaryAxis(XYPlot plot, XYDataset dataset, String label, TimeAxisMode timeAxisMode,
            BarSeries series, Duration duration) {
        NumberAxis secondaryAxis = null;
        if (plot.getRangeAxisCount() > 1) {
            secondaryAxis = (NumberAxis) plot.getRangeAxis(1);
        }

        if (secondaryAxis == null) {
            String axisLabel = label != null ? truncateAxisLabel(label) : "Value";
            secondaryAxis = new NumberAxis(axisLabel);
            secondaryAxis.setAutoRangeIncludesZero(false);
            secondaryAxis.setTickLabelPaint(Color.LIGHT_GRAY);
            secondaryAxis.setLabelPaint(Color.LIGHT_GRAY);
            plot.setRangeAxis(1, secondaryAxis);
        } else if (!Objects.equals(secondaryAxis.getLabel(), label)) {
            LOG.warn("Secondary axis already configured with label '{}'; skipping overlay '{}'.",
                    secondaryAxis.getLabel(), label);
            return;
        }

        int datasetIndex = plot.getDatasetCount();
        plot.setDataset(datasetIndex, dataset);
        plot.mapDatasetToRangeAxis(datasetIndex, 1);
        StandardXYItemRenderer secondaryRenderer = new StandardXYItemRenderer();
        // Apply styling to all series segments (for gap handling with NaN values)
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            secondaryRenderer.setSeriesPaint(i, Color.BLUE);
        }
        // Set tooltip generator to show series name, date, and value
        if (timeAxisMode == TimeAxisMode.BAR_INDEX) {
            DateFormat dateFormat = resolveDateFormat(duration);
            secondaryRenderer.setDefaultToolTipGenerator(new BarIndexToolTipGenerator(series, dateFormat));
        } else {
            secondaryRenderer.setDefaultToolTipGenerator(new TimeSeriesToolTipGenerator());
        }
        plot.setRenderer(datasetIndex, secondaryRenderer);
    }

    private void attachHorizontalMarkers(XYPlot plot, ChartBuilder.PlotDefinition definition) {
        for (ChartBuilder.HorizontalMarkerDefinition marker : definition.horizontalMarkers()) {
            ValueMarker valueMarker = new ValueMarker(marker.yValue());
            ChartBuilder.OverlayStyle style = marker.style();
            Color baseColor = style.color();
            float opacity = style.opacity();
            Color colorWithOpacity = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                    Math.round(opacity * 255));
            valueMarker.setPaint(colorWithOpacity);
            valueMarker.setStroke(new BasicStroke(style.lineWidth()));
            plot.addRangeMarker(valueMarker, Layer.FOREGROUND);
        }
    }

    /**
     * Serializable tooltip generator for TimeSeriesCollection datasets.
     */
    private static final class TimeSeriesToolTipGenerator implements XYToolTipGenerator, Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public String generateToolTip(XYDataset dataset, int seriesIdx, int item) {
            String seriesName = dataset.getSeriesKey(seriesIdx).toString();
            TimeSeriesCollection timeSeriesCollection = (TimeSeriesCollection) dataset;
            TimeSeries timeSeries = timeSeriesCollection.getSeries(seriesIdx);
            org.jfree.data.time.TimeSeriesDataItem dataItem = timeSeries.getDataItem(item);
            String dateStr = dataItem.getPeriod().toString();
            double value = dataItem.getValue().doubleValue();
            return String.format("%s: %s, Value: %s", seriesName, dateStr, PRICE_FORMAT.get().format(value));
        }
    }

    /**
     * Serializable tooltip generator for XYSeriesCollection datasets with epoch
     * milliseconds.
     */
    private static final class XYSeriesToolTipGenerator implements XYToolTipGenerator, Serializable {

        private static final long serialVersionUID = 1L;
        private final DateFormat dateFormat;

        XYSeriesToolTipGenerator(DateFormat dateFormat) {
            this.dateFormat = dateFormat;
        }

        @Override
        public String generateToolTip(XYDataset dataset, int seriesIdx, int item) {
            String seriesName = dataset.getSeriesKey(seriesIdx).toString();
            double xValue = dataset.getXValue(seriesIdx, item);
            double yValue = dataset.getYValue(seriesIdx, item);
            return String.format("%s: %s, Value: %s", seriesName, dateFormat.format(new Date((long) xValue)),
                    PRICE_FORMAT.get().format(yValue));
        }
    }

    /**
     * Tooltip generator that maps bar-indexed X values back to bar timestamps.
     */
    private static final class BarIndexToolTipGenerator implements XYToolTipGenerator, Serializable {

        private static final long serialVersionUID = 1L;
        private final BarSeries series;
        private final DateFormat dateFormat;

        private BarIndexToolTipGenerator(BarSeries series, DateFormat dateFormat) {
            this.series = series;
            this.dateFormat = dateFormat;
        }

        @Override
        public String generateToolTip(XYDataset dataset, int seriesIdx, int item) {
            String seriesName = dataset.getSeriesKey(seriesIdx).toString();
            int barIndex = (int) Math.round(dataset.getXValue(seriesIdx, item));
            if (barIndex < series.getBeginIndex() || barIndex > series.getEndIndex()) {
                return seriesName;
            }
            Bar bar = series.getBar(barIndex);
            String dateStr = dateFormat.format(Date.from(bar.getEndTime()));
            double yValue = dataset.getYValue(seriesIdx, item);
            return String.format("%s: %s, Value: %s", seriesName, dateStr, PRICE_FORMAT.get().format(yValue));
        }
    }

    /**
     * Tooltip generator for OHLC datasets when bars are plotted by index.
     */
    private static final class BarIndexOhlcToolTipGenerator implements XYToolTipGenerator, Serializable {

        private static final long serialVersionUID = 1L;
        private final BarSeries series;
        private final DateFormat dateFormat;

        private BarIndexOhlcToolTipGenerator(BarSeries series, DateFormat dateFormat) {
            this.series = series;
            this.dateFormat = dateFormat;
        }

        @Override
        public String generateToolTip(XYDataset dataset, int seriesIdx, int item) {
            String seriesName = dataset.getSeriesKey(seriesIdx).toString();
            int barIndex = (int) Math.round(dataset.getXValue(seriesIdx, item));
            if (barIndex < series.getBeginIndex() || barIndex > series.getEndIndex()) {
                return seriesName;
            }
            Bar bar = series.getBar(barIndex);
            String dateStr = dateFormat.format(Date.from(bar.getEndTime()));
            return String.format("%s --> Date=%s High=%s Low=%s Open=%s Close=%s", seriesName, dateStr,
                    PRICE_FORMAT.get().format(bar.getHighPrice().doubleValue()),
                    PRICE_FORMAT.get().format(bar.getLowPrice().doubleValue()),
                    PRICE_FORMAT.get().format(bar.getOpenPrice().doubleValue()),
                    PRICE_FORMAT.get().format(bar.getClosePrice().doubleValue()));
        }
    }

    /**
     * Number format that maps bar-index tick values to bar timestamps.
     */
    private static final class BarIndexDateFormat extends NumberFormat implements Serializable {

        private static final long serialVersionUID = 1L;
        private final BarSeries series;
        private final DateFormat dateFormat;
        private final int beginIndex;
        private final int endIndex;

        private BarIndexDateFormat(BarSeries series, DateFormat dateFormat) {
            this.series = series;
            this.dateFormat = dateFormat;
            this.beginIndex = series.getBeginIndex();
            this.endIndex = series.getEndIndex();
        }

        @Override
        public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
            int index = (int) Math.round(number);
            if (index < beginIndex) {
                index = beginIndex;
            } else if (index > endIndex) {
                index = endIndex;
            }
            Bar bar = series.getBar(index);
            toAppendTo.append(dateFormat.format(Date.from(bar.getEndTime())));
            return toAppendTo;
        }

        @Override
        public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
            return format((double) number, toAppendTo, pos);
        }

        @Override
        public Number parse(String source, ParsePosition parsePosition) {
            return null;
        }
    }
}
