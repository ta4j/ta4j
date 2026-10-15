/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Wilder's moving average indicator.
 *
 * The Wilder’s Moving Average indicator (Wilder’s Smoothed Moving Average ) was
 * developed by Welles Wilder and introduced in his 1978 book, “New Concepts in
 * Technical Trading Systems.”
 *
 * The WMA inherently "remembers" past values due to its recursive formula, but
 * the initialization WMA can significantly bias early values. This bias
 * diminishes over time as more data contributes to the calculation. On a
 * bounded series, retained values survive head advances and, once the window
 * holds more than {@code barCount} bars, each appended bar continues the cached
 * recursion. Initialization is counted from where the recursion was seeded:
 * only when no cached predecessor exists (for example a fresh indicator) does
 * it restart at the retained head, using its first retained value as the
 * missing-history proxy. See the test cases for this indicator as it skips the
 * initial data until the calculation stabilizes.
 *
 * @see <a href=
 *      "https://www.tradingview.com/script/wXtQeoOg/#:~:text=Wilder%20did%20not%20use%20the,where%20K%20%3D1%2FN.">
 *      https://www.tradingview.com/script/wXtQeoOg/#:~:text=Wilder%20did%20not%20use%20the,where%20K%20%3D1%2FN.</a>
 */
public class WildersMAIndicator extends RecursiveCachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final transient RunningTotalIndicator sumPriceIndicator;
    /**
     * First index of the live recursion: the retained head at which it was last
     * seeded because no cached predecessor existed. Initialization is counted from
     * here, so a recursion continued across head advances is not re-initialized.
     */
    private volatile transient int chainStartIndex = getBarSeries().getBeginIndex();

    /**
     * Constructor.
     *
     * @param indicator an indicator
     * @param barCount  the Wilder Moving Average time frame
     */
    public WildersMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        this.sumPriceIndicator = new RunningTotalIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final int beginIndex = getBarSeries().getBeginIndex();
        if (beginIndex < 0) {
            // Empty series: there is no bar to seed from or recurse to.
            return NaN.NaN;
        }
        if (index < beginIndex) {
            // A removed index mapped here by CachedIndicator: serve the first
            // available bar's value (the retained recursion, or its seed) without
            // re-seeding the live chain. calculate(beginIndex) reads no predecessor.
            return getValue(beginIndex);
        }
        if (index == beginIndex) {
            // Seed at the first retained bar. On a bounded series retained values
            // survive head advances, so this is only reached when no cached
            // predecessor exists (for example a fresh indicator).
            chainStartIndex = beginIndex;
            return indicator.getValue(index);
        }
        NumFactory numFactory = getBarSeries().numFactory();
        Num one = numFactory.one();

        Num numBars = numFactory.numOf(barCount);
        Num k = one.dividedBy(numBars);

        // Resolve the predecessor first: when it is not cached the recursion walks
        // back to the retained head and re-seeds there, updating the chain start.
        Num previousWildersAverage = getValue(index - 1);
        final int chainStart = chainStartIndex;
        final long chainIndex = (long) index - chainStart;
        if (chainIndex < barCount && chainStart >= beginIndex) {
            // Simulate extended historical data for initialization, counted from
            // where the chain was seeded. Use the seed value as a proxy for the
            // "missing" earlier data.
            Num simulatedValue = indicator.getValue(chainStart);

            // Initialize with a simulated Simple Moving Average (SMA)
            Num sum = sumPriceIndicator.getValue(index);

            // Return average of available points for initialization
            Num simulatedSum = sum.plus(simulatedValue.multipliedBy(numFactory.numOf(barCount - chainIndex - 1)));
            return simulatedSum.dividedBy(numBars);
        }

        // Past initialization, or an initialization band whose seed bars have left
        // the window: continue the recursion from the cached predecessor.
        Num currentPrice = indicator.getValue(index);
        return currentPrice.multipliedBy(k).plus(previousWildersAverage.multipliedBy(one.minus(k)));
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + (barCount * 2);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}
