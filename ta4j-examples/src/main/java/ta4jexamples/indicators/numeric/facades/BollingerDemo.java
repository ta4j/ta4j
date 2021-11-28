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
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;

import ta4jexamples.indicators.numeric.NumericIndicator;
import ta4jexamples.loaders.CsvTradesLoader;

public class BollingerDemo {

    public static void main(String[] args) {

        BarSeries bs = CsvTradesLoader.loadBitstampSeries();
        BollingerBands bb = new BollingerBands(bs, 20, 2.0);

        NumericIndicator price = NumericIndicator.of(new ClosePriceIndicator(bs));
        NumericIndicator volume = NumericIndicator.of(new VolumeIndicator(bs));
        NumericIndicator sma20 = price.sma(20);
        NumericIndicator sma60 = price.sma(60);
        NumericIndicator smaVolume = volume.sma(200);

        // rules inspired by
        // https://school.stockcharts.com/doku.php?id=technical_indicators:bollinger_bands#bullish_bollinger_band_crossover
        // these might not be very sensible rules; this is just an example to show the
        // fluent methods

        Rule buy = price.crossedOver(bb.upper())
                .and(volume.isGreaterThan(smaVolume.multipliedBy(1.2)).and(sma20.isGreaterThan(sma60)));

        Rule sell = price.crossedUnder(bb.lower())
                .and(volume.isGreaterThan(smaVolume.multipliedBy(1.2)).or(sma20.crossedUnder(sma60)));

        // print a few numbers just to show all the various bb components
        for (int i = 0; i < bs.getBarCount(); i++) {
            System.out.print(" BB upper " + bb.upper().getValue(i));
            System.out.print(" BB middle " + bb.middle().getValue(i));
            System.out.print(" BB lower " + bb.lower().getValue(i));
            System.out.print(" BB bandwidth " + bb.bandwidth().getValue(i));
            System.out.print(" %B " + bb.percentB().getValue(i));
            System.out.println();
        }

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
