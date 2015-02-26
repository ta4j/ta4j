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
import eu.verdelhan.ta4j.Operation.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class OrStrategyTest {

    private Operation[] enter;
    private Operation[] exit;
    private Operation[] enter2;
    private Operation[] exit2;
    private MockStrategy fakeStrategy;
    private MockStrategy fakeStrategy2;
    private Strategy orStrategy;

    @Before
    public void setUp() {
        enter = new Operation[] { 
                Operation.buyAt(0), 
                null,
                null, 
                null,
                Operation.buyAt(4), 
                null};
        
        exit = new Operation[] {
                null,
                Operation.sellAt(1), 
                null,
                null,
                null,
                Operation.sellAt(5)
        };
        enter2 = new Operation[] {
                null,
                Operation.buyAt(1), 
                null,
                null,
                Operation.buyAt(4), 
                null};
        
        exit2 = new Operation[] {
                null,
                null,
                Operation.sellAt(2), 
                null,
                null,
                Operation.sellAt(5)
        };
        this.fakeStrategy = new MockStrategy(enter,exit);
        this.fakeStrategy2 = new MockStrategy(enter2,exit2);
        
        this.orStrategy = new OrStrategy(fakeStrategy,fakeStrategy2);
    }
    
    @Test
    public void AndStrategyShouldEnterWhenThe2StrategiesEnter() {
        assertTrue(orStrategy.shouldEnter(0));
        assertTrue(orStrategy.shouldEnter(1));
        assertFalse(orStrategy.shouldEnter(2));
        assertFalse(orStrategy.shouldEnter(3));
        assertTrue(orStrategy.shouldEnter(4));
        assertFalse(orStrategy.shouldEnter(5));
    }
    
    @Test
    public void AndStrategyShouldExitWhenThe2StrategiesExit() {
        assertFalse(orStrategy.shouldExit(0));
        assertTrue(orStrategy.shouldExit(1));
        assertTrue(orStrategy.shouldExit(2));
        assertFalse(orStrategy.shouldExit(3));
        assertFalse(orStrategy.shouldExit(4));
        assertTrue(orStrategy.shouldExit(5));
    }    
}
        