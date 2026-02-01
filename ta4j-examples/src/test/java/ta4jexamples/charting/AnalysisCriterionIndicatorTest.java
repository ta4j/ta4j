/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.LiveTrade;
import org.ta4j.core.LiveTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.ExpectancyCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.num.Num;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AnalysisCriterionIndicator}.
 */
class AnalysisCriterionIndicatorTest {

    private BarSeries barSeries;
    private TradingRecord tradingRecord;

    @BeforeEach
    void setUp() {
        barSeries = ChartingTestFixtures.standardDailySeries();
        tradingRecord = ChartingTestFixtures.completedTradeRecord(barSeries);
    }

    @Test
    void testConstructorNullSeries() {
        AnalysisCriterion criterion = new NumberOfPositionsCriterion();
        assertThrows(NullPointerException.class, () -> new AnalysisCriterionIndicator(null, criterion, tradingRecord),
                "Should throw exception for null series");
    }

    @Test
    void testConstructorNullCriterion() {
        assertThrows(NullPointerException.class, () -> new AnalysisCriterionIndicator(barSeries, null, tradingRecord),
                "Should throw exception for null criterion");
    }

    @Test
    void testConstructorNullTradingRecord() {
        AnalysisCriterion criterion = new NumberOfPositionsCriterion();
        assertThrows(NullPointerException.class, () -> new AnalysisCriterionIndicator(barSeries, criterion, null),
                "Should throw exception for null trading record");
    }

    @Test
    void testGetCountOfUnstableBars() {
        AnalysisCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, criterion, tradingRecord);

