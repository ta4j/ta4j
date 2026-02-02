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
        super(pivotPointIndicator);
        this.pivotPointIndicator = pivotPointIndicator;
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
