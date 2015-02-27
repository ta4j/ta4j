/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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

import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.TradingRecord;
import eu.verdelhan.ta4j.analysis.criteria.TotalProfitCriterion;
import ta4jexamples.loaders.CsvTradesLoader;

/**
 * 2-Period RSI Strategy
 * <p>
 * @see http://stockcharts.com/school/doku.php?id=chart_school:trading_strategies:rsi2
 */
public class RSI2Strategy {

    /**
     * @param series a time series
     * @return a 2-period RSI strategy
     */
    public static Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

//        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
//        SMAIndicator shortSma = new SMAIndicator(closePrice, 5);
//        SMAIndicator longSma = new SMAIndicator(closePrice, 200);
//
//        // Exit point.
//        // Exiting long positions on a move above the 5-period SMA and short positions on a move below the 5-day SMA.
//        IndicatorOverIndicatorStrategy priceBelowSma = new IndicatorOverIndicatorStrategy(shortSma, closePrice);
//
//        // Identifying the major trend using a long-term moving average.
//        // The long-term trend is up when a security is above its 200-period SMA and down when a security is below its 200-period SMA.
//        IndicatorOverIndicatorStrategy shortSmaAboveLongSma = new IndicatorOverIndicatorStrategy(shortSma, longSma);
//
//        // Identifying buying or selling opportunities within the bigger trend.
//        // We use a 2-period RSI indicator.
//        RSIIndicator rsi = new RSIIndicator(closePrice, 2);
//        SupportStrategy support5 = new SupportStrategy(rsi, priceBelowSma, 5);
//        ResistanceStrategy resist95 = new ResistanceStrategy(rsi, priceBelowSma, 95);
//        Strategy buyAndSellSignalsStrategy = new CombinedEntryAndExitStrategy(support5, resist95);
//
//        // To Do
//        // Entering on close.
//
//        return shortSmaAboveLongSma.and(buyAndSellSignalsStrategy);
        return new Strategy();
    }

    public static void main(String[] args) {

        // Getting the time series
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();

        // Building the trading strategy
        Strategy strategy = buildStrategy(series);

        // Running the strategy
        TradingRecord tradingRecord = series.run(strategy);
        System.out.println("Number of trades for the strategy: " + tradingRecord.getTradeCount());

        // Analysis
        System.out.println("Total profit for the strategy: " + new TotalProfitCriterion().calculate(series, tradingRecord));
    }

}
