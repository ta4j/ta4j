/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.indicators.adx;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * +DM indicator.
 * 
 * <p>
 * Part of the Directional Movement System.
 */
public class PlusDMIndicator extends CachedIndicator<Num> {

    /**
     * Constructor.
     * 
     * @param series the bar series
     */
    public PlusDMIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return zero();
        }
        final Bar prevBar = getBarSeries().getBar(index - 1);
        final Bar currentBar = getBarSeries().getBar(index);

        final Num upMove = currentBar.getHighPrice().minus(prevBar.getHighPrice());
        final Num downMove = prevBar.getLowPrice().minus(currentBar.getLowPrice());
        if (upMove.isGreaterThan(downMove) && upMove.isGreaterThan(zero())) {
            return upMove;
        } else {
            return zero();
        }
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }
}
