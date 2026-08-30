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
 * DeMark Reversal Indicator.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-overlays/pivot-points">
 *      https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-overlays/pivot-points</a>
 */
public class DeMarkReversalIndicator extends RecursiveCachedIndicator<Num> {

    private final DeMarkPivotPointIndicator pivotPointIndicator;
    private final DeMarkPivotLevel level;

    public enum DeMarkPivotLevel {
        RESISTANCE, SUPPORT,
    }

    /**
     * Constructor.
     *
     * Calculates the DeMark reversal for the corresponding pivot level.
     *
     * @param pivotPointIndicator the {@link DeMarkPivotPointIndicator} for this
     *                            reversal
     * @param level               the {@link DeMarkPivotLevel} for this reversal
     *                            (RESISTANT, SUPPORT)
     */
    public DeMarkReversalIndicator(DeMarkPivotPointIndicator pivotPointIndicator, DeMarkPivotLevel level) {
        super(pivotPointIndicator.copy());
        this.pivotPointIndicator = pivotPointIndicator.copy();
        this.level = level;
    }

    @Override
    protected Num calculate(int index) {
        Num x = pivotPointIndicator.getValue(index).multipliedBy(getBarSeries().numFactory().numOf(4));
        Num result;

        if (level == DeMarkPivotLevel.SUPPORT) {
            result = calculateSupport(x, index);
        } else {
            result = calculateResistance(x, index);
        }

        return result;
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
     * recomputed from the bars that remain available.
     *
     * @param firstRetainedIndex the first series index that remains available
     * @return {@link Integer#MAX_VALUE}, evicting every cached entry
     */
    @Override
    protected int minimumCacheableIndexAfterHeadAdvance(int firstRetainedIndex) {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    private Num calculateResistance(Num x, int index) {
        List<Integer> barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index);
        if (barsOfPreviousPeriod.isEmpty()) {
            return NaN;
        }
        Bar bar = getBarSeries().getBar(barsOfPreviousPeriod.get(0));
        Num low = bar.getLowPrice();
        for (int i : barsOfPreviousPeriod) {
            low = getBarSeries().getBar(i).getLowPrice().min(low);
        }

        return x.dividedBy(getBarSeries().numFactory().two()).minus(low);
    }

    private Num calculateSupport(Num x, int index) {
        List<Integer> barsOfPreviousPeriod = pivotPointIndicator.getBarsOfPreviousPeriod(index);
        if (barsOfPreviousPeriod.isEmpty()) {
            return NaN;
        }
        Bar bar = getBarSeries().getBar(barsOfPreviousPeriod.get(0));
        Num high = bar.getHighPrice();
        for (int i : barsOfPreviousPeriod) {
            high = getBarSeries().getBar(i).getHighPrice().max(high);
        }

        return x.dividedBy(getBarSeries().numFactory().two()).minus(high);
    }
}
