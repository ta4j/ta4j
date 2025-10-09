/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package ta4jexamples.indicators;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Stroke;
import java.util.Date;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.chart.ui.UIUtils;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultHighLowDataset;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.XYDataset;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ChopIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import ta4jexamples.loaders.CsvTradesLoader;

/**
 * This class builds a traditional candlestick chart.
 */
public class CandlestickChartWithChopIndicator {
    private static final int CHOP_INDICATOR_TIMEFRAME = 14;
    private static final double CHOP_UPPER_THRESHOLD = 61.8;
    private static final double CHOP_LOWER_THRESHOLD = 38.2;
    private static final int VOLUME_DATASET_INDEX = 1;
    private static final int CHOP_SCALE_VALUE = 100;
    private static CombinedDomainXYPlot combinedPlot;
    private static JFreeChart combinedChart;
    static DateAxis xAxis = new DateAxis("Time");
    private static ChartPanel combinedChartPanel;
    private static XYPlot indicatorXYPlot;
    static Stroke dashedThinLineStyle = new BasicStroke(0.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f,
            new float[] { 8.0f, 4.0f }, 0.0f);
    static BarSeries series;

    /**
     * Builds a JFreeChart OHLC dataset from a ta4j bar series.
     *
     * @param series a bar series
     * @return an Open-High-Low-Close dataset
     */
    private static OHLCDataset createOHLCDataset(BarSeries series) {
        final int nbBars = series.getBarCount();

        Date[] dates = new Date[nbBars];
        double[] opens = new double[nbBars];
        double[] highs = new double[nbBars];
        double[] lows = new double[nbBars];
        double[] closes = new double[nbBars];
        double[] volumes = new double[nbBars];

        for (int i = 0; i < nbBars; i++) {
            Bar bar = series.getBar(i);
            dates[i] = new Date(bar.getEndTime().toEpochSecond() * 1000);
            opens[i] = bar.getOpenPrice().doubleValue();
            highs[i] = bar.getHighPrice().doubleValue();
            lows[i] = bar.getLowPrice().doubleValue();
            closes[i] = bar.getClosePrice().doubleValue();
            volumes[i] = bar.getVolume().doubleValue();
        }

        return new DefaultHighLowDataset("btc", dates, highs, lows, opens, closes, volumes);
    }

    /**
     * Builds an additional JFreeChart dataset from a ta4j bar series.
     *
     * @param series a bar series
     * @return an additional dataset
     */
    private static TimeSeriesCollection createAdditionalDataset(BarSeries series) {
        ClosePriceIndicator indicator = new ClosePriceIndicator(series);
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        org.jfree.data.time.TimeSeries chartTimeSeries = new org.jfree.data.time.TimeSeries("Btc price");
        for (int i = 0; i < series.getBarCount(); i++) {
            Bar bar = series.getBar(i);
            chartTimeSeries.add(new Second(new Date(bar.getEndTime().toEpochSecond() * 1000)),
                    indicator.getValue(i).doubleValue());
        }
        dataset.addSeries(chartTimeSeries);
        return dataset;
    }

    private static TimeSeriesCollection createChopDataset(BarSeries series) {
        ChopIndicator indicator = new ChopIndicator(series, CHOP_INDICATOR_TIMEFRAME, CHOP_SCALE_VALUE);
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        org.jfree.data.time.TimeSeries chartTimeSeries = new org.jfree.data.time.TimeSeries("CHOP_14");
        for (int i = 0; i < series.getBarCount(); i++) {
            Bar bar = series.getBar(i);
            if (i < CHOP_INDICATOR_TIMEFRAME)
                continue;
            chartTimeSeries.add(new Second(new Date(bar.getEndTime().toEpochSecond() * 1000)),
                    indicator.getValue(i).doubleValue());
        }
        dataset.addSeries(chartTimeSeries);
        return dataset;
    }

