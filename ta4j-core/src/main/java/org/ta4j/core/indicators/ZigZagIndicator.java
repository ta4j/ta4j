package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * See <a href="https://www.investopedia.com/terms/z/zig_zag_indicator.asp">Zig Zag Indicator</a>
 */
public class ZigZagIndicator extends CachedIndicator<Num> {

    /**
     * The threshold ratio (positive number).
     */
    private final Num thresholdRatio;

    /**
     * The indicator to provide values.
     * <p>It can be {@link org.ta4j.core.indicators.helpers.ClosePriceIndicator} or something similar.
     */
    private final Indicator<Num> indicator;

    private Num lastExtreme;

    public ZigZagIndicator(Indicator<Num> indicator, Num thresholdRatio) {
        super(indicator);
        this.indicator = indicator;
        validateThreshold(thresholdRatio);
        this.thresholdRatio = thresholdRatio;
    }

    private void validateThreshold(Num threshold) {
        if (threshold == null) {
            throw new IllegalArgumentException("Threshold ratio is mandatory");
        }
        if (threshold.isNegativeOrZero()) {
            throw new IllegalArgumentException("Threshold ratio value must be positive");
        }
    }

    /**
     * @param index the bar index
     * @return the indicator's value if it changes over the threshold, otherwise {@link NaN}
     */
    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            lastExtreme = indicator.getValue(0);
            return lastExtreme;
        } else {
            if (lastExtreme.isZero()) {
                // Treat this case separately
                // because one cannot divide by zero
                return lastExtreme;
            } else {
                Num indicatorValue = indicator.getValue(index);
                Num differenceRatio = indicatorValue.minus(lastExtreme).dividedBy(lastExtreme);

                if (differenceRatio.abs().isGreaterThanOrEqual(thresholdRatio)) {
                    lastExtreme = indicatorValue;
                    return lastExtreme;
                } else {
                    return NaN.NaN;
                }
            }
        }
    }
}
