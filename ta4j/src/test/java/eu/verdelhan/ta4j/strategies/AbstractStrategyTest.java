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

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class AbstractStrategyTest {

    private Operation[] enter;
    private Operation[] exit;
    private Operation[] enter2;
    private Operation[] exit2;
    private MockStrategy fakeStrategy;
    private MockStrategy fakeStrategy2;

    @Before
    public void setUp() {
        enter = new Operation[] { 
                new Operation(0, OperationType.BUY), 
                null,
                new Operation(2, OperationType.BUY), 
                null,
                new Operation(4, OperationType.BUY),
                null};
        
        exit = new Operation[] {
                null,
                new Operation(1, OperationType.SELL),
                null,
                new Operation(3, OperationType.SELL),
                null,
                new Operation(5, OperationType.SELL)
        };
        enter2 = new Operation[] {
                null,
                new Operation(1, OperationType.BUY), 
                null,
                null,
                new Operation(4, OperationType.BUY), 
                null};
        
        exit2 = new Operation[] {
                null,
                null,
                new Operation(2, OperationType.SELL),
                null,
                new Operation(4, OperationType.SELL),
                new Operation(5, OperationType.SELL)
        };
        this.fakeStrategy = new MockStrategy(enter,exit);
        this.fakeStrategy2 = new MockStrategy(enter2,exit2);
    }

    @Test
    public void and() {
        Strategy strategy = fakeStrategy.and(fakeStrategy2);  
        
        assertThat(strategy.shouldEnter(0)).isFalse();
        assertThat(strategy.shouldEnter(1)).isFalse();
        assertThat(strategy.shouldEnter(2)).isFalse();
        assertThat(strategy.shouldEnter(3)).isFalse();
        assertThat(strategy.shouldEnter(4)).isTrue();
        assertThat(strategy.shouldEnter(5)).isFalse();
        
        assertThat(strategy.shouldExit(0)).isFalse();
        assertThat(strategy.shouldExit(1)).isFalse();
        assertThat(strategy.shouldExit(2)).isFalse();
        assertThat(strategy.shouldExit(3)).isFalse();
        assertThat(strategy.shouldExit(4)).isFalse();
        assertThat(strategy.shouldExit(5)).isTrue();
    }

    @Test
    public void or() {
        Strategy strategy = fakeStrategy.or(fakeStrategy2);
        
        assertThat(strategy.shouldEnter(0)).isTrue();
        assertThat(strategy.shouldEnter(1)).isTrue();
        assertThat(strategy.shouldEnter(2)).isTrue();
        assertThat(strategy.shouldEnter(3)).isFalse();
        assertThat(strategy.shouldEnter(4)).isTrue();
        assertThat(strategy.shouldEnter(5)).isFalse();
        
        assertThat(strategy.shouldExit(0)).isFalse();
        assertThat(strategy.shouldExit(1)).isTrue();
        assertThat(strategy.shouldExit(2)).isTrue();
        assertThat(strategy.shouldExit(3)).isTrue();
        assertThat(strategy.shouldExit(4)).isTrue();
        assertThat(strategy.shouldExit(5)).isTrue();
    }

    @Test
    public void opposite() {
        Strategy opposite = fakeStrategy.opposite();

        for (int i = 0; i < enter.length; i++) {
            assertThat(opposite.shouldEnter(i)).isNotEqualTo(fakeStrategy.shouldEnter(i));
        }
    }
}
