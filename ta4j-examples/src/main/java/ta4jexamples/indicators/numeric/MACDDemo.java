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
package ta4jexamples.indicators.numeric;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.facades.MACD;
import org.ta4j.core.num.Num;

import ta4jexamples.loaders.CsvTradesLoader;

/**
 * A simple demo of the MACD facade
 * 
 * 
 */
public class MACDDemo {

    public static void main(String[] args) {

        BarSeries bs = CsvTradesLoader.loadBitstampSeries();
        Indicator<Num> close = new ClosePriceIndicator(bs);

        // the MACD indicator has "pretty" toString() now
        System.out.println(new MACDIndicator(close, 12, 26));

        // the MACD facade makes it a little easier to use MACD, signal and histogram
        MACD macd = new MACD(bs, 12, 26);

        Rule macdSignalCross = macd.line().crossedOver(macd.signal(9));
        Rule sameRuleSomewhatSimplified = macd.histogram(9).crossedOver(0);

        // the facade's MACD line actually an instance of BinaryOperation
        // it doesn't pretty print correctly yet
        // it can be made to print like:
        // EMA(12) - EMA(26)

        // the signal is a wrapper around an EMAIndicator
        // it can be made to print like:
        // EMA(MACD(12,26),9) -- not perfect, but...

        // the histogram is actually an instance of BinaryOperation
        // it can be made to print like:
        // MACD(12,26) - EMA(MACD(12,26),9) -- even less perfect, but...

        System.out.println(macd.line());
        System.out.println(macd.signal(9));
        System.out.println(macd.histogram(9));

        // some values... I am careless with the cached signal and histogram here
        for (int i = 0; i < 50; i++) {
            System.out.println(" macd line  " + macd.line().getValue(i) + " signal " + macd.signal(9).getValue(i)
                    + " histogram " + macd.histogram(9).getValue(i));
        }

    }

}
