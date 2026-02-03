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
package org.ta4j.core.indicators.wyckoff;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Provides relative volume measurements to support Wyckoff event detection.
 *
 * @since 0.19
 */
public final class WyckoffVolumeProfile {

    private final SMAIndicator shortSma;
    private final SMAIndicator longSma;
    private final VolumeIndicator volumeIndicator;
    private final Num climaxThreshold;
    private final Num dryUpThreshold;

    /**
     * Creates a profile that compares short and long volume averages.
     *
     * @param series          bar series to analyse
     * @param shortWindow     size of the fast moving average window
     * @param longWindow      size of the slow moving average window
     * @param climaxThreshold ratio above which volume is treated as a climax
     * @param dryUpThreshold  ratio below which volume is treated as drying up
     * @since 0.19
     */
    public WyckoffVolumeProfile(BarSeries series, int shortWindow, int longWindow, Num climaxThreshold,
            Num dryUpThreshold) {
        if (shortWindow < 1) {
            throw new IllegalArgumentException("shortWindow must be greater than 0");
        }
        if (longWindow < shortWindow) {
            throw new IllegalArgumentException("longWindow must be greater than or equal to shortWindow");
        }
        this.volumeIndicator = new VolumeIndicator(series);
        this.shortSma = new SMAIndicator(volumeIndicator, shortWindow);
        this.longSma = new SMAIndicator(volumeIndicator, longWindow);
        this.climaxThreshold = Objects.requireNonNull(climaxThreshold, "climaxThreshold");
        this.dryUpThreshold = Objects.requireNonNull(dryUpThreshold, "dryUpThreshold");
    }

    /**
     * Returns a snapshot of the relative volume state.
     *
     * @param index the bar index
     * @return snapshot capturing the current relative volume conditions
     * @since 0.19
     */
    public VolumeSnapshot snapshot(int index) {
        final Num rawVolume = volumeIndicator.getValue(index);
        if (rawVolume.isNaN()) {
            return VolumeSnapshot.empty();
        }
        final Num shortAverage = shortSma.getValue(index);
        final Num longAverage = longSma.getValue(index);
        if (shortAverage.isNaN() || longAverage.isNaN() || longAverage.isZero()) {
            return new VolumeSnapshot(rawVolume, NaN, false, false);
        }
        final Num ratio = shortAverage.dividedBy(longAverage);
        final boolean climax = !ratio.isNaN() && ratio.isGreaterThan(climaxThreshold);
        final boolean dryUp = !ratio.isNaN() && ratio.isLessThan(dryUpThreshold);
        return new VolumeSnapshot(rawVolume, ratio, climax, dryUp);
    }

    /**
     * Immutable view of the current volume regime.
     *
     * @param volume         raw volume at {@code index}
     * @param relativeVolume ratio of short to long averages
     * @param climax         whether the ratio indicates a volume climax
     * @param dryUp          whether the ratio indicates volume dry-up
     * @since 0.19
     */
    public record VolumeSnapshot(Num volume, Num relativeVolume, boolean climax, boolean dryUp) {

        private static VolumeSnapshot empty() {
            return new VolumeSnapshot(NaN, NaN, false, false);
        }
    }
}
