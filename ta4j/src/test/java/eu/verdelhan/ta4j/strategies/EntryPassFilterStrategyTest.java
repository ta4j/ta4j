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
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class EntryPassFilterStrategyTest {

    private Operation[] enter;
    private Operation[] exit;
    private MockStrategy mockStrategy;
    private EntryPassFilterStrategy filteredStrategy;

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
        mockStrategy = new MockStrategy(enter, exit);
        
        filteredStrategy = new EntryPassFilterStrategy(mockStrategy);
    }
    
    @Test
    public void filteredStrategyShouldEnterWhenStrategyShouldEnter() {
        assertTrue(filteredStrategy.shouldEnter(0));
        assertFalse(filteredStrategy.shouldEnter(1));
        assertTrue(filteredStrategy.shouldEnter(2));
        assertFalse(filteredStrategy.shouldEnter(3));
        assertTrue(filteredStrategy.shouldEnter(4));
        assertFalse(filteredStrategy.shouldEnter(5));
    }
    
    @Test
    public void filteredStrategyShouldNeverExit() {
        assertFalse(filteredStrategy.shouldExit(0));
        assertFalse(filteredStrategy.shouldExit(1));
        assertFalse(filteredStrategy.shouldExit(2));
        assertFalse(filteredStrategy.shouldExit(3));
        assertFalse(filteredStrategy.shouldExit(4));
        assertFalse(filteredStrategy.shouldExit(5));
    }    
}
        