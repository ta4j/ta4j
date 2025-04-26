/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * The Kaufman's Adaptive Moving Average (KAMA) Indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:kaufman_s_adaptive_moving_average">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:kaufman_s_adaptive_moving_average</a>
 */
public class KAMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> price;
    private final int barCountEffectiveRatio;
    private final Num fastest;
    private final Num slowest;

    /**
     * Constructor.
     *
     * @param price                  the price
     * @param barCountEffectiveRatio the time frame of the effective ratio (usually
     *                               10)
     * @param barCountFast           the time frame fast (usually 2)
     * @param barCountSlow           the time frame slow (usually 30)
     */
    public KAMAIndicator(Indicator<Num> price, int barCountEffectiveRatio, int barCountFast, int barCountSlow) {
        super(price);
        this.price = price;
        this.barCountEffectiveRatio = barCountEffectiveRatio;
        final var numFactory = getBarSeries().numFactory();
        final var two = numFactory.two();
        this.fastest = two.dividedBy(numFactory.numOf(barCountFast + 1));
        this.slowest = two.dividedBy(numFactory.numOf(barCountSlow + 1));
    }

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code barCountEffectiveRatio} = 10
     * <li>{@code barCountFast} = 2
     * <li>{@code barCountSlow} = 30
     * </ul>
     *
     * @param price the priceindicator
     */
    public KAMAIndicator(Indicator<Num> price) {
        this(price, 10, 2, 30);
    }

    @Override
    protected Num calculate(int index) {
        final int start = barCountEffectiveRatio;

        // 1) Warm-up: before we have enough bars, just return the price
        if (index < start) {
            return price.getValue(index);
        }

        // 2) Compute the exact KAMA at bar =start using the original formula
        Num kama = calculateInitialKAMA(start);

        // 3) If the user asked exactly for start, we’re done
        if (index == start) {
            return kama;
        }

        // 4) Build initial volatility over [1 … start]
        Num volatility = getBarSeries().numFactory().zero();
        for (int i = 1; i <= start; i++) {
            volatility = volatility.plus(price.getValue(i).minus(price.getValue(i - 1)).abs());
        }

        // 5) Slide forward from start+1 to the requested index
        for (int i = start + 1; i <= index; i++) {

            // a) remove the oldest bar’s diff…
            int oldest = i - start;
            volatility = volatility.minus(price.getValue(oldest).minus(price.getValue(oldest - 1)).abs());
            // …and add the newest bar’s diff
            volatility = volatility.plus(price.getValue(i).minus(price.getValue(i - 1)).abs());

            // b) Efficiency Ratio on the window of length `start`
            Num change = price.getValue(i).minus(price.getValue(i - start)).abs();
            Num er = change.dividedBy(volatility);

            // c) Smoothing Constant = [ER × (fastest – slowest) + slowest]²
            Num sc = er.multipliedBy(fastest.minus(slowest)).plus(slowest).pow(2);

            // d) One‐step KAMA update
            kama = kama.plus(sc.multipliedBy(price.getValue(i).minus(kama)));
        }

        return kama;
    }

    /**
     * Reproduces exactly what the old recursive calculate(index) did for a single
     * bar—so we can seed our iterative loop.
     */
    private Num calculateInitialKAMA(int index) {
        Num currentPrice = price.getValue(index);

        // volatility over the last `barCountEffectiveRatio` bars
        int startChangeIndex = index - barCountEffectiveRatio;
        Num change = currentPrice.minus(price.getValue(startChangeIndex)).abs();

        Num volatility = getBarSeries().numFactory().zero();
        for (int i = startChangeIndex; i < index; i++) {
            volatility = volatility.plus(price.getValue(i + 1).minus(price.getValue(i)).abs());
        }

        Num er = change.dividedBy(volatility);
        Num sc = er.multipliedBy(fastest.minus(slowest)).plus(slowest).pow(2);

        // note: for index==start, priorKAMA was price.getValue(start-1)
        Num priorKama = price.getValue(index - 1);
        return priorKama.plus(sc.multipliedBy(currentPrice.minus(priorKama)));
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
