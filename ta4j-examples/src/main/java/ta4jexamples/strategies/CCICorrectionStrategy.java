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
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.criteria.TotalProfitCriterion;
import java.util.List;
import ta4jexamples.loaders.CsvTradesLoader;

/**
 * CCI Correction Strategy
 * <p>
 * @see http://stockcharts.com/school/doku.php?id=chart_school:trading_strategies:cci_correction
 */
public class CCICorrectionStrategy {

    /**
     * @param series a time series
     * @return a CCI correction strategy
     */
    public static Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

//        CCIIndicator longCci = new CCIIndicator(series, 200);
//        CCIIndicator shortCci = new CCIIndicator(series, 5);
//        ConstantIndicator<Decimal> plus100 = new ConstantIndicator<Decimal>(Decimal.HUNDRED);
//        ConstantIndicator<Decimal> minus100 = new ConstantIndicator<Decimal>(Decimal.valueOf(-100));
//
//        // Trend
//        IndicatorOverIndicatorStrategy bullTrend = new IndicatorOverIndicatorStrategy(longCci, plus100);
//        IndicatorOverIndicatorStrategy bearTrend = new IndicatorOverIndicatorStrategy(longCci, minus100);
//        Strategy trend = new CombinedEntryAndExitStrategy(bullTrend, bearTrend);
//
//        // Signals
//        IndicatorOverIndicatorStrategy buySignal = new IndicatorOverIndicatorStrategy(minus100, shortCci);
//        IndicatorOverIndicatorStrategy sellSignal = new IndicatorOverIndicatorStrategy(plus100, shortCci);
//        Strategy signals = new CombinedEntryAndExitStrategy(buySignal, sellSignal);
//
//        return trend.and(signals);
        return new Strategy();
    }

    public static void main(String[] args) {

        // Getting the time series
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();

        // Building the trading strategy
        Strategy strategy = buildStrategy(series);

        // Running the strategy
        List<Trade> trades = series.run(strategy).getTrades();
        System.out.println("Number of trades for the strategy: " + trades.size());

        // Analysis
        System.out.println("Total profit for the strategy: " + new TotalProfitCriterion().calculate(series, trades));
    }
}
