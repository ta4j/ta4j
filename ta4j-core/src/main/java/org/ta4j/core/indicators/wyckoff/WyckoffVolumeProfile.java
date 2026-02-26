/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import static org.ta4j.core.indicators.IndicatorUtils.isInvalid;
import static org.ta4j.core.num.NaN.NaN;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Provides relative volume measurements to support Wyckoff event detection.
 *
 * <p>
 * This is a lower-level building block used by {@link WyckoffPhaseIndicator}
 * and the higher-level entry points {@link WyckoffCycleFacade} and
 * {@link WyckoffCycleAnalysis}.
 *
 * @since 0.22.3
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
     * @since 0.22.3
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
        if (isInvalid(this.climaxThreshold) || isInvalid(this.dryUpThreshold)) {
            throw new IllegalArgumentException("Volume thresholds must be valid numbers");
        }
    }

    /**
     * Returns a snapshot of the relative volume state.
     *
     * @param index the bar index
     * @return snapshot capturing the current relative volume conditions
     * @since 0.22.3
     */
    public VolumeSnapshot snapshot(int index) {
        final Num rawVolume = volumeIndicator.getValue(index);
        if (isInvalid(rawVolume)) {
            return VolumeSnapshot.empty();
        }
        final Num shortAverage = shortSma.getValue(index);
        final Num longAverage = longSma.getValue(index);
        if (isInvalid(shortAverage) || isInvalid(longAverage) || longAverage.isZero()) {
            return new VolumeSnapshot(rawVolume, NaN, false, false);
        }
        final Num ratio = shortAverage.dividedBy(longAverage);
        final boolean climax = !isInvalid(ratio) && ratio.isGreaterThan(climaxThreshold);
        final boolean dryUp = !isInvalid(ratio) && ratio.isLessThan(dryUpThreshold);
        return new VolumeSnapshot(rawVolume, ratio, climax, dryUp);
    }

    /**
     * Immutable view of the current volume regime.
     *
     * @param volume         raw volume at {@code index}
     * @param relativeVolume ratio of short to long averages
     * @param climax         whether the ratio indicates a volume climax
     * @param dryUp          whether the ratio indicates volume dry-up
     * @since 0.22.3
     */
    public record VolumeSnapshot(Num volume, Num relativeVolume, boolean climax, boolean dryUp) {

        /**
         * Implements empty.
         */
        private static VolumeSnapshot empty() {
            return new VolumeSnapshot(NaN, NaN, false, false);
        }
    }

}
