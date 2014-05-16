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


import eu.verdelhan.ta4j.strategies.OrStrategy;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.assertj.core.api.Assertions.*;
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
                new Operation(0, OperationType.BUY), 
                null,
                null, 
                null,
                new Operation(4, OperationType.BUY),
                null};
        
        exit = new Operation[] {
                null,
                new Operation(1, OperationType.SELL),
                null,
                null,
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
                null,
                new Operation(5, OperationType.SELL)
        };
        this.fakeStrategy = new MockStrategy(enter,exit);
        this.fakeStrategy2 = new MockStrategy(enter2,exit2);
        
        this.orStrategy = new OrStrategy(fakeStrategy,fakeStrategy2);
    }
    
    @Test
    public void AndStrategyShouldEnterWhenThe2StrategiesEnter() {
        assertThat(orStrategy.shouldEnter(0)).isEqualTo(true);
        assertThat(orStrategy.shouldEnter(1)).isEqualTo(true);
        assertThat(orStrategy.shouldEnter(2)).isEqualTo(false);
        assertThat(orStrategy.shouldEnter(3)).isEqualTo(false);
        assertThat(orStrategy.shouldEnter(4)).isEqualTo(true);
        assertThat(orStrategy.shouldEnter(5)).isEqualTo(false);
    }
    
    @Test
    public void AndStrategyShouldExitWhenThe2StrategiesExit() {
        assertThat(orStrategy.shouldExit(0)).isEqualTo(false);
        assertThat(orStrategy.shouldExit(1)).isEqualTo(true);
        assertThat(orStrategy.shouldExit(2)).isEqualTo(true);
        assertThat(orStrategy.shouldExit(3)).isEqualTo(false);
        assertThat(orStrategy.shouldExit(4)).isEqualTo(false);
        assertThat(orStrategy.shouldExit(5)).isEqualTo(true);
    }    
}
        