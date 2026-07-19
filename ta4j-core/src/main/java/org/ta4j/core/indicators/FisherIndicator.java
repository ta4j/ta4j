/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * The Fisher Indicator.
 *
 * <p>
 * <b>API note:</b> Minimal deviations in the last decimal places are possible.
 * During calculations this indicator converts {@link Num} to {@link Double
 * double}.
 * </p>
 *
 * @see <a href=
 *      "http://www.tradingsystemlab.com/files/The%20Fisher%20Transform.pdf">
 *      http://www.tradingsystemlab.com/files/The%20Fisher%20Transform.pdf</a>
 * @see <a href="https://www.investopedia.com/terms/f/fisher-transform.asp">
 *      https://www.investopedia.com/terms/f/fisher-transform.asp</a>
 */
public class FisherIndicator extends RecursiveCachedIndicator<Num> {

    private static final double ZERO_DOT_FIVE = 0.5;
    private static final double VALUE_MAX = 0.999;
    private static final double VALUE_MIN = -0.999;

    private final Indicator<Num> ref;
    private final int barCount;
    private final double alpha;
    private final double beta;
    private final double gamma;
    private final double delta;
    private final double densityFactor;
    private final boolean isPriceIndicator;
    private final transient Indicator<Num> intermediateValue;
    private final transient Num densityFactorNum;
    private final transient Num gammaNum;
    private final transient Num deltaNum;
    private final transient Num one;

    /**
     * Constructor.
     *
     * @param series the series
     */
    public FisherIndicator(BarSeries series) {
        this(new MedianPriceIndicator(series), 10);
    }

    /**
     * Constructor (with alpha 0.33, beta 0.67, gamma 0.5, delta 0.5).
     *
     * @param price    the price indicator (usually {@link MedianPriceIndicator})
     * @param barCount the time frame (usually 10)
     */
    public FisherIndicator(Indicator<Num> price, int barCount) {
        this(price, barCount, 0.33, 0.67, ZERO_DOT_FIVE, ZERO_DOT_FIVE, 1, true);
    }

    /**
     * Constructor (with gamma 0.5, delta 0.5).
     *
     * @param price    the price indicator (usually {@link MedianPriceIndicator})
     * @param barCount the time frame (usually 10)
     * @param alpha    the alpha (usually 0.33 or 0.5)
     * @param beta     the beta (usually 0.67 or 0.5)
     */
    public FisherIndicator(Indicator<Num> price, int barCount, double alpha, double beta) {
        this(price, barCount, alpha, beta, ZERO_DOT_FIVE, ZERO_DOT_FIVE, 1, true);
    }

    /**
     * Constructor.
     *
     * @param price    the price indicator (usually {@link MedianPriceIndicator})
     * @param barCount the time frame (usually 10)
     * @param alpha    the alpha (usually 0.33 or 0.5)
     * @param beta     the beta (usually 0.67 or 0.5)
     * @param gamma    the gamma (usually 0.25 or 0.5)
     * @param delta    the delta (usually 0.5)
     */
    public FisherIndicator(Indicator<Num> price, int barCount, double alpha, double beta, double gamma, double delta) {
        this(price, barCount, alpha, beta, gamma, delta, 1, true);
    }

    /**
     * Constructor (with alpha 0.33, beta 0.67, gamma 0.5, delta 0.5).
     *
     * @param ref              the indicator
     * @param barCount         the time frame (usually 10)
     * @param isPriceIndicator use true, if "ref" is a price indicator
     */
    public FisherIndicator(Indicator<Num> ref, int barCount, boolean isPriceIndicator) {
        this(ref, barCount, 0.33, 0.67, ZERO_DOT_FIVE, ZERO_DOT_FIVE, 1, isPriceIndicator);
    }

