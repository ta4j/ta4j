/*
 * SPDX-License-Identifier: MIT
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
 * The indicator classifies the latest swing as either a retracement or an
 * extension:
 * <ul>
 * <li><b>Retracement</b>: amplitude of the latest swing divided by the
 * amplitude of the most recent prior swing with the opposite direction.</li>
 * <li><b>Extension</b>: amplitude of the latest swing divided by the amplitude
 * of the most recent prior swing with the same direction, but only when the
 * latest swing makes a new extreme in that direction (higher high / lower
 * low).</li>
 * </ul>
 *
 * <p>
 * Use this indicator when you need a lightweight, per-bar Fibonacci ratio
 * signal for validation, confluence checks, or chart annotation. For
 * multi-scenario ratio scoring, use {@link ElliottScenarioGenerator} or
 * {@link ElliottFibonacciValidator}.
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
        final Num latestAmplitude = latest.amplitude();
        if (!Num.isValid(latestAmplitude)) {
            return new ElliottRatio(NaN, RatioType.NONE);
        }

        final int searchStart = swings.size() - 2;
        final int sameDirectionIndex = findMostRecentSwingIndex(swings, latest.isRising(), searchStart);
        final int oppositeDirectionIndex = findMostRecentSwingIndex(swings, !latest.isRising(), searchStart);

        if (sameDirectionIndex >= 0) {
            final ElliottSwing reference = swings.get(sameDirectionIndex);
            if (isExtensionExtreme(latest, reference)) {
                final ElliottRatio extension = ratio(latestAmplitude, reference, RatioType.EXTENSION);
                if (extension.type() != RatioType.NONE) {
                    return extension;
                }
            }
        }

        if (oppositeDirectionIndex >= 0) {
            final ElliottSwing reference = swings.get(oppositeDirectionIndex);
            return ratio(latestAmplitude, reference, RatioType.RETRACEMENT);
        }

        return new ElliottRatio(NaN, RatioType.NONE);
    }

    @Override
    public int getCountOfUnstableBars() {
        return swingIndicator.getCountOfUnstableBars();
    }

    private ElliottRatio ratio(final Num numerator, final ElliottSwing denominatorSwing, final RatioType type) {
        if (denominatorSwing == null) {
            return new ElliottRatio(NaN, RatioType.NONE);
        }
        if (!Num.isValid(numerator)) {
            return new ElliottRatio(NaN, RatioType.NONE);
        }
        final Num denominator = denominatorSwing.amplitude();
        if (!Num.isValid(denominator) || denominator.isZero()) {
            return new ElliottRatio(NaN, RatioType.NONE);
        }
        return new ElliottRatio(numerator.dividedBy(denominator), type);
    }

    private int findMostRecentSwingIndex(final List<ElliottSwing> swings, final boolean rising, final int startIndex) {
        for (int i = startIndex; i >= 0; i--) {
            final ElliottSwing swing = swings.get(i);
            if (swing != null && swing.isRising() == rising) {
                return i;
            }
        }
        return -1;
    }

    private boolean isExtensionExtreme(final ElliottSwing latest, final ElliottSwing reference) {
        if (latest == null || reference == null) {
            return false;
        }
        if (latest.isRising() != reference.isRising()) {
            return false;
        }
        final Num latestPrice = latest.toPrice();
        final Num referencePrice = reference.toPrice();
        if (!Num.isValid(latestPrice) || !Num.isValid(referencePrice)) {
            return false;
        }
        return latest.isRising() ? latestPrice.isGreaterThan(referencePrice) : latestPrice.isLessThan(referencePrice);
    }

    /**
     * @return underlying swing indicator used for ratio calculation
     * @since 0.22.0
     */
    public ElliottSwingIndicator getSwingIndicator() {
        return swingIndicator;
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
        if (!Num.isValid(value)) {
            return false;
        }
        final Num delta = value.minus(target).abs();
        return !delta.isGreaterThan(tolerance);
    }

}
