/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.wyckoff;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Detects Wyckoff structural events using structure and volume information.
 *
 * @since 0.22.2
 */
public final class WyckoffEventDetector {

    private final BarSeries series;
    private final ClosePriceIndicator closePriceIndicator;
    private final HighPriceIndicator highPriceIndicator;
    private final LowPriceIndicator lowPriceIndicator;
    private final Num retestTolerance;
    private final transient Map<Integer, Num> lowestLowCache;
    private final transient Map<Integer, Num> highestHighCache;

    /**
     * Creates a detector for the provided series.
     *
     * @param series          the series under analysis
     * @param retestTolerance tolerance applied when checking for retests relative
     *                        to the trading range bounds
     * @since 0.22.2
     */
    public WyckoffEventDetector(BarSeries series, Num retestTolerance) {
        this.series = Objects.requireNonNull(series, "series");
        this.closePriceIndicator = new ClosePriceIndicator(series);
        this.highPriceIndicator = new HighPriceIndicator(series);
        this.lowPriceIndicator = new LowPriceIndicator(series);
        this.retestTolerance = Objects.requireNonNull(retestTolerance, "retestTolerance");
        if (isInvalid(this.retestTolerance)) {
            throw new IllegalArgumentException("retestTolerance must be a valid number");
        }
        this.lowestLowCache = new ConcurrentHashMap<>();
        this.highestHighCache = new ConcurrentHashMap<>();
    }

    /**
     * Detects Wyckoff events for {@code index} using the supplied context.
     *
     * @param index         evaluation index
     * @param structure     structure snapshot for the index
     * @param volume        volume snapshot for the index
     * @param previousPhase previously inferred phase (or {@code null})
     * @return set of events observed at the index
     * @since 0.22.2
     */
    public EnumSet<WyckoffEvent> detect(int index, WyckoffStructureTracker.StructureSnapshot structure,
            WyckoffVolumeProfile.VolumeSnapshot volume, WyckoffPhase previousPhase) {
        final EnumSet<WyckoffEvent> events = EnumSet.noneOf(WyckoffEvent.class);
        if (structure == null || volume == null) {
            return events;
        }
        final Num closePrice = closePriceIndicator.getValue(index);
        if (isInvalid(closePrice)) {
            return events;
        }
        final boolean hasRange = !isInvalid(structure.rangeHigh()) && !isInvalid(structure.rangeLow());
        final WyckoffCycleType priorCycle = previousPhase != null ? previousPhase.cycleType()
                : WyckoffCycleType.UNKNOWN;
        if (!hasRange && volume.climax()) {
            if (isNewExtremeLow(index)) {
                events.add(WyckoffEvent.SELLING_CLIMAX);
            }
            if (isNewExtremeHigh(index)) {
                events.add(WyckoffEvent.BUYING_CLIMAX);
            }
        }
        if (!hasRange) {
            return events;
        }
        if (volume.climax() && isNear(structure.rangeLow(), closePrice)
                && priorCycle != WyckoffCycleType.DISTRIBUTION) {
            events.add(WyckoffEvent.SELLING_CLIMAX);
        }
        if (volume.climax() && isNear(structure.rangeHigh(), closePrice)
                && priorCycle != WyckoffCycleType.ACCUMULATION) {
            events.add(WyckoffEvent.BUYING_CLIMAX);
        }
        if (structure.inRange() && volume.climax()) {
            if (closePrice.isLessThan(structure.rangeLow()
                    .plus(structure.rangeHigh().minus(structure.rangeLow()).multipliedBy(retestTolerance)))) {
                events.add(WyckoffEvent.PRELIMINARY_SUPPORT);
            }
            if (closePrice.isGreaterThan(structure.rangeHigh()
                    .minus(structure.rangeHigh().minus(structure.rangeLow()).multipliedBy(retestTolerance)))) {
                events.add(WyckoffEvent.PRELIMINARY_SUPPLY);
            }
        }
        if (structure.brokeAboveRange()) {
            events.add(WyckoffEvent.RANGE_BREAKOUT);
            if (volume.climax()) {
                if (priorCycle == WyckoffCycleType.ACCUMULATION) {
                    events.add(WyckoffEvent.SIGN_OF_STRENGTH);
                } else {
                    events.add(WyckoffEvent.BUYING_CLIMAX);
                }
            }
        }
        if (structure.brokeBelowRange()) {
            events.add(WyckoffEvent.RANGE_BREAKDOWN);
            if (volume.climax()) {
                if (priorCycle == WyckoffCycleType.ACCUMULATION || priorCycle == WyckoffCycleType.UNKNOWN) {
                    events.add(WyckoffEvent.SPRING);
                }
            }
        }
        if (isNear(structure.rangeLow(), closePrice) && volume.dryUp()) {
            events.add(WyckoffEvent.LAST_POINT_OF_SUPPORT);
        }
        if (isNear(structure.rangeHigh(), closePrice) && volume.dryUp()) {
            events.add(WyckoffEvent.LAST_POINT_OF_SUPPLY);
        }
        if (structure.inRange() && !volume.climax() && !volume.dryUp()) {
            events.add(WyckoffEvent.SECONDARY_TEST);
        }
        if (structure.brokeAboveRange() && !volume.climax()) {
            events.add(WyckoffEvent.UPTHRUST);
        }
        if (structure.brokeAboveRange() && volume.dryUp()) {
            events.add(WyckoffEvent.UPTHRUST_AFTER_DISTRIBUTION);
        }
        return events;
    }

