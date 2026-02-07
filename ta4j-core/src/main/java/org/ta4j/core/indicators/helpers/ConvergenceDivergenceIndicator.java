/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.statistics.CorrelationCoefficientIndicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.IsFallingRule;
import org.ta4j.core.rules.IsRisingRule;

/**
 * Convergence-Divergence indicator.
 */
public class ConvergenceDivergenceIndicator extends CachedIndicator<Boolean> {

    /**
     * Select the type of convergence or divergence.
     */
    public enum ConvergenceDivergenceType {
        /**
         * Returns true for <b>"positiveConvergent"</b> when the values of the
         * ref-{@link Indicator indicator} and the values of the other-{@link Indicator
         * indicator} increase within the barCount. In short: "other" and "ref" makes
         * higher highs.
         */
        positiveConvergent,

        /**
         * Returns true for <b>"negativeConvergent"</b> when the values of the
         * ref-{@link Indicator indicator} and the values of the other-{@link Indicator
         * indicator} decrease within the barCount. In short: "other" and "ref" makes
         * lower lows.
         */
        negativeConvergent,

        /**
         * Returns true for <b>"positiveDivergent"</b> when the values of the
         * ref-{@link Indicator indicator} increase and the values of the
         * other-{@link Indicator indicator} decrease within a barCount. In short:
         * "other" makes lower lows while "ref" makes higher highs.
         */
        positiveDivergent,

        /**
         * Returns true for <b>"negativeDivergent"</b> when the values of the
         * ref-{@link Indicator indicator} decrease and the values of the
         * other-{@link Indicator indicator} increase within a barCount. In short:
         * "other" makes higher highs while "ref" makes lower lows.
         */
        negativeDivergent
    }

    /**
     * Select the type of strict convergence or divergence.
     */
    public enum ConvergenceDivergenceStrictType {

        /**
         * Returns true for <b>"positiveConvergentStrict"</b> when the values of the
         * ref-{@link Indicator indicator} and the values of the other-{@link Indicator
         * indicator} increase consecutively within a barCount. In short: "other" and
         * "ref" makes strict higher highs.
         */
        positiveConvergentStrict,

        /**
         * Returns true for <b>"negativeConvergentStrict"</b> when the values of the
         * ref-{@link Indicator indicator} and the values of the other-{@link Indicator
         * indicator} decrease consecutively within a barCount. In short: "other" and
         * "ref" makes strict lower lows.
         */
        negativeConvergentStrict,

        /**
         * Returns true for <b>"positiveDivergentStrict"</b> when the values of the
         * ref-{@link Indicator indicator} increase consecutively and the values of the
         * other-{@link Indicator indicator} decrease consecutively within a barCount.
         * In short: "other" makes strict higher highs and "ref" makes strict lower
         * lows.
         */
        positiveDivergentStrict,

        /**
         * Returns true for <b>"negativeDivergentStrict"</b> when the values of the
         * ref-{@link Indicator indicator} decrease consecutively and the values of the
         * other-{@link Indicator indicator} increase consecutively within a barCount.
         * In short: "other" makes strict lower lows and "ref" makes strict higher
         * highs.
         */
        negativeDivergentStrict
    }

    /** The actual indicator. */
    private final Indicator<Num> ref;

    /** The other indicator. */
    private final Indicator<Num> other;

    /** The barCount. */
    private final int barCount;

    /** The type of the convergence or divergence **/
    private final ConvergenceDivergenceType type;

    /** The type of the strict convergence or strict divergence **/
    private final ConvergenceDivergenceStrictType strictType;

    /** The minimum strength for convergence or divergence. **/
    private final Num minStrength;

    /** The minimum slope for convergence or divergence. **/
    private final Num minSlope;
    private final int unstableBars;

