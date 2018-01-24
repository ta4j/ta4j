/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.statistics.CorrelationCoefficientIndicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator;
import org.ta4j.core.trading.rules.IsFallingRule;
import org.ta4j.core.trading.rules.IsRisingRule;

/**
 * Indicator-convergence-divergence.
 */
public class ConvergenceDivergenceIndicator extends CachedIndicator<Boolean> {

	private static final long serialVersionUID = -6735646430246479066L;

	/**
	 * Select the type of convergence or divergence.
	 */
	public enum ConvergenceDivergenceType {
		/**
		 * Returns true for <b>"positiveConvergent"</b> when the values of the
		 * ref-{@link Indicator indicator} and the values of the
		 * other-{@link Indicator indicator} increase within the timeFrame. In
		 * short: "other" and "ref" makes higher highs.
		 */
		positiveConvergent, 
		
		/**
		 * Returns true for <b>"negativeConvergent"</b> when the values of the
		 * ref-{@link Indicator indicator} and the values of the
		 * other-{@link Indicator indicator} decrease within the timeFrame. In
		 * short: "other" and "ref" makes lower lows.
		 */
		negativeConvergent, 
		
		/**
		 * Returns true for <b>"positiveDivergent"</b> when the values of the
		 * ref-{@link Indicator indicator} increase and the values of the
		 * other-{@link Indicator indicator} decrease within a timeFrame. In
		 * short: "other" makes lower lows while "ref" makes higher highs.
		 */
		positiveDivergent, 
		
		/**
		 * Returns true for <b>"negativeDivergent"</b> when the values of the
		 * ref-{@link Indicator indicator} decrease and the values of the
		 * other-{@link Indicator indicator} increase within a timeFrame. In
		 * short: "other" makes higher highs while "ref" makes lower lows.
		 */
		negativeDivergent
	}
	
	/**
	 * Select the type of strict convergence or divergence.
	 */
	public enum ConvergenceDivergenceStrictType {

		/**
		 * Returns true for <b>"positiveConvergentStrict"</b> when the values of
		 * the ref-{@link Indicator indicator} and the values of the
		 * other-{@link Indicator indicator} increase consecutively within a
		 * timeFrame. In short: "other" and "ref" makes strict higher highs.
		 */
		positiveConvergentStrict,
		
		/**
		 * Returns true for <b>"negativeConvergentStrict"</b> when the values of
		 * the ref-{@link Indicator indicator} and the values of the
		 * other-{@link Indicator indicator} decrease consecutively within a
		 * timeFrame. In short: "other" and "ref" makes strict lower lows.
		 */
		negativeConvergentStrict, 
		
		/**
		 * Returns true for <b>"positiveDivergentStrict"</b> when the values of
		 * the ref-{@link Indicator indicator} increase consecutively and the
		 * values of the other-{@link Indicator indicator} decrease
		 * consecutively within a timeFrame. In short: "other" makes strict
		 * higher highs and "ref" makes strict lower lows.
		 */
		positiveDivergentStrict, 
		
		/**
		 * Returns true for <b>"negativeDivergentStrict"</b> when the values of
		 * the ref-{@link Indicator indicator} decrease consecutively and the
		 * values of the other-{@link Indicator indicator} increase
		 * consecutively within a timeFrame. In short: "other" makes strict
		 * lower lows and "ref" makes strict higher highs.
		 */
		negativeDivergentStrict
	}


	/** The actual indicator. */
	private final Indicator<Decimal> ref;

	/** The other indicator. */
	private final Indicator<Decimal> other;

	/** The timeFrame. */
	private final int timeFrame;
	
	/** The type of the convergence or divergence **/
	private final ConvergenceDivergenceType type;
	
	/** The type of the strict convergence or strict divergence **/
	private final ConvergenceDivergenceStrictType strictType;
	
	/** The minimum strenght for convergence or divergence. **/
	private Decimal minStrenght;
	
	/** The minimum slope for convergence or divergence. **/
	private Decimal minSlope;
    
