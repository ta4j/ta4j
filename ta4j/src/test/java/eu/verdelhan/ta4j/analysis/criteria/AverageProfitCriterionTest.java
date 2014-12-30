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
package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.TATestsUtils;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class AverageProfitCriterionTest {
    private MockTimeSeries series;

    private List<Trade> trades;

    @Before
    public void setUp() {
        trades = new ArrayList<Trade>();
    }

    @Test
    public void calculateOnlyWithGainTrades() {
        series = new MockTimeSeries(100d, 105d, 110d, 100d, 95d, 105d);
        trades.clear();
        trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
        trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));
        AnalysisCriterion averageProfit = new AverageProfitCriterion();
        assertEquals(1.0243, TATestsUtils.TA_OFFSET, averageProfit.calculate(series, trades));
    }

    @Test
    public void calculateWithASimpleTrade() {
        series = new MockTimeSeries(100d, 105d, 110d, 100d, 95d, 105d);
        trades.clear();
        trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
        AnalysisCriterion averageProfit = new AverageProfitCriterion();
        assertEquals(Math.pow(110d/100, 1d/3), averageProfit.calculate(series, trades), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void calculateOnlyWithLossTrades() {
        series = new MockTimeSeries(100, 95, 100, 80, 85, 70);
        trades.clear();
        trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
        trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));
        AnalysisCriterion averageProfit = new AverageProfitCriterion();
        assertEquals(Math.pow(95d/100 * 70d/100, 1d / 6), averageProfit.calculate(series, trades), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void calculateWithNoTicksShouldReturn1() {
        series = new MockTimeSeries(100, 95, 100, 80, 85, 70);
        trades.clear();
        AnalysisCriterion averageProfit = new AverageProfitCriterion();
        assertEquals(1d, averageProfit.calculate(series, trades), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void calculateWithOneTrade() {
        series = new MockTimeSeries(100, 105);
        Trade trade = new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL));
        AnalysisCriterion average = new AverageProfitCriterion();
        assertEquals(Math.pow(105d / 100, 1d/2), average.calculate(series, trade), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = new AverageProfitCriterion();
        assertTrue(criterion.betterThan(2.0, 1.5));
        assertFalse(criterion.betterThan(1.5, 2.0));
    }
}
