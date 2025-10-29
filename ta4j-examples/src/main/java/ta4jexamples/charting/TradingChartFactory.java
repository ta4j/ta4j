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
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.OHLCDataItem;
import org.jfree.data.xy.XYDataset;
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
        domainAxis.setLowerMargin(0.02);
        domainAxis.setUpperMargin(0.02);
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

    JFreeChart createAnalysisChart(BarSeries series, AnalysisType... analysisTypes) {
        DefaultOHLCDataset data = createChartDataset(series);
        String chartTitle = buildChartTitle(series.getName(), "");
        JFreeChart chart = buildChart(chartTitle, series.getFirstBar().getTimePeriod(), data);

        if (analysisTypes.length > 0) {
            addAnalysisLinesToChart((XYPlot) chart.getPlot(), data, analysisTypes);
        }
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
        domainAxis.setLowerMargin(0.02);
        domainAxis.setUpperMargin(0.02);
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

        annotateTrade(plot, trade, positionIndex, orderDateTime, price);
    }

    private void annotateTrade(XYPlot plot, Trade trade, int positionIndex, double orderDateTime, double price) {
        String labelPrefix = trade.isBuy() ? "B" : "S";
        String label = labelPrefix + positionIndex + " @" + PRICE_FORMAT.get().format(price);

        XYTextAnnotation annotation = new XYTextAnnotation(label, orderDateTime, price);
        annotation.setFont(TRADE_LABEL_FONT);
        annotation.setPaint(trade.isBuy() ? BUY_MARKER_COLOR : SELL_MARKER_COLOR);
        annotation.setBackgroundPaint(TRADE_LABEL_BACKGROUND);
        annotation.setTextAnchor(
                trade.isBuy() ? org.jfree.chart.ui.TextAnchor.BOTTOM_LEFT : org.jfree.chart.ui.TextAnchor.TOP_LEFT);
        plot.addAnnotation(annotation);
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
        marker.setLabelAnchor(org.jfree.chart.ui.RectangleAnchor.TOP_LEFT);
        marker.setLabelTextAnchor(org.jfree.chart.ui.TextAnchor.TOP_LEFT);
        plot.addDomainMarker(marker, org.jfree.chart.ui.Layer.BACKGROUND);
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

    private void addAnalysisLinesToChart(XYPlot plot, DefaultOHLCDataset data, AnalysisType... analysisTypes) {
        try {
            plot.addRangeMarker(new ValueMarker(300d, Color.RED, new BasicStroke(0.1f)),
                    org.jfree.chart.ui.Layer.FOREGROUND);

            int index = 1;
            for (AnalysisType analysisType : analysisTypes) {
                XYDataset analysisDataSet = analysisType.dataSet.getAnalysis(data);
                plot.setDataset(index, analysisDataSet);
                plot.setRenderer(index, new StandardXYItemRenderer());
                index++;
            }
        } catch (Exception ex) {
            LOG.error("Failed to add analysis lines to chart", ex);
        }
    }

    private org.jfree.data.time.TimeSeriesCollection createTimeSeriesDataset(BarSeries series, Indicator<Num> indicator,
            String seriesName) {
        org.jfree.data.time.TimeSeries timeSeries = new org.jfree.data.time.TimeSeries(seriesName);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Bar bar = series.getBar(i);
            Date barDate = Date.from(bar.getEndTime());
            Num value = indicator.getValue(i);
            if (value != null && !value.isNaN()) {
                timeSeries.add(new org.jfree.data.time.Minute(barDate), value.doubleValue());
            }
        }
        return new org.jfree.data.time.TimeSeriesCollection(timeSeries);
    }

    private void configureDualAxisPlot(XYPlot plot, Duration duration) {
        configureDomainAxis(plot, duration);
        configureRangeAxis(plot);
        configurePlotAppearance(plot);
    }

    private void addSecondaryAxis(XYPlot plot, TimeSeriesCollection dataset, String label) {
        NumberAxis secondaryAxis = new NumberAxis(label);
        secondaryAxis.setAutoRangeIncludesZero(false);
        secondaryAxis.setTickLabelPaint(Color.LIGHT_GRAY);
        secondaryAxis.setLabelPaint(Color.LIGHT_GRAY);
        plot.setRangeAxis(1, secondaryAxis);
        plot.setDataset(1, dataset);
        plot.mapDatasetToRangeAxis(1, 1);
        StandardXYItemRenderer secondaryRenderer = new StandardXYItemRenderer();
        secondaryRenderer.setSeriesPaint(0, Color.BLUE);
        plot.setRenderer(1, secondaryRenderer);
    }
}
