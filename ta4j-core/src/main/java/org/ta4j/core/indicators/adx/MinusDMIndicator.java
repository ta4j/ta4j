/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

/**
 * -DM indicator.
 *
 * <p>
 * Part of the Directional Movement System.
 */
public class MinusDMIndicator extends AbstractIndicator<Num> {

    private Bar previousBar;
    private Num value;
    private ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
    private boolean stable;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public MinusDMIndicator(final BarSeries series) {
        super(series);
    }

    protected Num calculate() {
        if (this.previousBar == null) {
            this.previousBar = getBarSeries().getBar();
            return getBarSeries().numFactory().zero();
        }

        this.stable = true;
        final Bar prevBar = this.previousBar;
        final Bar currentBar = getBarSeries().getBar();

        final Num upMove = currentBar.getHighPrice().minus(prevBar.getHighPrice());
        final Num downMove = prevBar.getLowPrice().minus(currentBar.getLowPrice());

        this.previousBar = currentBar;
        if (downMove.isGreaterThan(upMove) && downMove.isGreaterThan(getBarSeries().numFactory().zero())) {
            return downMove;
        }

        return getBarSeries().numFactory().zero();
    }

    @Override
    public Num getValue() {
        return this.value;
    }

    @Override
    public void refresh(final ZonedDateTime tick) {
        if (tick.isAfter(this.currentTick)) {
            this.value = calculate();
            this.currentTick = tick;
        } else if (tick.isBefore(this.currentTick)) {
            this.previousBar = null;
            this.stable = false;
            this.value = calculate();
        }
    }

    @Override
    public boolean isStable() {
        return this.stable;
    }
}
