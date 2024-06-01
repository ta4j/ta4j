/*
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
package org.ta4j.core.indicators.helpers;

import java.time.Instant;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SeriesRelatedNumericIndicator;
import org.ta4j.core.num.Num;

/**
 * True range indicator.
 *
 * <pre>
 * TrueRange = MAX(high - low, high - previousClose, previousClose - low)
 * </pre>
 */
public class TRIndicator extends SeriesRelatedNumericIndicator {

    private Bar previousBar;
    private Num value;
    private Instant currentTick = Instant.EPOCH;
    private boolean stable;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    public TRIndicator(final BarSeries series) {
        super(series);
    }

    protected Num calculate() {
        final Bar bar = getBarSeries().getBar();
        final Num high = bar.highPrice();
        final Num low = bar.lowPrice();
        final Num hl = high.minus(low);

        if (this.previousBar == null) {
            this.previousBar = bar;
            return hl.abs();
        }

        this.stable = true;
        final Num previousClose = this.previousBar.closePrice();
        final Num hc = high.minus(previousClose);
        final Num cl = previousClose.minus(low);
        this.previousBar = bar;
        return hl.abs().max(hc.abs()).max(cl.abs());

    }

    @Override
    public Num getValue() {
        return this.value;
    }

    @Override
    public void refresh(final Instant tick) {
        if (tick.isAfter(this.currentTick)) {
            this.value = calculate();
            this.currentTick = tick;
        } else if (tick.isBefore(this.currentTick)) {
            this.previousBar = null;
            this.stable = false;
            this.value = calculate();
            this.currentTick = tick;
        }
    }

    @Override
    public boolean isStable() {
        return this.stable;
    }
}
