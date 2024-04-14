///**
// * The MIT License (MIT)
// *
// * Copyright (c) 2017-2023 Ta4j Organization & respective
// * authors (see AUTHORS)
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy of
// * this software and associated documentation files (the "Software"), to deal in
// * the Software without restriction, including without limitation the rights to
// * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
// * the Software, and to permit persons to whom the Software is furnished to do so,
// * subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all
// * copies or substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
// * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
// * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
// * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
// * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
// */
//package org.ta4j.core.criteria;
//
//import static org.junit.Assert.assertFalse;
//import static org.junit.Assert.assertTrue;
//import static org.ta4j.core.TestUtils.assertNumEquals;
//import static org.ta4j.core.num.NaN.NaN;
//
//import org.junit.Test;
//import org.ta4j.core.AnalysisCriterion;
//import org.ta4j.core.BaseTradingRecord;
//import org.ta4j.core.Position;
//import org.ta4j.core.Trade;
//import org.ta4j.core.TradingRecord;
//import org.ta4j.core.criteria.pnl.ReturnCriterion;
//import org.ta4j.core.mocks.MockBarSeriesBuilder;
//import org.ta4j.core.num.NumFactory;
//
//public class VersusEnterAndHoldCriterionTest extends AbstractCriterionTest {
//
//    public VersusEnterAndHoldCriterionTest(NumFactory numFactory) {
//        super(params -> new VersusEnterAndHoldCriterion((AnalysisCriterion) params[0]), numFactory);
//    }
//
//    @Test
//    public void calculateOnlyWithGainPositions() {
//        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
//                .withData(100, 105, 110, 100, 95, 105)
//                .build();
//        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
//                Trade.buyAt(3, series), Trade.sellAt(5, series));
//
//        AnalysisCriterion buyAndHold = getCriterion(new ReturnCriterion());
//        assertNumEquals(1.10 * 1.05 / 1.05, buyAndHold.calculate(series, tradingRecord));
//    }
//
//    @Test
//    public void calculateOnlyWithLossPositions() {
//        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
//        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
//                Trade.buyAt(2, series), Trade.sellAt(5, series));
//
//        AnalysisCriterion buyAndHold = getCriterion(new ReturnCriterion());
//        assertNumEquals(0.95 * 0.7 / 0.7, buyAndHold.calculate(series, tradingRecord));
//    }
//
//    @Test
//    public void calculateWithOnlyOnePosition() {
//        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
//        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
//
//        AnalysisCriterion buyAndHold = getCriterion(new ReturnCriterion());
//        assertNumEquals((100d / 70) / (100d / 95), buyAndHold.calculate(series, position));
//    }
//
//    @Test
//    public void calculateWithNoPositions() {
//        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
//
//        AnalysisCriterion buyAndHold = getCriterion(new ReturnCriterion());
//        assertNumEquals(1 / 0.7, buyAndHold.calculate(series, new BaseTradingRecord()));
//    }
//
//    @Test
//    public void calculateWithAverageProfit() {
//        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 130).build();
//        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, NaN, NaN), Trade.sellAt(1, NaN, NaN),
//                Trade.buyAt(2, NaN, NaN), Trade.sellAt(5, NaN, NaN));
//
//        AnalysisCriterion buyAndHold = getCriterion(new AverageReturnPerBarCriterion());
//
//        assertNumEquals(Math.pow(95d / 100 * 130d / 100, 1d / 6) / Math.pow(130d / 100, 1d / 6),
//                buyAndHold.calculate(series, tradingRecord));
//    }
//
//    @Test
//    public void calculateWithNumberOfBars() {
//        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 130).build();
//        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
//                Trade.buyAt(2, series), Trade.sellAt(5, series));
//
//        AnalysisCriterion buyAndHold = getCriterion(new NumberOfBarsCriterion());
//
//        assertNumEquals(6d / 6d, buyAndHold.calculate(series, tradingRecord));
//    }
//
//    @Test
//    public void betterThan() {
//        AnalysisCriterion criterion = getCriterion(new ReturnCriterion());
//        assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
//        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
//    }
//}