        assertEquals(0, indicator.getCountOfUnstableBars(), "Analysis criteria should have no unstable bars");
    }

    @Test
    void testNumberOfPositionsCriterionCalculation() {
        // Create a trading record with multiple completed positions
        TradingRecord record = new BaseTradingRecord();
        // First position: buy at index 1, sell at index 3
        record.enter(1, barSeries.getBar(1).getClosePrice(), barSeries.numFactory().numOf(1));
        record.exit(3, barSeries.getBar(3).getClosePrice(), barSeries.numFactory().numOf(1));
        // Second position: buy at index 5, sell at index 7
        record.enter(5, barSeries.getBar(5).getClosePrice(), barSeries.numFactory().numOf(1));
        record.exit(7, barSeries.getBar(7).getClosePrice(), barSeries.numFactory().numOf(1));

        AnalysisCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, criterion, record);

        // Before any trades
        assertEquals(barSeries.numFactory().numOf(0), indicator.getValue(0), "Should be 0 positions before any trades");

        // After first buy (position opened but not closed)
        assertEquals(barSeries.numFactory().numOf(0), indicator.getValue(1),
                "Should be 0 positions (position not yet closed)");

        // After first position closed
        assertEquals(barSeries.numFactory().numOf(1), indicator.getValue(3),
                "Should be 1 position after first position closed");

        // After second buy
        assertEquals(barSeries.numFactory().numOf(1), indicator.getValue(5),
                "Should still be 1 position (second position not yet closed)");

        // After second position closed
        assertEquals(barSeries.numFactory().numOf(2), indicator.getValue(7),
                "Should be 2 positions after second position closed");

        // At end
        assertEquals(barSeries.numFactory().numOf(2), indicator.getValue(barSeries.getEndIndex()),
                "Should be 2 positions at end");
    }

    @Test
    void testPartialTradingRecordFiltering() {
        // Create a trading record with trades at different indices
        TradingRecord record = new BaseTradingRecord();
        record.enter(1, barSeries.getBar(1).getClosePrice(), barSeries.numFactory().numOf(1));
        record.exit(3, barSeries.getBar(3).getClosePrice(), barSeries.numFactory().numOf(1));
        record.enter(5, barSeries.getBar(5).getClosePrice(), barSeries.numFactory().numOf(1));
        record.exit(7, barSeries.getBar(7).getClosePrice(), barSeries.numFactory().numOf(1));

        AnalysisCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, criterion, record);

        // At index 2, only the first buy should be included (no sell yet)
        Num valueAt2 = indicator.getValue(2);
        // Create a partial record manually to verify
        TradingRecord partialAt2 = new BaseTradingRecord();
        partialAt2.enter(1, barSeries.getBar(1).getClosePrice(), barSeries.numFactory().numOf(1));
        Num expectedAt2 = criterion.calculate(barSeries, partialAt2);
        assertEquals(expectedAt2, valueAt2, "Value at index 2 should match partial record calculation");

        // At index 4, first position should be complete, second not started
        Num valueAt4 = indicator.getValue(4);
        TradingRecord partialAt4 = new BaseTradingRecord();
        partialAt4.enter(1, barSeries.getBar(1).getClosePrice(), barSeries.numFactory().numOf(1));
        partialAt4.exit(3, barSeries.getBar(3).getClosePrice(), barSeries.numFactory().numOf(1));
        Num expectedAt4 = criterion.calculate(barSeries, partialAt4);
        assertEquals(expectedAt4, valueAt4, "Value at index 4 should match partial record calculation");
    }

    @Test
    void testEmptyTradingRecord() {
        TradingRecord emptyRecord = new BaseTradingRecord();
        AnalysisCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, criterion, emptyRecord);

        // Should return the criterion value for an empty record at all indices
        for (int i = barSeries.getBeginIndex(); i <= barSeries.getEndIndex(); i++) {
            Num value = indicator.getValue(i);
            Num expected = criterion.calculate(barSeries, emptyRecord);
            assertEquals(expected, value, "Value at index " + i + " should match empty record calculation");
        }
    }

    @Test
    void testExpectancyCriterionCalculation() {
        // Create a trading record with one completed position
        TradingRecord record = new BaseTradingRecord();
        record.enter(1, barSeries.getBar(1).getClosePrice(), barSeries.numFactory().numOf(1));
        record.exit(3, barSeries.getBar(3).getClosePrice(), barSeries.numFactory().numOf(1));

        AnalysisCriterion criterion = new ExpectancyCriterion();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, criterion, record);

        // Before any trades
        Num valueBefore = indicator.getValue(0);
        TradingRecord emptyRecord = new BaseTradingRecord();
        Num expectedBefore = criterion.calculate(barSeries, emptyRecord);
        assertEquals(expectedBefore, valueBefore, "Expectancy before trades should match empty record");

        // After position closed
        Num valueAfter = indicator.getValue(3);
        Num expectedAfter = criterion.calculate(barSeries, record);
        assertEquals(expectedAfter, valueAfter, "Expectancy after position closed should match full record");
    }

    @Test
    void testNetProfitCriterionCalculation() {
        // Create a trading record with one completed position
        TradingRecord record = new BaseTradingRecord();
        record.enter(1, barSeries.getBar(1).getClosePrice(), barSeries.numFactory().numOf(1));
        record.exit(3, barSeries.getBar(3).getClosePrice(), barSeries.numFactory().numOf(1));

        AnalysisCriterion criterion = new NetProfitCriterion();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, criterion, record);

        // Before any trades
        Num valueBefore = indicator.getValue(0);
        TradingRecord emptyRecord = new BaseTradingRecord();
        Num expectedBefore = criterion.calculate(barSeries, emptyRecord);
        assertEquals(expectedBefore, valueBefore, "Net profit before trades should be zero or match empty record");

        // After position closed
        Num valueAfter = indicator.getValue(3);
        Num expectedAfter = criterion.calculate(barSeries, record);
        assertEquals(expectedAfter, valueAfter, "Net profit after position closed should match full record");
    }

    @Test
    void testCachingBehavior() {
        AnalysisCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, criterion, tradingRecord);

        // Get value multiple times - should return same result (cached)
        Num value1 = indicator.getValue(3);
        Num value2 = indicator.getValue(3);
        Num value3 = indicator.getValue(3);

        assertEquals(value1, value2, "Cached values should be equal");
        assertEquals(value2, value3, "Cached values should be equal");
        assertSame(value1, value2, "Cached values should be the same instance");
    }

    @Test
    void testCreatePartialLiveTradingRecordPreservesMetadata() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), 1, 9);
        record.setName("Live Record");

        record.recordFill(
                new LiveTrade(0, Instant.EPOCH, barSeries.getBar(0).getClosePrice(), barSeries.numFactory().one(),
                        barSeries.numFactory().numOf(0.1), ExecutionSide.BUY, "order-1", "corr-1"));
        record.recordFill(
                new LiveTrade(1, Instant.EPOCH, barSeries.getBar(1).getClosePrice(), barSeries.numFactory().one(),
                        barSeries.numFactory().numOf(0.1), ExecutionSide.SELL, "order-1", "corr-1"));
        record.recordFill(
                new LiveTrade(2, Instant.EPOCH, barSeries.getBar(2).getClosePrice(), barSeries.numFactory().one(),
                        barSeries.numFactory().numOf(0.1), ExecutionSide.BUY, "order-2", "corr-2"));

        AnalysisCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, criterion, record);

        TradingRecord partialRecord = indicator.createPartialLiveTradingRecord(record, 1);

        assertInstanceOf(LiveTradingRecord.class, partialRecord);
        LiveTradingRecord partialLive = (LiveTradingRecord) partialRecord;
        assertEquals(record.getStartingType(), partialLive.getStartingType());
        assertEquals(record.getMatchPolicy(), partialLive.getMatchPolicy());
        assertTrue(record.getTransactionCostModel().equals(partialLive.getTransactionCostModel()));
        assertTrue(record.getHoldingCostModel().equals(partialLive.getHoldingCostModel()));
        assertEquals(record.getStartIndex(), partialLive.getStartIndex());
        assertEquals(record.getEndIndex(), partialLive.getEndIndex());
        assertEquals(record.getName(), partialLive.getName());

        List<Trade> partialTrades = partialLive.getTrades();
        assertEquals(2, partialTrades.size());
        assertTrue(partialTrades.stream().allMatch(trade -> trade.getIndex() <= 1));
    }

    @Test
    void testCreatePartialLiveTradingRecordRejectsNonLiveTrades() {
        LiveTradingRecord record = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null) {
            @Override
            public List<Trade> getTrades() {
                return List.of(Trade.buyAt(0, barSeries.getBar(0).getClosePrice(), barSeries.numFactory().one()));
            }
        };

        AnalysisCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, criterion, record);

        assertThrows(IllegalArgumentException.class, () -> indicator.createPartialLiveTradingRecord(record, 1));
    }

    @Test
    void testMultiplePositionsAtDifferentIndices() {
        // Create a trading record with three completed positions
        TradingRecord record = new BaseTradingRecord();
        // Position 1: indices 0-2
        record.enter(0, barSeries.getBar(0).getClosePrice(), barSeries.numFactory().numOf(1));
        record.exit(2, barSeries.getBar(2).getClosePrice(), barSeries.numFactory().numOf(1));
        // Position 2: indices 3-5
        record.enter(3, barSeries.getBar(3).getClosePrice(), barSeries.numFactory().numOf(1));
        record.exit(5, barSeries.getBar(5).getClosePrice(), barSeries.numFactory().numOf(1));
        // Position 3: indices 6-8
        record.enter(6, barSeries.getBar(6).getClosePrice(), barSeries.numFactory().numOf(1));
        record.exit(8, barSeries.getBar(8).getClosePrice(), barSeries.numFactory().numOf(1));

        AnalysisCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, criterion, record);

        // Verify progressive counting
        assertEquals(barSeries.numFactory().numOf(0), indicator.getValue(0), "Should be 0 positions at index 0");
        assertEquals(barSeries.numFactory().numOf(1), indicator.getValue(2), "Should be 1 position after first closed");
        assertEquals(barSeries.numFactory().numOf(1), indicator.getValue(3),
                "Should still be 1 position (second not yet closed)");
        assertEquals(barSeries.numFactory().numOf(2), indicator.getValue(5),
                "Should be 2 positions after second closed");
        assertEquals(barSeries.numFactory().numOf(3), indicator.getValue(8),
                "Should be 3 positions after third closed");
    }

    @Test
    void testOpenPositionNotCounted() {
        // Create a trading record with one open position (not closed)
        TradingRecord record = new BaseTradingRecord();
        record.enter(1, barSeries.getBar(1).getClosePrice(), barSeries.numFactory().numOf(1));
        // No exit

        AnalysisCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, criterion, record);

        // Open positions should not be counted
        Num value = indicator.getValue(barSeries.getEndIndex());
        TradingRecord emptyRecord = new BaseTradingRecord();
        Num expected = criterion.calculate(barSeries, emptyRecord);
        assertEquals(expected, value, "Open position should not be counted in number of positions");
    }

    @Test
    void testIndexBeforeFirstTrade() {
        TradingRecord record = new BaseTradingRecord();
        record.enter(5, barSeries.getBar(5).getClosePrice(), barSeries.numFactory().numOf(1));
        record.exit(7, barSeries.getBar(7).getClosePrice(), barSeries.numFactory().numOf(1));

        AnalysisCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, criterion, record);

        // At index 4, before any trades
        Num value = indicator.getValue(4);
        TradingRecord emptyRecord = new BaseTradingRecord();
        Num expected = criterion.calculate(barSeries, emptyRecord);
        assertEquals(expected, value, "Value before first trade should match empty record");
    }

    @Test
    void testIndexAfterLastTrade() {
        TradingRecord record = new BaseTradingRecord();
        record.enter(1, barSeries.getBar(1).getClosePrice(), barSeries.numFactory().numOf(1));
        record.exit(3, barSeries.getBar(3).getClosePrice(), barSeries.numFactory().numOf(1));

        AnalysisCriterion criterion = new NumberOfPositionsCriterion();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, criterion, record);

        // At index after last trade, should include all trades
        Num valueAtEnd = indicator.getValue(barSeries.getEndIndex());
        Num expectedAtEnd = criterion.calculate(barSeries, record);
        assertEquals(expectedAtEnd, valueAtEnd, "Value after last trade should match full record");
    }
}