    private boolean isNear(Num anchor, Num value) {
        if (isInvalid(anchor) || isInvalid(value)) {
            return false;
        }
        final Num distance = anchor.minus(value).abs();
        final Num allowance = anchor.multipliedBy(retestTolerance);
        return distance.isLessThan(allowance);
    }

    private boolean isNewExtremeLow(int index) {
        final Num currentLow = lowPriceIndicator.getValue(index);
        if (isInvalid(currentLow)) {
            return false;
        }
        if (index <= series.getBeginIndex()) {
            lowestLowCache.put(index, currentLow);
            return true;
        }
        final Num priorLowest = lowestLowUpTo(index - 1);
        final boolean isNewLow = isInvalid(priorLowest) || currentLow.isLessThan(priorLowest);
        lowestLowCache.put(index, isNewLow ? currentLow : priorLowest);
        return isNewLow;
    }

    private boolean isNewExtremeHigh(int index) {
        final Num currentHigh = highPriceIndicator.getValue(index);
        if (isInvalid(currentHigh)) {
            return false;
        }
        if (index <= series.getBeginIndex()) {
            highestHighCache.put(index, currentHigh);
            return true;
        }
        final Num priorHighest = highestHighUpTo(index - 1);
        final boolean isNewHigh = isInvalid(priorHighest) || currentHigh.isGreaterThan(priorHighest);
        highestHighCache.put(index, isNewHigh ? currentHigh : priorHighest);
        return isNewHigh;
    }

    private Num lowestLowUpTo(int index) {
        if (index < series.getBeginIndex()) {
            return NaN;
        }
        final Num cached = lowestLowCache.get(index);
        if (cached != null) {
            return cached;
        }
        final Num previous = index == series.getBeginIndex() ? NaN : lowestLowUpTo(index - 1);
        final Num candidate = lowPriceIndicator.getValue(index);
        final Num next;
        if (isInvalid(previous)) {
            next = isInvalid(candidate) ? NaN : candidate;
        } else if (!isInvalid(candidate) && candidate.isLessThan(previous)) {
            next = candidate;
        } else {
            next = previous;
        }
        lowestLowCache.put(index, next);
        return next;
    }

    private Num highestHighUpTo(int index) {
        if (index < series.getBeginIndex()) {
            return NaN;
        }
        final Num cached = highestHighCache.get(index);
        if (cached != null) {
            return cached;
        }
        final Num previous = index == series.getBeginIndex() ? NaN : highestHighUpTo(index - 1);
        final Num candidate = highPriceIndicator.getValue(index);
        final Num next;
        if (isInvalid(previous)) {
            next = isInvalid(candidate) ? NaN : candidate;
        } else if (!isInvalid(candidate) && candidate.isGreaterThan(previous)) {
            next = candidate;
        } else {
            next = previous;
        }
        highestHighCache.put(index, next);
        return next;
    }

    private static boolean isInvalid(Num value) {
        return Num.isNaNOrNull(value);
    }
}
