/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.BaseTradingRecord;
import eu.verdelhan.ta4j.Order;
import eu.verdelhan.ta4j.TATestsUtils;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.TradingRecord;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class RewardRiskRatioCriterionTest {

    private RewardRiskRatioCriterion rrc;

    @Before
    public void setUp() {
        this.rrc = new RewardRiskRatioCriterion();
    }

    @Test
    public void rewardRiskRatioCriterion() {
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0), Order.sellAt(1),
                Order.buyAt(2), Order.sellAt(4),
                Order.buyAt(5), Order.sellAt(7));

        MockTimeSeries series = new MockTimeSeries(100, 105, 95, 100, 90, 95, 80, 120);

        double totalProfit = (105d / 100) * (90d / 95d) * (120d / 95);
        double peak = (105d / 100) * (100d / 95);
        double low = (105d / 100) * (90d / 95) * (80d / 95);

        assertEquals(totalProfit / ((peak - low) / peak), rrc.calculate(series, tradingRecord), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void rewardRiskRatioCriterionOnlyWithGain() {
        MockTimeSeries series = new MockTimeSeries(1, 2, 3, 6, 8, 20, 3);
        TradingRecord tradingRecord = new BaseTradingRecord(
                Order.buyAt(0), Order.sellAt(1),
                Order.buyAt(2), Order.sellAt(5));
        assertTrue(Double.isInfinite(rrc.calculate(series, tradingRecord)));
    }

    @Test
    public void rewardRiskRatioCriterionWithNoTrades() {
        MockTimeSeries series = new MockTimeSeries(1, 2, 3, 6, 8, 20, 3);
        assertTrue(Double.isInfinite(rrc.calculate(series, new BaseTradingRecord())));
    }
    
    @Test
    public void withOneTrade() {
        Trade trade = new Trade(Order.buyAt(0), Order.sellAt(1));

        MockTimeSeries series = new MockTimeSeries(100, 95, 95, 100, 90, 95, 80, 120);

        RewardRiskRatioCriterion ratioCriterion = new RewardRiskRatioCriterion();
        assertEquals((95d/100) / ((1d - 0.95d)), TATestsUtils.TA_OFFSET, ratioCriterion.calculate(series, trade));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = new RewardRiskRatioCriterion();
        assertTrue(criterion.betterThan(3.5, 2.2));
        assertFalse(criterion.betterThan(1.5, 2.7));
    }
}
