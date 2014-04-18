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

import eu.verdelhan.ta4j.strategies.NotSoFastStrategy;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class NotSoFastStrategyTest {

    private NotSoFastStrategy strategy;

    private Operation[] enter;

    private Operation[] exit;

    private MockStrategy fakeStrategy;

    @Before
    public void setUp() {
        enter = new Operation[] { new Operation(0, OperationType.BUY), null, null, null, null, null };

        exit = new Operation[] { null, new Operation(1, OperationType.SELL), null,
                new Operation(3, OperationType.SELL), new Operation(4, OperationType.SELL),
                new Operation(5, OperationType.SELL), };

        fakeStrategy = new MockStrategy(enter, exit);
    }

    @Test
    public void testWith3Ticks() {
        strategy = new NotSoFastStrategy(fakeStrategy, 3);

        assertThat(strategy.shouldEnter(0)).isTrue();
        assertThat(strategy.shouldExit(0)).isFalse();
        assertThat(strategy.shouldExit(1)).isFalse();
        assertThat(strategy.shouldExit(2)).isFalse();
        assertThat(strategy.shouldExit(3)).isFalse();
        assertThat(strategy.shouldExit(4)).isTrue();
        assertThat(strategy.shouldExit(5)).isTrue();
    }

    @Test
    public void testWith0Ticks() {
        strategy = new NotSoFastStrategy(fakeStrategy, 0);

        assertThat(strategy.shouldEnter(0)).isTrue();

        assertThat(strategy.shouldExit(0)).isFalse();
        assertThat(strategy.shouldExit(1)).isTrue();
        assertThat(strategy.shouldExit(2)).isFalse();
        assertThat(strategy.shouldExit(3)).isTrue();
        assertThat(strategy.shouldExit(4)).isTrue();
        assertThat(strategy.shouldExit(5)).isTrue();
    }
}
