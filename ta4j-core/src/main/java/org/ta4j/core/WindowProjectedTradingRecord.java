/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.Serial;
import java.util.List;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;

/**
 * Record produced by an explicit analysis-window projection.
 *
 * <p>
 * The projection has already selected its positions with the requested
 * {@link org.ta4j.core.analysis.AnalysisContext.PositionInclusionPolicy}, so
 * {@link #getContainedPositions(BarSeries)} returns them unchanged instead of
 * re-applying the stricter record-bounds containment. This keeps windowed
 * results independent of whether a criterion is invoked directly or through a
 * delegating criterion.
 * </p>
 */
final class WindowProjectedTradingRecord extends BaseTradingRecord {

    @Serial
    private static final long serialVersionUID = 1L;

    WindowProjectedTradingRecord(TradeType startingType, int startIndex, int endIndex, CostModel transactionCostModel,
            CostModel holdingCostModel) {
        super(startingType, startIndex, endIndex, transactionCostModel, holdingCostModel);
    }

    @Override
    public List<Position> getContainedPositions(BarSeries series) {
        return getPositions();
    }
}
