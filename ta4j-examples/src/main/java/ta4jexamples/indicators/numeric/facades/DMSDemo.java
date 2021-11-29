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
package ta4jexamples.indicators.numeric.facades;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;

import ta4jexamples.indicators.numeric.NumericIndicator;
import ta4jexamples.loaders.CsvTradesLoader;

public class DMSDemo {

    public static void main(String[] args) {

        BarSeries bs = CsvTradesLoader.loadBitstampSeries();

        // inspired by the ADXStrategy example
        DirectionalMovementSystem dms = new DirectionalMovementSystem(bs, 14, 14);
        NumericIndicator close = NumericIndicator.closePrice(bs);
        NumericIndicator sma50 = close.sma(50);
        
        Rule entryRule = dms.adx().isGreaterThan(20)
        		.and(dms.plusDI().crossedOver(dms.minusDI()))
        		.and(close.isGreaterThan(sma50));

        Rule exitRule = dms.adx().isGreaterThan(20)
    			.and(dms.plusDI().crossedUnder(dms.minusDI()))
    			.and(close.isLessThan(sma50));
        
        //with pretty indicator and rule toString() (issue #813) this would print like
        // ADX(14,14) > 20 AND PlusDI(14) crossedOver MinusDI(14) AND Close > SMA(Close,50)
        // ADX(14,14) > 20 AND PlusDI(14) crossedUnder MinusDI(14) AND Close < SMA(Close,50)

        Strategy strategy = new BaseStrategy(entryRule, exitRule); 
        
        BarSeriesManager seriesManager = new BarSeriesManager(bs);
        TradingRecord tradingRecord = seriesManager.run(strategy);
        System.out.println("Number of positions for the strategy: " + tradingRecord.getPositionCount());

        // Analysis
        System.out.println(
                "Total return for the strategy: " + new GrossReturnCriterion().calculate(bs, tradingRecord));


    }
}
