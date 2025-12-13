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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Utility for post-processing swing sequences.
 *
 * @since 0.22.0
 */
public class ElliottSwingCompressor {

    private final Num minimumAmplitude;
    private final int minimumLength;

    /**
     * @param minimumAmplitude swings must reach this absolute price delta to be
     *                         retained
     * @param minimumLength    swings must cover at least this many bars to be
     *                         retained
     * @since 0.22.0
     */
    public ElliottSwingCompressor(final Num minimumAmplitude, final int minimumLength) {
        this.minimumAmplitude = minimumAmplitude;
        if (minimumLength < 0) {
            throw new IllegalArgumentException("minimumLength must be non-negative");
        }
        this.minimumLength = minimumLength;
    }

    /**
     * Filters the supplied swings according to the configured thresholds.
     *
     * @param swings original swing sequence
     * @return immutable filtered view
     * @since 0.22.0
     */
    public List<ElliottSwing> compress(final List<ElliottSwing> swings) {
        Objects.requireNonNull(swings, "swings");
        if (swings.isEmpty()) {
            return List.of();
        }
        final List<ElliottSwing> filtered = new ArrayList<>(swings.size());
        for (final ElliottSwing swing : swings) {
            if (swing == null) {
                continue;
            }
            if (minimumAmplitude != null && swing.amplitude().isLessThan(minimumAmplitude)) {
                continue;
            }
            if (minimumLength > 0 && swing.length() < minimumLength) {
                continue;
            }
            filtered.add(swing);
        }
        if (filtered.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(filtered);
    }
}