    /**
     * Displays a chart in a frame.
     *
     * @param ohlcDataset
     * @param xyDataset
     * @param chopSeries
     */
    private static void displayChart(XYDataset ohlcDataset, XYDataset xyDataset, XYDataset chopSeries) {
        /*
         * Create the chart
         */
        CandlestickRenderer renderer = new CandlestickRenderer();
        XYPlot pricePlot = new XYPlot(ohlcDataset, xAxis, new NumberAxis("Price"), renderer);
        renderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_SMALLEST);
        // volume dataset
        pricePlot.setDataset(VOLUME_DATASET_INDEX, xyDataset);
        pricePlot.mapDatasetToRangeAxis(VOLUME_DATASET_INDEX, 0);
        // plot.setDomainAxis( xAxis );
        XYLineAndShapeRenderer renderer2 = new XYLineAndShapeRenderer(true, false);
        renderer2.setSeriesPaint(VOLUME_DATASET_INDEX, Color.blue);
        pricePlot.setRenderer(VOLUME_DATASET_INDEX, renderer2);
        // Misc
        pricePlot.setRangeGridlinePaint(Color.lightGray);
        pricePlot.setBackgroundPaint(Color.white);
        NumberAxis numberAxis = (NumberAxis) pricePlot.getRangeAxis();
        pricePlot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
        renderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_SMALLEST);
        // Misc
        pricePlot.setRangeGridlinePaint(Color.lightGray);
        pricePlot.setBackgroundPaint(Color.white);
        numberAxis.setAutoRangeIncludesZero(false);
        pricePlot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
        // secondary study plot
        indicatorXYPlot = new XYPlot( /* null, xAxis, yAxis, renderer */);
        indicatorXYPlot.setDataset(chopSeries);
        indicatorXYPlot.setRangeAxis(0, new NumberAxis(""));
        indicatorXYPlot.setRenderer(0, new XYLineAndShapeRenderer());
        NumberAxis yIndicatorAxis = new NumberAxis("");
        yIndicatorAxis.setRange(0, CHOP_SCALE_VALUE);
        indicatorXYPlot.setRangeAxis(0, yIndicatorAxis);

        // combinedPlot
        combinedPlot = new CombinedDomainXYPlot(xAxis); // DateAxis
        combinedPlot.setGap(10.0);
        // combinedPlot.setDomainAxis( xAxis );
        combinedPlot.setBackgroundPaint(Color.LIGHT_GRAY);
        combinedPlot.setDomainGridlinePaint(Color.GRAY);
        combinedPlot.setRangeGridlinePaint(Color.GRAY);
        combinedPlot.setOrientation(PlotOrientation.VERTICAL);
        combinedPlot.add(pricePlot, 70);
        combinedPlot.add(indicatorXYPlot, 30);

        // Now create the chart that contains the combinedPlot
        combinedChart = new JFreeChart("Bitstamp BTC price with Chop indicator", null, combinedPlot, true);
        combinedChart.setBackgroundPaint(Color.LIGHT_GRAY);

        // combinedChartPanel to contain combinedChart
        combinedChartPanel = new ChartPanel(combinedChart);
        combinedChartPanel.setLayout(new GridLayout(0, 1));
        combinedChartPanel.setBackground(Color.LIGHT_GRAY);
        combinedChartPanel.setPreferredSize(new java.awt.Dimension(740, 300));

        // Application frame
        ApplicationFrame frame = new ApplicationFrame("Ta4j example - Candlestick chart");
        frame.setContentPane(combinedChartPanel);
        frame.pack();
        UIUtils.centerFrameOnScreen(frame);
        frame.setVisible(true);

        // CHOP oscillator upper/lower threshold guidelines
        XYLineAnnotation lineAnnotation = new XYLineAnnotation(
                (double) series.getFirstBar().getBeginTime().toEpochSecond() * 1000d, CHOP_LOWER_THRESHOLD,
                (double) series.getLastBar().getEndTime().toEpochSecond() * 1000d, CHOP_LOWER_THRESHOLD,
                dashedThinLineStyle, Color.GREEN);
        lineAnnotation.setToolTipText("tradable below this");
        indicatorXYPlot.addAnnotation(lineAnnotation);
        lineAnnotation = new XYLineAnnotation((double) series.getFirstBar().getBeginTime().toEpochSecond() * 1000d,
                CHOP_UPPER_THRESHOLD, (double) series.getLastBar().getEndTime().toEpochSecond() * 1000d,
                CHOP_UPPER_THRESHOLD, dashedThinLineStyle, Color.RED);
        lineAnnotation.setToolTipText("too choppy above this");
        indicatorXYPlot.addAnnotation(lineAnnotation);
    }

    public static void main(String[] args) {
        series = CsvTradesLoader.loadBitstampSeries();
        /*
         * Create the OHLC dataset from the data series
         */
        OHLCDataset ohlcDataset = createOHLCDataset(series);
        /*
         * Create volume dataset
         */
        TimeSeriesCollection xyDataset = createAdditionalDataset(series);
        /*
         * add the CHOP Indicator
         */
        TimeSeriesCollection chopSeries = createChopDataset(series);
        /*
         * Display the chart
         */
        displayChart(ohlcDataset, xyDataset, chopSeries);
    }
}
