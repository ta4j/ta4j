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
package org.ta4j.core.indicators.volume;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Anchored volume-weighted average price (AVWAP) indicator.
 * <p>
 * The indicator resets its VWAP window whenever an anchor condition evaluates
 * to {@code true}. Anchors typically correspond to notable events such as
 * earnings reports, major swing highs/lows or policy announcements.
 *
 * @since 0.19
 */
public class AnchoredVWAPIndicator extends AbstractVWAPIndicator {

    private final Indicator<Boolean> anchorSignal;
    private final int baseAnchorIndex;
    private final int baseIndex;
    private final List<Integer> anchorIndexCache = new ArrayList<>();

    /**
     * Creates an anchored VWAP using typical price and volume from the provided
     * series and a fixed anchor index.
     *
     * @param series      the bar series
     * @param anchorIndex the inclusive index to start accumulation from
     *
     * @since 0.19
     */
    public AnchoredVWAPIndicator(BarSeries series, int anchorIndex) {
        this(new TypicalPriceIndicator(series), new VolumeIndicator(series), anchorIndex);
    }

    /**
     * Creates an anchored VWAP using custom price and volume indicators and a fixed
     * anchor index.
     *
     * @param priceIndicator  the price indicator
     * @param volumeIndicator the volume indicator
     * @param anchorIndex     the inclusive index to start accumulation from
     *
     * @since 0.19
     */
    public AnchoredVWAPIndicator(Indicator<Num> priceIndicator, Indicator<Num> volumeIndicator, int anchorIndex) {
        this(priceIndicator, volumeIndicator, null, anchorIndex);
    }

    /**
     * Creates an anchored VWAP whose anchor resets whenever the
     * {@code anchorSignal} is {@code true}.
     *
     * @param series       the bar series
     * @param anchorSignal indicator returning {@code true} on anchor bars
     *
     * @since 0.19
     */
    public AnchoredVWAPIndicator(BarSeries series, Indicator<Boolean> anchorSignal) {
        this(new TypicalPriceIndicator(series), new VolumeIndicator(series), anchorSignal, series.getBeginIndex());
    }

    /**
     * Creates an anchored VWAP whose anchor resets whenever the
     * {@code anchorSignal} evaluates to {@code true}. Anchoring begins at
     * {@code defaultAnchorIndex} if no anchor has fired yet.
     *
     * @param priceIndicator     the price indicator
     * @param volumeIndicator    the volume indicator
     * @param anchorSignal       indicator returning {@code true} on anchor bars
     * @param defaultAnchorIndex the default anchor index if the signal has not
     *                           fired yet
     *
     * @since 0.19
     */
    public AnchoredVWAPIndicator(Indicator<Num> priceIndicator, Indicator<Num> volumeIndicator,
            Indicator<Boolean> anchorSignal, int defaultAnchorIndex) {
        super(priceIndicator, volumeIndicator);
        if (anchorSignal != null) {
            IndicatorSeriesUtils.requireSameSeries(priceIndicator, anchorSignal);
        }
        this.anchorSignal = anchorSignal;
        this.baseIndex = getBarSeries().getBeginIndex();
        int clampedDefault = Math.max(defaultAnchorIndex, baseIndex);
        this.baseAnchorIndex = clampedDefault;
        ensureAnchorCache(baseIndex - 1);
    }

    @Override
    protected int resolveWindowStartIndex(int index) {
        ensureAnchorCache(index);
        int offset = index - baseIndex;
        if (offset < 0) {
            return baseIndex;
        }
        return anchorIndexCache.get(offset);
    }

    /**
     * Returns the currently active anchor index for the provided bar.
     *
     * @param index the bar index
     * @return anchor index for the bar
     *
     * @since 0.19
     */
    public int getAnchorIndex(int index) {
        ensureAnchorCache(index);
        int offset = index - baseIndex;
        if (offset < 0) {
            return baseAnchorIndex;
        }
        return anchorIndexCache.get(offset);
    }

    private void ensureAnchorCache(int index) {
        int offset = index - baseIndex;
        if (offset < 0) {
            return;
        }
        int currentSize = anchorIndexCache.size();
        if (offset < currentSize) {
            return;
        }

        int lastAnchor = currentSize == 0 ? baseAnchorIndex : anchorIndexCache.get(currentSize - 1);
        for (int i = currentSize; i <= offset; i++) {
            int seriesIndex = baseIndex + i;
            int anchor = lastAnchor;
            if (anchorSignal != null) {
                Boolean signal = anchorSignal.getValue(seriesIndex);
                if (Boolean.TRUE.equals(signal)) {
                    anchor = seriesIndex;
                }
            }
            anchor = Math.max(anchor, baseAnchorIndex);
            anchorIndexCache.add(anchor);
            lastAnchor = anchor;
        }
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " baseAnchorIndex: " + baseAnchorIndex;
    }
}
