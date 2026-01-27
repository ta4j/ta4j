/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import java.util.Objects;

import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;

/**
 * A rule that monitors when an {@link Indicator} shows a specified slope.
 *
 * <p>
 * Satisfied when the difference between the current value of the
 * {@link Indicator indicator} and its previous (n-th) value is within the
 * specified slope range. The rule checks that the difference is greater than or
 * equal to {@code minSlope} (if specified) and less than or equal to
 * {@code maxSlope} (if specified). It can test both positive and negative
 * slopes.
 *
 * <p>
 * The {@code nthPrevious} parameter allows comparing with the n-th previous
 * value instead of just the immediately previous value (which is the default
 * when {@code nthPrevious} is 1).
 *
 * <p>
 * This rule does not use the {@code tradingRecord}.
 */
public class InSlopeRule extends AbstractRule {

    /**
     * Reference indicator tracked for slope calculations (serialized for
     * rebuilding).
     */
    private final Indicator<Num> reference;

    /** The minimum slope between the reference indicator and its previous value. */
    private final Num minSlope;

    /** The maximum slope between the reference indicator and its previous value. */
    private final Num maxSlope;

    /**
     * The difference indicator used to calculate the slope between the reference
     * indicator and its previous values. This field stores the calculated
     * difference which is essential for determining whether the slope falls within
     * the specified bounds.
     */
    private final transient Indicator<Num> difference;

    /**
     * Constructor.
     *
     * @param ref      the reference indicator
     * @param minSlope minimum slope between reference and previous indicator
     */
    public InSlopeRule(Indicator<Num> ref, Num minSlope) {
        this(ref, 1, minSlope, NaN);
    }

    /**
     * Constructor.
     *
     * @param ref      the reference indicator
     * @param minSlope minimum slope between value of reference and previous
     *                 indicator
     * @param maxSlope maximum slope between value of reference and previous
     *                 indicator
     */
    public InSlopeRule(Indicator<Num> ref, Num minSlope, Num maxSlope) {
        this(ref, 1, minSlope, maxSlope);
    }

    /**
     * Creates an InSlopeRule with the specified reference indicator and slope
     * bounds provided as strings.
     *
     * @param ref      the reference indicator
     * @param minSlope the minimum slope between the reference and previous
     *                 indicator values, provided as a string
     * @param maxSlope the maximum slope between the reference and previous
     *                 indicator values, provided as a string
     */
    public InSlopeRule(Indicator<Num> ref, String minSlope, String maxSlope) {
        this(ref, 1, parseSlope(ref, minSlope), parseSlope(ref, maxSlope));
    }

    /**
     * Constructor.
     *
     * @param ref         the reference indicator
     * @param nthPrevious defines the previous n-th indicator
     * @param maxSlope    maximum slope between value of reference and previous
     *                    indicator
     */
    public InSlopeRule(Indicator<Num> ref, int nthPrevious, Num maxSlope) {
        this(ref, nthPrevious, NaN, maxSlope);
    }

    /**
     * Constructor.
     *
     * @param ref         the reference indicator
     * @param nthPrevious defines the previous n-th indicator
     * @param minSlope    minimum slope between value of reference and previous
     *                    indicator
     * @param maxSlope    maximum slope between value of reference and previous
     *                    indicator
     */
    public InSlopeRule(Indicator<Num> ref, int nthPrevious, Num minSlope, Num maxSlope) {
        this.reference = Objects.requireNonNull(ref, "ref");
        if (nthPrevious < 1) {
            throw new IllegalArgumentException("nthPrevious must be >= 1");
        }
        this.minSlope = normalizeSlope(minSlope);
        this.maxSlope = normalizeSlope(maxSlope);
        this.difference = BinaryOperationIndicator.difference(reference,
                new PreviousValueIndicator(reference, nthPrevious));
    }

    /** This rule does not use the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        final var val = difference.getValue(index);
        final boolean minSlopeSatisfied = minSlope.isNaN() || val.isGreaterThanOrEqual(minSlope);
        final boolean maxSlopeSatisfied = maxSlope.isNaN() || val.isLessThanOrEqual(maxSlope);
        final boolean isNaN = minSlope.isNaN() && maxSlope.isNaN();

        final boolean satisfied = minSlopeSatisfied && maxSlopeSatisfied && !isNaN;
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private static Num parseSlope(Indicator<Num> ref, String slope) {
        if (slope == null) {
            return NaN;
        }
        return ref.getBarSeries().numFactory().numOf(slope);
    }

    private static Num normalizeSlope(Num slope) {
        return slope == null ? NaN : slope;
    }
}
