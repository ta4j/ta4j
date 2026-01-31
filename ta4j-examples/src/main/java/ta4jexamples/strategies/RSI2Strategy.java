/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.BitStampCsvTradesFileBarSeriesDataSource;

/**
 * 2-Period RSI Strategy
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:trading_strategies:rsi2">
 *      http://stockcharts.com/school/doku.php?id=chart_school:trading_strategies:rsi2</a>
 */
public class RSI2Strategy {

    private static final Logger LOG = LogManager.getLogger(RSI2Strategy.class);

    /**
     * @param series a bar series
     * @return a 2-period RSI strategy
     */
    public static Strategy buildStrategy(BarSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(closePrice, 5);
        SMAIndicator longSma = new SMAIndicator(closePrice, 200);

        // We use a 2-period RSI indicator to identify buying
        // or selling opportunities within the bigger trend.
        RSIIndicator rsi = new RSIIndicator(closePrice, 2);

        // Entry rule
        // The long-term trend is up when a security is above its 200-period SMA.
        Rule entryRule = new OverIndicatorRule(shortSma, longSma) // Trend
                .and(new CrossedDownIndicatorRule(rsi, 5)) // Signal 1
                .and(new OverIndicatorRule(shortSma, closePrice)); // Signal 2

        // Exit rule
        // The long-term trend is down when a security is below its 200-period SMA.
        Rule exitRule = new UnderIndicatorRule(shortSma, longSma) // Trend
                .and(new CrossedUpIndicatorRule(rsi, 95)) // Signal 1
                .and(new UnderIndicatorRule(shortSma, closePrice)); // Signal 2

        String strategyName = "RSI2Strategy";
        return new BaseStrategy(strategyName, entryRule, exitRule);
    }

    public static void main(String[] args) {

        // Getting the bar series
        BarSeries series = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();

        // Building the trading strategy
        Strategy strategy = buildStrategy(series);

        // Running the strategy
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);
        LOG.debug(() -> strategy.toJson());
        LOG.debug("{}'s number of positions: {}", strategy.getName(), tradingRecord.getPositionCount());

        // Analysis
        var grossReturn = new GrossReturnCriterion().calculate(series, tradingRecord);
        LOG.debug("{}'s gross return: {}", strategy.getName(), grossReturn);

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(closePrice, 5);
        SMAIndicator longSma = new SMAIndicator(closePrice, 200);
        RSIIndicator rsiOverlay = new RSIIndicator(closePrice, 2);

        // Charting
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withTradingRecordOverlay(tradingRecord)
                .withIndicatorOverlay(shortSma)
                .withIndicatorOverlay(longSma)
                .withAnalysisCriterionOverlay(new GrossReturnCriterion(), tradingRecord)
                .withSubChart(rsiOverlay)
                .toChart();
        chartWorkflow.displayChart(chart);
        chartWorkflow.saveChartImage(chart, series, "rsi2-strategy", "temp/charts");
    }

}