    /**
     * Constructor (with alpha 0.33, beta 0.67, gamma 0.5, delta 0.5).
     *
     * @param ref              the indicator
     * @param barCount         the time frame (usually 10)
     * @param densityFactor    the density factor (usually 1.0)
     * @param isPriceIndicator use true, if "ref" is a price indicator
     */
    public FisherIndicator(Indicator<Num> ref, int barCount, double densityFactor, boolean isPriceIndicator) {
        this(ref, barCount, 0.33, 0.67, ZERO_DOT_FIVE, ZERO_DOT_FIVE, densityFactor, isPriceIndicator);
    }

    /**
     * Constructor
     *
     * @param ref              the indicator
     * @param barCount         the time frame (usually 10)
     * @param alpha            the alpha (usually 0.33 or 0.5)
     * @param beta             the beta (usually 0.67 or 0.5)
     * @param gamma            the gamma (usually 0.25 or 0.5)
     * @param delta            the delta (usually 0.5)
     * @param densityFactor    the density factor (usually 1.0)
     * @param isPriceIndicator use true, if "ref" is a price indicator
     */
    public FisherIndicator(Indicator<Num> ref, int barCount, final double alpha, final double beta, final double gamma,
            final double delta, double densityFactor, boolean isPriceIndicator) {
        super(ref);
        this.ref = ref;
        this.barCount = barCount;
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
        this.delta = delta;
        this.densityFactor = densityFactor;
        this.isPriceIndicator = isPriceIndicator;
        final var numFactory = getBarSeries().numFactory();
        this.gammaNum = numFactory.numOf(gamma);
        this.deltaNum = numFactory.numOf(delta);
        this.densityFactorNum = numFactory.numOf(densityFactor);
        this.one = numFactory.one();

        Num alphaNum = numFactory.numOf(alpha);
        Num betaNum = numFactory.numOf(beta);
        final Indicator<Num> periodHigh = new HighestValueIndicator(
                isPriceIndicator ? new HighPriceIndicator(ref.getBarSeries()) : ref, barCount);
        final Indicator<Num> periodLow = new LowestValueIndicator(
                isPriceIndicator ? new LowPriceIndicator(ref.getBarSeries()) : ref, barCount);

        this.intermediateValue = new RecursiveCachedIndicator<Num>(ref) {

            @Override
            protected Num calculate(int index) {
                if (index <= 0) {
                    return numFactory.zero();
                }

                // Value = (alpha * 2 * ((ref - MinL) / (MaxH - MinL) - 0.5) + beta *
                // priorValue) / densityFactor
                Num currentRef = FisherIndicator.this.ref.getValue(index);
                Num minL = periodLow.getValue(index);
                Num maxH = periodHigh.getValue(index);
                Num term1 = currentRef.minus(minL).dividedBy(maxH.minus(minL)).minus(numFactory.numOf(ZERO_DOT_FIVE));
                Num term2 = alphaNum.multipliedBy(numFactory.numOf(2)).multipliedBy(term1);
                Num term3 = term2.plus(betaNum.multipliedBy(getValue(index - 1)));
                return term3.dividedBy(FisherIndicator.this.densityFactorNum);
            }

            @Override
            public int getCountOfUnstableBars() {
                return Math.max(periodHigh.getCountOfUnstableBars(), periodLow.getCountOfUnstableBars());
            }
        };
    }

    @Override
    protected Num calculate(int index) {
        final var numFactory = getBarSeries().numFactory();
        if (index <= 0) {
            return numFactory.zero();
        }

        Num value = intermediateValue.getValue(index);

        if (value.isGreaterThan(numFactory.numOf(VALUE_MAX))) {
            value = numFactory.numOf(VALUE_MAX);
        } else if (value.isLessThan(numFactory.numOf(VALUE_MIN))) {
            value = numFactory.numOf(VALUE_MIN);
        }

        // Fisher = gamma * Log((1 + Value) / (1 - Value)) + delta * priorFisher
        Num term1 = numFactory.numOf((Math.log(one.plus(value).dividedBy(one.minus(value)).doubleValue())));
        Num term2 = getValue(index - 1);
        return gammaNum.multipliedBy(term1).plus(deltaNum.multipliedBy(term2));
    }

    @Override
    public int getCountOfUnstableBars() {
        return intermediateValue.getCountOfUnstableBars();
    }

}
