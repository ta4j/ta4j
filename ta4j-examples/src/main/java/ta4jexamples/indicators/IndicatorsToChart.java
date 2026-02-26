/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.indicators;

import java.awt.Dimension;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.chart.ui.UIUtils;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

/**
 * This class builds a graphical chart showing values from indicators.
 */
public class IndicatorsToChart {

    /**
     * Builds a JFreeChart time series from a Ta4j bar series and an indicator.
     *
     * @param barSeries the ta4j bar series
     * @param indicator the indicator
     * @param name      the name of the chart time series
     * @return the JFreeChart time series
     */
    private static TimeSeries buildChartBarSeries(BarSeries barSeries, Indicator<Num> indicator, String name) {
        TimeSeries chartTimeSeries = new TimeSeries(name);
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            Bar bar = barSeries.getBar(i);
            chartTimeSeries.add(new Day(Date.from(bar.getEndTime())), indicator.getValue(i).doubleValue());
        }
        return chartTimeSeries;
    }

    /**
     * Displays a chart in a frame. The frame is configured to avoid stealing focus
     * when launched from tests or scripts.
     *
     * @param chart the chart to be displayed
     */
    private static void displayChart(JFreeChart chart) {
        // Chart panel
        ChartPanel panel = new ChartPanel(chart);
        panel.setFillZoomRectangle(true);
        panel.setMouseWheelEnabled(true);
        panel.setPreferredSize(new Dimension(500, 270));
        // Application frame
        ApplicationFrame frame = new ApplicationFrame("Ta4j example - Indicators to chart");
        frame.setContentPane(panel);
        frame.pack();
        UIUtils.centerFrameOnScreen(frame);
        frame.setFocusableWindowState(false);
        frame.setVisible(true);
        frame.setAlwaysOnTop(false);
        frame.setAutoRequestFocus(false);
    }

    public static void main(String[] args) {

        /*
         * Getting bar series
         */
        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();

        /*
         * Creating indicators
         */
        // Close price
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator avg14 = new EMAIndicator(closePrice, 14);
        StandardDeviationIndicator sd14 = new StandardDeviationIndicator(closePrice, 14);

        // Bollinger bands
        BollingerBandsMiddleIndicator middleBBand = new BollingerBandsMiddleIndicator(avg14);
        BollingerBandsLowerIndicator lowBBand = new BollingerBandsLowerIndicator(middleBBand, sd14);
        BollingerBandsUpperIndicator upBBand = new BollingerBandsUpperIndicator(middleBBand, sd14);

        /*
         * Building chart dataset
         */
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(buildChartBarSeries(series, closePrice, "Apple Inc. (AAPL) - NASDAQ GS"));
        dataset.addSeries(buildChartBarSeries(series, lowBBand, "Low Bollinger Band"));
        dataset.addSeries(buildChartBarSeries(series, upBBand, "High Bollinger Band"));

        /*
         * Creating the chart
         */
        JFreeChart chart = ChartFactory.createTimeSeriesChart("Apple Inc. 2013 Close Prices", // title
                "Date", // x-axis label
                "Price Per Unit", // y-axis label
                dataset, // data
                true, // create legend?
                true, // generate tooltips?
                false // generate URLs?
        );
        XYPlot plot = (XYPlot) chart.getPlot();
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setDateFormatOverride(new SimpleDateFormat("yyyy-MM-dd"));

        /*
         * Displaying the chart
         */
        displayChart(chart);
    }

}
