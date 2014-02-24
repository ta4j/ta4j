/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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
package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.strategies.DistanceBetweenIndicatorsStrategy;
import eu.verdelhan.ta4j.strategies.AbstractStrategy;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class DistanceBetweenIndicatorsStrategyTest {
	private MockIndicator<Double> upper;

	private MockIndicator<Double> lower;

	private AbstractStrategy distanceEnter;

	@Before
	public void setUp() {
		upper = new MockIndicator<Double>(new Double[] { 30d, 32d, 33d, 32d, 35d, 33d, 32d });
		lower = new MockIndicator<Double>(new Double[] { 10d, 10d, 10d, 12d, 14d, 15d, 15d });
		distanceEnter = new DistanceBetweenIndicatorsStrategy(upper, lower, 20, 0.1);
	}

	@Test
	public void testStrategyIsBuyingCorreclty() {
		Trade trade = new Trade();

		assertThat(distanceEnter.shouldOperate(trade, 0)).isFalse();
		assertThat(distanceEnter.shouldOperate(trade, 1)).isTrue();
		trade = new Trade();
		assertThat(distanceEnter.shouldOperate(trade, 2)).isTrue();
		assertThat(distanceEnter.shouldOperate(trade, 3)).isFalse();
		assertThat(distanceEnter.shouldOperate(trade, 4)).isFalse();
	}

	@Test
	public void testStrategyIsSellingCorrectly() {
		Trade trade = new Trade();
		trade.operate(2);

		assertThat(distanceEnter.shouldOperate(trade, 0)).isFalse();
		assertThat(distanceEnter.shouldOperate(trade, 5)).isTrue();

		trade = new Trade();
		trade.operate(2);

		assertThat(distanceEnter.shouldOperate(trade, 6)).isTrue();
		assertThat(distanceEnter.shouldOperate(trade, 3)).isFalse();
		assertThat(distanceEnter.shouldOperate(trade, 4)).isFalse();

	}
}
