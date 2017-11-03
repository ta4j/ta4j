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
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.HMAIndicator;
import org.ta4j.core.indicators.statistics.CorrelationCoefficientIndicator;
import org.ta4j.core.trading.rules.IsFallingRule;
import org.ta4j.core.trading.rules.IsHighestRule;
import org.ta4j.core.trading.rules.IsRisingRule;

/**
 * Indicator-convergence-divergence.
 * <p>
 * - Returns true for <b>"positiveConvergent"</b> when the values of the
 * ref-{@link Indicator indicator} and the values of the other-{@link Indicator
 * indicator} increase within the timeFrame. In short: "other" and "ref" makes
 * higher highs.
 * 
 * <p>
 * - Returns true for <b>"negativeConvergent"</b> when the values of the
 * ref-{@link Indicator indicator} and the values of the other-{@link Indicator
 * indicator} decrease within the timeFrame. In short: "other" and "ref" makes
 * lower lows.
 * 
 * <p>
 * - Returns true for <b>"positiveDivergent"</b> when the values of the
 * ref-{@link Indicator indicator} increase and the values of the
 * other-{@link Indicator indicator} decrease within a timeFrame. In short:
 * "other" makes lower lows while "ref" makes higher lows.
 * 
 * <p>
 * - Returns true for <b>"negativeDivergent"</b> when the values of the
 * ref-{@link Indicator indicator} decrease and the values of the
 * other-{@link Indicator indicator} increase within a timeFrame. In short:
 * "other" makes higher highs while "ref" makes lower highs.
 */
public class ConvergenceDivergenceIndicator extends CachedIndicator<Boolean> {

	private static final long serialVersionUID = -6735646430246479066L;

	/**
	 * Select the type of convergence or divergence.
	 */
	public enum ConvergenceDivergenceType {
		positiveConvergent, 
		negativeConvergent, 
		positiveDivergent, 
		negativeDivergent,
		positiveConvergentStrict, 
		negativeConvergentStrict, 
		positiveDivergentStrict, 
		negativeDivergentStrict;
	}

	/** The actual indicator. */
	private final Indicator<Decimal> ref;

	/** The other indicator. */
	private final Indicator<Decimal> other;

	/** The timeFrame. */
	private final int timeFrame;
	
	/** The type of the convergence or divergence **/
	private final ConvergenceDivergenceType type;
	
	/** The minimum strenght for convergence. **/
	private Decimal minStrenght;
	
	
    
	/**
	 * Constructor. <br/>
	 * <br/>
	 * 
	 * The <b>"minStrenght"</b> is the minimum required strenght for convergence or divergence
	 * and must be a number between "0.1" and "1.0": <br/>
	 * <br/>
	 * 0.1: very weak <br/>
	 * 0.8: strong (recommended) <br/>
	 * 1.0: very strong/strict <br/>
	 * 
	 * @param ref the indicator
	 * @param other the other indicator
	 * @param timeFrame
	 * @param type of convergence or divergence
	 * @param minStrenght the minimum required strenght for convergence or divergence
	 */
	public ConvergenceDivergenceIndicator(Indicator<Decimal> ref, Indicator<Decimal> other, int timeFrame,
			ConvergenceDivergenceType type, double minStrenght) {
		super(ref);
		this.ref = ref;
		this.other = other;
		this.timeFrame = timeFrame;
		this.type = type;
		this.minStrenght = Decimal.valueOf(minStrenght).abs();
	}
	
	/**
	 * Constructor for strong convergence or divergence.
	 * 
	 * @param ref the indicator
	 * @param other the other indicator
	 * @param timeFrame
	 * @param ype of convergence or divergence
	 */
	public ConvergenceDivergenceIndicator(Indicator<Decimal> ref, Indicator<Decimal> other, int timeFrame,
			ConvergenceDivergenceType type) {
		super(ref);
		this.ref = ref;
		this.other = other;
		this.timeFrame = timeFrame;
		this.type = type;
		this.minStrenght = Decimal.valueOf(0.8).abs();
	}

	@Override
	protected Boolean calculate(int index) {

		if (minStrenght.isZero()) {
			return false;
		}

		if (minStrenght.isGreaterThan(Decimal.ONE)) {
			minStrenght = Decimal.ONE;
		}

		CorrelationCoefficientIndicator cc = new CorrelationCoefficientIndicator(ref, other, timeFrame);

		switch (type) {
		case positiveConvergent:
			return calculatePositiveConvergence(cc, index);
		case negativeConvergent:
			return calculateNegativeConvergence(cc, index);
		case positiveDivergent:
			return calculatePositiveDivergence(cc, index);
		case negativeDivergent:
			return calculateNegativeDivergence(cc, index);
		case positiveConvergentStrict:
			return calculatePositiveConvergenceStrict(timeFrame, index);
		case negativeConvergentStrict:
			return calculateNegativeConvergenceStrict(timeFrame, index);
		case positiveDivergentStrict:
			return calculatePositiveDivergenceStrict(timeFrame, index);
		case negativeDivergentStrict:
			return calculateNegativeDivergenceStrict(timeFrame, index);
		default:
			return false;
		}
	}
	
