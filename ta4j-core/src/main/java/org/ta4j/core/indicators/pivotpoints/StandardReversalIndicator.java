package org.ta4j.core.indicators.pivotpoints;

import org.ta4j.core.Bar;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

import java.util.List;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Pivot Reversal Indicator.
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points</a>
 */
public class StandardReversalIndicator extends RecursiveCachedIndicator<Num> {

    private final PivotPointIndicator pivotPointIndicator;
    private final PivotLevel level;

    /**
     * Constructor.
     * <p>
     * Calculates the (standard) reversal for the corresponding pivot level
     * @param pivotPointIndicator the {@link PivotPointIndicator} for this reversal
     * @param level the {@link PivotLevel} for this reversal
     */
    public StandardReversalIndicator(PivotPointIndicator pivotPointIndicator, PivotLevel level) {
        super(pivotPointIndicator);
        this.pivotPointIndicator = pivotPointIndicator;
        this.level =level;
    }

    @Override
    protected Num calculate(int index) {
        List<Integer> barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index);
        if (barsOfPreviousPeriod.isEmpty()) {
            return NaN;
        }
        switch (level){
            case RESISTANCE_3:
                return calculateR3(barsOfPreviousPeriod, index);
            case RESISTANCE_2:
                return calculateR2(barsOfPreviousPeriod, index);
            case RESISTANCE_1:
                return calculateR1(barsOfPreviousPeriod, index);
            case SUPPORT_1:
                return  calculateS1(barsOfPreviousPeriod, index);
            case SUPPORT_2:
                return calculateS2(barsOfPreviousPeriod, index);
            case SUPPORT_3:
                return calculateS3(barsOfPreviousPeriod, index);
            default:
                return NaN;
        }

    }

    private Num calculateR3(List<Integer> barsOfPreviousPeriod, int index){
        Bar bar = getTimeSeries().getBar(barsOfPreviousPeriod.get(0));
        Num low = bar.getLowPrice();
        Num high =  bar.getHighPrice();
        for(int i: barsOfPreviousPeriod){
            low = (getTimeSeries().getBar(i).getLowPrice()).min(low);
            high = (getTimeSeries().getBar(i).getHighPrice()).max(high);
        }
        return high.plus(numOf(2).multipliedBy((pivotPointIndicator.getValue(index).minus(low))));
    }

    private Num calculateR2(List<Integer> barsOfPreviousPeriod, int index){
        Bar bar = getTimeSeries().getBar(barsOfPreviousPeriod.get(0));
        Num low = bar.getLowPrice();
        Num high = bar.getHighPrice();
        for(int i: barsOfPreviousPeriod){
            low = (getTimeSeries().getBar(i).getLowPrice()).min(low);
            high = (getTimeSeries().getBar(i).getHighPrice()).max(high);
        }
        return pivotPointIndicator.getValue(index).plus((high.minus(low)));
    }

    private Num calculateR1(List<Integer> barsOfPreviousPeriod, int index){
        Num low = getTimeSeries().getBar(barsOfPreviousPeriod.get(0)).getLowPrice();
        for(int i: barsOfPreviousPeriod){
            low = (getTimeSeries().getBar(i).getLowPrice()).min(low);
        }
        return numOf(2).multipliedBy(pivotPointIndicator.getValue(index)).minus(low);
    }

    private Num calculateS1(List<Integer> barsOfPreviousPeriod, int index){
        Num high =  getTimeSeries().getBar(barsOfPreviousPeriod.get(0)).getHighPrice();
        for(int i: barsOfPreviousPeriod){
            high = (getTimeSeries().getBar(i).getHighPrice()).max(high);
        }
        return numOf(2).multipliedBy(pivotPointIndicator.getValue(index)).minus(high);
    }

    private Num calculateS2(List<Integer> barsOfPreviousPeriod, int index){
        Bar bar = getTimeSeries().getBar(barsOfPreviousPeriod.get(0));
        Num high =  bar.getHighPrice();
        Num low = bar.getLowPrice();
        for(int i: barsOfPreviousPeriod){
            high = (getTimeSeries().getBar(i).getHighPrice()).max(high);
            low = (getTimeSeries().getBar(i).getLowPrice()).min(low);
        }
        return pivotPointIndicator.getValue(index).minus((high.minus(low)));
    }

    private Num calculateS3(List<Integer> barsOfPreviousPeriod, int index){
        Bar bar = getTimeSeries().getBar(barsOfPreviousPeriod.get(0));
        Num high =  bar.getHighPrice();
        Num low = bar.getLowPrice();
        for(int i: barsOfPreviousPeriod){
            high = (getTimeSeries().getBar(i).getHighPrice()).max(high);
            low = (getTimeSeries().getBar(i).getLowPrice()).min(low);
        }
        return low.minus(numOf(2).multipliedBy((high.minus(pivotPointIndicator.getValue(index)))));
    }
}
