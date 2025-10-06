/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
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
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class VersusEnterAndHoldCriterionTest extends AbstractCriterionTest {

    public VersusEnterAndHoldCriterionTest(NumFactory numFactory) {
        super(params -> new VersusEnterAndHoldCriterion((AnalysisCriterion) params[0]), numFactory);
    }

    private static double xVsEnterAndHold(double x, double enterAndHold) {
        return (x - enterAndHold) / Math.abs(enterAndHold);
    }

    @Test
    public void calculateOnlyWithGainPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        // firstTrade = +10%, secondTrade = +5 %

        // Return from trading: 1.155 - 1 = 0.155 = 15.5%
        var tradingResult = 1.155 - 1;

        // Return from enter-and-hold: 1.05 - 1 = 0.5 = 5%
        var enterAndHoldResult = 1.05 - 1;

        // tradingResult is approx. 210% better than "buy and hold"
        var tradingVsEnterAndHold = xVsEnterAndHold(tradingResult, enterAndHoldResult);

        var vsBuyAndHold = getCriterion(new GrossReturnCriterion(false));
        assertNumEquals(tradingVsEnterAndHold, vsBuyAndHold.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        // firstTrade = -5%, secondTrade = -30 %
        // Return from trading per trade: firstTrade =-95%, secondTrade = -70%
        // Return from trading: (-0.95) x (-0.7) = 0.665 = 66.5%
        var tradingResult = 0.665;

        // Return from enter-and-hold: 0.7 = 70%
        var enterAndHoldResult = 0.7;

        // trading is approx. 0.05 worse than "enter and hold"
        var tradingVsEnterAndHold = xVsEnterAndHold(tradingResult, enterAndHoldResult);

        var vsBuyAndHold = getCriterion(new GrossReturnCriterion(true));
        assertNumEquals(tradingVsEnterAndHold, vsBuyAndHold.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOnlyOnePosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        var tradingResult = 100d / 70;
        var enterAndHoldResult = 100d / 95;
        // tradingResult is approx. 35% better than "buy and hold"
        var tradingVsEnterAndHold = xVsEnterAndHold(tradingResult, enterAndHoldResult);

        var vsBuyAndHold = getCriterion(new GrossReturnCriterion());
        assertNumEquals(tradingVsEnterAndHold, vsBuyAndHold.calculate(series, position));
    }

    @Test
    public void calculateWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();

        var tradingResult = 1;
        var enterAndHoldResult = 0.7;
        // tradingResult is approx. 42% better than "buy and hold"
        var tradingVsEnterAndHold = xVsEnterAndHold(tradingResult, enterAndHoldResult);

        var vsBuyAndHold = getCriterion(new GrossReturnCriterion());
        assertNumEquals(tradingVsEnterAndHold, vsBuyAndHold.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithAverageProfit() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 130).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        var tradingResult = Math.pow(95d / 100 * 130d / 100, 1d / 6);
        var enterAndHoldResult = Math.pow(130d / 100, 1d / 6);
        // tradingResult is approx. -0.85% worse than "buy and hold"
        var tradingVsEnterAndHold = xVsEnterAndHold(tradingResult, enterAndHoldResult);

        var vsBuyAndHold = getCriterion(new AverageReturnPerBarCriterion());
        assertNumEquals(tradingVsEnterAndHold, vsBuyAndHold.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithNumberOfBars() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 130).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        var tradingResult = 6d;
        var enterAndHoldResult = 6d;
        // tradingResult is approx. 0% better or worse than "buy and hold"
        var tradingVsEnterAndHold = xVsEnterAndHold(tradingResult, enterAndHoldResult);

        var vsBuyAndHold = getCriterion(new NumberOfBarsCriterion());
        assertNumEquals(tradingVsEnterAndHold, vsBuyAndHold.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithAmount() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();

        // 2 winning positions
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        // vs buy and hold of pnl with amount of 1
        var vsBuyAndHoldPnl1 = new VersusEnterAndHoldCriterion(TradeType.BUY, new NetProfitLossCriterion(),
                BigDecimal.ONE);
        var vsBuyAndHoldPnlValue1 = vsBuyAndHoldPnl1.calculate(series, tradingRecord);

        // vs buy and hold of pnl with amount of 10
        var vsBuyAndHoldPnl2 = new VersusEnterAndHoldCriterion(TradeType.BUY, new NetProfitLossCriterion(),
                BigDecimal.TEN);
        var vsBuyAndHoldPnlValue2 = vsBuyAndHoldPnl2.calculate(series, tradingRecord);

        assertNumEquals(2, vsBuyAndHoldPnlValue1);
        assertNumEquals(-0.7, vsBuyAndHoldPnlValue2);

        // The less amount you need to achieve a given (absolute) profit, the better.
        assertTrue(vsBuyAndHoldPnl1.betterThan(vsBuyAndHoldPnlValue1, vsBuyAndHoldPnlValue2));
        assertFalse(vsBuyAndHoldPnl2.betterThan(vsBuyAndHoldPnlValue2, vsBuyAndHoldPnlValue1));
    }

    @Test
    public void betterThan() {
        var criterion = getCriterion(new GrossReturnCriterion());
        assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
    }
}
