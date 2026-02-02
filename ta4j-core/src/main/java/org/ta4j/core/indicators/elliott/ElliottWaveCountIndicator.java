/*
 * SPDX-License-Identifier: MIT
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
