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
