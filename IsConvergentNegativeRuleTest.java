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

public class IsConvergentNegativeRuleTest {

	private Indicator<Decimal> ref;
	private Indicator<Decimal> other;
	private IsConvergentNegativeRule isNegCon;
	private IsConvergentNegativeRule isNegConStrict;
	

	@Before
	public void setUp() {
		ref = new FixedDecimalIndicator(3, 2, 1, 0, 2, 5, 7, 8, -2, 1);
		other = new FixedDecimalIndicator(3, 2, 1, 0, 2, 5, 7, 5, 3, 2);
		isNegCon = new IsConvergentNegativeRule(ref, other, 3, false);
		isNegConStrict = new IsConvergentNegativeRule(ref, other, 3, true);
	}

	@Test
	public void isSatisfied() {
		assertFalse(isNegConStrict.isSatisfied(0));
		assertFalse(isNegConStrict.isSatisfied(1));
		assertTrue(isNegConStrict.isSatisfied(2));
		assertTrue(isNegConStrict.isSatisfied(3));
		
		assertFalse(isNegCon.isSatisfied(4));
		assertFalse(isNegCon.isSatisfied(5));
		assertFalse(isNegCon.isSatisfied(6));
		
		assertFalse(isNegCon.isSatisfied(7));
		assertTrue(isNegCon.isSatisfied(8));
		assertTrue(isNegCon.isSatisfied(9));
	}
}
