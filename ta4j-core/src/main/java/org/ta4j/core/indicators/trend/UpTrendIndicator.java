/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.trend;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.adx.MinusDIIndicator;
import org.ta4j.core.indicators.adx.PlusDIIndicator;
import org.ta4j.core.num.Num;

public class UpTrendIndicator extends AbstractIndicator<Boolean> {

    private static final int DEFAULT_UNSTABLE_BARS = 5;
    private static final double DEFAULT_STRENGTH_THRESHOLD = 25;

    private final ADXIndicator directionStrengthIndicator;
    private final MinusDIIndicator minusDIIndicator;
    private final PlusDIIndicator plusDIIndicator;
    private final Num strengthThreshold;
    private final int unstableBars;

    public UpTrendIndicator(final BarSeries series) {
        this(series, DEFAULT_UNSTABLE_BARS);
    }

    public UpTrendIndicator(final BarSeries series, int unstableBars) {
        this(series, unstableBars, DEFAULT_STRENGTH_THRESHOLD);
    }

    public UpTrendIndicator(final BarSeries series, int unstableBars, double strengthThreshold) {
        super(series);
        this.unstableBars = unstableBars;
        this.strengthThreshold = getBarSeries().numFactory().numOf(strengthThreshold);
        this.directionStrengthIndicator = new ADXIndicator(series, unstableBars);
        this.minusDIIndicator = new MinusDIIndicator(series, unstableBars);
        this.plusDIIndicator = new PlusDIIndicator(series, unstableBars);
    }

    @Override
    public Boolean getValue(final int index) {
        // calculate trend excluding this bar
        final var previousIndex = index - 1;
        return this.directionStrengthIndicator.getValue(index).isGreaterThan(strengthThreshold)
                && this.minusDIIndicator.getValue(previousIndex)
                        .isLessThan(this.plusDIIndicator.getValue(previousIndex));
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }
}
