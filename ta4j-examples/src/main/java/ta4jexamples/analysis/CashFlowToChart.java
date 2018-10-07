package ta4jexamples.analysis;

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
import org.ta4j.core.*;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.strategies.MovingMomentumStrategy;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This class builds a graphical chart showing the cash flow of a strategy.
 */
public class CashFlowToChart {

    /**
     * Builds a JFreeChart time series from a Ta4j time series and an indicator.
     * @param barseries the ta4j time series
     * @param indicator the indicator
     * @param name the name of the chart time series
     * @return the JFreeChart time series
     */
    private static org.jfree.data.time.TimeSeries buildChartTimeSeries(TimeSeries barseries, Indicator<Num> indicator, String name) {
        org.jfree.data.time.TimeSeries chartTimeSeries = new org.jfree.data.time.TimeSeries(name);
        for (int i = 0; i < barseries.getBarCount(); i++) {
            Bar bar = barseries.getBar(i);
            chartTimeSeries.add(new Minute(new Date(bar.getEndTime().toEpochSecond() * 1000)), indicator.getValue(i).doubleValue());
        }
        return chartTimeSeries;
    }

    /**
     * Adds the cash flow axis to the plot.
     * @param plot the plot
     * @param dataset the cash flow dataset
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
     * @param chart the chart to be displayed
     */
    private static void displayChart(JFreeChart chart) {
        // Chart panel
        ChartPanel panel = new ChartPanel(chart);
        panel.setFillZoomRectangle(true);
        panel.setMouseWheelEnabled(true);
        panel.setPreferredSize(new Dimension(1024, 400));
        // Application frame
        ApplicationFrame frame = new ApplicationFrame("Ta4j example - Cash flow to chart");
        frame.setContentPane(panel);
        frame.pack();
        RefineryUtilities.centerFrameOnScreen(frame);
        frame.setVisible(true);
    }

    public static void main(String[] args) {

        // Getting the time series
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();
        // Building the trading strategy
        Strategy strategy = MovingMomentumStrategy.buildStrategy(series);
        // Running the strategy
        TimeSeriesManager seriesManager = new TimeSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);
        // Getting the cash flow of the resulting trades
        CashFlow cashFlow = new CashFlow(series, tradingRecord);

        /*
          Building chart datasets
         */
        TimeSeriesCollection datasetAxis1 = new TimeSeriesCollection();
        datasetAxis1.addSeries(buildChartTimeSeries(series, new ClosePriceIndicator(series), "Bitstamp Bitcoin (BTC)"));
        TimeSeriesCollection datasetAxis2 = new TimeSeriesCollection();
        datasetAxis2.addSeries(buildChartTimeSeries(series, cashFlow, "Cash Flow"));

        /*
          Creating the chart
         */
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                "Bitstamp BTC", // title
                "Date", // x-axis label
                "Price", // y-axis label
                datasetAxis1, // data
                true, // create legend?
                true, // generate tooltips?
                false // generate URLs?
                );
        XYPlot plot = (XYPlot) chart.getPlot();
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setDateFormatOverride(new SimpleDateFormat("MM-dd HH:mm"));

        /*
          Adding the cash flow axis (on the right)
         */
        addCashFlowAxis(plot, datasetAxis2);

        /*
          Displaying the chart
         */
        displayChart(chart);
    }
}
