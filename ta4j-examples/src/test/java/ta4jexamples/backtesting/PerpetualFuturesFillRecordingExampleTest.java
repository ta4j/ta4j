/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.criteria.commissions.TotalFeesCriterion;
import org.ta4j.core.criteria.pnl.GrossProfitLossCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;

class PerpetualFuturesFillRecordingExampleTest {

    @Test
    void reconcilesFillsAndBothFundingSigns() {
        BaseTradingRecord record = PerpetualFuturesFillRecordingExample.buildRecord();

        assertEquals(3, record.getPositionCount());
        assertEquals(0, record.getOpenPositions().size());
        assertEquals(25.0,
                new GrossProfitLossCriterion().calculate(PerpetualFuturesFillRecordingExample.ANALYSIS_SERIES, record)
                        .doubleValue(),
                1e-9);
        assertEquals(6.0,
                new TotalFeesCriterion().calculate(PerpetualFuturesFillRecordingExample.ANALYSIS_SERIES, record)
                        .doubleValue(),
                1e-9);
        assertEquals(0.0, record.getPositions().stream().mapToDouble(p -> p.getHoldingCost(2).doubleValue()).sum(),
                1e-9);
        assertEquals(6.0, record.getPositions().stream().mapToDouble(p -> p.getHoldingCost(3).doubleValue()).sum(),
                1e-9);
        assertEquals(4.0, record.getPositions().stream().mapToDouble(p -> p.getHoldingCost(5).doubleValue()).sum(),
                1e-9);
        assertEquals(4.0, record.getPositions().stream().mapToDouble(p -> p.getHoldingCost().doubleValue()).sum(),
                1e-9);
        assertEquals(15.0,
                new NetProfitLossCriterion().calculate(PerpetualFuturesFillRecordingExample.ANALYSIS_SERIES, record)
                        .doubleValue(),
                1e-9);

        Position firstClose = record.getPositions().get(0);
        assertEquals(1.0, firstClose.getEntry().getAmount().doubleValue(), 1e-9);
        assertEquals(2.0, firstClose.getHoldingCost().doubleValue(), 1e-9);
        assertEquals(6.0, firstClose.getProfit().doubleValue(), 1e-9);
    }
}
