/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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

import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class PipeEnterStrategyTest {
    private MockIndicator<Double> upper;

    private MockIndicator<Double> lower;

    @Before
    public void setUp() {
        upper = new MockIndicator<Double>(30d, 32d, 33d, 32d, 35d, 33d, 32d, 33d, 31d, 30d, 31d, 32d, 32d, 34d, 35d);
        lower = new MockIndicator<Double>(10d, 12d, 13d, 12d, 15d, 13d, 12d, 13d, 11d, 10d, 11d, 12d, 12d, 14d, 15d);
    }

    @Test
    public void firstSellLastBuy() {
        Trade trade = new Trade();
        MockIndicator<Double> value = new MockIndicator<Double>(25d, 27d, 28d, 27d, 30d, 33d, 35d, 37d, 35d, 29d, 11d, 10d, 15d,
                30d, 31d);

        PipeEnterStrategy pipeEnter = new PipeEnterStrategy(upper, lower, value);

        assertThat(pipeEnter.shouldOperate(trade, 1)).isFalse();
        assertThat(pipeEnter.shouldOperate(trade, 8)).isFalse();
        assertThat(pipeEnter.shouldOperate(trade, 10)).isFalse();
        trade.operate(8);
        assertThat(pipeEnter.shouldOperate(trade, 9)).isTrue();
        trade = new Trade();
        assertThat(pipeEnter.shouldOperate(trade, 11)).isFalse();
        assertThat(pipeEnter.shouldOperate(trade, 12)).isTrue();
    }

    @Test
    public void firstBuyLastSell() {
        Trade trade = new Trade();
        MockIndicator<Double> value = new MockIndicator<Double>(8d, 15d, 16d, 33d, 40d, 45d, 47d, 40d, 32d, 25d, 15d, 16d, 11d,
                10d, 12d);

        PipeEnterStrategy pipeEnter = new PipeEnterStrategy(upper, lower, value);

        assertThat(pipeEnter.shouldOperate(trade, 0)).isFalse();
        assertThat(pipeEnter.shouldOperate(trade, 1)).isTrue();
        trade.operate(1);
        assertThat(pipeEnter.shouldOperate(trade, 8)).isFalse();
        assertThat(pipeEnter.shouldOperate(trade, 10)).isFalse();

        assertThat(pipeEnter.shouldOperate(trade, 8)).isFalse();
        assertThat(pipeEnter.shouldOperate(trade, 9)).isTrue();
    }
}
