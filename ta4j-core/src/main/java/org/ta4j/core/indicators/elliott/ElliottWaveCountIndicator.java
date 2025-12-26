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

import java.util.List;
import java.util.Objects;

import org.ta4j.core.indicators.CachedIndicator;

/**
 * Counts swings provided by an {@link ElliottSwingIndicator} instance.
 *
 * @since 0.22.0
 */
public class ElliottWaveCountIndicator extends CachedIndicator<Integer> {

    private final ElliottSwingIndicator swingIndicator;
    private final ElliottSwingCompressor compressor;

    /**
     * @param swingIndicator indicator providing swing lists
     * @since 0.22.0
     */
    public ElliottWaveCountIndicator(final ElliottSwingIndicator swingIndicator) {
        this(swingIndicator, null);
    }

    /**
     * @param swingIndicator indicator providing swing lists
     * @param compressor     optional compressor to filter swings before counting
     * @since 0.22.0
     */
    public ElliottWaveCountIndicator(final ElliottSwingIndicator swingIndicator,
            final ElliottSwingCompressor compressor) {
        super(Objects.requireNonNull(swingIndicator, "swingIndicator"));
        this.swingIndicator = swingIndicator;
        this.compressor = compressor;
    }

    @Override
    protected Integer calculate(final int index) {
        return Integer.valueOf(getSwings(index).size());
    }

    @Override
    public int getCountOfUnstableBars() {
        return swingIndicator.getCountOfUnstableBars();
    }

    /**
     * @return underlying swing indicator used for wave counting
     * @since 0.22.0
     */
    public ElliottSwingIndicator getSwingIndicator() {
        return swingIndicator;
    }

    /**
     * @param index bar index
     * @return immutable swing view used for counting
     * @since 0.22.0
     */
    public List<ElliottSwing> getSwings(final int index) {
        final List<ElliottSwing> swings = swingIndicator.getValue(index);
        if (swings.isEmpty() || compressor == null) {
            return swings;
        }
        return compressor.compress(swings);
    }
}
