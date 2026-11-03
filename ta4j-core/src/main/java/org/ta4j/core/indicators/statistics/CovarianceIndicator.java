/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

/**
 * Covariance indicator.
 */
public class CovarianceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator1;
    private final Indicator<Num> indicator2;
    private final int barCount;
    private final transient SMAIndicator sma1;
    private final transient SMAIndicator sma2;
    private final transient RunningTotalIndicator productTotal;

    /**
     * Constructor for indicators backed by a shared bar series.
     *
     * @param series     the bar series underlying both indicators
     * @param indicator1 the first indicator
     * @param indicator2 the second indicator
     * @param barCount   the time frame
     * @since 0.24.3
     */
    public CovarianceIndicator(BarSeries series, Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        super(series, indicator1, indicator2);
        this.indicator1 = indicator1;
        this.indicator2 = indicator2;
        this.barCount = barCount;
        this.sma1 = new SMAIndicator(indicator1, barCount);
        this.sma2 = new SMAIndicator(indicator2, barCount);
        this.productTotal = new RunningTotalIndicator(new CachedIndicator<Num>(series, indicator1, indicator2) {
            @Override
            protected Num calculate(int index) {
                return indicator1.getValue(index).multipliedBy(indicator2.getValue(index));
            }

            @Override
            public int getCountOfUnstableBars() {
                return Math.max(indicator1.getCountOfUnstableBars(), indicator2.getCountOfUnstableBars());
            }
        }, barCount);
    }

    /**
     * Constructor. The underlying series is the first indicator's series.
     *
     * @param indicator1 the first indicator
     * @param indicator2 the second indicator
     * @param barCount   the time frame
     */
    public CovarianceIndicator(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        this(indicator1.getBarSeries(), indicator1, indicator2, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(Math.max(0, getBarSeries().getBeginIndex()), index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        Num covariance = getBarSeries().numFactory().zero();
        Num average1 = sma1.getValue(index);
        Num average2 = sma2.getValue(index);
        if (barCount > 1 && numberOfObservations > 0
                && IndicatorUtils.isSameSeries(indicator1.getBarSeries(), getBarSeries())
                && IndicatorUtils.isSameSeries(indicator2.getBarSeries(), getBarSeries())
                && (!(covariance instanceof DecimalNum decimal) || decimal.getMathContext().getPrecision() >= 16)) {
            try {
                Num meanProduct = productTotal.getValue(index)
                        .dividedBy(getBarSeries().numFactory().numOf(numberOfObservations));
                Num productMeans = average1.multipliedBy(average2);
                Num result = meanProduct.minus(productMeans);
                Num scale = meanProduct.abs().max(productMeans.abs());
                if (Num.isFinite(result) && Num.isFinite(scale)
                        && result.abs().isGreaterThan(scale.dividedBy(getBarSeries().numFactory().numOf(100000000)))) {
                    return result;
                }
            } catch (ArithmeticException ignored) {
                // Preserve the centered calculation when raw products overflow.
            }
        }
        for (int i = startIndex; i <= index; i++) {
            Num mul = indicator1.getValue(i).minus(average1).multipliedBy(indicator2.getValue(i).minus(average2));
            covariance = covariance.plus(mul);
        }
        covariance = covariance.dividedBy(getBarSeries().numFactory().numOf(numberOfObservations));
        return covariance;
    }

    @Override
    public int getCountOfUnstableBars() {
        int baseUnstableBars = Math.max(indicator1.getCountOfUnstableBars(), indicator2.getCountOfUnstableBars());
        return baseUnstableBars + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
