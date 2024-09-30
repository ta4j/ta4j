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
package org.ta4j.core.indicators.trend;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.adx.MinusDIIndicator;
import org.ta4j.core.indicators.adx.PlusDIIndicator;

public class UpTrendIndicator extends AbstractIndicator<Boolean> {

    private static final int DEFAULT_UNSTABLE_BARS = 5;

    private final ADXIndicator directionStrengthIndicator;
    private final MinusDIIndicator minusDIIndicator;
    private final PlusDIIndicator plusDIIndicator;
    private final int unstableBars;

    public UpTrendIndicator(final BarSeries series) {
        this(series, DEFAULT_UNSTABLE_BARS);
    }

    public UpTrendIndicator(final BarSeries series, int unstableBars) {
        super(series);
        this.unstableBars = unstableBars;
        this.directionStrengthIndicator = new ADXIndicator(series, unstableBars);
        this.minusDIIndicator = new MinusDIIndicator(series, unstableBars);
        this.plusDIIndicator = new PlusDIIndicator(series, unstableBars);
    }

    @Override
    public Boolean getValue(final int index) {
        // calculate trend excluding this bar
        final var previousIndex = index - 1;
        return this.directionStrengthIndicator.getValue(index).isGreaterThan(getBarSeries().numFactory().numOf(25))
                && this.minusDIIndicator.getValue(previousIndex)
                        .isLessThan(this.plusDIIndicator.getValue(previousIndex));
    }

    @Override
    public int getUnstableBars() {
        return unstableBars;
    }
}
