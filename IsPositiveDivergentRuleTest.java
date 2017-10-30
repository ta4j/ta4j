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
package org.ta4j.core.trading.rules;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.FixedDecimalIndicator;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class IsPositiveDivergentRuleTest {

	private Indicator<Decimal> ref;
	private Indicator<Decimal> other;
	private IsPositiveDivergentRule isPosDiv;
	private IsPositiveDivergentRule isPosDivStrict;
	

	@Before
	public void setUp() {
		ref = new FixedDecimalIndicator(1, 2, 3, 4, 5, 7, 0, 1, 8, 9);
		other = new FixedDecimalIndicator(3, 2, 1, 0, 5, 7, 0, 3, 2, 1);
		isPosDiv = new IsPositiveDivergentRule(ref, other, 3, false);
		isPosDivStrict = new IsPositiveDivergentRule(ref, other, 3, true);
	}

	@Test
	public void isSatisfied() {
		assertFalse(isPosDivStrict.isSatisfied(0));
		assertFalse(isPosDivStrict.isSatisfied(1));
		assertTrue(isPosDivStrict.isSatisfied(2));
		assertTrue(isPosDivStrict.isSatisfied(3));
		
		assertFalse(isPosDiv.isSatisfied(4));
		assertFalse(isPosDiv.isSatisfied(5));
		assertFalse(isPosDiv.isSatisfied(6));
		
		assertTrue(isPosDiv.isSatisfied(7));
		assertTrue(isPosDiv.isSatisfied(8));
		assertTrue(isPosDiv.isSatisfied(9));
	}
}
