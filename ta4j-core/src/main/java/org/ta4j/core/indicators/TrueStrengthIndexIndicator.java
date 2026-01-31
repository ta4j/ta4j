/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.numeric.UnaryOperationIndicator;
import org.ta4j.core.num.Num;

/**
 * True strength index (TSI) indicator.
 *
 * <p>
 * This oscillator double smooths price changes to quantify trend direction and
 * momentum.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/true-strength-index">
 *      StockCharts: True Strength Index (TSI)</a>
 * @since 0.20
 */
public class TrueStrengthIndexIndicator extends CachedIndicator<Num> {

    @SuppressWarnings("unused")
    private final Indicator<Num> priceIndicator;
    private final transient EMAIndicator doubleSmoothedChange;
    private final transient EMAIndicator doubleSmoothedAbsoluteChange;
    private final int firstSmoothingPeriod;
    private final int secondSmoothingPeriod;

    /**
     * Constructor using the common (25, 13) smoothing periods.
     *
     * @param indicator the {@link Indicator} containing the price series
     * @since 0.20
     */
    public TrueStrengthIndexIndicator(Indicator<Num> indicator) {
        this(indicator, 25, 13);
    }

    /**
     * Constructor.
     *
     * @param indicator             the {@link Indicator} containing the price
     *                              series
     * @param firstSmoothingPeriod  the long look-back period (commonly 25)
     * @param secondSmoothingPeriod the short look-back period (commonly 13)
     * @since 0.20
     */
    public TrueStrengthIndexIndicator(Indicator<Num> indicator, int firstSmoothingPeriod, int secondSmoothingPeriod) {
        super(indicator);
        if (firstSmoothingPeriod < 1) {
            throw new IllegalArgumentException("First smoothing period must be a positive integer");
        }
        if (secondSmoothingPeriod < 1) {
            throw new IllegalArgumentException("Second smoothing period must be a positive integer");
        }
        this.firstSmoothingPeriod = firstSmoothingPeriod;
        this.secondSmoothingPeriod = secondSmoothingPeriod;
        this.priceIndicator = indicator;

        DifferenceIndicator priceChangeIndicator = new DifferenceIndicator(this.priceIndicator);
        EMAIndicator firstSmoothingChange = new EMAIndicator(priceChangeIndicator, firstSmoothingPeriod);
        this.doubleSmoothedChange = new EMAIndicator(firstSmoothingChange, secondSmoothingPeriod);

        UnaryOperationIndicator absolutePriceChange = UnaryOperationIndicator.abs(priceChangeIndicator);
        EMAIndicator firstSmoothingAbsoluteChange = new EMAIndicator(absolutePriceChange, firstSmoothingPeriod);
        this.doubleSmoothedAbsoluteChange = new EMAIndicator(firstSmoothingAbsoluteChange, secondSmoothingPeriod);
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        Num numerator = doubleSmoothedChange.getValue(index);
        Num denominator = doubleSmoothedAbsoluteChange.getValue(index);
        if (Num.isNaNOrNull(numerator) || Num.isNaNOrNull(denominator) || denominator.isZero()) {
            return NaN;
        }
        return numerator.dividedBy(denominator).multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        // Unstable period is the sum of both smoothing periods
        return firstSmoothingPeriod + secondSmoothingPeriod;
    }
}
