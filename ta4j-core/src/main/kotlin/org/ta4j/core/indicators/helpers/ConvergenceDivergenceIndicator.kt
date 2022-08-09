/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2022 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.helpers

import org.ta4j.core.Indicator
import org.ta4j.core.Rule
import org.ta4j.core.indicators.CachedIndicator
import org.ta4j.core.indicators.statistics.CorrelationCoefficientIndicator
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator
import org.ta4j.core.num.*
import org.ta4j.core.rules.IsFallingRule
import org.ta4j.core.rules.IsRisingRule
import kotlin.math.max

/**
 * Indicator-convergence-divergence.
 */
class ConvergenceDivergenceIndicator : CachedIndicator<Boolean> {
    /**
     * Select the type of convergence or divergence.
     */
    enum class ConvergenceDivergenceType {
        /**
         * Returns true for **"positiveConvergent"** when the values of the
         * ref-[indicator][Indicator] and the values of the other-[ indicator][Indicator] increase within the barCount. In short: "other" and "ref" makes
         * higher highs.
         */
        positiveConvergent,

        /**
         * Returns true for **"negativeConvergent"** when the values of the
         * ref-[indicator][Indicator] and the values of the other-[ indicator][Indicator] decrease within the barCount. In short: "other" and "ref" makes
         * lower lows.
         */
        negativeConvergent,

        /**
         * Returns true for **"positiveDivergent"** when the values of the
         * ref-[indicator][Indicator] increase and the values of the
         * other-[indicator][Indicator] decrease within a barCount. In short:
         * "other" makes lower lows while "ref" makes higher highs.
         */
        positiveDivergent,

        /**
         * Returns true for **"negativeDivergent"** when the values of the
         * ref-[indicator][Indicator] decrease and the values of the
         * other-[indicator][Indicator] increase within a barCount. In short:
         * "other" makes higher highs while "ref" makes lower lows.
         */
        negativeDivergent
    }

    /**
     * Select the type of strict convergence or divergence.
     */
    enum class ConvergenceDivergenceStrictType {
        /**
         * Returns true for **"positiveConvergentStrict"** when the values of the
         * ref-[indicator][Indicator] and the values of the other-[ indicator][Indicator] increase consecutively within a barCount. In short: "other" and
         * "ref" makes strict higher highs.
         */
        positiveConvergentStrict,

        /**
         * Returns true for **"negativeConvergentStrict"** when the values of the
         * ref-[indicator][Indicator] and the values of the other-[ indicator][Indicator] decrease consecutively within a barCount. In short: "other" and
         * "ref" makes strict lower lows.
         */
        negativeConvergentStrict,

        /**
         * Returns true for **"positiveDivergentStrict"** when the values of the
         * ref-[indicator][Indicator] increase consecutively and the values of the
         * other-[indicator][Indicator] decrease consecutively within a barCount.
         * In short: "other" makes strict higher highs and "ref" makes strict lower
         * lows.
         */
        positiveDivergentStrict,

        /**
         * Returns true for **"negativeDivergentStrict"** when the values of the
         * ref-[indicator][Indicator] decrease consecutively and the values of the
         * other-[indicator][Indicator] increase consecutively within a barCount.
         * In short: "other" makes strict lower lows and "ref" makes strict higher
         * highs.
         */
        negativeDivergentStrict
    }

    /** The actual indicator.  */
    private val ref: Indicator<Num>

    /** The other indicator.  */
    private val other: Indicator<Num>

    /** The barCount.  */
    private val barCount: Int

    /** The type of the convergence or divergence  */
    private val type: ConvergenceDivergenceType?

    /** The type of the strict convergence or strict divergence  */
    private val strictType: ConvergenceDivergenceStrictType?

    /** The minimum strength for convergence or divergence.  */
    private var minStrength: Num?

    /** The minimum slope for convergence or divergence.  */
    private val minSlope: Num?

