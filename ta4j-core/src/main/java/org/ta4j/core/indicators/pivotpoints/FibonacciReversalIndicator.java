/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.pivotpoints;

import static org.ta4j.core.num.NaN.NaN;

import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Fibonacci Reversal Indicator.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-overlays/pivot-points">
 *      https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-overlays/pivot-points</a>
 */
public class FibonacciReversalIndicator extends RecursiveCachedIndicator<Num> {

    private final PivotPointIndicator pivotPointIndicator;
    private final FibReversalTyp fibReversalTyp;
    private final Num fibonacciFactor;

    public enum FibReversalTyp {
        SUPPORT, RESISTANCE
    }

    /**
     * Standard Fibonacci factors.
     */
    public enum FibonacciFactor {
        FACTOR_1(0.382), FACTOR_2(0.618), FACTOR_3(1);

        private final double factor;

        FibonacciFactor(double factor) {
            this.factor = factor;
        }

        public double getFactor() {
            return this.factor;
        }

    }

    /**
     * Constructor.
     *
     * Calculates a (fibonacci) reversal
     *
     * @param pivotPointIndicator the {@link PivotPointIndicator} for this reversal
     * @param fibonacciFactor     the fibonacci factor for this reversal
     * @param fibReversalTyp      the FibonacciReversalIndicator.FibReversalTyp of
     *                            the reversal (SUPPORT, RESISTANCE)
     */
    public FibonacciReversalIndicator(PivotPointIndicator pivotPointIndicator, double fibonacciFactor,
            FibReversalTyp fibReversalTyp) {
        super(pivotPointIndicator);
        this.pivotPointIndicator = pivotPointIndicator;
        this.fibonacciFactor = getBarSeries().numFactory().numOf(fibonacciFactor);
        this.fibReversalTyp = fibReversalTyp;
    }

    /**
     * Constructor.
     *
     * Calculates a (fibonacci) reversal
     *
     * @param pivotPointIndicator the {@link PivotPointIndicator} for this reversal
     * @param fibonacciFactor     the {@link FibonacciFactor} factor for this
     *                            reversal
     * @param fibReversalTyp      the FibonacciReversalIndicator.FibReversalTyp of
     *                            the reversal (SUPPORT, RESISTANCE)
     */
    public FibonacciReversalIndicator(PivotPointIndicator pivotPointIndicator, FibonacciFactor fibonacciFactor,
            FibReversalTyp fibReversalTyp) {
        this(pivotPointIndicator, fibonacciFactor.getFactor(), fibReversalTyp);
    }

    @Override
    protected Num calculate(int index) {
        List<Integer> barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index);
        if (barsOfPreviousPeriod.isEmpty())
            return NaN;
        Bar bar = getBarSeries().getBar(barsOfPreviousPeriod.get(0));
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();
        for (int i : barsOfPreviousPeriod) {
            Bar iBar = getBarSeries().getBar(i);
            high = iBar.getHighPrice().max(high);
            low = iBar.getLowPrice().min(low);
        }

        Num pivotPointValue = pivotPointIndicator.getValue(index);
        Num fibValue = fibonacciFactor.multipliedBy(high.minus(low));

        return fibReversalTyp == FibReversalTyp.RESISTANCE ? pivotPointValue.plus(fibValue)
                : pivotPointValue.minus(fibValue);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