	/**
	 * @param timeFrame
	 * @param index
	 * @return true, if strict positive convergent
	 */
	private Boolean calculatePositiveConvergenceStrict(int timeFrame, int index) {
		Rule refIsRising = new IsRisingRule(ref, timeFrame);
		Rule otherIsRising = new IsRisingRule(ref, timeFrame);

		return (refIsRising.and(otherIsRising)).isSatisfied(index);
	}

	/**
	 * @param timeFrame
	 * @param index
	 * @return true, if strict negative convergent
	 */
	private Boolean calculateNegativeConvergenceStrict(int timeFrame, int index) {
		Rule refIsFalling = new IsFallingRule(ref, timeFrame);
		Rule otherIsFalling = new IsFallingRule(ref, timeFrame);

		return (refIsFalling.and(otherIsFalling)).isSatisfied(index);
	}

	/**
	 * @param timeFrame
	 * @param index
	 * @return true, if positive divergent
	 */
	private Boolean calculatePositiveDivergenceStrict(int timeFrame, int index) {
		Rule refIsRising = new IsRisingRule(ref, timeFrame);
		Rule otherIsFalling = new IsFallingRule(ref, timeFrame);

		return (refIsRising.and(otherIsFalling)).isSatisfied(index);
	}

	/**
	 * @param timeFrame
	 * @param index
	 * @return true, if negative divergent
	 */
	private Boolean calculateNegativeDivergenceStrict(int timeFrame, int index) {
		Rule refIsFalling = new IsFallingRule(ref, timeFrame);
		Rule otherIsRising = new IsRisingRule(ref, timeFrame);

		return (refIsFalling.and(otherIsRising)).isSatisfied(index);
	}
    
    /**
     * 
     * @param cc the correlation coefficient
     * @param ma the comparable indicator to test for positive-part of the convergence
     * @param index
     * @return true, if positive convergent
     */
	private Boolean calculatePositiveConvergence(CorrelationCoefficientIndicator cc, int index) {
		boolean isConvergent = cc.getValue(index).isGreaterThanOrEqual(minStrenght);
		
		HMAIndicator hma = new HMAIndicator(ref, timeFrame);
		boolean isPositive = ref.getValue(index).isGreaterThan(hma.getValue(index));
		
		return isConvergent && isPositive;
	}
	

    /**
     * TODO: check logic
     * 
     * @param cc the correlation coefficient
     * @param ma the comparable indicator to test for negative-part of the convergence
     * @param index
     * @return true, if negative convergent
     */
    private Boolean calculateNegativeConvergence(CorrelationCoefficientIndicator cc, int index) {
    		boolean isConvergent = cc.getValue(index).isGreaterThanOrEqual(minStrenght);
		
    		HMAIndicator hma = new HMAIndicator(ref, timeFrame);
    		boolean isNegative = ref.getValue(index).isLessThan(hma.getValue(index));
		
		return isConvergent && isNegative;
    }
	
    /**
     * TODO: check logic
     * 
     * @param cc the correlation coefficient
     * @param index
     * @return true, if positive divergent
     */
	private Boolean calculatePositiveDivergence(CorrelationCoefficientIndicator cc, int index) {
		
		boolean isDivergent = cc.getValue(index).isLessThanOrEqual(minStrenght.multipliedBy(Decimal.valueOf(-1)));

		if (isDivergent) {
			HMAIndicator hmaRef = new HMAIndicator(ref, timeFrame);
			// if "isDivergent" and "ref" is positive, then "other" must be negative.
			boolean isRefPositive = ref.getValue(index).isGreaterThan(hmaRef.getValue(index));

			// higher peak in the "ref" against lower lows in "other"
			boolean refIsHighest = new IsHighestRule(ref, timeFrame).isSatisfied(index);
			boolean otherIsNotHighest = new IsHighestRule(other, timeFrame).negation().isSatisfied(index);

			return isRefPositive && refIsHighest && otherIsNotHighest;
		}

		return false;
	}
	
	/**
     * 
     * @param cc the correlation coefficient
     * @param ma the comparable indicator to test for negative-part of the divergence
     * @param index
     * @return true, if negative divergent
     */
	private Boolean calculateNegativeDivergence(CorrelationCoefficientIndicator cc, int index) {
		
		boolean isDivergent = cc.getValue(index).isLessThanOrEqual(minStrenght.multipliedBy(Decimal.valueOf(-1)));

		if (isDivergent) {
			HMAIndicator hmaRef = new HMAIndicator(ref, timeFrame);
			// if "isDivergent" and "ref" is negative, then "other" must be positive.
			boolean isRefNegative = ref.getValue(index).isLessThan(hmaRef.getValue(index));

			// A lower peak in the ref against higher highs in other.
			boolean refIsNotHighest = new IsHighestRule(ref, timeFrame).negation().isSatisfied(index);
			boolean otherIsHighest = new IsHighestRule(other, timeFrame).isSatisfied(index);

			return isRefNegative && refIsNotHighest && otherIsHighest;
		}

		return false;
	}
}
