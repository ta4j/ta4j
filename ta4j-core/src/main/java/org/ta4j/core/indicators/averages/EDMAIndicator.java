/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Exponential Displaced Moving Average (EDMA)
 *
 * Exponential Displaced Moving Average (EDMA) is a variation of the traditional
 * Exponential Moving Average (EMA) where the calculated average is shifted in
 * time by a specified number of periods. This displacement enhances trend
 * visualization and provides traders with an adjusted perspective of price
 * action, either anticipating future price movements or analyzing past trends.
 *
 */
public class EDMAIndicator extends AbstractIndicator<Num> {

    private final int barCount;
    private final int displacement;
    /** List of cached results. */
    private final List<Num> results;
    private final Indicator<Num> indicator;
    private final int unstableBars;

    /**
     * Constructor.
     *
     * @param indicator    an indicator
     * @param barCount     the Exponential Moving Average time frame
     * @param displacement the Exponential Moving Average displacement
     */
    public EDMAIndicator(Indicator<Num> indicator, int barCount, int displacement) {
        super(indicator.getBarSeries());
        BarSeries series = indicator.getBarSeries();
        this.barCount = barCount;
        this.displacement = displacement;
        this.indicator = indicator;
        this.results = new ArrayList<>();

        EMAIndicator ema = new EMAIndicator(indicator, barCount);
        for (int i = 0; i < series.getBarCount(); i++) {
            results.add(ema.getValue(i));
        }
        int emaUnstableBars = indicator.getCountOfUnstableBars() + barCount;
        this.unstableBars = Math.max(0, emaUnstableBars + displacement);
    }

    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        int displacedIndex = index - displacement;
        if (index <= displacement) {
            return results.get(0);
        }
        return results.get(displacedIndex);
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " displacement: " + displacement;
    }

    @Override
    public Num getValue(int index) {
        return calculate(index);
    }

}