	/**
	 * Constructor. <br/>
	 * <br/>
	 * 
	 * The <b>"minStrenght"</b> is the minimum required strenght for convergence or divergence
	 * and must be a number between "0.1" and "1.0": <br/>
	 * <br/>
	 * 0.1: very weak <br/>
	 * 0.8: strong (recommended) <br/>
	 * 1.0: very strong <br/>
	 * 
	 * <br/>
	 * 
	 * The <b>"minSlope"</b> is the minimum required slope for convergence or divergence
	 * and must be a number between "0.1" and "1.0": <br/>
	 * <br/>
	 * 0.1: very unstrict<br/>
	 * 0.3: strict (recommended) <br/>
	 * 1.0: very strict <br/>
	 * 
	 * @param ref the indicator
	 * @param other the other indicator
	 * @param timeFrame the time frame
	 * @param type of convergence or divergence
	 * @param minStrenght the minimum required strenght for convergence or divergence
	 * @param minSlope the minimum required slope for convergence or divergence
	 */
	public ConvergenceDivergenceIndicator(Indicator<Decimal> ref, Indicator<Decimal> other, int timeFrame,
			ConvergenceDivergenceType type, double minStrenght, double minSlope) {
		super(ref);
		this.ref = ref;
		this.other = other;
		this.timeFrame = timeFrame;
		this.type = type;
		this.strictType = null;
		this.minStrenght = Decimal.valueOf(minStrenght).abs();
		this.minSlope = Decimal.valueOf(minSlope);
	}
	
	/**
	 * Constructor for strong convergence or divergence.
	 * 
	 * @param ref the indicator
	 * @param other the other indicator
	 * @param timeFrame the time frame
	 * @param type of convergence or divergence
	 */
	public ConvergenceDivergenceIndicator(Indicator<Decimal> ref, Indicator<Decimal> other, int timeFrame,
			ConvergenceDivergenceType type) {
		super(ref);
		this.ref = ref;
		this.other = other;
		this.timeFrame = timeFrame;
		this.type = type;
		this.strictType = null;
		this.minStrenght = Decimal.valueOf(0.8).abs();
		this.minSlope = Decimal.valueOf(0.3);
	}
	
	/**
	 * Constructor for strict convergence or divergence.
	 * 
	 * @param ref the indicator
	 * @param other the other indicator
	 * @param timeFrame the time frame
	 */
	public ConvergenceDivergenceIndicator(Indicator<Decimal> ref, Indicator<Decimal> other, int timeFrame,
			ConvergenceDivergenceStrictType strictType) {
		super(ref);
		this.ref = ref;
		this.other = other;
		this.timeFrame = timeFrame;
		this.type = null;
		this.strictType = strictType;
		this.minStrenght = null;
		this.minSlope = null;
	}

