/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;

/**
 * Trend direction indicator that emits {@code +1} for upward moves and
 * {@code -1} for downward moves.
 *
 * <p>
 * Input is an arbitrary basis indicator. On equal basis values, the previous
 * trend sign is carried forward.
 *
 * @since 0.22.2
 */
public class TrendDirectionIndicator extends RecursiveCachedIndicator<Num> {

    @SuppressWarnings("unused")
    private final Indicator<Num> basisIndicator;

    private final Num one;
    private final Num minusOne;
    private final int unstableBars;

    /**
     * Constructor using {@code high + low + close} as the trend basis.
     *
     * @param series the bar series
     * @since 0.22.2
     */
    public TrendDirectionIndicator(final BarSeries series) {
        this(new HighPriceIndicator(series), new LowPriceIndicator(series), new ClosePriceIndicator(series));
    }

    /**
     * Constructor using {@code high + low + close} as the trend basis.
     *
     * @param highPriceIndicator  high-price indicator
     * @param lowPriceIndicator   low-price indicator
     * @param closePriceIndicator close-price indicator
     * @since 0.22.2
     */
    public TrendDirectionIndicator(final Indicator<Num> highPriceIndicator, final Indicator<Num> lowPriceIndicator,
            final Indicator<Num> closePriceIndicator) {
        this(buildTrendBasis(highPriceIndicator, lowPriceIndicator, closePriceIndicator));
    }

    /**
     * Constructor.
     *
     * @param basisIndicator basis indicator used to derive direction
     * @since 0.22.2
     */
    public TrendDirectionIndicator(final Indicator<Num> basisIndicator) {
        super(basisIndicator);
        this.basisIndicator = basisIndicator;
        this.one = getBarSeries().numFactory().one();
        this.minusOne = getBarSeries().numFactory().minusOne();
        this.unstableBars = basisIndicator.getCountOfUnstableBars() + 1;
    }

    @Override
    protected Num calculate(final int index) {
        final int beginIndex = getBarSeries().getBeginIndex();
        if (beginIndex < 0 || index <= beginIndex) {
            return one;
        }

        final Num currentBasis = basisIndicator.getValue(index);
        final Num previousBasis = basisIndicator.getValue(index - 1);
        if (isInvalid(currentBasis) || isInvalid(previousBasis)) {
            return NaN;
        }

        if (currentBasis.isGreaterThan(previousBasis)) {
            return one;
        }
        if (currentBasis.isLessThan(previousBasis)) {
            return minusOne;
        }

        final Num previousTrend = getValue(index - 1);
        return isInvalid(previousTrend) ? NaN : previousTrend;
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    private static boolean isInvalid(final Num value) {
        return Num.isNaNOrNull(value) || Double.isNaN(value.doubleValue());
    }

    private static Indicator<Num> buildTrendBasis(final Indicator<Num> highPriceIndicator,
            final Indicator<Num> lowPriceIndicator, final Indicator<Num> closePriceIndicator) {
        IndicatorUtils.requireSameSeries(highPriceIndicator, lowPriceIndicator, closePriceIndicator);
        return BinaryOperationIndicator.sum(BinaryOperationIndicator.sum(highPriceIndicator, lowPriceIndicator),
                closePriceIndicator);
    }
}
