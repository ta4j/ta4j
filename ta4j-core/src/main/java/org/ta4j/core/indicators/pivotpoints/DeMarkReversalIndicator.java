package org.ta4j.core.indicators.pivotpoints;

import org.ta4j.core.Bar;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

import java.util.List;

import static org.ta4j.core.num.NaN.NaN;

/**
 * DeMark Reversal Indicator.
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points</a>
 */
public class DeMarkReversalIndicator extends RecursiveCachedIndicator<Num> {

    private final DeMarkPivotPointIndicator pivotPointIndicator;
    private final DeMarkPivotLevel level;
    private final Num TWO;

    public enum DeMarkPivotLevel{
        RESISTANCE,
        SUPPORT,
    }

    /**
     * Constructor.
     * <p>
     * Calculates the DeMark reversal for the corresponding pivot level
     * @param pivotPointIndicator the {@link DeMarkPivotPointIndicator} for this reversal
     * @param level the {@link DeMarkPivotLevel} for this reversal (RESISTANT, SUPPORT)
     */
    public DeMarkReversalIndicator(DeMarkPivotPointIndicator pivotPointIndicator, DeMarkPivotLevel level) {
        super(pivotPointIndicator);
        this.pivotPointIndicator = pivotPointIndicator;
        this.level =level;
        TWO = numOf(2);
    }

    @Override
    protected Num calculate(int index) {
        Num x = pivotPointIndicator.getValue(index).multipliedBy(numOf(4));
        Num result;

        if(level == DeMarkPivotLevel.SUPPORT){
            result = calculateSupport(x, index);
        }
        else{
            result = calculateResistance(x, index);
        }

        return result;

    }

    private Num calculateResistance(Num x, int index) {
        List<Integer> barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index);
        if (barsOfPreviousPeriod.isEmpty()){
            return NaN;
        }
        Bar bar = getTimeSeries().getBar(barsOfPreviousPeriod.get(0));
        Num low = bar.getLowPrice();
        for(int i: barsOfPreviousPeriod){
            low = getTimeSeries().getBar(i).getLowPrice().min(low);
        }

        return x.dividedBy(TWO).minus(low);
    }

    private Num calculateSupport(Num x, int index){
       List<Integer> barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index);
       if (barsOfPreviousPeriod.isEmpty()) {
           return NaN;
       }
       Bar bar = getTimeSeries().getBar(barsOfPreviousPeriod.get(0));
       Num high = bar.getHighPrice();
       for(int i: barsOfPreviousPeriod){
           high = getTimeSeries().getBar(i).getHighPrice().max(high);
       }

       return x.dividedBy(TWO).minus(high);
   }
}
