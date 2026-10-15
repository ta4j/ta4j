/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.adx;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * DX indicator.
 *
 * <p>
 * Part of the Directional Movement System.
 */
public class DXIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final transient PlusDIIndicator plusDIIndicator;
    private final transient MinusDIIndicator minusDIIndicator;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the bar count for {@link #plusDIIndicator} and
     *                 {@link #minusDIIndicator}
     */
    public DXIndicator(BarSeries series, int barCount) {
        PlusDIIndicator plusDI = new PlusDIIndicator(series, barCount);
        MinusDIIndicator minusDI = new MinusDIIndicator(series, barCount);
        super(series, plusDI, minusDI);
        this.barCount = barCount;
        this.plusDIIndicator = plusDI;
        this.minusDIIndicator = minusDI;
    }

    @Override
    protected Num calculate(int index) {
        Num pdiValue = plusDIIndicator.getValue(index);
        Num mdiValue = minusDIIndicator.getValue(index);
        final var sum = pdiValue.plus(mdiValue);
        if (sum.equals(getBarSeries().numFactory().zero())) {
            return getBarSeries().numFactory().zero();
        }
        return pdiValue.minus(mdiValue).abs().dividedBy(sum).multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(plusDIIndicator.getCountOfUnstableBars(), minusDIIndicator.getCountOfUnstableBars());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
