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

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.Indicator;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.strategies.MovingMomentumStrategy;

/**
 * This class builds a graphical chart showing the cash flow of a strategy.
 * * 本课程构建了一个显示策略现金流的图形图表。
 */
public class CashFlowToChart {

    /**
     * Builds a JFreeChart time series from a Ta4j bar series and an indicator.
     * * 从 Ta4j 条形序列和指标构建 JFreeChart 时间序列。
     *
     * @param barSeries the ta4j bar series
     *                  ta4j 酒吧系列
     * @param indicator the indicator
     *                  指标
     * @param name      the name of the chart time series
     *                  图表时间序列的名称
     * @return the JFreeChart time series
     * * @return JFreeChart 时间序列
     */
    private static org.jfree.data.time.TimeSeries buildChartBarSeries(BarSeries barSeries, Indicator<Num> indicator,
            String name) {
        org.jfree.data.time.TimeSeries chartBarSeries = new org.jfree.data.time.TimeSeries(name);
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            Bar bar = barSeries.getBar(i);
            chartBarSeries.add(new Minute(new Date(bar.getEndTime().toEpochSecond() * 1000)),
                    indicator.getValue(i).doubleValue());
        }
        return chartBarSeries;
    }

    /**
     * Adds the cash flow axis to the plot.
     * * 将现金流轴添加到绘图中。
     *
     * @param plot    the plot
     *                剧情
     * @param dataset the cash flow dataset
     *                现金流数据集
     */
    private static void addCashFlowAxis(XYPlot plot, TimeSeriesCollection dataset) {
        final NumberAxis cashAxis = new NumberAxis("Cash Flow Ratio");
        cashAxis.setAutoRangeIncludesZero(false);
        plot.setRangeAxis(1, cashAxis);
        plot.setDataset(1, dataset);
        plot.mapDatasetToRangeAxis(1, 1);
        final StandardXYItemRenderer cashFlowRenderer = new StandardXYItemRenderer();
        cashFlowRenderer.setSeriesPaint(0, Color.blue);
        plot.setRenderer(1, cashFlowRenderer);
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
        ApplicationFrame frame = new ApplicationFrame("Ta4j example - Cash flow to chart Ta4j 示例 - 现金流量图表");
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
        // Running the strategy
        // 运行策略
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);
        // Getting the cash flow of the resulting positions
        // 获取结果头寸的现金流
        CashFlow cashFlow = new CashFlow(series, tradingRecord);

        /*
         * Building chart datasets
         * * 构建图表数据集
         */
        TimeSeriesCollection datasetAxis1 = new TimeSeriesCollection();
        datasetAxis1.addSeries(buildChartBarSeries(series, new ClosePriceIndicator(series), "Bitstamp Bitcoin (BTC)"));
        TimeSeriesCollection datasetAxis2 = new TimeSeriesCollection();
        datasetAxis2.addSeries(buildChartBarSeries(series, cashFlow, "Cash Flow"));

        /*
         * Creating the chart
         * * 创建图表
         */
        JFreeChart chart = ChartFactory.createTimeSeriesChart("Bitstamp BTC", // title // 标题
                "Date", // x-axis label // x轴标签
                "Price", // y-axis label // y轴标签
                datasetAxis1, // data    // 数据
                true, // create legend?  // 创建图例？
                true, // generate tooltips? // 生成工具提示？
                false // generate URLs?  // 生成网址？
        );
        XYPlot plot = (XYPlot) chart.getPlot();
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setDateFormatOverride(new SimpleDateFormat("MM-dd HH:mm"));

        /*
         * Adding the cash flow axis (on the right)
         * * 添加现金流轴（右侧）
         */
        addCashFlowAxis(plot, datasetAxis2);

        /*
         * Displaying the chart
         * * 显示图表
         */
        displayChart(chart);
    }
}