    /**
     * Constructor. <br></br>
     * <br></br>
     *
     * The **"minStrength"** is the minimum required strength for convergence or
     * divergence and must be a number between "0.1" and "1.0": <br></br>
     * <br></br>
     * 0.1: very weak <br></br>
     * 0.8: strong (recommended) <br></br>
     * 1.0: very strong <br></br>
     *
     * <br></br>
     *
     * The **"minSlope"** is the minimum required slope for convergence or
     * divergence and must be a number between "0.1" and "1.0": <br></br>
     * <br></br>
     * 0.1: very unstrict<br></br>
     * 0.3: strict (recommended) <br></br>
     * 1.0: very strict <br></br>
     *
     * @param ref         the indicator
     * @param other       the other indicator
     * @param barCount    the time frame
     * @param type        of convergence or divergence
     * @param minStrength the minimum required strength for convergence or
     * divergence
     * @param minSlope    the minimum required slope for convergence or divergence
     */
    constructor(
        ref: Indicator<Num>, other: Indicator<Num>, barCount: Int,
        type: ConvergenceDivergenceType?, minStrength: Double, minSlope: Double
    ) : super(ref) {
        this.ref = ref
        this.other = other
        this.barCount = barCount
        this.type = type
        strictType = null
        this.minStrength = numOf(minStrength).abs()
        this.minSlope = numOf(minSlope)
    }

    /**
     * Constructor for strong convergence or divergence.
     *
     * @param ref      the indicator
     * @param other    the other indicator
     * @param barCount the time frame
     * @param type     of convergence or divergence
     */
    constructor(
        ref: Indicator<Num>, other: Indicator<Num>, barCount: Int,
        type: ConvergenceDivergenceType?
    ) : super(ref) {
        this.ref = ref
        this.other = other
        this.barCount = barCount
        this.type = type
        strictType = null
        minStrength = numOf(0.8).abs()
        minSlope = numOf(0.3)
    }

    /**
     * Constructor for strict convergence or divergence.
     *
     * @param ref        the indicator
     * @param other      the other indicator
     * @param barCount   the time frame
     * @param strictType of strict convergence or divergence
     */
    constructor(
        ref: Indicator<Num>, other: Indicator<Num>, barCount: Int,
        strictType: ConvergenceDivergenceStrictType?
    ) : super(ref) {
        this.ref = ref
        this.other = other
        this.barCount = barCount
        type = null
        this.strictType = strictType
        minStrength = null
        minSlope = null
    }

    override fun calculate(index: Int): Boolean {
        if (minStrength != null && minStrength!!.isZero) {
            return false
        }
        if (minStrength != null && minStrength!!.isGreaterThan(numOf(1))) {
            minStrength = numOf(1)
        }
        if (type != null) {
            return when (type) {
                ConvergenceDivergenceType.positiveConvergent -> calculatePositiveConvergence(index)
                ConvergenceDivergenceType.negativeConvergent -> calculateNegativeConvergence(index)
                ConvergenceDivergenceType.positiveDivergent -> calculatePositiveDivergence(index)
                ConvergenceDivergenceType.negativeDivergent -> calculateNegativeDivergence(index)
            }
        } else if (strictType != null) {
            return when (strictType) {
                ConvergenceDivergenceStrictType.positiveConvergentStrict -> calculatePositiveConvergenceStrict(index)
                ConvergenceDivergenceStrictType.negativeConvergentStrict -> calculateNegativeConvergenceStrict(index)
                ConvergenceDivergenceStrictType.positiveDivergentStrict -> calculatePositiveDivergenceStrict(index)
                ConvergenceDivergenceStrictType.negativeDivergentStrict -> calculateNegativeDivergenceStrict(index)
            }
        }
        return false
    }

