/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.pivotpoints;

import static org.ta4j.core.num.NaN.NaN;

import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Pivot Reversal Indicator.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-overlays/pivot-points">
 *      https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-overlays/pivot-points</a>
 */
public class StandardReversalIndicator extends RecursiveCachedIndicator<Num> {

    private final PivotPointIndicator pivotPointIndicator;
    private final PivotLevel level;
    private final Num two;

    /**
     * Constructor.
     *
     * Calculates the (standard) reversal for the corresponding pivot level
     *
     * @param pivotPointIndicator the {@link PivotPointIndicator} for this reversal
     * @param level               the {@link PivotLevel} for this reversal
     */
    public StandardReversalIndicator(PivotPointIndicator pivotPointIndicator, PivotLevel level) {
        super(pivotPointIndicator.copy());
        this.pivotPointIndicator = pivotPointIndicator.copy();
        this.level = level;
        this.two = pivotPointIndicator.getBarSeries().numFactory().numOf(2);
    }

    @Override
    protected Num calculate(int index) {
        List<Integer> barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index);
        if (barsOfPreviousPeriod.isEmpty()) {
            return NaN;
        }
        switch (level) {
        case RESISTANCE_3:
            return calculateR3(barsOfPreviousPeriod, index);
        case RESISTANCE_2:
            return calculateR2(barsOfPreviousPeriod, index);
        case RESISTANCE_1:
            return calculateR1(barsOfPreviousPeriod, index);
        case SUPPORT_1:
            return calculateS1(barsOfPreviousPeriod, index);
        case SUPPORT_2:
            return calculateS2(barsOfPreviousPeriod, index);
        case SUPPORT_3:
            return calculateS3(barsOfPreviousPeriod, index);
        default:
            return NaN;
        }
    }

    /**
     * Reversal levels reference the same-index pivot value and carry no history
     * dependence of their own, so the recursive default is opted out.
     *
     * @return {@code false}
     */
    @Override
    protected boolean hasRecursiveDependencies() {
        return false;
    }

    /**
     * The underlying pivot look-back spans an unbounded number of bars, so the zero
     * stable count would retain reversal values that still depend on evicted bars:
     * the whole cache is discarded on head advance so that every retained index is
     * recomputed from the bars that remain available. Declaring this through the
     * explicit policy also resets recursive consumers, which cannot continue a
     * chain across the rebaselined values.
     *
     * @return {@code true}
     */
    @Override
    protected boolean requiresFullCacheInvalidationAfterHeadAdvance() {
        return true;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    private Num calculateR3(List<Integer> barsOfPreviousPeriod, int index) {
        BarSeries series = getBarSeries();
        Bar firstBar = series.getBar(barsOfPreviousPeriod.get(0));
        Num low = firstBar.getLowPrice();
        Num high = firstBar.getHighPrice();
        for (int i = 1; i < barsOfPreviousPeriod.size(); i++) {
            Bar iBar = series.getBar(barsOfPreviousPeriod.get(i));
            low = iBar.getLowPrice().min(low);
            high = iBar.getHighPrice().max(high);
        }
        return high.plus(two.multipliedBy((pivotPointIndicator.getValue(index).minus(low))));
    }

    private Num calculateR2(List<Integer> barsOfPreviousPeriod, int index) {
        BarSeries series = getBarSeries();
        Bar firstBar = series.getBar(barsOfPreviousPeriod.get(0));
        Num low = firstBar.getLowPrice();
        Num high = firstBar.getHighPrice();
        for (int i = 1; i < barsOfPreviousPeriod.size(); i++) {
            Bar iBar = series.getBar(barsOfPreviousPeriod.get(i));
            low = iBar.getLowPrice().min(low);
            high = iBar.getHighPrice().max(high);
        }
        return pivotPointIndicator.getValue(index).plus((high.minus(low)));
    }

    private Num calculateR1(List<Integer> barsOfPreviousPeriod, int index) {
        BarSeries series = getBarSeries();
        Num low = series.getBar(barsOfPreviousPeriod.get(0)).getLowPrice();
        for (int i = 1; i < barsOfPreviousPeriod.size(); i++) {
            low = series.getBar(barsOfPreviousPeriod.get(i)).getLowPrice().min(low);
        }
        return two.multipliedBy(pivotPointIndicator.getValue(index)).minus(low);
    }

    private Num calculateS1(List<Integer> barsOfPreviousPeriod, int index) {
        BarSeries series = getBarSeries();
        Num high = series.getBar(barsOfPreviousPeriod.get(0)).getHighPrice();
        for (int i = 1; i < barsOfPreviousPeriod.size(); i++) {
            high = series.getBar(barsOfPreviousPeriod.get(i)).getHighPrice().max(high);
        }
        return two.multipliedBy(pivotPointIndicator.getValue(index)).minus(high);
    }

    private Num calculateS2(List<Integer> barsOfPreviousPeriod, int index) {
        BarSeries series = getBarSeries();
        Bar firstBar = series.getBar(barsOfPreviousPeriod.get(0));
        Num high = firstBar.getHighPrice();
        Num low = firstBar.getLowPrice();
        for (int i = 1; i < barsOfPreviousPeriod.size(); i++) {
            Bar iBar = series.getBar(barsOfPreviousPeriod.get(i));
            high = iBar.getHighPrice().max(high);
            low = iBar.getLowPrice().min(low);
        }
        return pivotPointIndicator.getValue(index).minus((high.minus(low)));
    }

    private Num calculateS3(List<Integer> barsOfPreviousPeriod, int index) {
        BarSeries series = getBarSeries();
        Bar firstBar = series.getBar(barsOfPreviousPeriod.get(0));
        Num high = firstBar.getHighPrice();
        Num low = firstBar.getLowPrice();
        for (int i = 1; i < barsOfPreviousPeriod.size(); i++) {
            Bar iBar = series.getBar(barsOfPreviousPeriod.get(i));
            high = iBar.getHighPrice().max(high);
            low = iBar.getLowPrice().min(low);
        }
        return low.minus(two.multipliedBy((high.minus(pivotPointIndicator.getValue(index)))));
    }
}
