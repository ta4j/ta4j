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
import org.ta4j.core.indicators.helpers.ConvergenceDivergenceIndicator.ConvergenceDivergenceType;

import org.ta4j.core.indicators.helpers.FixedDecimalIndicator;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class ConvergenceDivergenceIndicatorTest {

	private Indicator<Decimal> refPosCon;
	private Indicator<Decimal> otherPosCon;

	private Indicator<Decimal> refNegCon;
	private Indicator<Decimal> otherNegCon;
	
	private Indicator<Decimal> refDiv;
	private Indicator<Decimal> otherDiv;

	private ConvergenceDivergenceIndicator isNegCon;
	private ConvergenceDivergenceIndicator isPosCon;
	private ConvergenceDivergenceIndicator isPosDiv;
	private ConvergenceDivergenceIndicator isNegDiv;

	@Before
	public void setUp() {
		refPosCon = new FixedDecimalIndicator(1, 2, 3, 4, 5, 8, 3, 2, -2, 1);
		otherPosCon = new FixedDecimalIndicator(10, 20, 30, 40, 50, 60, 7, 5, 3, 2);

		refNegCon = new FixedDecimalIndicator(50, 20, 10, 0, -2, -6, -20, -1, -2, 1);
		otherNegCon = new FixedDecimalIndicator(80, 50, 40, 30, 10, 0, -3, 2, 5, 7);
		
		refDiv = new FixedDecimalIndicator(1, 4, 8, 12, 15, 20, 3, 2, -2, 1);
		otherDiv = new FixedDecimalIndicator(80, 50, 20, -10, 0, -100, -200, -2, 5, 7);

		isPosCon = new ConvergenceDivergenceIndicator(refPosCon, otherPosCon, 3, 
				ConvergenceDivergenceType.positiveConvergent);
		
		isNegCon = new ConvergenceDivergenceIndicator(refNegCon, otherNegCon, 3,
				ConvergenceDivergenceType.negativeConvergent);
		
		isPosDiv = new ConvergenceDivergenceIndicator(refDiv, otherDiv, 3, 
				 ConvergenceDivergenceType.positiveDivergent);
		 
		isNegDiv = new ConvergenceDivergenceIndicator(otherDiv, refDiv, 3,
				 ConvergenceDivergenceType.negativeDivergent);
	}

	@Test
	public void isSatisfied() {

		// positive convergence
		assertFalse(isPosCon.getValue(0));
		assertFalse(isPosCon.getValue(1));
		assertFalse(isPosCon.getValue(2));
		assertFalse(isPosCon.getValue(3));
		assertFalse(isPosCon.getValue(4));
		assertFalse(isPosCon.getValue(5));
		assertTrue(isPosCon.getValue(6));
		assertTrue(isPosCon.getValue(7));
		assertTrue(isPosCon.getValue(8));
		assertFalse(isPosCon.getValue(9));
		
		// negative convergence
		assertFalse(isNegCon.getValue(0));
		assertFalse(isNegCon.getValue(1));
		assertFalse(isNegCon.getValue(2));
		assertFalse(isNegCon.getValue(3));
		assertFalse(isNegCon.getValue(4));
		assertFalse(isNegCon.getValue(5));
		assertFalse(isNegCon.getValue(6));
		assertTrue(isNegCon.getValue(7));
		assertTrue(isNegCon.getValue(8));
		assertFalse(isNegCon.getValue(9));

		// TODO: positive divergence
		assertFalse(isPosDiv.getValue(0));
		assertFalse(isPosDiv.getValue(1));
		assertFalse(isPosDiv.getValue(2));
		assertFalse(isPosDiv.getValue(3));
		assertFalse(isPosDiv.getValue(4));
		assertFalse(isPosDiv.getValue(5));
		assertFalse(isPosDiv.getValue(6));
		assertFalse(isPosDiv.getValue(7));
		assertFalse(isPosDiv.getValue(8));
		assertFalse(isPosDiv.getValue(9));
		
		//  TODO: negative divergence
		assertFalse(isNegDiv.getValue(0));
		assertFalse(isNegDiv.getValue(1));
		assertFalse(isNegDiv.getValue(2));
		assertFalse(isNegDiv.getValue(3));
		assertFalse(isNegDiv.getValue(4));
		assertFalse(isNegDiv.getValue(5));
		assertFalse(isNegDiv.getValue(6));
		assertFalse(isNegDiv.getValue(7));
		assertFalse(isNegDiv.getValue(8));
		assertFalse(isNegDiv.getValue(9));
	}
}
