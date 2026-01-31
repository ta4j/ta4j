/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.BitStampCsvTradesFileBarSeriesDataSource;

/**
 * Strategies which compares current price to global extrema over a week.
 */
public class GlobalExtremaStrategy {

    private static final Logger LOG = LogManager.getLogger(GlobalExtremaStrategy.class);

    // We assume that there were at least one position every 5 minutes during the
    // whole
    // week
    private static final int NB_BARS_PER_WEEK = 12 * 24 * 7;

    /**
     * @param series the bar series
     * @return the global extrema strategy
     */
    public static Strategy buildStrategy(final BarSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        final var closePrices = new ClosePriceIndicator(series);

        // Getting the high price over the past week
        final var highPrices = new HighPriceIndicator(series);
        final var weekHighPrice = new HighestValueIndicator(highPrices, NB_BARS_PER_WEEK);
        // Getting the low price over the past week
        final var lowPrices = new LowPriceIndicator(series);
        final var weekLowPrice = new LowestValueIndicator(lowPrices, NB_BARS_PER_WEEK);

        // Going long if the close price goes below the low price
        final var downWeek = BinaryOperationIndicator.product(weekLowPrice, 1.004);
        final var buyingRule = new UnderIndicatorRule(closePrices, downWeek);

        // Going short if the close price goes above the high price
        final var upWeek = BinaryOperationIndicator.product(weekHighPrice, 0.996);
        final var sellingRule = new OverIndicatorRule(closePrices, upWeek);

        String strategyName = "GlobalExtremaStrategy";
        return new BaseStrategy(strategyName, buyingRule, sellingRule);
    }

    public static void main(final String[] args) {

        // Getting the bar series
        final var series = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();

        // Building the trading strategy
        final var strategy = buildStrategy(series);

        // Running the strategy
        final var seriesManager = new BarSeriesManager(series);
        final var tradingRecord = seriesManager.run(strategy);
        LOG.debug(() -> strategy.toJson());
        LOG.debug("{}'s number of positions: {}", strategy.getName(), tradingRecord.getPositionCount());

        // Analysis
        final var grossReturn = new GrossReturnCriterion().calculate(series, tradingRecord);
        LOG.debug("{}'s gross return: {}", strategy.getName(), grossReturn);

        final var highPrices = new HighPriceIndicator(series);
        final var weekHighPrice = new HighestValueIndicator(highPrices, NB_BARS_PER_WEEK);
        final var lowPrices = new LowPriceIndicator(series);
        final var weekLowPrice = new LowestValueIndicator(lowPrices, NB_BARS_PER_WEEK);
        final var downWeek = BinaryOperationIndicator.product(weekLowPrice, 1.004);
        final var upWeek = BinaryOperationIndicator.product(weekHighPrice, 0.996);

        // Charting
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withTradingRecordOverlay(tradingRecord)
                .withIndicatorOverlay(weekHighPrice)
                .withIndicatorOverlay(weekLowPrice)
                .withIndicatorOverlay(downWeek)
                .withIndicatorOverlay(upWeek)
                .withAnalysisCriterionOverlay(new NetProfitCriterion(), tradingRecord)
                .toChart();
        chartWorkflow.displayChart(chart);
        chartWorkflow.saveChartImage(chart, series, "global-extrema-strategy", "temp/charts");
    }
}
