///**
// * The MIT License (MIT)
// *
// * Copyright (c) 2017-2023 Ta4j Organization & respective
// * authors (see AUTHORS)
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy of
// * this software and associated documentation files (the "Software"), to deal in
// * the Software without restriction, including without limitation the rights to
// * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
// * the Software, and to permit persons to whom the Software is furnished to do so,
// * subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all
// * copies or substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
// * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
// * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
// * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
// * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
// */
//package org.ta4j.core.indicators.helpers;
//
//import org.ta4j.core.Rule;
//import org.ta4j.core.indicators.AbstractIndicator;
//import org.ta4j.core.indicators.Indicator;
//import org.ta4j.core.indicators.statistics.CorrelationCoefficientIndicator;
//import org.ta4j.core.num.Num;
//import org.ta4j.core.rules.IsFallingRule;
//import org.ta4j.core.rules.IsRisingRule;
//
///**
// * Convergence-Divergence indicator.
// */
//public class ConvergenceDivergenceIndicator extends AbstractIndicator<Boolean> {
//
//    /**
//     * Select the type of convergence or divergence.
//     */
//    public enum ConvergenceDivergenceType {
//        /**
//         * Returns true for <b>"positiveConvergent"</b> when the values of the
//         * ref-{@link Indicator indicator} and the values of the other-{@link Indicator
//         * indicator} increase within the barCount. In short: "other" and "ref" makes
//         * higher highs.
//         */
//        positiveConvergent,
//
//        /**
//         * Returns true for <b>"negativeConvergent"</b> when the values of the
//         * ref-{@link Indicator indicator} and the values of the other-{@link Indicator
//         * indicator} decrease within the barCount. In short: "other" and "ref" makes
//         * lower lows.
//         */
//        negativeConvergent,
//
//        /**
//         * Returns true for <b>"positiveDivergent"</b> when the values of the
//         * ref-{@link Indicator indicator} increase and the values of the
//         * other-{@link Indicator indicator} decrease within a barCount. In short:
//         * "other" makes lower lows while "ref" makes higher highs.
//         */
//        positiveDivergent,
//
//        /**
//         * Returns true for <b>"negativeDivergent"</b> when the values of the
//         * ref-{@link Indicator indicator} decrease and the values of the
//         * other-{@link Indicator indicator} increase within a barCount. In short:
//         * "other" makes higher highs while "ref" makes lower lows.
//         */
//        negativeDivergent
//    }
//
//    /**
//     * Select the type of strict convergence or divergence.
//     */
//    public enum ConvergenceDivergenceStrictType {
//
//        /**
//         * Returns true for <b>"positiveConvergentStrict"</b> when the values of the
//         * ref-{@link Indicator indicator} and the values of the other-{@link Indicator
//         * indicator} increase consecutively within a barCount. In short: "other" and
//         * "ref" makes strict higher highs.
//         */
//        positiveConvergentStrict,
//
//        /**
//         * Returns true for <b>"negativeConvergentStrict"</b> when the values of the
//         * ref-{@link Indicator indicator} and the values of the other-{@link Indicator
//         * indicator} decrease consecutively within a barCount. In short: "other" and
//         * "ref" makes strict lower lows.
//         */
//        negativeConvergentStrict,
//
//        /**
//         * Returns true for <b>"positiveDivergentStrict"</b> when the values of the
//         * ref-{@link Indicator indicator} increase consecutively and the values of the
//         * other-{@link Indicator indicator} decrease consecutively within a barCount.
//         * In short: "other" makes strict higher highs and "ref" makes strict lower
//         * lows.
//         */
//        positiveDivergentStrict,
//
//        /**
//         * Returns true for <b>"negativeDivergentStrict"</b> when the values of the
//         * ref-{@link Indicator indicator} decrease consecutively and the values of the
//         * other-{@link Indicator indicator} increase consecutively within a barCount.
//         * In short: "other" makes strict lower lows and "ref" makes strict higher
//         * highs.
//         */
//        negativeDivergentStrict
//    }
//
//    /** The actual indicator. */
//    private final Indicator<Num> ref;
//
//    /** The other indicator. */
//    private final Indicator<Num> other;
//
//    /** The barCount. */
//    private final int barCount;
//
//    /** The type of the convergence or divergence **/
//    private final ConvergenceDivergenceType type;
//
//    /** The type of the strict convergence or strict divergence **/
//    private final ConvergenceDivergenceStrictType strictType;
//
//    /** The minimum strength for convergence or divergence. **/
//    private final Num minStrength;
//
//    /** The minimum slope for convergence or divergence. **/
//    private final Num minSlope;
//
//    /**
//     * Constructor. <br/>
//     * <br/>
//     *
//     * The <b>"minStrength"</b> is the minimum required strength for convergence or
//     * divergence and must be a number between "0.1" and "1.0": <br/>
//     * <br/>
//     * 0.1: very weak <br/>
//     * 0.8: strong (recommended) <br/>
//     * 1.0: very strong <br/>
//     *
//     * <br/>
//     *
//     * The <b>"minSlope"</b> is the minimum required slope for convergence or
//     * divergence and must be a number between "0.1" and "1.0": <br/>
//     * <br/>
//     * 0.1: very unstrict<br/>
//     * 0.3: strict (recommended) <br/>
//     * 1.0: very strict <br/>
//     *
//     * @param ref         the indicator
//     * @param other       the other indicator
//     * @param barCount    the time frame
//     * @param type        of convergence or divergence
//     * @param minStrength the minimum required strength for convergence or
//     *                    divergence
//     * @param minSlope    the minimum required slope for convergence or divergence
//     */
//    public ConvergenceDivergenceIndicator(Indicator<Num> ref, Indicator<Num> other, int barCount,
//            ConvergenceDivergenceType type, double minStrength, double minSlope) {
//        super(ref.getBarSeries());
//        this.ref = ref;
//        this.other = other;
//        this.barCount = barCount;
//        this.type = type;
//        this.strictType = null;
//        this.minStrength = getBarSeries().numFactory().numOf(Math.min(1, Math.abs(minStrength)));
//        this.minSlope = getBarSeries().numFactory().numOf(minSlope);
//    }
//
//    /**
//     * Constructor for strong convergence or divergence.
//     *
//     * @param ref      the indicator
//     * @param other    the other indicator
//     * @param barCount the time frame
//     * @param type     of convergence or divergence
//     */
//    public ConvergenceDivergenceIndicator(Indicator<Num> ref, Indicator<Num> other, int barCount,
//            ConvergenceDivergenceType type) {
//        this(ref, other, barCount, type, 0.8, 0.3);
//    }
//
//    /**
//     * Constructor for strict convergence or divergence.
//     *
//     * @param ref        the indicator
//     * @param other      the other indicator
//     * @param barCount   the time frame
//     * @param strictType of strict convergence or divergence
//     */
//    public ConvergenceDivergenceIndicator(Indicator<Num> ref, Indicator<Num> other, int barCount,
//            ConvergenceDivergenceStrictType strictType) {
//        super(ref.getBarSeries());
//        this.ref = ref;
//        this.other = other;
//        this.barCount = barCount;
//        this.type = null;
//        this.strictType = strictType;
//        this.minStrength = null;
//        this.minSlope = null;
//    }
//
//    protected Boolean calculate() {
//
//        if (minStrength != null && minStrength.isZero()) {
//            return false;
//        }
//
//        if (type != null) {
//            switch (type) {
//            case positiveConvergent:
//                return calculatePositiveConvergence();
//            case negativeConvergent:
//                return calculateNegativeConvergence();
//            case positiveDivergent:
//                return calculatePositiveDivergence();
//            case negativeDivergent:
//                return calculateNegativeDivergence();
//            default:
//                return false;
//            }
//        }
//
//        else if (strictType != null) {
//            switch (strictType) {
//            case positiveConvergentStrict:
//                return calculatePositiveConvergenceStrict();
//            case negativeConvergentStrict:
//                return calculateNegativeConvergenceStrict();
//            case positiveDivergentStrict:
//                return calculatePositiveDivergenceStrict();
//            case negativeDivergentStrict:
//                return calculateNegativeDivergenceStrict();
//            default:
//                return false;
//            }
//        }
//
//        return false;
//    }
//
//
//    @Override
//    public Boolean getValue() {
//        return calculate();
//    }
//
//
//    /** @return {@link #barCount} */
//    @Override
//    public int getUnstableBars() {
//        return barCount;
//    }
//
//    /**
//     * @return true, if strict positive convergent
//     */
//    private Boolean calculatePositiveConvergenceStrict() {
//        Rule refIsRising = new IsRisingRule(ref, barCount);
//        Rule otherIsRising = new IsRisingRule(ref, barCount);
//
//        return (refIsRising.and(otherIsRising)).isSatisfied();
//    }
//
//    /**
//     * @return true, if strict negative convergent
//     */
//    private Boolean calculateNegativeConvergenceStrict() {
//        Rule refIsFalling = new IsFallingRule(ref, barCount);
//        Rule otherIsFalling = new IsFallingRule(ref, barCount);
//
//        return (refIsFalling.and(otherIsFalling)).isSatisfied();
//    }
//
//    /**
//     * @return true, if positive divergent
//     */
//    private Boolean calculatePositiveDivergenceStrict() {
//        Rule refIsRising = new IsRisingRule(ref, barCount);
//        Rule otherIsFalling = new IsFallingRule(ref, barCount);
//
//        return (refIsRising.and(otherIsFalling)).isSatisfied();
//    }
//
//    /**
//     * @return true, if negative divergent
//     */
//    private Boolean calculateNegativeDivergenceStrict() {
//        Rule refIsFalling = new IsFallingRule(ref, barCount);
//        Rule otherIsRising = new IsRisingRule(ref, barCount);
//
//        return (refIsFalling.and(otherIsRising)).isSatisfied();
//    }
//
//    /**
//     * @return true, if positive convergent
//     */
//    private Boolean calculatePositiveConvergence() {
//        CorrelationCoefficientIndicator cc = new CorrelationCoefficientIndicator(ref, other, barCount);
//        boolean isConvergent = cc.getValue().isGreaterThanOrEqual(minStrength);
//
//        Num slope = calculateSlopeRel();
//        boolean isPositive = slope.isGreaterThanOrEqual(minSlope.abs());
//
//        return isConvergent && isPositive;
//    }
//
//    /**
//     * @return true, if negative convergent
//     */
//    private Boolean calculateNegativeConvergence() {
//        CorrelationCoefficientIndicator cc = new CorrelationCoefficientIndicator(ref, other, barCount);
//        boolean isConvergent = cc.getValue().isGreaterThanOrEqual(minStrength);
//
//        Num slope = calculateSlopeRel();
//        boolean isNegative = slope
//                .isLessThanOrEqual(minSlope.abs().multipliedBy(getBarSeries().numFactory().minusOne()));
//
//        return isConvergent && isNegative;
//    }
//
//    /**
//     * @return true, if positive divergent
//     */
//    private Boolean calculatePositiveDivergence() {
//
//        CorrelationCoefficientIndicator cc = new CorrelationCoefficientIndicator(ref, other, barCount);
//        boolean isDivergent = cc.getValue()
//                .isLessThanOrEqual(minStrength.multipliedBy(getBarSeries().numFactory().minusOne()));
//
//        if (isDivergent) {
//            // If "isDivergent" and "ref" is positive, then "other" must be negative.
//            Num slope = calculateSlopeRel();
//            return slope.isGreaterThanOrEqual(minSlope.abs());
//        }
//
//        return false;
//    }
//
//    /**
//     * @return true, if negative divergent
//     */
//    private Boolean calculateNegativeDivergence() {
//
//        CorrelationCoefficientIndicator cc = new CorrelationCoefficientIndicator(ref, other, barCount);
//        boolean isDivergent = cc.getValue()
//                .isLessThanOrEqual(minStrength.multipliedBy(getBarSeries().numFactory().numOf(-1)));
//
//        if (isDivergent) {
//            // If "isDivergent" and "ref" is positive, then "other" must be negative.
//            Num slope = calculateSlopeRel();
//            return slope.isLessThanOrEqual(minSlope.abs().multipliedBy(getBarSeries().numFactory().numOf(-1)));
//        }
//
//        return false;
//    }
//
//    /**
//     * @return the relative slope
//     */
//    private Num calculateSlopeRel() {
//        return null;
//        // TODO
//        //        var slrRef = new SimpleLinearRegressionIndicator(ref, barCount);
//        //        int firstIndex = Math.max(0, index - barCount + 1);
//        //        return (slrRef.getValue(index).minus(slrRef.getValue(firstIndex))).dividedBy(slrRef.getValue(index));
//    }
//
//}
