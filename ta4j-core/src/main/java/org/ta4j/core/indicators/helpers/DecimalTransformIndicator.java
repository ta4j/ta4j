/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Simple decimal transform indicator.
 * </p>
 * @apiNote Minimal deviations in last decimal places possible. During the calculations this indicator converts {@link Num Decimal/BigDecimal} to to {@link Double double}
 * Transforms any indicator by using common math operations.
 */
public class DecimalTransformIndicator extends CachedIndicator<Num> {

	private static final long serialVersionUID = -8017034587193428498L;

	/**
	 * Select the type for transformation.
	 */
	public enum DecimalTransformType {

		/**
		 * Transforms the input indicator 
		 * by indicator.plus(coefficient).
		 */
		plus,

		/**
		 * Transforms the input indicator 
		 * by indicator.minus(coefficient).
		 */
		minus,

		/**
		 * Transforms the input indicator by
		 * indicator.multipliedBy(coefficient).
		 */
		multiply,

		/**
		 * Transforms the input indicator 
		 * by indicator.dividedBy(coefficient).
		 */
		divide,

		/**
		 * Transforms the input indicator 
		 * by indicator.max(coefficient).
		 */
		max,

		/**
		 * Transforms the input indicator 
		 * by indicator.min(coefficient).
		 */
		min
	}

	/**
	 * Select the type for transformation.
	 */
	public enum DecimalTransformSimpleType {
		/**
		 * Transforms the input indicator 
		 * by indicator.abs().
		 */
		abs,

		/**
		 * Transforms the input indicator 
		 * by indicator.sqrt().
		 */
		sqrt,

		/**
		 * Transforms the input indicator 
		 * by indicator.log().
		 */
		log
	}

	private Indicator<Num> indicator;
	private Num coefficient;
	private DecimalTransformType type;
	private DecimalTransformSimpleType simpleType;

	/**
	 * Constructor.
	 * 
	 * @param indicator the indicator
	 * @param coefficient the value for transformation
	 * @param type the type of the transformation
	 */
	public DecimalTransformIndicator(Indicator<Num> indicator, double coefficient, DecimalTransformType type) {
		super(indicator);
		this.indicator = indicator;
		this.coefficient = numOf(coefficient);
		this.type = type;
	}

	/**
	 * Constructor.
	 * 
	 * @param indicator the indicator
	 * @param type the type of the transformation
	 */
	public DecimalTransformIndicator(Indicator<Num> indicator, DecimalTransformSimpleType type) {
		super(indicator);
		this.indicator = indicator;
		this.simpleType = type;
	}

	@Override
	protected Num calculate(int index) {

		Num val = indicator.getValue(index);

		if (type != null) {
			switch (type) {
			case plus:
				return val.plus(coefficient);
			case minus:
				return val.minus(coefficient);
			case multiply:
				return val.multipliedBy(coefficient);
			case divide:
				return val.dividedBy(coefficient);
			case max:
				return val.max(coefficient);
			case min:
				return val.min(coefficient);
			default:
				break;
			}
		}

		else if (simpleType != null) {
			switch (simpleType) {
			case sqrt:
				return numOf(Math.sqrt(val.doubleValue()));
			case abs:
				return val.abs();
			case log:
				return numOf(Math.log(val.doubleValue()));
			default:
				break;
			}
		}

		return val;
	}

	@Override
	public String toString() {
		if (type != null) {
			return getClass().getSimpleName() + " Coefficient: " + coefficient + " Transform(" + type.name() + ")";
		}
		return getClass().getSimpleName() + "Transform(" + simpleType.name() + ")";
	}
}
