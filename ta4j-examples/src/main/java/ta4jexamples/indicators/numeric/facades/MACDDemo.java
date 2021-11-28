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
import org.ta4j.core.Rule;

import ta4jexamples.indicators.numeric.NumericIndicator;
import ta4jexamples.loaders.CsvTradesLoader;

public class MACDDemo {

    public static void main(String[] args) {

        BarSeries bs = CsvTradesLoader.loadBitstampSeries();

        NumericIndicator price = NumericIndicator.closePrice(bs);
        NumericIndicator sma200 = price.sma(200);

        MACD macd = new MACD(bs, 12, 26);
        NumericIndicator signal = macd.signal(9);

        // inspired by
        // https://school.stockcharts.com/doku.php?id=technical_indicators:moving_average_convergence_divergence_macd

        Rule buy = price.isGreaterThan(sma200).and(macd.line().crossedOver(signal)).and(macd.line().isLessThan(0));

        Rule sell = price.isLessThan(sma200).and(macd.line().crossedUnder(signal)).and(macd.line().isGreaterThan(0));

        int buyCount = 0;
        int sellCount = 0;
        for (int i = 0; i < bs.getBarCount(); i++) {

            if (buy.isSatisfied(i)) {
                System.out.println("Buy at index: " + i);
                buyCount++;
            }

            if (sell.isSatisfied(i)) {
                System.out.println("Sell at index: " + i);
                sellCount++;
            }
        }

        System.out.println(" barCount " + bs.getBarCount());
        System.out.println(" buyCount " + buyCount);
        System.out.println(" sellCount " + sellCount);

    }
}
