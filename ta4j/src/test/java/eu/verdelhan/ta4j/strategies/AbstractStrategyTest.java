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

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.junit.Assert.*;
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
        
        assertFalse(strategy.shouldEnter(0));
        assertFalse(strategy.shouldEnter(1));
        assertFalse(strategy.shouldEnter(2));
        assertFalse(strategy.shouldEnter(3));
        assertTrue(strategy.shouldEnter(4));
        assertFalse(strategy.shouldEnter(5));
        
        assertFalse(strategy.shouldExit(0));
        assertFalse(strategy.shouldExit(1));
        assertFalse(strategy.shouldExit(2));
        assertFalse(strategy.shouldExit(3));
        assertFalse(strategy.shouldExit(4));
        assertTrue(strategy.shouldExit(5));
    }

    @Test
    public void or() {
        Strategy strategy = fakeStrategy.or(fakeStrategy2);
        
        assertTrue(strategy.shouldEnter(0));
        assertTrue(strategy.shouldEnter(1));
        assertTrue(strategy.shouldEnter(2));
        assertFalse(strategy.shouldEnter(3));
        assertTrue(strategy.shouldEnter(4));
        assertFalse(strategy.shouldEnter(5));
        
        assertFalse(strategy.shouldExit(0));
        assertTrue(strategy.shouldExit(1));
        assertTrue(strategy.shouldExit(2));
        assertTrue(strategy.shouldExit(3));
        assertTrue(strategy.shouldExit(4));
        assertTrue(strategy.shouldExit(5));
    }

    @Test
    public void opposite() {
        Strategy opposite = fakeStrategy.opposite();

        for (int i = 0; i < enter.length; i++) {
            assertNotEquals(opposite.shouldEnter(i), fakeStrategy.shouldEnter(i));
        }
    }
}
