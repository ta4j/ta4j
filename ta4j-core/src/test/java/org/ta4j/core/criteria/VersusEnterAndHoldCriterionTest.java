/*
 * SPDX-License-Identifier: MIT
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

        // DECIMAL representation: ratio is returned as-is (0-based rate)
        var vsBuyAndHoldDecimal = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.DECIMAL), BigDecimal.ONE, ReturnRepresentation.DECIMAL);
        assertNumEquals(tradingVsEnterAndHold, vsBuyAndHoldDecimal.calculate(series, tradingRecord));

        // PERCENTAGE representation: ratio is multiplied by 100
        var vsBuyAndHoldPercentage = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE,
                ReturnRepresentation.PERCENTAGE);
        assertNumEquals(tradingVsEnterAndHold * 100, vsBuyAndHoldPercentage.calculate(series, tradingRecord));

        // MULTIPLICATIVE representation: ratio is converted to 1 + ratio
        var vsBuyAndHoldMultiplicative = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE), BigDecimal.ONE,
                ReturnRepresentation.MULTIPLICATIVE);
        assertNumEquals(1 + tradingVsEnterAndHold, vsBuyAndHoldMultiplicative.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        // firstTrade = -5%, secondTrade = -30 %
        // Return from trading per trade: firstTrade =-95%, secondTrade = -70%
        // Return from trading: (-0.95) x (-0.7) = 0.665 = 66.5% (MULTIPLICATIVE)
        // Return from enter-and-hold: 0.7 = 70% (MULTIPLICATIVE)
        //
        // MULTIPLICATIVE values are normalized to rates before comparison:
        // tradingResult rate = 0.665 - 1 = -0.335
        // enterAndHoldResult rate = 0.7 - 1 = -0.3
        // Ratio = (-0.335 - (-0.3)) / abs(-0.3) = -0.035 / 0.3 = -0.1167
        var expectedRatio = -0.11666666666666675;

        // DECIMAL representation: ratio is returned as-is (0-based rate)
        var vsBuyAndHoldDecimal = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.DECIMAL), BigDecimal.ONE, ReturnRepresentation.DECIMAL);
        assertNumEquals(expectedRatio, vsBuyAndHoldDecimal.calculate(series, tradingRecord));

        // PERCENTAGE representation: ratio is multiplied by 100
        var vsBuyAndHoldPercentage = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE,
                ReturnRepresentation.PERCENTAGE);
        assertNumEquals(expectedRatio * 100, vsBuyAndHoldPercentage.calculate(series, tradingRecord));

        // MULTIPLICATIVE representation: ratio is converted to 1 + ratio
        var vsBuyAndHoldMultiplicative = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE), BigDecimal.ONE,
                ReturnRepresentation.MULTIPLICATIVE);
        assertNumEquals(1 + expectedRatio, vsBuyAndHoldMultiplicative.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOnlyOnePosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        // Position: buy at 100, sell at 95 -> return = 95/100 = 0.95 (MULTIPLICATIVE)
        // Enter and hold: buy at 100 (first bar), sell at 70 (last bar) -> return =
        // 70/100 = 0.7 (MULTIPLICATIVE)
        //
        // MULTIPLICATIVE values are normalized to rates before comparison:
        // tradingResult rate = 0.95 - 1 = -0.05
        // enterAndHoldResult rate = 0.7 - 1 = -0.3
        // Ratio = (-0.05 - (-0.3)) / abs(-0.3) = 0.25 / 0.3 = 0.8333
        var expectedRatio = 0.8333333333333333;

        // DECIMAL representation: ratio is returned as-is (0-based rate)
        var vsBuyAndHoldDecimal = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.DECIMAL), BigDecimal.ONE, ReturnRepresentation.DECIMAL);
        assertNumEquals(expectedRatio, vsBuyAndHoldDecimal.calculate(series, position));

        // PERCENTAGE representation: ratio is multiplied by 100
        var vsBuyAndHoldPercentage = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE,
                ReturnRepresentation.PERCENTAGE);
        assertNumEquals(expectedRatio * 100, vsBuyAndHoldPercentage.calculate(series, position));

        // MULTIPLICATIVE representation: ratio is converted to 1 + ratio
        var vsBuyAndHoldMultiplicative = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE), BigDecimal.ONE,
                ReturnRepresentation.MULTIPLICATIVE);
        assertNumEquals(1 + expectedRatio, vsBuyAndHoldMultiplicative.calculate(series, position));
    }

    @Test
    public void calculateWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();

        // No positions: tradingResult = 1.0 (MULTIPLICATIVE, neutral)
        // Enter and hold: 0.7 (MULTIPLICATIVE)
        //
        // MULTIPLICATIVE values are normalized to rates before comparison:
        // tradingResult rate = 1.0 - 1 = 0
        // enterAndHoldResult rate = 0.7 - 1 = -0.3
        // Ratio = (0 - (-0.3)) / abs(-0.3) = 0.3 / 0.3 = 1.0
        var expectedRatio = 1.0;

        // DECIMAL representation: ratio is returned as-is (0-based rate)
        var vsBuyAndHoldDecimal = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.DECIMAL), BigDecimal.ONE, ReturnRepresentation.DECIMAL);
        assertNumEquals(expectedRatio, vsBuyAndHoldDecimal.calculate(series, new BaseTradingRecord()));

        // PERCENTAGE representation: ratio is multiplied by 100
        var vsBuyAndHoldPercentage = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE,
                ReturnRepresentation.PERCENTAGE);
        assertNumEquals(expectedRatio * 100, vsBuyAndHoldPercentage.calculate(series, new BaseTradingRecord()));

        // MULTIPLICATIVE representation: ratio is converted to 1 + ratio
        var vsBuyAndHoldMultiplicative = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE), BigDecimal.ONE,
                ReturnRepresentation.MULTIPLICATIVE);
        assertNumEquals(1 + expectedRatio, vsBuyAndHoldMultiplicative.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithAverageProfit() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 130).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        // AverageReturnPerBarCriterion uses MULTIPLICATIVE by default
        // tradingResult = pow(95/100 * 130/100, 1/6) = pow(1.235, 1/6) ≈ 1.0359
        // enterAndHoldResult = pow(130/100, 1/6) = pow(1.3, 1/6) ≈ 1.0447
        //
        // MULTIPLICATIVE values are normalized to rates before comparison:
        // tradingResult rate = 1.0359 - 1 = 0.0359
        // enterAndHoldResult rate = 1.0447 - 1 = 0.0447
        // Ratio = (0.0359 - 0.0447) / 0.0447 ≈ -0.197
        var vsBuyAndHold = new VersusEnterAndHoldCriterion(TradeType.BUY, new AverageReturnPerBarCriterion(),
                BigDecimal.ONE, ReturnRepresentation.DECIMAL);
        // The exact value will be calculated by the implementation
        var result = vsBuyAndHold.calculate(series, tradingRecord);
        // Verify it's negative (worse than buy-and-hold) and reasonable
        assertTrue(result.doubleValue() < 0);
        assertTrue(result.doubleValue() > -0.5); // Should be around -0.197
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

        // NumberOfBarsCriterion doesn't use ReturnRepresentation, so the ratio is
        // calculated as-is and then converted
        var vsBuyAndHold = new VersusEnterAndHoldCriterion(TradeType.BUY, new NumberOfBarsCriterion(), BigDecimal.ONE,
                ReturnRepresentation.DECIMAL);
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
        // NetProfitLossCriterion doesn't use ReturnRepresentation, so the ratio is
        // calculated as-is and then converted
        var vsBuyAndHoldPnl1 = new VersusEnterAndHoldCriterion(TradeType.BUY, new NetProfitLossCriterion(),
                BigDecimal.ONE, ReturnRepresentation.DECIMAL);
        var vsBuyAndHoldPnlValue1 = vsBuyAndHoldPnl1.calculate(series, tradingRecord);

        // vs buy and hold of pnl with amount of 10
        var vsBuyAndHoldPnl2 = new VersusEnterAndHoldCriterion(TradeType.BUY, new NetProfitLossCriterion(),
                BigDecimal.TEN, ReturnRepresentation.DECIMAL);
        var vsBuyAndHoldPnlValue2 = vsBuyAndHoldPnl2.calculate(series, tradingRecord);

        assertNumEquals(2, vsBuyAndHoldPnlValue1);
        assertNumEquals(-0.7, vsBuyAndHoldPnlValue2);

        // The less amount you need to achieve a given (absolute) profit, the better.
        assertTrue(vsBuyAndHoldPnl1.betterThan(vsBuyAndHoldPnlValue1, vsBuyAndHoldPnlValue2));
        assertFalse(vsBuyAndHoldPnl2.betterThan(vsBuyAndHoldPnlValue2, vsBuyAndHoldPnlValue1));
    }

    @Test
    public void betterThan() {
        // MULTIPLICATIVE representation: ratio of 2.0 (100% better) vs 1.5 (50% better)
        var criterionMultiplicative = new VersusEnterAndHoldCriterion(TradeType.BUY, new GrossReturnCriterion(),
                BigDecimal.ONE, ReturnRepresentation.MULTIPLICATIVE);
        assertTrue(criterionMultiplicative.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterionMultiplicative.betterThan(numOf(1.5), numOf(2.0)));

        // DECIMAL representation: ratio of 0.2 (20% better) vs 0.15 (15% better)
        var criterionDecimal = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.DECIMAL), BigDecimal.ONE, ReturnRepresentation.DECIMAL);
        assertTrue(criterionDecimal.betterThan(numOf(0.2), numOf(0.15)));
        assertFalse(criterionDecimal.betterThan(numOf(0.15), numOf(0.2)));

        // PERCENTAGE representation: ratio of 20.0 (20% better) vs 15.0 (15% better)
        var criterionPercentage = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE,
                ReturnRepresentation.PERCENTAGE);
        assertTrue(criterionPercentage.betterThan(numOf(20.0), numOf(15.0)));
        assertFalse(criterionPercentage.betterThan(numOf(15.0), numOf(20.0)));
    }
}
