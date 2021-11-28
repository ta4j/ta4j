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
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import ta4jexamples.indicators.numeric.NumericIndicator;
import ta4jexamples.loaders.CsvTradesLoader;

public class KeltnerDemo {

    public static void main(String[] args) {

        BarSeries bs = CsvTradesLoader.loadBitstampSeries();

        KeltnerChannels kc = new KeltnerChannels(bs, 20, 10, 2.0);

        // print the numbers
        for (int i = 0; i < bs.getBarCount(); i++) {
            System.out.print(" KC upper " + kc.upper().getValue(i));
            System.out.print(" KC middle " + kc.middle().getValue(i));
            System.out.print(" KC lower " + kc.lower().getValue(i));
            System.out.println();
        }

        // shortcuts... close price and volume are used a lot (in my examples, at least)
        NumericIndicator price = NumericIndicator.closePrice(bs);
        NumericIndicator volume = NumericIndicator.volume(bs);
        NumericIndicator smaVolume = volume.sma(100);
        NumericIndicator cci = NumericIndicator.of(new CCIIndicator(bs, 10));

        // rules inspired by
        // https://school.stockcharts.com/doku.php?id=technical_indicators:keltner_channels
        // they might not be entirely sensible for actual trading
        // the use of previous values looks strange here
        // this code looks pretty fluent, and that's the real point here

        Rule buy = price.previous(20)
                .isGreaterThan(kc.upper().previous(20))
                .and(volume.isGreaterThan(smaVolume.multipliedBy(1.2)).and(cci.isLessThan(-100)));

        Rule sell = price.previous(20)
                .isLessThan(kc.lower().previous(20))
                .and(volume.isGreaterThan(smaVolume.multipliedBy(1.2)).and(cci.isGreaterThan(100)));

        // try the rules
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
