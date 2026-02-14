/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Daily measurement indicator ({@code high - low}).
 *
 * <p>
 * This lightweight indicator is useful as an atomic building block for range
 * based volume formulas such as the Klinger family of indicators.
 *
 * @since 0.22.2
 */
public class DailyMeasurementIndicator extends CachedIndicator<Num> {

    @SuppressWarnings("unused")
    private final Indicator<Num> highPriceIndicator;

    @SuppressWarnings("unused")
    private final Indicator<Num> lowPriceIndicator;

    /**
     * Constructor.
     *
     * @param series the bar series
     * @since 0.22.2
     */
    public DailyMeasurementIndicator(final BarSeries series) {
        this(new HighPriceIndicator(series), new LowPriceIndicator(series));
    }

    /**
     * Constructor.
     *
     * @param highPriceIndicator high-price indicator
     * @param lowPriceIndicator  low-price indicator
     * @since 0.22.2
     */
    public DailyMeasurementIndicator(final Indicator<Num> highPriceIndicator, final Indicator<Num> lowPriceIndicator) {
        super(IndicatorUtils.requireSameSeries(highPriceIndicator, lowPriceIndicator));
        this.highPriceIndicator = highPriceIndicator;
        this.lowPriceIndicator = lowPriceIndicator;
    }

    @Override
    protected Num calculate(final int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        final Num high = highPriceIndicator.getValue(index);
        final Num low = lowPriceIndicator.getValue(index);
        if (Num.isNaNOrNull(high) || Num.isNaNOrNull(low)) {
            return NaN;
        }
        return high.minus(low);
    }

    /**
     * Returns the first stable index for daily measurement values.
     *
     * @return unstable bar count
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(highPriceIndicator.getCountOfUnstableBars(), lowPriceIndicator.getCountOfUnstableBars());
    }
}