    /**
     * @param index the actual index
     * @return true, if strict positive convergent
     */
    private fun calculatePositiveConvergenceStrict(index: Int): Boolean {
        val refIsRising: Rule = IsRisingRule(ref, barCount)
        val otherIsRising: Rule = IsRisingRule(ref, barCount)
        return refIsRising.and(otherIsRising).isSatisfied(index)
    }

    /**
     * @param index the actual index
     * @return true, if strict negative convergent
     */
    private fun calculateNegativeConvergenceStrict(index: Int): Boolean {
        val refIsFalling: Rule = IsFallingRule(ref, barCount)
        val otherIsFalling: Rule = IsFallingRule(ref, barCount)
        return refIsFalling.and(otherIsFalling).isSatisfied(index)
    }

    /**
     * @param index the actual index
     * @return true, if positive divergent
     */
    private fun calculatePositiveDivergenceStrict(index: Int): Boolean {
        val refIsRising: Rule = IsRisingRule(ref, barCount)
        val otherIsFalling: Rule = IsFallingRule(ref, barCount)
        return refIsRising.and(otherIsFalling).isSatisfied(index)
    }

    /**
     * @param index the actual index
     * @return true, if negative divergent
     */
    private fun calculateNegativeDivergenceStrict(index: Int): Boolean {
        val refIsFalling: Rule = IsFallingRule(ref, barCount)
        val otherIsRising: Rule = IsRisingRule(ref, barCount)
        return refIsFalling.and(otherIsRising).isSatisfied(index)
    }

    /**
     * @param index the actual index
     * @return true, if positive convergent
     */
    private fun calculatePositiveConvergence(index: Int): Boolean {
        val cc = CorrelationCoefficientIndicator(ref, other, barCount)
        val isConvergent = cc[index].isGreaterThanOrEqual(minStrength)
        val slope = calculateSlopeRel(index)
        val isPositive = slope.isGreaterThanOrEqual(minSlope!!.abs())
        return isConvergent && isPositive
    }

    /**
     * @param index the actual index
     * @return true, if negative convergent
     */
    private fun calculateNegativeConvergence(index: Int): Boolean {
        val cc = CorrelationCoefficientIndicator(ref, other, barCount)
        val isConvergent = cc[index].isGreaterThanOrEqual(minStrength)
        val slope = calculateSlopeRel(index)
        val isNegative = slope.isLessThanOrEqual(minSlope!!.abs().times(numOf(-1)))
        return isConvergent && isNegative
    }

    /**
     * @param index the actual index
     * @return true, if positive divergent
     */
    private fun calculatePositiveDivergence(index: Int): Boolean {
        val cc = CorrelationCoefficientIndicator(ref, other, barCount)
        val isDivergent = cc[index].isLessThanOrEqual(minStrength!!.times(numOf(-1)))
        if (isDivergent) {
            // If "isDivergent" and "ref" is positive, then "other" must be negative.
            val slope = calculateSlopeRel(index)
            return slope.isGreaterThanOrEqual(minSlope!!.abs())
        }
        return false
    }

    /**
     * @param index the actual index
     * @return true, if negative divergent
     */
    private fun calculateNegativeDivergence(index: Int): Boolean {
        val cc = CorrelationCoefficientIndicator(ref, other, barCount)
        val isDivergent = cc[index].isLessThanOrEqual(minStrength!!.times(numOf(-1)))
        if (isDivergent) {
            // If "isDivergent" and "ref" is positive, then "other" must be negative.
            val slope = calculateSlopeRel(index)
            return slope.isLessThanOrEqual(minSlope!!.abs().times(numOf(-1)))
        }
        return false
    }

    /**
     * @param index the actual index
     * @return the relative slope
     */
    private fun calculateSlopeRel(index: Int): Num {
        val slrRef = SimpleLinearRegressionIndicator(ref, barCount)
        val firstIndex = max(0, index - barCount + 1)
        return slrRef[index].minus(slrRef.getValue(firstIndex)).div(slrRef[index])
    }
}