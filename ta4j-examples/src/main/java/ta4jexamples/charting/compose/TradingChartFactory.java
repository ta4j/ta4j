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

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.Layer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.OHLCDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Polygon;
import java.awt.Shape;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Builds {@link JFreeChart} instances with different overlays for TA4J trading
 * data.
 *
 * @since 0.19
 */
final class TradingChartFactory {

    private static final Logger LOG = LogManager.getLogger(TradingChartFactory.class);

    private static final Color CHART_BACKGROUND_COLOR = Color.BLACK;
    private static final float CHART_BACKGROUND_ALPHA = 0.85f;
    private static final Color GRIDLINE_COLOR = new Color(0x232323);
    private static final Color BUY_MARKER_COLOR = new Color(0x26A69A);
    private static final Color SELL_MARKER_COLOR = new Color(0xEF5350);
    private static final Color TRADE_LABEL_BACKGROUND = new Color(0, 0, 0, 170);
    private static final float POSITION_BAND_ALPHA = 0.14f;
    private static final int TRADE_MARKER_SIZE = 7;
    private static final Color[] POSITION_BAND_COLORS = { new Color(33, 150, 243), new Color(156, 39, 176),
            new Color(76, 175, 80) };
    private static final Font TRADE_LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private static final Font POSITION_LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final int LABEL_EDGE_BARS = 5;
    private static final ThreadLocal<DecimalFormat> PRICE_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00###");
        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);
        return decimalFormat;
    });

    private static final String DATE_FORMAT_DAILY = "yyyy-MM-dd";
    private static final String DATE_FORMAT_INTRADAY = "yyyy-MM-dd HH:mm:ss";

    JFreeChart createTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        DefaultOHLCDataset data = createChartDataset(series);
        String chartTitle = buildChartTitle(series.getName(), strategyName);
        JFreeChart chart = buildChart(chartTitle, series.getFirstBar().getTimePeriod(), data);
        addTradingRecordToChart((XYPlot) chart.getPlot(), series, tradingRecord);
        return chart;
    }

    @SafeVarargs
    final JFreeChart createTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord,
            Indicator<Num>... indicators) {
        if (indicators == null || indicators.length == 0) {
            return createTradingRecordChart(series, strategyName, tradingRecord);
        }

        JFreeChart chart = createIndicatorChart(series, indicators);
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
                addTradingRecordToChart(subplots.get(0), series, tradingRecord);
            }
        } else if (chart.getPlot() instanceof XYPlot plot) {
            addTradingRecordToChart(plot, series, tradingRecord);
        }

        return chart;
    }

    @SafeVarargs
    final JFreeChart createIndicatorChart(BarSeries series, Indicator<Num>... indicators) {
        if (indicators == null || indicators.length == 0) {
            // No indicators, return simple OHLC chart
            DefaultOHLCDataset data = createChartDataset(series);
            String chartTitle = series.toString();
            if (series.getName() != null) {
                chartTitle = series.getName();
            }
            return buildChart(chartTitle, series.getFirstBar().getTimePeriod(), data);
        }

        // Create shared domain axis (X-axis)
        DateAxis domainAxis = new DateAxis("Date");
        Duration duration = series.getFirstBar().getTimePeriod();
        if (duration.toDays() >= 1) {
            domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_DAILY));
        } else {
            domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_INTRADAY));
        }
        domainAxis.setAutoRange(true);
        domainAxis.setLowerMargin(0.03);
        domainAxis.setUpperMargin(0.07);
        domainAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        domainAxis.setLabelPaint(Color.LIGHT_GRAY);

        // Create combined plot
        CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(domainAxis);
        combinedPlot.setGap(10.0);
        combinedPlot.setOrientation(PlotOrientation.VERTICAL);
        combinedPlot.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        combinedPlot.setBackgroundAlpha(CHART_BACKGROUND_ALPHA);
        combinedPlot.setDomainGridlinePaint(GRIDLINE_COLOR);
        combinedPlot.setRangeGridlinePaint(GRIDLINE_COLOR);

        // Create main OHLC candlestick plot
        DefaultOHLCDataset ohlcData = createChartDataset(series);
        XYPlot mainPlot = createOHLCPlot(ohlcData, duration);
        combinedPlot.add(mainPlot, calculateMainPlotWeight(indicators.length));

        // Create a separate subplot for each indicator
        for (Indicator<Num> indicator : indicators) {
            XYPlot indicatorPlot = createIndicatorSubplot(series, indicator);
            combinedPlot.add(indicatorPlot, calculateIndicatorPlotWeight(indicators.length));
        }

        // Create chart with combined plot
        String chartTitle = series.toString();
        if (series.getName() != null) {
            chartTitle = series.getName();
        }
        JFreeChart chart = new JFreeChart(chartTitle, null, combinedPlot, true);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        chart.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        chart.setBackgroundImageAlpha(CHART_BACKGROUND_ALPHA);

        // Style the title to be visible on black background
        if (chart.getTitle() != null) {
            chart.getTitle().setPaint(Color.LIGHT_GRAY);
        }

        return chart;
    }

    JFreeChart compose(ChartBuilder.ChartDefinition definition) {
        Objects.requireNonNull(definition, "Chart definition cannot be null");
        ChartBuilder.PlotDefinition baseDefinition = Objects.requireNonNull(definition.basePlot(),
                "Base plot cannot be null");

        CombinedDomainXYPlot combinedPlot = createCombinedPlot(baseDefinition.series(), definition.subplots().size());
        XYPlot basePlot = buildPlotFromDefinition(baseDefinition);
        combinedPlot.add(basePlot, calculateMainPlotWeight(definition.subplots().size()));

        if (!definition.subplots().isEmpty()) {
            int subplotWeight = calculateIndicatorPlotWeight(definition.subplots().size());
            for (ChartBuilder.PlotDefinition subplot : definition.subplots()) {
                combinedPlot.add(buildPlotFromDefinition(subplot), subplotWeight);
            }
        }

        String resolvedTitle = definition.title() != null && !definition.title().trim().isEmpty() ? definition.title()
                : buildChartTitle(baseDefinition.series() != null ? baseDefinition.series().getName() : "", "");
        JFreeChart chart = new JFreeChart(resolvedTitle, JFreeChart.DEFAULT_TITLE_FONT, combinedPlot, true);
        chart.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        return chart;
    }

    JFreeChart createDualAxisChart(BarSeries series, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> secondaryIndicator, String secondaryLabel) {
        return createDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator, secondaryLabel, null);
    }

    JFreeChart createDualAxisChart(BarSeries series, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> secondaryIndicator, String secondaryLabel, String chartTitle) {
        String effectiveTitle = chartTitle != null && !chartTitle.trim().isEmpty() ? chartTitle
                : (series.getName() != null ? series.getName() : series.toString());
        TimeSeriesCollection primaryDataset = createTimeSeriesDataset(series, primaryIndicator, primaryLabel);
        TimeSeriesCollection secondaryDataset = createTimeSeriesDataset(series, secondaryIndicator, secondaryLabel);

        JFreeChart chart = ChartFactory.createTimeSeriesChart(effectiveTitle, "Date", primaryLabel, primaryDataset,
                true, true, false);

        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        chart.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        chart.setBackgroundImageAlpha(CHART_BACKGROUND_ALPHA);

        // Style the title to be visible on black background
        if (chart.getTitle() != null) {
            chart.getTitle().setPaint(Color.LIGHT_GRAY);
        }

        XYPlot plot = (XYPlot) chart.getPlot();
        configureDualAxisPlot(plot, series.getFirstBar().getTimePeriod());
        addSecondaryAxis(plot, secondaryDataset, secondaryLabel);

        return chart;
    }

    /**
     * Creates a dual-axis chart with a trading record (OHLC) on the left axis and
     * an analysis criterion on the right axis.
     *
     * @param series               the bar series
     * @param strategyName         the strategy name
     * @param tradingRecord        the trading record
     * @param primaryIndicator     the primary indicator (typically close price) for
     *                             the left axis
     * @param primaryLabel         the label for the primary axis
     * @param criterionIndicator   the analysis criterion indicator for the right
     *                             axis
     * @param criterionLabel       the label for the criterion axis
     * @param additionalIndicators optional additional indicators to add as subplots
     * @param chartTitle           optional custom chart title
     * @return the dual-axis chart
     */
    final JFreeChart createDualAxisChartWithAnalysisCriterion(BarSeries series, String strategyName,
            TradingRecord tradingRecord, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> criterionIndicator, String criterionLabel, Indicator<Num>[] additionalIndicators,
            String chartTitle) {
        // Create base trading record chart (may include indicators as subplots)
        JFreeChart chart;
        if (additionalIndicators != null && additionalIndicators.length > 0) {
            chart = createTradingRecordChart(series, strategyName, tradingRecord, additionalIndicators);
        } else {
            chart = createTradingRecordChart(series, strategyName, tradingRecord);
        }

        // Apply custom title if specified
        if (chartTitle != null && !chartTitle.trim().isEmpty()) {
            chart.setTitle(chartTitle);
            if (chart.getTitle() != null) {
                chart.getTitle().setPaint(Color.LIGHT_GRAY);
            }
        }

        // Get the main plot (first subplot if combined, or the plot itself)
        XYPlot mainPlot;
        if (chart.getPlot() instanceof CombinedDomainXYPlot combinedPlot) {
            List<XYPlot> subplots = combinedPlot.getSubplots();
            if (subplots != null && !subplots.isEmpty()) {
                mainPlot = subplots.get(0);
            } else {
                throw new IllegalStateException("Combined plot has no subplots");
            }
        } else if (chart.getPlot() instanceof XYPlot plot) {
            mainPlot = plot;
        } else {
            throw new IllegalStateException("Chart plot is not an XYPlot");
        }

        // Add secondary axis with analysis criterion
        TimeSeriesCollection criterionDataset = createTimeSeriesDataset(series, criterionIndicator, criterionLabel);
        addSecondaryAxis(mainPlot, criterionDataset, criterionLabel);

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

        // Add secondary axis with analysis criterion
        TimeSeriesCollection criterionDataset = createTimeSeriesDataset(series, criterionIndicator, criterionLabel);
        addSecondaryAxis(mainPlot, criterionDataset, criterionLabel);

        return chart;
    }

    String buildChartTitle(String barSeriesName, String strategyName) {
        if (barSeriesName == null || barSeriesName.trim().isEmpty()) {
            return strategyName;
        }
        String[] shortenedBarSeriesName = barSeriesName.split(" ");
        if (strategyName == null || strategyName.trim().isEmpty()) {
            return shortenedBarSeriesName[0];
        }
        return strategyName + "@" + shortenedBarSeriesName[0];
    }

    /**
     * Adds indicators as subplots to an existing chart, converting it to a combined
     * plot if necessary.
     *
     * @param chart      the chart to add indicators to
     * @param series     the bar series
     * @param indicators the indicators to add as subplots
     * @return the modified chart (may be a new chart instance if conversion was
     *         needed)
     */
    JFreeChart addIndicatorsToChart(JFreeChart chart, BarSeries series, Indicator<Num>... indicators) {
        if (chart == null || series == null || indicators == null || indicators.length == 0) {
            return chart;
        }

        // If chart already has a CombinedDomainXYPlot, add indicators to it
        if (chart.getPlot() instanceof CombinedDomainXYPlot combinedPlot) {
            for (Indicator<Num> indicator : indicators) {
                XYPlot indicatorPlot = createIndicatorSubplot(series, indicator);
                List<XYPlot> existingSubplots = combinedPlot.getSubplots();
                int currentIndicatorCount = existingSubplots != null ? existingSubplots.size() - 1 : 0;
                combinedPlot.add(indicatorPlot,
                        calculateIndicatorPlotWeight(currentIndicatorCount + indicators.length));
            }
            return chart;
        }

        // If chart has a simple XYPlot, convert to CombinedDomainXYPlot
        if (chart.getPlot() instanceof XYPlot mainPlot) {
            // Extract the main plot's domain axis configuration
            DateAxis domainAxis = (DateAxis) mainPlot.getDomainAxis();
            if (domainAxis == null) {
                domainAxis = new DateAxis("Date");
                Duration duration = series.getFirstBar().getTimePeriod();
                if (duration.toDays() >= 1) {
                    domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_DAILY));
                } else {
                    domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_INTRADAY));
                }
                domainAxis.setAutoRange(true);
                domainAxis.setLowerMargin(0.02);
                domainAxis.setUpperMargin(0.02);
                domainAxis.setTickLabelPaint(Color.LIGHT_GRAY);
                domainAxis.setLabelPaint(Color.LIGHT_GRAY);
            }

            // Create combined plot
            CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(domainAxis);
            combinedPlot.setGap(10.0);
            combinedPlot.setOrientation(PlotOrientation.VERTICAL);
            combinedPlot.setBackgroundPaint(CHART_BACKGROUND_COLOR);
            combinedPlot.setBackgroundAlpha(CHART_BACKGROUND_ALPHA);
            combinedPlot.setDomainGridlinePaint(GRIDLINE_COLOR);
            combinedPlot.setRangeGridlinePaint(GRIDLINE_COLOR);

            // Add main plot
            combinedPlot.add(mainPlot, calculateMainPlotWeight(indicators.length));

            // Add indicator subplots
            for (Indicator<Num> indicator : indicators) {
                XYPlot indicatorPlot = createIndicatorSubplot(series, indicator);
                combinedPlot.add(indicatorPlot, calculateIndicatorPlotWeight(indicators.length));
            }

            // Create new chart with combined plot
            String chartTitle = chart.getTitle() != null ? chart.getTitle().getText() : series.getName();
            JFreeChart newChart = new JFreeChart(chartTitle, null, combinedPlot, true);
            newChart.setAntiAlias(true);
            newChart.setTextAntiAlias(true);
            newChart.setBackgroundPaint(CHART_BACKGROUND_COLOR);
            newChart.setBackgroundImageAlpha(CHART_BACKGROUND_ALPHA);

            if (newChart.getTitle() != null) {
                newChart.getTitle().setPaint(Color.LIGHT_GRAY);
            }

            return newChart;
        }

        return chart;
    }

    /**
     * Adds additional indicators as series to a dual-axis chart.
     *
     * @param chart      the dual-axis chart
     * @param series     the bar series
     * @param indicators the indicators to add as additional series
     */
    void addIndicatorsToDualAxisChart(JFreeChart chart, BarSeries series, Indicator<Num>... indicators) {
        if (chart == null || series == null || indicators == null || indicators.length == 0) {
            return;
        }

        if (!(chart.getPlot() instanceof XYPlot plot)) {
            return;
        }

        // Determine next dataset index
        int nextDatasetIndex = plot.getDatasetCount();

        // Add each indicator as a new series, alternating between left (0) and right
        // (1) axes
        for (int i = 0; i < indicators.length; i++) {
            Indicator<Num> indicator = indicators[i];
            TimeSeriesCollection dataset = createTimeSeriesDataset(series, indicator, indicator.toString());

            // Alternate between left (0) and right (1) axes
            int axisIndex = (i % 2 == 0) ? 1 : 0; // Start with right axis (1), then alternate

            plot.setDataset(nextDatasetIndex, dataset);
            plot.mapDatasetToRangeAxis(nextDatasetIndex, axisIndex);
            StandardXYItemRenderer renderer = new StandardXYItemRenderer();
            // Use different colors for additional series
            Color[] colors = { Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.ORANGE };
            renderer.setSeriesPaint(0, colors[i % colors.length]);
            plot.setRenderer(nextDatasetIndex, renderer);

            nextDatasetIndex++;
        }
    }

    private DefaultOHLCDataset createChartDataset(BarSeries series) {
        List<OHLCDataItem> dataItems = new ArrayList<>();

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Bar bar = series.getBar(i);
            OHLCDataItem item = new OHLCDataItem(Date.from(bar.getEndTime()), bar.getOpenPrice().doubleValue(),
                    bar.getHighPrice().doubleValue(), bar.getLowPrice().doubleValue(),
                    bar.getClosePrice().doubleValue(), bar.getVolume().doubleValue());
            dataItems.add(item);
        }

        String seriesName = series.getName() != null ? series.getName().split(" ")[0] : "Unknown";
        return new DefaultOHLCDataset(seriesName, dataItems.toArray(new OHLCDataItem[0]));
    }

    private JFreeChart buildChart(String chartTitle, Duration duration, DefaultOHLCDataset dataSet) {
        JFreeChart chart = ChartFactory.createCandlestickChart(chartTitle, "Date", "Price (USD)", dataSet, true);

        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        chart.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        chart.setBackgroundImageAlpha(CHART_BACKGROUND_ALPHA);

        // Style the title to be visible on black background
        if (chart.getTitle() != null) {
            chart.getTitle().setPaint(Color.LIGHT_GRAY);
        }

        XYPlot plot = (XYPlot) chart.getPlot();
        configureDomainAxis(plot, duration);
        configureRangeAxis(plot);
        configureCandlestickRenderer(plot);
        configurePlotAppearance(plot);

        return chart;
    }

    private void configureDomainAxis(XYPlot plot, Duration duration) {
        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();

        if (duration.toDays() >= 1) {
            domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_DAILY));
        } else {
            domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_INTRADAY));
        }
        domainAxis.setAutoRange(true);
        domainAxis.setLowerMargin(0.03);
        domainAxis.setUpperMargin(0.07);
        domainAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        domainAxis.setLabelPaint(Color.LIGHT_GRAY);
    }

    private void configureRangeAxis(XYPlot plot) {
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        rangeAxis.setLabelPaint(Color.LIGHT_GRAY);
    }

    private void configureCandlestickRenderer(XYPlot plot) {
        BaseCandleStickRenderer candleStickRenderer = new BaseCandleStickRenderer();
        candleStickRenderer.setDownPaint(BaseCandleStickRenderer.DEFAULT_DOWN_COLOR);
        candleStickRenderer.setUpPaint(BaseCandleStickRenderer.DEFAULT_UP_COLOR);
        candleStickRenderer.setAutoWidthMethod(BaseCandleStickRenderer.WIDTHMETHOD_SMALLEST);
        candleStickRenderer.setAutoWidthGap(0.35);
        candleStickRenderer.setAutoWidthFactor(0.65);
        candleStickRenderer.setUseOutlinePaint(true);
        candleStickRenderer.setDrawVolume(false);
        plot.setRenderer(candleStickRenderer);
    }

    private void configurePlotAppearance(XYPlot plot) {
        plot.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        plot.setBackgroundAlpha(CHART_BACKGROUND_ALPHA);
        plot.setDomainGridlinePaint(GRIDLINE_COLOR);
        plot.setRangeGridlinePaint(GRIDLINE_COLOR);
    }

    private CombinedDomainXYPlot createCombinedPlot(BarSeries series, int subplotCount) {
        Objects.requireNonNull(series, "BarSeries cannot be null");
        DateAxis domainAxis = new DateAxis("Date");
        Duration duration = series.getFirstBar().getTimePeriod();
        if (duration.toDays() >= 1) {
            domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_DAILY));
        } else {
            domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_INTRADAY));
        }
        domainAxis.setAutoRange(true);
        domainAxis.setLowerMargin(0.02);
        domainAxis.setUpperMargin(0.02);
        domainAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        domainAxis.setLabelPaint(Color.LIGHT_GRAY);

        CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(domainAxis);
        combinedPlot.setGap(10.0);
        combinedPlot.setOrientation(PlotOrientation.VERTICAL);
        combinedPlot.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        combinedPlot.setBackgroundAlpha(CHART_BACKGROUND_ALPHA);
        combinedPlot.setDomainGridlinePaint(GRIDLINE_COLOR);
        combinedPlot.setRangeGridlinePaint(GRIDLINE_COLOR);
        return combinedPlot;
    }

    private XYPlot buildPlotFromDefinition(ChartBuilder.PlotDefinition definition) {
        XYPlot plot = switch (definition.type()) {
        case CANDLESTICK -> buildCandlestickPlot(definition);
        case INDICATOR -> buildIndicatorPlot(definition);
        case TRADING_RECORD -> buildTradingRecordPlot(definition);
        };
        attachOverlays(plot, definition);
        return plot;
    }

    private XYPlot buildCandlestickPlot(ChartBuilder.PlotDefinition definition) {
        DefaultOHLCDataset dataset = createChartDataset(definition.series());
        return createOHLCPlot(dataset, definition.series().getFirstBar().getTimePeriod());
    }

    private XYPlot buildIndicatorPlot(ChartBuilder.PlotDefinition definition) {
        Indicator<Num> indicator = Objects.requireNonNull(definition.baseIndicator(),
                "Indicator plot requires a base indicator");
        return createIndicatorSubplot(definition.series(), indicator);
    }

    private XYPlot buildTradingRecordPlot(ChartBuilder.PlotDefinition definition) {
        TradingRecord tradingRecord = Objects.requireNonNull(definition.tradingRecord(),
                "Trading record plot requires trading data");
        BarSeries series = Objects.requireNonNull(definition.series(),
                "Trading record plots require a BarSeries for context");

        XYPlot plot = new XYPlot();
        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries closePriceSeries = createDataSeriesForIndicator(new ClosePriceIndicator(series));
        dataset.addSeries(closePriceSeries);
        plot.setDataset(0, dataset);

        configureDomainAxis(plot, series.getFirstBar().getTimePeriod());
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
        addTradingRecordToChart(plot, series, tradingRecord);
        return plot;
    }

    private void attachOverlays(XYPlot plot, ChartBuilder.PlotDefinition definition) {
        for (ChartBuilder.OverlayDefinition overlay : definition.overlays()) {
            switch (overlay.type()) {
            case INDICATOR:
            case ANALYSIS_CRITERION:
                attachIndicatorOverlay(plot, definition, overlay);
                break;
            case TRADING_RECORD:
                addTradingRecordToChart(plot, definition.series(), overlay.tradingRecord());
                break;
            default:
                break;
            }
        }
    }

    private void attachIndicatorOverlay(XYPlot plot, ChartBuilder.PlotDefinition definition,
            ChartBuilder.OverlayDefinition overlay) {
        Indicator<Num> indicator = overlay.indicator();
        if (indicator == null) {
            return;
        }
        BarSeries series = indicator.getBarSeries() != null ? indicator.getBarSeries() : definition.series();
        TimeSeriesCollection dataset = createTimeSeriesDataset(series, indicator, indicator.toString());
        int datasetIndex = plot.getDatasetCount();
        plot.setDataset(datasetIndex, dataset);

        StandardXYItemRenderer renderer = new StandardXYItemRenderer();
        renderer.setSeriesPaint(0, overlay.style().color());
        renderer.setSeriesStroke(0, new BasicStroke(overlay.style().lineWidth()));
        plot.setRenderer(datasetIndex, renderer);

        int axisIndex = overlay.axisSlot() == ChartBuilder.AxisSlot.SECONDARY ? 1 : 0;
        if (axisIndex == 1) {
            ensureSecondaryAxisExists(plot, indicator.toString());
        }
        plot.mapDatasetToRangeAxis(datasetIndex, axisIndex);
    }

    private void ensureSecondaryAxisExists(XYPlot plot, String label) {
        if (plot.getRangeAxisCount() > 1 && plot.getRangeAxis(1) != null) {
            return;
        }
        NumberAxis secondaryAxis = new NumberAxis(label != null ? label : "Value");
        secondaryAxis.setAutoRangeIncludesZero(false);
        secondaryAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        secondaryAxis.setLabelPaint(Color.LIGHT_GRAY);
        plot.setRangeAxis(1, secondaryAxis);
    }

    private void addTradingRecordToChart(XYPlot plot, BarSeries series, TradingRecord tradingRecord) {
        try {
            XYSeries buyMarkers = createTradeSeries("Buy trades");
            XYSeries sellMarkers = createTradeSeries("Sell trades");
            int positionIndex = 1;

            for (Position position : tradingRecord.getPositions()) {
                addTradeMarker(buyMarkers, sellMarkers, plot, series, position.getEntry(), positionIndex);
                addTradeMarker(buyMarkers, sellMarkers, plot, series, position.getExit(), positionIndex);
                addPositionBand(plot, series, positionIndex, position.getEntry(), position.getExit());
                positionIndex++;
            }

            if (tradingRecord.getCurrentPosition().isOpened()) {
                Trade lastTrade = tradingRecord.getLastTrade();
                if (lastTrade != null) {
                    addTradeMarker(buyMarkers, sellMarkers, plot, series, lastTrade, positionIndex);
                }
                addPositionBand(plot, series, positionIndex, tradingRecord.getCurrentPosition().getEntry(), null);
            }

            attachTradeDataset(plot, buyMarkers, sellMarkers);
        } catch (Exception ex) {
            LOG.error("Failed to add trading record to chart", ex);
        }
    }

    private void addTradeMarker(XYSeries buyMarkers, XYSeries sellMarkers, XYPlot plot, BarSeries series, Trade trade,
            int positionIndex) {
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

        double orderDateTime = tradeTime.toEpochMilli();
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

    private void addPositionBand(XYPlot plot, BarSeries series, int positionIndex, Trade entry, Trade exit) {
        if (entry == null) {
            return;
        }

        long start = series.getBar(entry.getIndex()).getEndTime().toEpochMilli();
        long end;
        if (exit != null) {
            end = series.getBar(exit.getIndex()).getEndTime().toEpochMilli();
        } else {
            end = series.getLastBar().getEndTime().toEpochMilli();
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

    private XYSeries createDataSeriesForIndicator(Indicator<Num> indicator) {
        XYSeries indicatorSeries = new XYSeries(indicator.toString());

        for (int i = indicator.getBarSeries().getBeginIndex(); i < indicator.getBarSeries().getEndIndex(); i++) {
            Num value = indicator.getValue(i);
            if (value != null && !value.isNaN()) {
                double orderDateTime = indicator.getBarSeries().getBar(i).getEndTime().toEpochMilli();
                indicatorSeries.add(orderDateTime, value.doubleValue());
            }
        }
        return indicatorSeries;
    }

    private XYPlot createOHLCPlot(DefaultOHLCDataset data, Duration duration) {
        XYPlot plot = new XYPlot();
        plot.setDataset(0, data);

        // Configure domain axis
        DateAxis domainAxis = new DateAxis("Date");
        if (duration.toDays() >= 1) {
            domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_DAILY));
        } else {
            domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_INTRADAY));
        }
        domainAxis.setAutoRange(true);
        domainAxis.setLowerMargin(0.02);
        domainAxis.setUpperMargin(0.02);
        domainAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        domainAxis.setLabelPaint(Color.LIGHT_GRAY);
        plot.setDomainAxis(domainAxis);

        // Configure range axis
        NumberAxis rangeAxis = new NumberAxis("Price (USD)");
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        rangeAxis.setLabelPaint(Color.LIGHT_GRAY);
        plot.setRangeAxis(rangeAxis);

        // Configure candlestick renderer
        configureCandlestickRenderer(plot);
        configurePlotAppearance(plot);

        return plot;
    }

    private XYPlot createIndicatorSubplot(BarSeries series, Indicator<Num> indicator) {
        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries indicatorSeries = createDataSeriesForIndicator(indicator);
        dataset.addSeries(indicatorSeries);

        XYPlot plot = new XYPlot();
        plot.setDataset(0, dataset);

        // Configure domain axis (will be shared by CombinedDomainXYPlot, but we set it
        // for consistency)
        DateAxis domainAxis = new DateAxis();
        Duration duration = series.getFirstBar().getTimePeriod();
        if (duration.toDays() >= 1) {
            domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_DAILY));
        } else {
            domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_INTRADAY));
        }
        domainAxis.setAutoRange(true);
        domainAxis.setLowerMargin(0.02);
        domainAxis.setUpperMargin(0.02);
        domainAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        domainAxis.setLabelPaint(Color.LIGHT_GRAY);
        plot.setDomainAxis(domainAxis);

        // Configure range axis with indicator label
        String indicatorLabel = indicator.toString();
        NumberAxis rangeAxis = new NumberAxis(indicatorLabel);
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        rangeAxis.setLabelPaint(Color.LIGHT_GRAY);
        plot.setRangeAxis(rangeAxis);

        // Configure renderer
        StandardXYItemRenderer renderer = new StandardXYItemRenderer();
        plot.setRenderer(0, renderer);
        configurePlotAppearance(plot);

        return plot;
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

    private TimeSeriesCollection createTimeSeriesDataset(BarSeries series, Indicator<Num> indicator,
            String seriesName) {
        TimeSeries timeSeries = new TimeSeries(seriesName);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Bar bar = series.getBar(i);
            Date barDate = Date.from(bar.getEndTime());
            Num value = indicator.getValue(i);
            if (value != null && !value.isNaN()) {
                timeSeries.add(new Minute(barDate), value.doubleValue());
            }
        }
        return new TimeSeriesCollection(timeSeries);
    }

    private void configureDualAxisPlot(XYPlot plot, Duration duration) {
        configureDomainAxis(plot, duration);
        configureRangeAxis(plot);
        configurePlotAppearance(plot);
    }

    private void addSecondaryAxis(XYPlot plot, TimeSeriesCollection dataset, String label) {
        NumberAxis secondaryAxis = null;
        if (plot.getRangeAxisCount() > 1) {
            secondaryAxis = (NumberAxis) plot.getRangeAxis(1);
        }

        if (secondaryAxis == null) {
            secondaryAxis = new NumberAxis(label);
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
        secondaryRenderer.setSeriesPaint(0, Color.BLUE);
        plot.setRenderer(datasetIndex, secondaryRenderer);
    }
}
