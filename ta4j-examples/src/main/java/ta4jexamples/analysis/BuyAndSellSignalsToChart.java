/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package ta4jexamples.analysis;

import java.awt.Color;
import java.awt.Dimension;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.strategies.MovingMomentumStrategy;

/**
 * This class builds a graphical chart showing the buy/sell signals of a strategy.
 * * 此类构建一个图形图表，显示策略的买入/卖出信号。
 */
public class BuyAndSellSignalsToChart {

    /**
     * Builds a JFreeChart time series from a Ta4j bar series and an indicator.
     * * 从 Ta4j 条形序列和指标构建 JFreeChart 时间序列。
     *
     * @param barSeries the ta4j bar series
     *                  ta4j 酒吧系列
     *
     * @param indicator the indicator
     *                  指标
     *
     * @param name      the name of the chart time series
     *                  图表时间序列的名称
     *
     * @return the JFreeChart time series
     * @return JFreeChart 时间序列
     */
    private static org.jfree.data.time.TimeSeries buildChartTimeSeries(BarSeries barSeries, Indicator<Num> indicator,
            String name) {
        org.jfree.data.time.TimeSeries chartTimeSeries = new org.jfree.data.time.TimeSeries(name);
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            Bar bar = barSeries.getBar(i);
            chartTimeSeries.add(new Minute(Date.from(bar.getEndTime().toInstant())),
                    indicator.getValue(i).doubleValue());
        }
        return chartTimeSeries;
    }

    /**
     * Runs a strategy over a bar series and adds the value markers corresponding to buy/sell signals to the plot.
     * * 在条形系列上运行策略，并将对应于买入/卖出信号的价值标记添加到图中。
     *
     * @param series   the bar series
     *                 酒吧系列
     *
     * @param strategy the trading strategy
     *                 交易策略
     *
     * @param plot     the plot
     *                 剧情
     */
    private static void addBuySellSignals(BarSeries series, Strategy strategy, XYPlot plot) {
        // Running the strategy
        // 运行策略
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        List<Position> positions = seriesManager.run(strategy).getPositions();
        // Adding markers to plot
        // 为绘图添加标记
        for (Position position : positions) {
            // Buy signal
            // 买入信号
            double buySignalBarTime = new Minute(
                    Date.from(series.getBar(position.getEntry().getIndex()).getEndTime().toInstant()))
                            .getFirstMillisecond();
            Marker buyMarker = new ValueMarker(buySignalBarTime);
            buyMarker.setPaint(Color.GREEN);
            buyMarker.setLabel("B");
            plot.addDomainMarker(buyMarker);
            // Sell signal
            // 卖出信号
            double sellSignalBarTime = new Minute(
                    Date.from(series.getBar(position.getExit().getIndex()).getEndTime().toInstant()))
                            .getFirstMillisecond();
            Marker sellMarker = new ValueMarker(sellSignalBarTime);
            sellMarker.setPaint(Color.RED);
            sellMarker.setLabel("S");
            plot.addDomainMarker(sellMarker);
        }
    }

    /**
     * Displays a chart in a frame.
     * * 在框架中显示图表。
     *
     * @param chart the chart to be displayed
     *              * @param chart 要显示的图表
     */
    private static void displayChart(JFreeChart chart) {
        // Chart panel
        // 图表面板
        ChartPanel panel = new ChartPanel(chart);
        panel.setFillZoomRectangle(true);
        panel.setMouseWheelEnabled(true);
        panel.setPreferredSize(new Dimension(1024, 400));
        // Application frame
        // 应用框架
        ApplicationFrame frame = new ApplicationFrame("Ta4j example - Buy and sell signals to chart Ta4j 示例 - 图表上的买卖信号");
        frame.setContentPane(panel);
        frame.pack();
        RefineryUtilities.centerFrameOnScreen(frame);
        frame.setVisible(true);
    }

    public static void main(String[] args) {

        // Getting the bar series
        // 获取柱状系列
        BarSeries series = CsvTradesLoader.loadBitstampSeries();
        // Building the trading strategy
        // 构建交易策略
        Strategy strategy = MovingMomentumStrategy.buildStrategy(series);

        /*
         * Building chart datasets
         * 构建图表数据集
         */
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(buildChartTimeSeries(series, new ClosePriceIndicator(series), "Bitstamp Bitcoin (BTC) Bitstamp 比特币 (BTC)"));

        /*
         * Creating the chart
         * 创建图表
         */
        JFreeChart chart = ChartFactory.createTimeSeriesChart("Bitstamp BTC", // title
                "Date", // x-axis label // x轴标签
                "Price", // y-axis label // y轴标签
                dataset, // data // 数据
                true, // create legend?  // 创建图例？
                true, // generate tooltips? // 生成工具提示？
                false // generate URLs?  // 生成网址？
        );
        XYPlot plot = (XYPlot) chart.getPlot();
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setDateFormatOverride(new SimpleDateFormat("MM-dd HH:mm"));

        /*
         * Running the strategy and adding the buy and sell signals to plot
         * * 运行策略并添加买入和卖出信号以进行绘图
         */
        addBuySellSignals(series, strategy, plot);

        /*
         * Displaying the chart
         * * 显示图表
         */
        displayChart(chart);
    }
}
