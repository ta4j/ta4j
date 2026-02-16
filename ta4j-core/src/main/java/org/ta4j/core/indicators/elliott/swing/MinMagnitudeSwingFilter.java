/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.num.Num;

/**
 * Filters out swings whose magnitude is below a percentage of the largest
 * swing.
 *
 * <p>
 * Use this filter to remove micro-swings before counting waves or generating
 * scenarios. It is especially helpful for noisy or high-frequency data.
 *
 * @since 0.22.2
 */
public final class MinMagnitudeSwingFilter implements SwingFilter {

    private final double minRelativeMagnitude;

    /**
     * @param minRelativeMagnitude relative threshold (0.0 - 1.0]
     * @since 0.22.2
     */
    public MinMagnitudeSwingFilter(final double minRelativeMagnitude) {
        if (minRelativeMagnitude <= 0.0 || minRelativeMagnitude > 1.0) {
            throw new IllegalArgumentException("minRelativeMagnitude must be in (0.0, 1.0]");
        }
        this.minRelativeMagnitude = minRelativeMagnitude;
    }

    @Override
    public List<ElliottSwing> filter(final List<ElliottSwing> swings) {
        Objects.requireNonNull(swings, "swings");
        if (swings.isEmpty()) {
            return List.of();
        }
        Num maxAmplitude = null;
        for (final ElliottSwing swing : swings) {
            if (swing == null) {
                continue;
            }
            final Num amplitude = swing.amplitude();
            if (Num.isNaNOrNull(amplitude)) {
                continue;
            }
            if (maxAmplitude == null || amplitude.isGreaterThan(maxAmplitude)) {
                maxAmplitude = amplitude;
            }
        }
        if (maxAmplitude == null) {
            return List.of();
        }
        final Num threshold = maxAmplitude.multipliedBy(maxAmplitude.getNumFactory().numOf(minRelativeMagnitude));
        final List<ElliottSwing> filtered = new ArrayList<>(swings.size());
        for (final ElliottSwing swing : swings) {
            if (swing == null) {
                continue;
            }
            if (Num.isNaNOrNull(swing.amplitude())) {
                continue;
            }
            if (!swing.amplitude().isLessThan(threshold)) {
                filtered.add(swing);
            }
        }
        return List.copyOf(filtered);
    }

    /**
     * @return relative threshold used by this filter
     * @since 0.22.2
     */
    public double getMinRelativeMagnitude() {
        return minRelativeMagnitude;
    }
}
