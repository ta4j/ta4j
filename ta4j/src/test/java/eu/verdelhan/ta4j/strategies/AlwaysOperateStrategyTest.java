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

import eu.verdelhan.ta4j.strategies.AlwaysOperateStrategy;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Trade;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class AlwaysOperateStrategyTest {
    private AlwaysOperateStrategy strategy;

    @Before
    public void setUp() {
        this.strategy = new AlwaysOperateStrategy();
    }

    @Test
    public void shouldEnterBuyingIfTradeIsNew() {
        Trade trade = new Trade();
        assertThat(strategy.shouldOperate(trade, 0)).isTrue();
    }

    @Test
    public void shouldExitSellingIfTradeIsOpened() {
        Trade trade = new Trade();
        trade.operate(0);

        assertThat(strategy.shouldOperate(trade, 1)).isTrue();
    }

    @Test
    public void shouldNotOperateIfTradeIsClosed() {
        Trade trade = new Trade();
        trade.operate(0);
        trade.operate(1);

        assertThat(strategy.shouldOperate(trade, 2)).isFalse();
    }

    @Test
    public void shouldEnterSellingIfUncoveredTradeIsNew() {
        Trade uncoveredTrade = new Trade(OperationType.SELL);
        assertThat(strategy.shouldOperate(uncoveredTrade, 0)).isTrue();
    }

    @Test
    public void shouldExitBuyingIfUncoveredTradeIsOpened() {
        Trade uncoveredTrade = new Trade(OperationType.SELL);
        uncoveredTrade.operate(0);

        assertThat(strategy.shouldOperate(uncoveredTrade, 1)).isTrue();
    }
}
