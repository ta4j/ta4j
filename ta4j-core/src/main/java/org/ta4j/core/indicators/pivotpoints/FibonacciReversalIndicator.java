package org.ta4j.core.indicators.pivotpoints;

import org.ta4j.core.Bar;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

import java.util.List;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Fibonacci Reversal Indicator.
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points</a>
 */
public class FibonacciReversalIndicator extends RecursiveCachedIndicator<Num> {

    private final PivotPointIndicator pivotPointIndicator;
    private final FibReversalTyp fibReversalTyp;
    private final Num fibonacciFactor;

    public enum FibReversalTyp{
        SUPPORT,
        RESISTANCE
    }

    /**
     * Standard Fibonacci factors
     */
    public enum FibonacciFactor {
        Factor1(0.382),
        Factor2(0.618),
        Factor3(1);

        private final double factor;

        FibonacciFactor(double factor) {
            this.factor = factor;
        }

        public double getFactor(){
            return this.factor;
        }

    }


    /**Constructor.
     * <p>
     * Calculates a (fibonacci) reversal
     * @param pivotPointIndicator the {@link PivotPointIndicator} for this reversal
     * @param fibonacciFactor the fibonacci factor for this reversal
     * @param fibReversalTyp the FibonacciReversalIndicator.FibReversalTyp of the reversal (SUPPORT, RESISTANCE)
     */
    public FibonacciReversalIndicator(PivotPointIndicator pivotPointIndicator, double fibonacciFactor, FibReversalTyp fibReversalTyp) {
        super(pivotPointIndicator);
        this.pivotPointIndicator = pivotPointIndicator;
        this.fibonacciFactor = numOf(fibonacciFactor);
        this.fibReversalTyp = fibReversalTyp;
    }

    /**Constructor.
     * <p>
     * Calculates a (fibonacci) reversal
     * @param pivotPointIndicator the {@link PivotPointIndicator} for this reversal
     * @param fibonacciFactor the {@link FibonacciFactor} factor for this reversal
     * @param fibReversalTyp the FibonacciReversalIndicator.FibReversalTyp of the reversal (SUPPORT, RESISTANCE)
     */
    public FibonacciReversalIndicator(PivotPointIndicator pivotPointIndicator, FibonacciFactor fibonacciFactor, FibReversalTyp fibReversalTyp){
        this(pivotPointIndicator,fibonacciFactor.getFactor(),fibReversalTyp);
    }

    @Override
    protected Num calculate(int index) {
        List<Integer> barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index);
        if (barsOfPreviousPeriod.isEmpty())
            return NaN;
        Bar bar = getTimeSeries().getBar(barsOfPreviousPeriod.get(0));
        Num high =  bar.getHighPrice();
        Num low = bar.getLowPrice();
        for(int i: barsOfPreviousPeriod){
            high = (getTimeSeries().getBar(i).getHighPrice()).max(high);
            low = (getTimeSeries().getBar(i).getLowPrice()).min(low);
        }

        if (fibReversalTyp == FibReversalTyp.RESISTANCE) {
            return pivotPointIndicator.getValue(index).plus(fibonacciFactor.multipliedBy(high.minus(low)));
        }
        return pivotPointIndicator.getValue(index).minus(fibonacciFactor.multipliedBy(high.minus(low)));
    }
}
