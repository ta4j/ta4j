/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.mocks.MockDecimalIndicator;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.junit.Assert.*;
import org.junit.Test;

public class CombinedEntryAndExitStrategyTest {

    private Operation[] enter;

    private Operation[] exit;

    private MockStrategy buyStrategy;

    private MockStrategy sellStrategy;

    private CombinedEntryAndExitStrategy combined;

    @Test
    public void shouldEnter() {

        enter = new Operation[] { Operation.buyAt(0), null, Operation.buyAt(2), null, Operation.buyAt(4) };
        exit = new Operation[] { null, null, null, null, null };

        buyStrategy = new MockStrategy(enter, null);
        sellStrategy = new MockStrategy(null, exit);

        combined = new CombinedEntryAndExitStrategy(buyStrategy, sellStrategy);

        assertTrue(combined.shouldEnter(0));
        assertFalse(combined.shouldEnter(1));
        assertTrue(combined.shouldEnter(2));
        assertFalse(combined.shouldEnter(3));
        assertTrue(combined.shouldEnter(4));

        assertFalse(combined.shouldExit(0));
        assertFalse(combined.shouldExit(1));
        assertFalse(combined.shouldExit(2));
        assertFalse(combined.shouldExit(3));
        assertFalse(combined.shouldExit(4));

    }

    @Test
    public void shouldExit() {

        exit = new Operation[] { Operation.sellAt(0), null, Operation.sellAt(2), null, Operation.sellAt(4) };
        enter = new Operation[] { null, null, null, null, null };

        buyStrategy = new MockStrategy(enter, null);
        sellStrategy = new MockStrategy(null, exit);

        combined = new CombinedEntryAndExitStrategy(buyStrategy, sellStrategy);

        assertTrue(combined.shouldExit(0));
        assertFalse(combined.shouldExit(1));
        assertTrue(combined.shouldExit(2));
        assertFalse(combined.shouldExit(3));
        assertTrue(combined.shouldExit(4));

        assertFalse(combined.shouldEnter(0));
        assertFalse(combined.shouldEnter(1));
        assertFalse(combined.shouldEnter(2));
        assertFalse(combined.shouldEnter(3));
        assertFalse(combined.shouldEnter(4));
    }

    @Test
    public void whenBuyStrategyAndSellStrategyAreEquals() {
        Indicator<Decimal> first = new MockDecimalIndicator(4d, 7d, 9d, 6d, 3d, 2d);
        Indicator<Decimal> second = new MockDecimalIndicator(3d, 6d, 10d, 8d, 2d, 1d);

        Strategy crossed = new IndicatorCrossedIndicatorStrategy(first, second);

        combined = new CombinedEntryAndExitStrategy(crossed, crossed);

        for (int index = 0; index < 6; index++) {
            assertEquals(crossed.shouldEnter(index), combined.shouldEnter(index));
            assertEquals(crossed.shouldExit(index), combined.shouldExit(index));
        }
    }
}