    /**
     * Constructor. <br/>
     * <br/>
     *
     * The <b>"minStrength"</b> is the minimum required strength for convergence or
     * divergence and must be a number between "0.1" and "1.0": <br/>
     * <br/>
     * 0.1: very weak <br/>
     * 0.8: strong (recommended) <br/>
     * 1.0: very strong <br/>
     *
     * <br/>
     *
     * The <b>"minSlope"</b> is the minimum required slope for convergence or
     * divergence and must be a number between "0.1" and "1.0": <br/>
     * <br/>
     * 0.1: very unstrict<br/>
     * 0.3: strict (recommended) <br/>
     * 1.0: very strict <br/>
     *
     * @param ref         the indicator
     * @param other       the other indicator
     * @param barCount    the time frame
     * @param type        of convergence or divergence
     * @param minStrength the minimum required strength for convergence or
     *                    divergence
     * @param minSlope    the minimum required slope for convergence or divergence
     */
    public ConvergenceDivergenceIndicator(Indicator<Num> ref, Indicator<Num> other, int barCount,
            ConvergenceDivergenceType type, double minStrength, double minSlope) {
        super(ref);
        this.ref = ref;
        this.other = other;
        this.barCount = barCount;
        this.type = type;
        this.strictType = null;
        this.minStrength = getBarSeries().numFactory().numOf(Math.min(1, Math.abs(minStrength)));
        this.minSlope = getBarSeries().numFactory().numOf(minSlope);
        this.unstableBars = computeUnstableBars(ref, other, barCount);
    }

    /**
     * Constructor for strong convergence or divergence.
     *
     * @param ref      the indicator
     * @param other    the other indicator
     * @param barCount the time frame
     * @param type     of convergence or divergence
     */
    public ConvergenceDivergenceIndicator(Indicator<Num> ref, Indicator<Num> other, int barCount,
            ConvergenceDivergenceType type) {
        this(ref, other, barCount, type, 0.8, 0.3);
    }

    /**
     * Constructor for strict convergence or divergence.
     *
     * @param ref        the indicator
     * @param other      the other indicator
     * @param barCount   the time frame
     * @param strictType of strict convergence or divergence
     */
    public ConvergenceDivergenceIndicator(Indicator<Num> ref, Indicator<Num> other, int barCount,
            ConvergenceDivergenceStrictType strictType) {
        super(ref);
        this.ref = ref;
        this.other = other;
        this.barCount = barCount;
        this.type = null;
        this.strictType = strictType;
        this.minStrength = null;
        this.minSlope = null;
        this.unstableBars = computeUnstableBars(ref, other, barCount);
    }

    @Override
    protected Boolean calculate(int index) {

        if (minStrength != null && minStrength.isZero()) {
            return false;
        }

        if (type != null) {
            switch (type) {
            case positiveConvergent:
                return calculatePositiveConvergence(index);
            case negativeConvergent:
                return calculateNegativeConvergence(index);
            case positiveDivergent:
                return calculatePositiveDivergence(index);
            case negativeDivergent:
                return calculateNegativeDivergence(index);
            default:
                return false;
            }
        }

        else if (strictType != null) {
            switch (strictType) {
            case positiveConvergentStrict:
                return calculatePositiveConvergenceStrict(index);
            case negativeConvergentStrict:
                return calculateNegativeConvergenceStrict(index);
            case positiveDivergentStrict:
                return calculatePositiveDivergenceStrict(index);
            case negativeDivergentStrict:
                return calculateNegativeDivergenceStrict(index);
            default:
                return false;
            }
        }

        return false;
    }

    /** @return {@link #barCount} */
    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    private static int computeUnstableBars(Indicator<Num> ref, Indicator<Num> other, int barCount) {
        int baseUnstable = Math.max(ref.getCountOfUnstableBars(), other.getCountOfUnstableBars());
        int correlationUnstable = new CorrelationCoefficientIndicator(ref, other, barCount).getCountOfUnstableBars();
        int slopeUnstable = new SimpleLinearRegressionIndicator(ref, barCount).getCountOfUnstableBars();
        return Math.max(baseUnstable, Math.max(correlationUnstable, slopeUnstable));
    }

    /**
     * @param index the actual index
     * @return true, if strict positive convergent
     */
    private Boolean calculatePositiveConvergenceStrict(int index) {
        Rule refIsRising = new IsRisingRule(ref, barCount);
        Rule otherIsRising = new IsRisingRule(other, barCount);

        return (refIsRising.and(otherIsRising)).isSatisfied(index);
    }

