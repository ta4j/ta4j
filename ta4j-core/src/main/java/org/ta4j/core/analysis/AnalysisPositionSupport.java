/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.LiveTradingRecord;
import org.ta4j.core.OpenPosition;
import org.ta4j.core.Position;
import org.ta4j.core.PositionLot;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;

final class AnalysisPositionSupport {

    private AnalysisPositionSupport() {
    }

    static List<Position> positionsForAnalysis(TradingRecord record, int finalIndex,
            OpenPositionHandling openPositionHandling, EquityCurveMode equityCurveMode) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling");
        Objects.requireNonNull(equityCurveMode, "equityCurveMode");
        var positions = new ArrayList<Position>();
        for (var position : record.getPositions()) {
            if (shouldIncludePosition(position, finalIndex, openPositionHandling, equityCurveMode)) {
                positions.add(position);
            }
        }
        if (shouldIncludeOpenPositions(openPositionHandling, equityCurveMode)) {
            positions.addAll(openPositions(record, finalIndex));
        }
        return positions;
    }

    static boolean shouldIncludePosition(Position position, int finalIndex, OpenPositionHandling openPositionHandling,
            EquityCurveMode equityCurveMode) {
        if (position == null || position.getEntry() == null) {
            return false;
        }
        var entryIndex = position.getEntry().getIndex();
        if (entryIndex > finalIndex) {
            return false;
        }
        if (!shouldIncludeOpenPositions(openPositionHandling, equityCurveMode)) {
            var exit = position.getExit();
            var isOpenAtFinalIndex = exit == null || exit.getIndex() > finalIndex;
            return !isOpenAtFinalIndex;
        }
        return true;
    }

    static boolean shouldIncludeOpenPositions(OpenPositionHandling openPositionHandling,
            EquityCurveMode equityCurveMode) {
        return equityCurveMode != EquityCurveMode.REALIZED
                && openPositionHandling == OpenPositionHandling.MARK_TO_MARKET;
    }

    static List<Position> openPositions(TradingRecord record, int finalIndex) {
        if (record instanceof LiveTradingRecord liveRecord) {
            return openPositionsFromLiveRecord(liveRecord, finalIndex);
        }
        var positions = new ArrayList<Position>();
        var current = record.getCurrentPosition();
        if (current != null && current.isOpened() && current.getEntry() != null
                && current.getEntry().getIndex() <= finalIndex) {
            positions.add(current);
        }
        return positions;
    }

    private static List<Position> openPositionsFromLiveRecord(LiveTradingRecord record, int finalIndex) {
        var positions = new ArrayList<Position>();
        var transactionCostModel = defaultCostModel(record.getTransactionCostModel());
        var holdingCostModel = defaultCostModel(record.getHoldingCostModel());
        var startingType = record.getStartingType();
        for (OpenPosition openPosition : record.getOpenPositions()) {
            for (PositionLot lot : openPosition.lots()) {
                if (lot.entryIndex() > finalIndex) {
                    continue;
                }
                var position = new Position(startingType, transactionCostModel, holdingCostModel);
                position.operate(lot.entryIndex(), lot.entryPrice(), lot.amount());
                positions.add(position);
            }
        }
        return positions;
    }

    private static CostModel defaultCostModel(CostModel costModel) {
        return costModel == null ? new ZeroCostModel() : costModel;
    }
}
