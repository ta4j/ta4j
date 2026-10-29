/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.ta4j.core.serialization.ComponentDescriptor;

/**
 * Evaluates a condition on the aligned bar end time. The exact-time constructor
 * is serializable; an arbitrary predicate is not.
 *
 * @since 0.27.1
 */
public final class EndTimeRule implements PortfolioRule {
    private final Predicate<Instant> predicate;
    private final Instant exactTime;

    /**
     * Matches one exact end time.
     *
     * @param exactTime required end time
     * @since 0.27.1
     */
    public EndTimeRule(Instant exactTime) {
        this.exactTime = Objects.requireNonNull(exactTime, "exactTime");
        this.predicate = exactTime::equals;
    }

    /**
     * Tests end times using a caller-supplied predicate. The predicate is opaque
     * and therefore this rule cannot be serialized.
     *
     * @param predicate condition to evaluate
     * @since 0.27.1
     */
    public EndTimeRule(Predicate<Instant> predicate) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.exactTime = null;
    }

    /**
     * {@inheritDoc}
     * @since 0.27.1
     */
    @Override
    public boolean isSatisfied(String asset, int index, PortfolioTradingRecord record) {
        PortfolioSeriesView view = Objects.requireNonNull(record, "record").series();
        return view.hasBar(asset, index) && predicate.test(view.getEndTime(index));
    }

    /**
     * {@inheritDoc}
     * @since 0.27.1
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        if (exactTime == null) {
            throw new UnsupportedOperationException("Cannot serialize an opaque end-time predicate");
        }
        return ComponentDescriptor.builder().withType(EndTimeRule.class.getName()).withLabel("EndTimeRule")
                .withParameters(Map.of("endTime", exactTime.toString())).build();
    }
}
