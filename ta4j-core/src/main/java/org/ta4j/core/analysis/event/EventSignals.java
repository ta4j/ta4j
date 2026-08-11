/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.event;

import java.util.Objects;
import java.util.function.IntPredicate;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

/**
 * Internal adapters that normalize event sources to {@link EventSignal}; not
 * part of the public API.
 *
 * <p>
 * The {@link Indicator} adapter treats only {@link Boolean#TRUE} as an event;
 * {@code null} and {@code false} values are non-events. The predicate adapter
 * exists for the internal tests and benchmarks that exercise the matcher with
 * explicit index predicates instead of full indicators.
 */
final class EventSignals {

    private EventSignals() {
    }

    /**
     * Adapts a Boolean indicator as an event signal.
     *
     * <p>
     * The series and unstable-bar boundary are taken from the indicator itself.
     *
     * @param indicator the indicator to adapt
     * @return an event signal over the indicator's bar series
     */
    static EventSignal fromIndicator(Indicator<Boolean> indicator) {
        Objects.requireNonNull(indicator, "indicator");
        return new IndicatorEventSignal(indicator);
    }

    /**
     * Adapts an explicit predicate as an event signal.
     *
     * <p>
     * Used by the internal tests and benchmarks that drive the matcher with index
     * predicates; the predicate receives the bar index and must be deterministic.
     *
     * @param series       the series the predicate is evaluated over
     * @param unstableBars the count of leading bars whose predicate results are not
     *                     trustworthy; must be {@code >= 0}
     * @param predicate    the event predicate
     * @return an event signal over {@code series}
     */
    static EventSignal fromPredicate(BarSeries series, int unstableBars, IntPredicate predicate) {
        Objects.requireNonNull(series, "series");
        if (unstableBars < 0) {
            throw new IllegalArgumentException("unstableBars must be >= 0");
        }
        Objects.requireNonNull(predicate, "predicate");
        return new PredicateEventSignal(series, unstableBars, predicate);
    }

    private static final class IndicatorEventSignal implements EventSignal {

        private final Indicator<Boolean> indicator;

        private IndicatorEventSignal(Indicator<Boolean> indicator) {
            this.indicator = indicator;
        }

        @Override
        public BarSeries getBarSeries() {
            return indicator.getBarSeries();
        }

        @Override
        public int getCountOfUnstableBars() {
            return indicator.getCountOfUnstableBars();
        }

        @Override
        public boolean isEvent(int index) {
            return Boolean.TRUE.equals(indicator.getValue(index));
        }
    }

    private static final class PredicateEventSignal implements EventSignal {

        private final BarSeries series;
        private final int unstableBars;
        private final IntPredicate predicate;

        private PredicateEventSignal(BarSeries series, int unstableBars, IntPredicate predicate) {
            this.series = series;
            this.unstableBars = unstableBars;
            this.predicate = predicate;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }

        @Override
        public int getCountOfUnstableBars() {
            return unstableBars;
        }

        @Override
        public boolean isEvent(int index) {
            return predicate.test(index);
        }
    }
}
