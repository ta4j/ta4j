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
package ta4jexamples.strategies;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperation;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import ta4jexamples.loaders.CsvTradesLoader;

/**
 * Strategies which compares current price to global extrema over a week.
 */
public class GlobalExtremaStrategy {

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
        final var downWeek = BinaryOperation.product(weekLowPrice, 1.004);
        final var buyingRule = new UnderIndicatorRule(closePrices, downWeek);

        // Going short if the close price goes above the high price
        final var upWeek = BinaryOperation.product(weekHighPrice, 0.996);
        final var sellingRule = new OverIndicatorRule(closePrices, upWeek);

        return new BaseStrategy(buyingRule, sellingRule);
    }

    public static void main(final String[] args) {

        // Getting the bar series
        final var series = CsvTradesLoader.loadBitstampSeries();

        // Building the trading strategy
        final var strategy = buildStrategy(series);

        // Running the strategy
        final var seriesManager = new BarSeriesManager(series);
        final var tradingRecord = seriesManager.run(strategy);
        System.out.println("Number of positions for the strategy: " + tradingRecord.getPositionCount());

        // Analysis
        final var grossReturn = new GrossReturnCriterion().calculate(series, tradingRecord);
        System.out.println("Gross return for the strategy: " + grossReturn);
    }
}
