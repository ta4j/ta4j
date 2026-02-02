/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.*;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.adx.MinusDIIndicator;
import org.ta4j.core.indicators.adx.PlusDIIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.BitStampCsvTradesFileBarSeriesDataSource;

/**
 * ADX indicator based strategy
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:average_directional_index_adx">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:average_directional_index_adx</a>
 */
public class ADXStrategy {

    private static final Logger LOG = LogManager.getLogger(ADXStrategy.class);

    /**
     * @param series a bar series
     * @return an adx indicator based strategy
     */
    public static Strategy buildStrategy(BarSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        final ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        final SMAIndicator smaIndicator = new SMAIndicator(closePriceIndicator, 50);

        final int adxBarCount = 14;
        final ADXIndicator adxIndicator = new ADXIndicator(series, adxBarCount);
        final OverIndicatorRule adxOver20Rule = new OverIndicatorRule(adxIndicator, 20);

        final PlusDIIndicator plusDIIndicator = new PlusDIIndicator(series, adxBarCount);
        final MinusDIIndicator minusDIIndicator = new MinusDIIndicator(series, adxBarCount);

        final Rule plusDICrossedUpMinusDI = new CrossedUpIndicatorRule(plusDIIndicator, minusDIIndicator);
        final Rule plusDICrossedDownMinusDI = new CrossedDownIndicatorRule(plusDIIndicator, minusDIIndicator);
        final OverIndicatorRule closePriceOverSma = new OverIndicatorRule(closePriceIndicator, smaIndicator);
        final Rule entryRule = adxOver20Rule.and(plusDICrossedUpMinusDI).and(closePriceOverSma);

        final UnderIndicatorRule closePriceUnderSma = new UnderIndicatorRule(closePriceIndicator, smaIndicator);
        final Rule exitRule = adxOver20Rule.and(plusDICrossedDownMinusDI).and(closePriceUnderSma);

        return new BaseStrategy("ADXStrategy", entryRule, exitRule, adxBarCount);
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

        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        SMAIndicator smaIndicator = new SMAIndicator(closePriceIndicator, 50);
        int adxBarCount = 14;
        ADXIndicator adxIndicator = new ADXIndicator(series, adxBarCount);
        PlusDIIndicator plusDIIndicator = new PlusDIIndicator(series, adxBarCount);
        MinusDIIndicator minusDIIndicator = new MinusDIIndicator(series, adxBarCount);

        // Charting
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withTradingRecordOverlay(tradingRecord)
                .withIndicatorOverlay(smaIndicator)
                .withSubChart(adxIndicator)
                .withIndicatorOverlay(plusDIIndicator)
                .withIndicatorOverlay(minusDIIndicator)
                .withSubChart(new GrossReturnCriterion(), tradingRecord)
                .toChart();
        chartWorkflow.displayChart(chart);
        chartWorkflow.saveChartImage(chart, series, "adx-strategy", "temp/charts");
    }
}
