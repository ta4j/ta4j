/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.*;
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
        List<Position> positions = new ArrayList<>();
        for (Position position : record.getPositions()) {
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
        int entryIndex = position.getEntry().getIndex();
        if (entryIndex > finalIndex) {
            return false;
        }
        if (!shouldIncludeOpenPositions(openPositionHandling, equityCurveMode)) {
            Trade exit = position.getExit();
            boolean isOpenAtFinalIndex = exit == null || exit.getIndex() > finalIndex;
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
        List<Position> openPositions = record.getOpenPositions();
        if (!openPositions.isEmpty()) {
            return openPositionsWithinRange(openPositions, finalIndex);
        }
        List<Position> positions = new ArrayList<>();
        Position current = record.getCurrentPosition();
        if (current != null && current.isOpened() && current.getEntry() != null
                && current.getEntry().getIndex() <= finalIndex) {
            positions.add(current);
        }
        return positions;
    }

    private static List<Position> openPositionsWithinRange(List<Position> openPositions, int finalIndex) {
        List<Position> positions = new ArrayList<>();
        for (Position openPosition : openPositions) {
            if (openPosition == null || !openPosition.isOpened()) {
                continue;
            }
            if (openPosition.getEntry().getIndex() > finalIndex) {
                continue;
            }
            positions.add(openPosition);
        }
        return positions;
    }
}
