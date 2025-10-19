/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.indicators.elliott;

import static org.ta4j.core.num.NaN.NaN;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Projects an Elliott price channel based on the most recent swing highs and
 * lows.
 *
 * <p>
 * The indicator mirrors the rolling-window design of
 * {@link org.ta4j.core.indicators.pivotpoints.PivotPointIndicator} by
 * recalculating the projected channel on each bar using the latest two swing
 * highs and lows.
 *
 * @since 0.19
 */
public class ElliottChannelIndicator extends CachedIndicator<ElliottChannel> {

    private final ElliottSwingIndicator swingIndicator;
    private final NumFactory numFactory;

    /**
     * Builds a channel indicator from the provided swing source.
     *
     * @param swingIndicator source of alternating swings
     * @since 0.19
     */
    public ElliottChannelIndicator(final ElliottSwingIndicator swingIndicator) {
        super(requireSeries(swingIndicator));
        this.swingIndicator = Objects.requireNonNull(swingIndicator, "swingIndicator");
        this.numFactory = getBarSeries().numFactory();
    }

    private static BarSeries requireSeries(final ElliottSwingIndicator swingIndicator) {
        final BarSeries series = Objects.requireNonNull(swingIndicator, "swingIndicator").getBarSeries();
        if (series == null) {
            throw new IllegalArgumentException("Swing indicator must expose a backing series");
        }
        return series;
    }

    @Override
    protected ElliottChannel calculate(final int index) {
        final List<ElliottSwing> swings = swingIndicator.getValue(index);
        if (swings.size() < 4) {
            return new ElliottChannel(NaN, NaN, NaN);
        }

        final List<ElliottSwing> rising = latestSwingsByDirection(swings, true);
        final List<ElliottSwing> falling = latestSwingsByDirection(swings, false);
        if (rising.size() < 2 || falling.size() < 2) {
            return new ElliottChannel(NaN, NaN, NaN);
        }

        final PivotLine upperLine = projectLine(rising.get(rising.size() - 2), rising.get(rising.size() - 1), index);
        final PivotLine lowerLine = projectLine(falling.get(falling.size() - 2), falling.get(falling.size() - 1),
                index);
        if (!upperLine.isValid() || !lowerLine.isValid()) {
            return new ElliottChannel(NaN, NaN, NaN);
        }

        final Num median = upperLine.value.plus(lowerLine.value).dividedBy(numFactory.two());
        return new ElliottChannel(upperLine.value, lowerLine.value, median);
    }

    private List<ElliottSwing> latestSwingsByDirection(final List<ElliottSwing> swings, final boolean rising) {
        final List<ElliottSwing> filtered = new ArrayList<>(2);
        for (int i = swings.size() - 1; i >= 0 && filtered.size() < 2; i--) {
            final ElliottSwing swing = swings.get(i);
            if (swing.isRising() == rising) {
                filtered.add(swing);
            }
        }
        Collections.reverse(filtered);
        return filtered;
    }

    private PivotLine projectLine(final ElliottSwing older, final ElliottSwing newer, final int index) {
        if (older == null || newer == null) {
            return PivotLine.invalid();
        }
        final int span = newer.toIndex() - older.toIndex();
        if (span == 0) {
            return PivotLine.invalid();
        }
        final Num spanNum = numFactory.numOf(span);
        if (spanNum.isZero()) {
            return PivotLine.invalid();
        }
        final Num slope = newer.toPrice().minus(older.toPrice()).dividedBy(spanNum);
        final int distance = index - newer.toIndex();
        final Num projected = newer.toPrice().plus(slope.multipliedBy(numFactory.numOf(distance)));
        if (projected.isNaN()) {
            return PivotLine.invalid();
        }
        return new PivotLine(projected);
    }

    @Override
    public int getCountOfUnstableBars() {
        return swingIndicator.getCountOfUnstableBars();
    }

    private record PivotLine(Num value) {

        private static PivotLine invalid() {
            return new PivotLine(NaN);
        }

        private boolean isValid() {
            return value != null && !value.isNaN();
        }
    }
}
