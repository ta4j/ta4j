/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Objects;
import java.util.function.Supplier;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

public class TradeFillRecordingExampleTest {

    @Test
    public void test() {
        TradeFillRecordingExample.main(null);
    }

    @Test
    public void streamingTradeFillsMatchGroupedTradeRecording() {
        BaseTradingRecord streamingRecord = TradeFillRecordingExample.buildStreamingRecord();
        BaseTradingRecord groupedTradeRecord = TradeFillRecordingExample.buildGroupedTradeRecord();

        assertEquivalent(streamingRecord, groupedTradeRecord, "example parity");

        assertEquals(4, streamingRecord.getTrades().size());
        assertEquals(2, streamingRecord.getPositionCount());
        assertEquals(0.41, streamingRecord.getRecordedTotalFees().doubleValue(), 1.0e-10);
        assertEquals(29.59, TradeFillRecordingExample.totalClosedProfit(streamingRecord).doubleValue(), 1.0e-10);
    }

    @Test
    public void exampleProducesTwoClosedWinningPositions() {
        BaseTradingRecord streamingRecord = TradeFillRecordingExample.buildStreamingRecord();

        assertTrue(streamingRecord.isClosed());
        assertEquals(0, streamingRecord.getOpenPositions().size());
        assertEquals(2, streamingRecord.getPositions().size());
        assertEquals(9.85, streamingRecord.getPositions().get(0).getProfit().doubleValue(), 1.0e-10);
        assertEquals(19.74, streamingRecord.getPositions().get(1).getProfit().doubleValue(), 1.0e-10);
    }

    @Test
    public void matchingPoliciesShowHowPartialExitsCloseDifferentLots() {
        BaseTradingRecord fifoRecord = TradeFillRecordingExample.buildMatchingPolicyRecord(ExecutionMatchPolicy.FIFO);
        BaseTradingRecord lifoRecord = TradeFillRecordingExample.buildMatchingPolicyRecord(ExecutionMatchPolicy.LIFO);
        BaseTradingRecord averageCostRecord = TradeFillRecordingExample
                .buildMatchingPolicyRecord(ExecutionMatchPolicy.AVG_COST);
        BaseTradingRecord specificIdRecord = TradeFillRecordingExample
                .buildMatchingPolicyRecord(ExecutionMatchPolicy.SPECIFIC_ID);

        assertPosition(fifoRecord.getPositions().get(0), "lot-a", 100.0, 1.0, 20.0);
        assertPosition(lifoRecord.getPositions().get(0), "lot-b", 106.0, 1.0, 14.0);
        assertPosition(averageCostRecord.getPositions().get(0), null, 102.0, 1.0, 18.0);
        assertPosition(specificIdRecord.getPositions().get(0), "lot-b", 106.0, 1.0, 14.0);

        assertOpenExposure(fifoRecord, 2, 103.0);
        assertOpenExposure(lifoRecord, 2, 100.0);
        assertOpenExposure(averageCostRecord, 2, 102.0);
        assertOpenExposure(specificIdRecord, 2, 100.0);

        assertEquals(2, fifoRecord.getOpenPositions().size());
        assertEquals(1, lifoRecord.getOpenPositions().size());
        assertEquals(1, averageCostRecord.getOpenPositions().size());
        assertEquals(1, specificIdRecord.getOpenPositions().size());

        assertOpenLot(fifoRecord.getOpenPositions().get(0), "lot-a", 100.0, 1.0);
        assertOpenLot(fifoRecord.getOpenPositions().get(1), "lot-b", 106.0, 1.0);
        assertOpenLot(lifoRecord.getOpenPositions().get(0), "lot-a", 100.0, 2.0);
        assertOpenLot(averageCostRecord.getOpenPositions().get(0), null, 102.0, 2.0);
        assertOpenLot(specificIdRecord.getOpenPositions().get(0), "lot-a", 100.0, 2.0);
    }

    private static void assertEquivalent(TradingRecord expected, TradingRecord actual, String label) {
        require(expected.getTrades().size() == actual.getTrades().size(), () -> label + ": trade count mismatch");
        require(expected.getPositionCount() == actual.getPositionCount(), () -> label + ": position count mismatch");
        assertNum(expected.getRecordedTotalFees(), actual.getRecordedTotalFees(), label + ": fee mismatch");

        for (int i = 0; i < expected.getTrades().size(); i++) {
            Trade left = expected.getTrades().get(i);
            Trade right = actual.getTrades().get(i);
            assertTrade(left, right, label + ": trade[" + i + "]");
        }

        for (int i = 0; i < expected.getPositions().size(); i++) {
            Position left = expected.getPositions().get(i);
            Position right = actual.getPositions().get(i);
            assertTrade(left.getEntry(), right.getEntry(), label + ": position[" + i + "].entry");
            assertTrade(left.getExit(), right.getExit(), label + ": position[" + i + "].exit");
            assertNum(left.getProfit(), right.getProfit(), label + ": position[" + i + "].profit");
        }
    }

    private static void assertTrade(Trade expected, Trade actual, String label) {
        require(expected.getType() == actual.getType(), () -> label + ": type mismatch");
        require(expected.getIndex() == actual.getIndex(), () -> label + ": index mismatch");
        assertNum(expected.getPricePerAsset(), actual.getPricePerAsset(), label + ": price mismatch");
        assertNum(expected.getAmount(), actual.getAmount(), label + ": amount mismatch");
        assertNum(expected.getCost(), actual.getCost(), label + ": fee mismatch");
        require(Objects.equals(expected.getOrderId(), actual.getOrderId()), () -> label + ": orderId mismatch");
        require(Objects.equals(expected.getCorrelationId(), actual.getCorrelationId()),
                () -> label + ": correlationId mismatch");
    }

    private static void assertPosition(Position position, String expectedCorrelationId, double expectedPrice,
            double expectedAmount, double expectedProfit) {
        require(Objects.equals(expectedCorrelationId, position.getEntry().getCorrelationId()),
                () -> "Unexpected closed lot correlationId");
        assertEquals(expectedPrice, position.getEntry().getPricePerAsset().doubleValue(), 1.0e-10);
        assertEquals(expectedAmount, position.getEntry().getAmount().doubleValue(), 1.0e-10);
        assertEquals(expectedProfit, position.getProfit().doubleValue(), 1.0e-10);
    }

    private static void assertOpenExposure(TradingRecord record, double expectedAmount,
            double expectedAverageEntryPrice) {
        assertEquals(expectedAmount, record.getCurrentPosition().amount().doubleValue(), 1.0e-10);
        assertEquals(expectedAverageEntryPrice, record.getCurrentPosition().averageEntryPrice().doubleValue(), 1.0e-10);
    }

    private static void assertOpenLot(Position position, String expectedCorrelationId, double expectedPrice,
            double expectedAmount) {
        require(Objects.equals(expectedCorrelationId, position.getEntry().getCorrelationId()),
                () -> "Unexpected open lot correlationId");
        assertEquals(expectedPrice, position.averageEntryPrice().doubleValue(), 1.0e-10);
        assertEquals(expectedAmount, position.amount().doubleValue(), 1.0e-10);
    }

    private static void assertNum(Num expected, Num actual, String message) {
        if (expected == null || actual == null) {
            require(Objects.equals(expected, actual), () -> message);
            return;
        }
        require(expected.isEqual(actual), () -> message + " expected=" + expected + ", actual=" + actual);
    }

    private static void require(boolean condition, Supplier<String> messageSupplier) {
        if (!condition) {
            throw new IllegalStateException(messageSupplier.get());
        }
    }
}
