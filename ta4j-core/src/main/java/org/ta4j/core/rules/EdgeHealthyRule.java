/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;

/**
 * Requires an edge indicator to stay above configured thresholds before an
 * entry is allowed.
 *
 * <p>
 * The rule can enforce both a minimum edge level and an optional minimum edge
 * slope, which makes it useful for gating entries on both absolute quality and
 * whether that quality is still improving or at least not deteriorating too
 * quickly.
 * </p>
 *
 * @since 0.22.7
 */
public class EdgeHealthyRule extends AbstractRule {

    private final Indicator<Num> edgeIndicator;
    private final Indicator<Num> minimumEdgeIndicator;
    private final Indicator<Num> edgeSlopeIndicator;
    private final Indicator<Num> minimumSlopeIndicator;

    /**
     * Creates a threshold-only edge rule.
     *
     * @param edgeIndicator the edge indicator to evaluate
     * @param minimumEdge   the minimum acceptable edge value
     * @since 0.22.7
     */
    public EdgeHealthyRule(Indicator<Num> edgeIndicator, Number minimumEdge) {
        this(edgeIndicator,
                new ConstantIndicator<>(Objects.requireNonNull(edgeIndicator, "edgeIndicator").getBarSeries(),
                        edgeIndicator.getBarSeries().numFactory().numOf(minimumEdge)),
                null, null);
    }

    /**
     * Creates an edge rule with optional slope filtering.
     *
     * @param edgeIndicator         the edge indicator to evaluate
     * @param minimumEdgeIndicator  indicator providing the minimum acceptable edge
     * @param edgeSlopeIndicator    optional edge slope indicator
     * @param minimumSlopeIndicator optional minimum acceptable slope; defaults to
     *                              zero when omitted and a slope indicator is
     *                              provided
     * @since 0.22.7
     */
    public EdgeHealthyRule(Indicator<Num> edgeIndicator, Indicator<Num> minimumEdgeIndicator,
            Indicator<Num> edgeSlopeIndicator, Indicator<Num> minimumSlopeIndicator) {
        this.edgeIndicator = Objects.requireNonNull(edgeIndicator, "edgeIndicator");
        this.minimumEdgeIndicator = Objects.requireNonNull(minimumEdgeIndicator, "minimumEdgeIndicator");
        this.edgeSlopeIndicator = edgeSlopeIndicator;
        this.minimumSlopeIndicator = minimumSlopeIndicator;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        Num edge = edgeIndicator.getValue(index);
        Num minimumEdge = minimumEdgeIndicator.getValue(index);
        if (Num.isNaNOrNull(edge) || Num.isNaNOrNull(minimumEdge) || edge.isLessThan(minimumEdge)) {
            traceIsSatisfied(index, false);
            return false;
        }
        if (edgeSlopeIndicator != null) {
            Num slope = edgeSlopeIndicator.getValue(index);
            if (Num.isNaNOrNull(slope)) {
                traceIsSatisfied(index, false);
                return false;
            }
            Num minimumSlope = minimumSlopeIndicator == null ? slope.getNumFactory().zero()
                    : minimumSlopeIndicator.getValue(index);
            if (Num.isNaNOrNull(minimumSlope) || slope.isLessThan(minimumSlope)) {
                traceIsSatisfied(index, false);
                return false;
            }
        }
        traceIsSatisfied(index, true);
        return true;
    }

    /**
     * @return the edge indicator
     * @since 0.22.7
     */
    public Indicator<Num> getEdgeIndicator() {
        return edgeIndicator;
    }

    /**
     * @return the minimum acceptable edge indicator
     * @since 0.22.7
     */
    public Indicator<Num> getMinimumEdgeIndicator() {
        return minimumEdgeIndicator;
    }

    /**
     * @return the optional edge slope indicator
     * @since 0.22.7
     */
    public Indicator<Num> getEdgeSlopeIndicator() {
        return edgeSlopeIndicator;
    }

    /**
     * @return the optional minimum slope indicator
     * @since 0.22.7
     */
    public Indicator<Num> getMinimumSlopeIndicator() {
        return minimumSlopeIndicator;
    }
}