	@Override
	protected Boolean calculate(int index) {

		if (minStrenght.isZero()) {
			return false;
		}

		if (minStrenght.isGreaterThan(Decimal.ONE)) {
			minStrenght = Decimal.ONE;
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
	
	/**
	 * @param index the actual index
	 * @return true, if strict positive convergent
	 */
	private Boolean calculatePositiveConvergenceStrict(int index) {
		Rule refIsRising = new IsRisingRule(ref, timeFrame);
		Rule otherIsRising = new IsRisingRule(ref, timeFrame);

		return (refIsRising.and(otherIsRising)).isSatisfied(index);
	}

	/**
	 * @param index the actual index
	 * @return true, if strict negative convergent
	 */
	private Boolean calculateNegativeConvergenceStrict(int index) {
		Rule refIsFalling = new IsFallingRule(ref, timeFrame);
		Rule otherIsFalling = new IsFallingRule(ref, timeFrame);

		return (refIsFalling.and(otherIsFalling)).isSatisfied(index);
	}

	/**
	 * @param index the actual index
	 * @return true, if positive divergent
	 */
	private Boolean calculatePositiveDivergenceStrict(int index) {
		Rule refIsRising = new IsRisingRule(ref, timeFrame);
		Rule otherIsFalling = new IsFallingRule(ref, timeFrame);

		return (refIsRising.and(otherIsFalling)).isSatisfied(index);
	}

	/**
	 * @param index the actual index
	 * @return true, if negative divergent
	 */
	private Boolean calculateNegativeDivergenceStrict(int index) {
		Rule refIsFalling = new IsFallingRule(ref, timeFrame);
		Rule otherIsRising = new IsRisingRule(ref, timeFrame);

		return (refIsFalling.and(otherIsRising)).isSatisfied(index);
	}
    
    /**
     * @param index the actual index
     * @return true, if positive convergent
     */
	private Boolean calculatePositiveConvergence(int index) {
		CorrelationCoefficientIndicator cc = new CorrelationCoefficientIndicator(ref, other, timeFrame);
		boolean isConvergent = cc.getValue(index).isGreaterThanOrEqual(minStrenght);

		Decimal slope = calculateSlopeRel(index);
		boolean isPositive = slope.isGreaterThanOrEqual(minSlope.abs());

		return isConvergent && isPositive;
	}
	

    /**
     * @param index the actual index
     * @return true, if negative convergent
     */
    private Boolean calculateNegativeConvergence(int index) {
    		CorrelationCoefficientIndicator cc = new CorrelationCoefficientIndicator(ref, other, timeFrame);
    		boolean isConvergent = cc.getValue(index).isGreaterThanOrEqual(minStrenght);
		
    		Decimal slope = calculateSlopeRel(index);
    		boolean isNegative = slope.isLessThanOrEqual(minSlope.abs().multipliedBy(Decimal.valueOf(-1)));
		
		return isConvergent && isNegative;
    }
	
    /**
     * @param index the actual index
     * @return true, if positive divergent
     */
	private Boolean calculatePositiveDivergence(int index) {
		
		CorrelationCoefficientIndicator cc = new CorrelationCoefficientIndicator(ref, other, timeFrame);
		boolean isDivergent = cc.getValue(index).isLessThanOrEqual(minStrenght.multipliedBy(Decimal.valueOf(-1)));

		if (isDivergent) {
			// If "isDivergent" and "ref" is positive, then "other" must be negative.
			Decimal slope = calculateSlopeRel(index);
			return slope.isGreaterThanOrEqual(minSlope.abs());
		}

		return false;
	}
	
	
	/**
     * @param index the actual index
     * @return true, if negative divergent
     */
	private Boolean calculateNegativeDivergence(int index) {
		
		CorrelationCoefficientIndicator cc = new CorrelationCoefficientIndicator(ref, other, timeFrame);
		boolean isDivergent = cc.getValue(index).isLessThanOrEqual(minStrenght.multipliedBy(Decimal.valueOf(-1)));

		if (isDivergent) {
			// If "isDivergent" and "ref" is positive, then "other" must be negative.
			Decimal slope = calculateSlopeRel(index);
			return slope.isLessThanOrEqual(minSlope.abs().multipliedBy(Decimal.valueOf(-1)));
		}

		return false;
	}
	
	/**
	 * @param index the actual index
	 * @return the absolute slope
	 */
	private Decimal calculateSlopeAbs(int index) {
		SimpleLinearRegressionIndicator slrRef = new SimpleLinearRegressionIndicator(ref, timeFrame);
		int firstIndex = Math.max(0, index - timeFrame + 1);
		return (slrRef.getValue(index).minus(slrRef.getValue(firstIndex)))
				.dividedBy(Decimal.valueOf(timeFrame).minus(Decimal.valueOf(firstIndex)));
	}
	
	/**
	 * @param index the actual index
	 * @return the relative slope
	 */
	private Decimal calculateSlopeRel(int index) {
		SimpleLinearRegressionIndicator slrRef = new SimpleLinearRegressionIndicator(ref, timeFrame);
		int firstIndex = Math.max(0, index - timeFrame + 1);
		return (slrRef.getValue(index).minus(slrRef.getValue(firstIndex)))
				.dividedBy(slrRef.getValue(index));
	}
	
}
