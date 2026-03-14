/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;

public class TradeFillRecordingExampleTest {

    @Test
    public void test() {
        TradeFillRecordingExample.main(null);
    }

    @Test
    public void streamingTradeFillsMatchGroupedTradeRecording() {
        BaseTradingRecord streamingRecord = TradeFillRecordingExample.buildStreamingRecord();
        BaseTradingRecord groupedTradeRecord = TradeFillRecordingExample.buildGroupedTradeRecord();

        TradeFillRecordingExample.assertEquivalent(streamingRecord, groupedTradeRecord, "example parity");

        assertEquals(4, streamingRecord.getTrades().size());
        assertEquals(2, streamingRecord.getPositionCount());
        assertEquals(0.41, streamingRecord.getRecordedTotalFees().doubleValue(), 1.0e-10);
        assertEquals(29.59, TradeFillRecordingExample.totalClosedProfit(streamingRecord).doubleValue(), 1.0e-10);
    }
}
