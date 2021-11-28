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

import ta4jexamples.indicators.numeric.NumericIndicator;
import ta4jexamples.loaders.CsvTradesLoader;

public class DMSDemo {

    public static void main(String[] args) {

        BarSeries bs = CsvTradesLoader.loadBitstampSeries();

        KeltnerChannels kc = new KeltnerChannels(bs, 20, 10, 2.0);

        DirectionalMovementSystem dms = new DirectionalMovementSystem(bs, 14, 14);

        // print the numbers
        for (int i = 0; i < bs.getBarCount(); i++) {
            System.out.print(" ADX " + dms.adx().getValue(i));
            System.out.print(" PlusDI " + dms.plusDI().getValue(i));
            System.out.print(" MinusDI " + dms.minusDI().getValue(i));
            System.out.println();
        }

    }
}
