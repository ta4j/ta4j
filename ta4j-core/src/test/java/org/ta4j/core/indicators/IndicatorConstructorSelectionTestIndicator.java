/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Test-only indicator used to verify that serialization prefers constructors
 * that consume all indicator components when multiple overloads exist.
 */
public class IndicatorConstructorSelectionTestIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> first;
    private final Indicator<Num> second;
    private final Num scale;
    private final Num offset;
    private final Num bias;

    public IndicatorConstructorSelectionTestIndicator(Indicator<Num> first, Indicator<Num> second, Num scale,
            Num offset, Num bias) {
        super(first);
        this.first = first;
        this.second = second;
        this.scale = scale;
        this.offset = offset;
        this.bias = bias;
    }

    public IndicatorConstructorSelectionTestIndicator(Indicator<Num> first, Num scale, Num offset, Num bias, Num drift,
            Num pad) {
        super(first);
        this.first = first;
        this.second = new ClosePriceIndicator(first.getBarSeries());
        this.scale = scale;
        this.offset = offset;
        this.bias = bias.plus(drift).plus(pad);
    }

    @Override
    protected Num calculate(int index) {
        return first.getValue(index).plus(second.getValue(index)).multipliedBy(scale).plus(offset).plus(bias);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
