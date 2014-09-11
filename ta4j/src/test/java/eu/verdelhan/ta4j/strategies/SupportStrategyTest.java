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

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class SupportStrategyTest {

    private MockIndicator<Double> indicator;

    @Before
    public void setUp() {
        indicator = new MockIndicator<Double>(96d, 90d, 94d, 97d, 95d, 110d);
    }

    @Test
    public void supportShouldBuy() {
        Operation[] enter = new Operation[] { null, null, null, null, null, null };

        Strategy neverBuy = new MockStrategy(enter, enter);

        Strategy support = new SupportStrategy(indicator, neverBuy, 95);

        Trade trade = new Trade();

        assertThat(support.shouldOperate(trade, 0)).isFalse();
        assertThat(support.shouldOperate(trade, 1)).isTrue();
        trade.operate(1);
        assertThat(trade.getEntry()).isEqualTo(new Operation(1, OperationType.BUY));
        trade = new Trade();
        assertThat(support.shouldOperate(trade, 2)).isTrue();
        trade.operate(2);
        assertThat(trade.getEntry()).isEqualTo(new Operation(2, OperationType.BUY));
        trade = new Trade();
        assertThat(support.shouldOperate(trade, 3)).isFalse();
        assertThat(support.shouldOperate(trade, 4)).isTrue();
        trade.operate(4);
        assertThat(trade.getEntry()).isEqualTo(new Operation(4, OperationType.BUY));
        assertThat(support.shouldOperate(trade, 5)).isFalse();
    }
}
