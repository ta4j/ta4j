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

import ta4jexamples.loaders.CsvTradesLoader;

public class AroonDemo {

    public static void main(String[] args) {

        BarSeries bs = CsvTradesLoader.loadBitstampSeries();

        Aroon aroon = new Aroon(bs, 25);

        // inspired by
        // https://school.stockcharts.com/doku.php?id=technical_indicators:aroon

        Rule previousAroonConsolidation = aroon.up()
                .previous(5)
                .isLessThan(20)
                .and(aroon.down().previous(5).isLessThan(20));

        Rule aroonUpBreak = aroon.up()
                .isGreaterThan(50)
                .and(aroon.down().isLessThan(50))
                .and(previousAroonConsolidation);

        Rule aroonDownBreak = aroon.down()
                .isGreaterThan(50)
                .and(aroon.up().isLessThan(50))
                .and(previousAroonConsolidation);

        int buyCount = 0;
        int sellCount = 0;
        for (int i = 0; i < bs.getBarCount(); i++) {

            if (aroonUpBreak.isSatisfied(i)) {
                System.out.println("Aroon up breaks at index: " + i);
                buyCount++;
            }

            if (aroonDownBreak.isSatisfied(i)) {
                System.out.println("Aroon down breaks at index: " + i);
                sellCount++;
            }
        }

        System.out.println(" barCount " + bs.getBarCount());
        System.out.println(" buyCount " + buyCount);
        System.out.println(" sellCount " + sellCount);

    }

}
