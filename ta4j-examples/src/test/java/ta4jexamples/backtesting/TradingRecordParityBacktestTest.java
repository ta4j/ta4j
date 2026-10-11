/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.SlippageExecutionModel;
import org.ta4j.core.backtest.TradeExecutionModel;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.backtest.TradeOnNextOpenModel;
import org.ta4j.core.num.Num;

public class TradingRecordParityBacktestTest {

    @Test
    public void test() {
        TradingRecordParityBacktest.main(null);
    }

    @Test
    public void executionModelsDemonstrateTimingAndSlippageDifferences() {
        BarSeries series = TradingRecordParityBacktest.createSeries();
        Strategy strategy = TradingRecordParityBacktest.createStrategy();

        TradingRecord nextOpenRecord = TradingRecordParityBacktest.runWithExecutionModel(series, strategy,
                new TradeOnNextOpenModel());
        TradingRecord currentCloseRecord = TradingRecordParityBacktest.runWithExecutionModel(series, strategy,
                new TradeOnCurrentCloseModel());
        TradingRecord slippageRecord = TradingRecordParityBacktest.runWithExecutionModel(series, strategy,
                new SlippageExecutionModel(series.numFactory().numOf(0.01),
                        TradeExecutionModel.PriceSource.CURRENT_CLOSE));

        Position nextOpenPosition = nextOpenRecord.getPositions().get(0);
        Position currentClosePosition = currentCloseRecord.getPositions().get(0);
        Position slippagePosition = slippageRecord.getPositions().get(0);

        assertEquals(2, nextOpenPosition.getEntry().getIndex());
        assertNumEquals(series.numFactory().numOf(109), nextOpenPosition.getEntry().getPricePerAsset());
        assertEquals(4, nextOpenPosition.getExit().getIndex());
        assertNumEquals(series.numFactory().numOf(99), nextOpenPosition.getExit().getPricePerAsset());

        assertEquals(1, currentClosePosition.getEntry().getIndex());
        assertNumEquals(series.numFactory().numOf(104), currentClosePosition.getEntry().getPricePerAsset());
        assertEquals(3, currentClosePosition.getExit().getIndex());
        assertNumEquals(series.numFactory().numOf(103), currentClosePosition.getExit().getPricePerAsset());

        Num onePercent = series.numFactory().numOf(1.01);
        Num ninetyNinePercent = series.numFactory().numOf(0.99);
        assertNumEquals(series.numFactory().numOf(104).multipliedBy(onePercent),
                slippagePosition.getEntry().getPricePerAsset());
        assertNumEquals(series.numFactory().numOf(103).multipliedBy(ninetyNinePercent),
                slippagePosition.getExit().getPricePerAsset());

        assertTrue(currentClosePosition.getGrossProfit().isGreaterThan(nextOpenPosition.getGrossProfit()));
        assertTrue(slippagePosition.getGrossProfit().isLessThan(currentClosePosition.getGrossProfit()));
    }

    @Test
    public void currentCloseParityHoldsAcrossProvidedAndFactoryRecordFlows() {
        BarSeries series = TradingRecordParityBacktest.createSeries();
        Strategy strategy = TradingRecordParityBacktest.createStrategy();

        TradingRecord defaultRecord = TradingRecordParityBacktest.runWithExecutionModel(series, strategy,
                new TradeOnCurrentCloseModel());
        TradingRecord providedRecord = TradingRecordParityBacktest.runWithProvidedRecord(series, strategy,
                new TradeOnCurrentCloseModel());
        TradingRecord factoryConfiguredRecord = TradingRecordParityBacktest.runWithFactoryConfiguredRecord(series,
                strategy, new TradeOnCurrentCloseModel());

        TradingRecordParityBacktest.assertEquivalent(defaultRecord, providedRecord, "provided record");
        TradingRecordParityBacktest.assertEquivalent(defaultRecord, factoryConfiguredRecord,
                "factory-configured record");
    }

    private static void assertNumEquals(Num expected, Num actual) {
        assertTrue("expected=" + expected + " actual=" + actual, actual.isEqual(expected));
    }
}
