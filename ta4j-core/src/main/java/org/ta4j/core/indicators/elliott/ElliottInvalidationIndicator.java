/*
 * SPDX-License-Identifier: MIT
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
 * <p>
 * Use this indicator when you only need a boolean invalidation signal. If you
 * need the exact price level, prefer {@link ElliottInvalidationLevelIndicator}.
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
