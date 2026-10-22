/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.List;
import java.util.stream.IntStream;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Positions that never reach an analysis window, each priced by a holding-cost
 * model that fails when evaluated, so curves can prove they check the window
 * before pricing a position's carry.
 */
final class OutOfWindowPositions {

    /** Last logical index of {@link #series(NumFactory)}. */
    static final int SERIES_END_INDEX = 4;

    private OutOfWindowPositions() {
    }

    /**
     * @return a series whose logical window is {@code [2, 4]} over raw bars
     *         {@code [0, 4]}
     */
    static BarSeries series(NumFactory numFactory) {
        return ConstrainedSeriesSupport.offsetSeries("out-of-window", numFactory, 2, SERIES_END_INDEX, 0, 10d, 11d, 12d,
                13d, 14d);
    }

    /**
     * @return a record whose only position closed before the window begins
     */
    static TradingRecord closedBeforeTheWindow(NumFactory numFactory) {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), failingHoldingCost());
        record.enter(0, numFactory.hundred(), numFactory.one());
        record.exit(1, numFactory.hundred(), numFactory.one());
        return record;
    }

    /**
     * @return a position closed before the window begins, marked through the window
     *         end
     */
    private static Position endsBeforeTheWindow(NumFactory numFactory) {
        return new Position(Trade.buyAt(0, numFactory.hundred(), numFactory.one()),
                Trade.sellAt(1, numFactory.hundred(), numFactory.one()), new ZeroCostModel(), failingHoldingCost());
    }

    /**
     * @return a position entering at the window end, marked through an earlier
     *         final index
     */
    private static Position entersAfterTheFinalIndex(NumFactory numFactory) {
        return new Position(Trade.buyAt(SERIES_END_INDEX, numFactory.hundred(), numFactory.one()), new ZeroCostModel(),
                failingHoldingCost());
    }

    /**
     * @return a position entering after the captured series end
     */
    private static Position entersAfterTheSeriesEnd(NumFactory numFactory) {
        return new Position(Trade.buyAt(SERIES_END_INDEX + 1, numFactory.hundred(), numFactory.one()),
                new ZeroCostModel(), failingHoldingCost());
    }

    /**
     * Runs {@code calculatePosition} for every out-of-window position.
     */
    static void calculateAll(PerformanceIndicator curve) {
        NumFactory numFactory = curve.getBarSeries().numFactory();
        curve.calculatePosition(endsBeforeTheWindow(numFactory), SERIES_END_INDEX);
        curve.calculatePosition(entersAfterTheFinalIndex(numFactory), SERIES_END_INDEX - 1);
        curve.calculatePosition(entersAfterTheSeriesEnd(numFactory), SERIES_END_INDEX + 1);
    }

    /**
     * @return a curve's values over its window
     */
    static List<Num> values(PerformanceIndicator curve) {
        return IntStream.rangeClosed(curve.getBeginIndex(), curve.getEndIndex()).mapToObj(curve::getValue).toList();
    }

    private static CostModel failingHoldingCost() {
        return new CostModel() {
            @Override
            public Num calculate(Position position, int finalIndex) {
                throw new AssertionError("holding cost evaluated for a position outside the window");
            }

            @Override
            public Num calculate(Position position) {
                throw new AssertionError("holding cost evaluated for a position outside the window");
            }

            @Override
            public Num calculate(Num price, Num amount) {
                throw new AssertionError("holding cost evaluated for a position outside the window");
            }

            @Override
            public boolean equals(CostModel otherModel) {
                return otherModel == this;
            }
        };
    }
}
