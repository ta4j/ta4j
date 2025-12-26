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
 * Flags when the current Elliott wave count breaks canonical invalidation
 * levels.
 *
 * <p>
 * Typical invalidations include wave two exceeding the start of wave one or
 * wave four overlapping wave one territory. Downstream consumers can reset
 * their state when this indicator returns {@code true}.
 *
 * @since 0.22.0
 */
public class ElliottInvalidationIndicator extends CachedIndicator<Boolean> {

    private final ElliottPhaseIndicator phaseIndicator;

    /**
     * @param phaseIndicator phase indicator providing the current wave state
     * @since 0.22.0
     */
    public ElliottInvalidationIndicator(final ElliottPhaseIndicator phaseIndicator) {
        super(Objects.requireNonNull(phaseIndicator, "phaseIndicator"));
        this.phaseIndicator = phaseIndicator;
    }

    @Override
    protected Boolean calculate(final int index) {
        final ElliottSwingMetadata metadata = phaseIndicator.metadata(index);
        if (!metadata.isValid()) {
            return Boolean.FALSE;
        }

        final ElliottPhaseIndicator.ImpulseAssessment impulse = phaseIndicator.assessImpulse(metadata);
        final List<ElliottSwing> swings = impulse.segment();
        if (swings.isEmpty()) {
            return Boolean.FALSE;
        }

        boolean invalid = false;
        if (swings.size() >= 2) {
            invalid = !phaseIndicator.isWaveTwoValid(swings.get(0), swings.get(1), impulse.rising());
        }
        if (!invalid && swings.size() >= 3) {
            invalid = !phaseIndicator.isWaveThreeValid(swings.get(0), swings.get(2), impulse.rising());
        }
        if (!invalid && swings.size() >= 4) {
            invalid = !phaseIndicator.isWaveFourValid(swings.get(0), swings.get(2), swings.get(3), impulse.rising());
        }
        if (!invalid && swings.size() >= 5) {
            invalid = !phaseIndicator.isWaveFiveValid(swings.get(0), swings.get(2), swings.get(4), impulse.rising());
        }
        return Boolean.valueOf(invalid);
    }

    @Override
    public int getCountOfUnstableBars() {
        return phaseIndicator.getCountOfUnstableBars();
    }
}