    /**
     * @param index the actual index
     * @return true, if strict negative convergent
     */
    private Boolean calculateNegativeConvergenceStrict(int index) {
        Rule refIsFalling = new IsFallingRule(ref, barCount);
        Rule otherIsFalling = new IsFallingRule(other, barCount);

        return (refIsFalling.and(otherIsFalling)).isSatisfied(index);
    }

    /**
     * @param index the actual index
     * @return true, if positive divergent
     */
    private Boolean calculatePositiveDivergenceStrict(int index) {
        Rule refIsRising = new IsRisingRule(ref, barCount);
        Rule otherIsFalling = new IsFallingRule(other, barCount);

        return (refIsRising.and(otherIsFalling)).isSatisfied(index);
    }

    /**
     * @param index the actual index
     * @return true, if negative divergent
     */
    private Boolean calculateNegativeDivergenceStrict(int index) {
        Rule refIsFalling = new IsFallingRule(ref, barCount);
        Rule otherIsRising = new IsRisingRule(other, barCount);

        return (refIsFalling.and(otherIsRising)).isSatisfied(index);
    }

    /**
     * @param index the actual index
     * @return true, if positive convergent
     */
    private Boolean calculatePositiveConvergence(int index) {
        CorrelationCoefficientIndicator cc = new CorrelationCoefficientIndicator(ref, other, barCount);
        boolean isConvergent = cc.getValue(index).isGreaterThanOrEqual(minStrength);

        Num slope = calculateSlopeRel(index);
        boolean isPositive = slope.isGreaterThanOrEqual(minSlope.abs());

        return isConvergent && isPositive;
    }

    /**
     * @param index the actual index
     * @return true, if negative convergent
     */
    private Boolean calculateNegativeConvergence(int index) {
        CorrelationCoefficientIndicator cc = new CorrelationCoefficientIndicator(ref, other, barCount);
        boolean isConvergent = cc.getValue(index).isGreaterThanOrEqual(minStrength);

        Num slope = calculateSlopeRel(index);
        boolean isNegative = slope
                .isLessThanOrEqual(minSlope.abs().multipliedBy(getBarSeries().numFactory().minusOne()));

        return isConvergent && isNegative;
    }

    /**
     * @param index the actual index
     * @return true, if positive divergent
     */
    private Boolean calculatePositiveDivergence(int index) {

        CorrelationCoefficientIndicator cc = new CorrelationCoefficientIndicator(ref, other, barCount);
        boolean isDivergent = cc.getValue(index)
                .isLessThanOrEqual(minStrength.multipliedBy(getBarSeries().numFactory().minusOne()));

        if (isDivergent) {
            // If "isDivergent" and "ref" is positive, then "other" must be negative.
            Num slope = calculateSlopeRel(index);
            return slope.isGreaterThanOrEqual(minSlope.abs());
        }

        return false;
    }

    /**
     * @param index the actual index
     * @return true, if negative divergent
     */
    private Boolean calculateNegativeDivergence(int index) {

        Num minusOne = getBarSeries().numFactory().minusOne();
        CorrelationCoefficientIndicator cc = new CorrelationCoefficientIndicator(ref, other, barCount);
        boolean isDivergent = cc.getValue(index).isLessThanOrEqual(minStrength.multipliedBy(minusOne));

        if (isDivergent) {
            // If "isDivergent" and "ref" is positive, then "other" must be negative.
            Num slope = calculateSlopeRel(index);
            return slope.isLessThanOrEqual(minSlope.abs().multipliedBy(minusOne));
        }

        return false;
    }

    /**
     * @param index the actual index
     * @return the relative slope
     */
    private Num calculateSlopeRel(int index) {
        SimpleLinearRegressionIndicator slrRef = new SimpleLinearRegressionIndicator(ref, barCount);
        int firstIndex = Math.max(0, index - barCount + 1);
        return (slrRef.getValue(index).minus(slrRef.getValue(firstIndex))).dividedBy(slrRef.getValue(index));
    }

}
