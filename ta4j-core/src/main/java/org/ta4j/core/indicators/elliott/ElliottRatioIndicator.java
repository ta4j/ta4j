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

import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.elliott.ElliottRatio.RatioType;
import org.ta4j.core.num.Num;

/**
 * Calculates Fibonacci-style ratios between consecutive Elliott swings.
 *
 * <p>
 * Ratios are computed as the absolute amplitude of the latest swing divided by
 * the amplitude of the previous swing. If both swings share the same direction
 * the ratio is treated as an extension; otherwise it is treated as a
 * retracement.
 *
 * @since 0.22.0
 */
public class ElliottRatioIndicator extends CachedIndicator<ElliottRatio> {

    private final ElliottSwingIndicator swingIndicator;

    /**
     * Creates a ratio indicator backed by the provided swing detector.
     *
     * @param swingIndicator source of alternating swings
     * @since 0.22.0
     */
    public ElliottRatioIndicator(final ElliottSwingIndicator swingIndicator) {
        super(requireSeries(swingIndicator));
        this.swingIndicator = Objects.requireNonNull(swingIndicator, "swingIndicator");
    }

    private static BarSeries requireSeries(final ElliottSwingIndicator swingIndicator) {
        final BarSeries series = Objects.requireNonNull(swingIndicator, "swingIndicator").getBarSeries();
        if (series == null) {
            throw new IllegalArgumentException("Swing indicator must expose a backing series");
        }
        return series;
    }

    @Override
    protected ElliottRatio calculate(final int index) {
        final List<ElliottSwing> swings = swingIndicator.getValue(index);
        if (swings.size() < 2) {
            return new ElliottRatio(NaN, RatioType.NONE);
        }

        final ElliottSwing latest = swings.get(swings.size() - 1);
        final ElliottSwing previous = swings.get(swings.size() - 2);
        final Num previousAmplitude = previous.amplitude();
        final Num latestAmplitude = latest.amplitude();

        if (previousAmplitude == null || latestAmplitude == null) {
            return new ElliottRatio(NaN, RatioType.NONE);
        }
        if (previousAmplitude.isNaN() || latestAmplitude.isNaN()) {
            return new ElliottRatio(NaN, RatioType.NONE);
        }
        if (previousAmplitude.isZero()) {
            return new ElliottRatio(NaN, RatioType.NONE);
        }

        final Num ratio = latestAmplitude.dividedBy(previousAmplitude);
        final RatioType type = latest.isRising() == previous.isRising() ? RatioType.EXTENSION : RatioType.RETRACEMENT;
        return new ElliottRatio(ratio, type);
    }

    @Override
    public int getCountOfUnstableBars() {
        return swingIndicator.getCountOfUnstableBars();
    }

    /**
     * Convenience helper to check whether the latest ratio sits within the provided
     * tolerance of a target level.
     *
     * @param index     current bar index
     * @param target    target Fibonacci level
     * @param tolerance symmetric absolute tolerance around the level
     * @return {@code true} when the absolute ratio difference is smaller than or
     *         equal to {@code tolerance}
     * @since 0.22.0
     */
    public boolean isNearLevel(final int index, final Num target, final Num tolerance) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(tolerance, "tolerance");
        final ElliottRatio ratio = getValue(index);
        if (ratio.type() == RatioType.NONE) {
            return false;
        }
        final Num value = ratio.value();
        if (value == null || value.isNaN()) {
            return false;
        }
        final Num delta = value.minus(target).abs();
        return !delta.isGreaterThan(tolerance);
    }

}
