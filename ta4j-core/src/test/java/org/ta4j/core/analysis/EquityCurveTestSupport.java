/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.time.Instant;
import java.util.SplittableRandom;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTrade;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

/** Shared deterministic inputs for curve equivalence and opt-in measurement. */
final class EquityCurveTestSupport {

    static final long SEED = 634_2026L;

    private EquityCurveTestSupport() {
    }

    static BarSeries series(NumFactory factory, int bars, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        double[] closes = new double[bars];
        for (int index = 0; index < bars; index++) {
            closes[index] = 100 + 8 * Math.sin(index / 13.0) + random.nextDouble(-2, 2);
        }
        return new MockBarSeriesBuilder().withNumFactory(factory).withData(closes).build();
    }

    /**
     * Produces {@code positions} unit entry lots (including two open lots, combined
     * into one open position under AVG_COST). The first two closes arrive out of
     * index order, another lot closes on entry's bar, and later entries/exits vary
     * by seed. All fills carry linear costs.
     */
    static BaseTradingRecord record(BarSeries series, int positions, ExecutionMatchPolicy policy, long seed) {
        TradeType type = (seed & 1) == 0 ? TradeType.BUY : TradeType.SELL;
        BaseTradingRecord record = new BaseTradingRecord(type, policy, new LinearTransactionCostModel(0.001),
                new LinearBorrowingCostModel(0.0001, LinearBorrowingCostModel.Applicability.BOTH), null, null);
        ExecutionSide entry = type == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
        ExecutionSide exit = type == TradeType.BUY ? ExecutionSide.SELL : ExecutionSide.BUY;
        fill(record, series, 0, entry);
        fill(record, series, 2, entry);
        fill(record, series, 8, exit);
        fill(record, series, 5, exit);
        fill(record, series, 10, entry);
        fill(record, series, 10, exit);
        SplittableRandom random = new SplittableRandom(seed);
        int stride = Math.max(2, (series.getBarCount() - 16) / positions);
        for (int lot = 3; lot < positions - 2; lot++) {
            int start = 12 + (lot - 3) * stride;
            fill(record, series, start, entry);
            fill(record, series, start + random.nextInt(1, stride), exit);
        }
        fill(record, series, series.getEndIndex() - 2, entry);
        fill(record, series, series.getEndIndex() - 1, entry);
        return record;
    }

    private static void fill(BaseTradingRecord record, BarSeries series, int index, ExecutionSide side) {
        record.operate(
                new BaseTrade(index, Instant.EPOCH.plusSeconds(index), series.getBar(index).getClosePrice(),
                        series.numFactory().one(),
                        record.getTransactionCostModel()
                                .calculate(series.getBar(index).getClosePrice(), series.numFactory().one()),
                        side, null, null));
    }
}
