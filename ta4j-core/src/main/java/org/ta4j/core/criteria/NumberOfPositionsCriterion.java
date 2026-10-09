/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.List;
import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.AnalysisContext;
import org.ta4j.core.analysis.AnalysisWindow;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.num.Num;

/**
 * Number of position criterion.
 */
public class NumberOfPositionsCriterion extends AbstractAnalysisCriterion {

    /**
     * Position status filter for counted positions.
     *
     * @since 0.22.7
     */
    public enum PositionStatusFilter {
        /**
         * Count closed positions. This is the default and preserves historical
         * behavior.
         */
        CLOSED,
        /** Count open positions. */
        OPEN,
        /** Count both closed and open positions. */
        ALL
    }

    /**
     * If true, then the lower the criterion value the better, otherwise the higher
     * the criterion value the better. This property is only used for
     * {@link #betterThan(Num, Num)}.
     */
    private final boolean lessIsBetter;

    private final PositionStatusFilter statusFilter;
    private final boolean filterSinglePositions;

    /**
     * Constructor with {@link #lessIsBetter} = true.
     */
    public NumberOfPositionsCriterion() {
        this(true, PositionStatusFilter.CLOSED, false);
    }

    /**
     * Constructor.
     *
     * @param lessIsBetter the {@link #lessIsBetter}
     */
    public NumberOfPositionsCriterion(boolean lessIsBetter) {
        this(lessIsBetter, PositionStatusFilter.CLOSED, false);
    }

    /**
     * Constructor with {@link #lessIsBetter} = true.
     *
     * @param statusFilter position status filter to count
     * @since 0.22.7
     */
    public NumberOfPositionsCriterion(PositionStatusFilter statusFilter) {
        this(true, statusFilter, true);
    }

    /**
     * Constructor.
     *
     * @param lessIsBetter the {@link #lessIsBetter}
     * @param statusFilter position status filter to count
     * @since 0.22.7
     */
    public NumberOfPositionsCriterion(boolean lessIsBetter, PositionStatusFilter statusFilter) {
        this(lessIsBetter, statusFilter, true);
    }

    private NumberOfPositionsCriterion(boolean lessIsBetter, PositionStatusFilter statusFilter,
            boolean filterSinglePositions) {
        this.lessIsBetter = lessIsBetter;
        this.statusFilter = Objects.requireNonNull(statusFilter, "statusFilter");
        this.filterSinglePositions = filterSinglePositions;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (!filterSinglePositions) {
            return series.numFactory().one();
        }

        int finalIndex = series.getEndIndex();
        boolean countPosition = switch (statusFilter) {
        case CLOSED -> isClosedAt(position, finalIndex);
        case OPEN -> isOpenAt(position, finalIndex);
        case ALL -> isClosedAt(position, finalIndex) || isOpenAt(position, finalIndex);
        };
        return countPosition ? series.numFactory().one() : series.numFactory().zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (statusFilter == PositionStatusFilter.CLOSED) {
            tradingRecord = boundedTradingRecord(series, tradingRecord);
        }
        return series.numFactory().numOf(countPositions(series, tradingRecord));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord, AnalysisWindow window,
            AnalysisContext context) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(tradingRecord, "tradingRecord");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(context, "context");

        if (series.isEmpty()) {
            return calculate(series, tradingRecord);
        }
        if (statusFilter == PositionStatusFilter.CLOSED) {
            return super.calculate(series, tradingRecord, window, context);
        }

        TradingRecord projectedRecord = projectClosedPositions(series, tradingRecord, window, context);
        int closedPositions = projectedRecord.getPositionCount();
        int openPositions = countOpenPositionsBetween(tradingRecord, projectedRecord.getStartIndex(series),
                projectedRecord.getEndIndex(series));
        int positionCount = statusFilter == PositionStatusFilter.OPEN ? openPositions : closedPositions + openPositions;
        return series.numFactory().numOf(positionCount);
    }

    /**
     * If {@link #lessIsBetter} == false, then the lower the criterion value, the
     * better, otherwise the higher the criterion value the better.
     */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return lessIsBetter ? criterionValue1.isLessThan(criterionValue2)
                : criterionValue1.isGreaterThan(criterionValue2);
    }

    private int countPositions(BarSeries series, TradingRecord tradingRecord) {
        int finalIndex = tradingRecord.getEndIndex(series);
        int closedPositions = countClosedPositionsAtOrBefore(tradingRecord, finalIndex);
        if (statusFilter == PositionStatusFilter.CLOSED) {
            return closedPositions;
        }

        int openPositions = countOpenPositionsAtOrBefore(tradingRecord, finalIndex);
        return statusFilter == PositionStatusFilter.OPEN ? openPositions : closedPositions + openPositions;
    }

    private TradingRecord projectClosedPositions(BarSeries series, TradingRecord tradingRecord, AnalysisWindow window,
            AnalysisContext context) {
        return projectTradingRecord(series, tradingRecord, window,
                context.withOpenPositionHandling(OpenPositionHandling.IGNORE));
    }

    private static int countClosedPositionsAtOrBefore(TradingRecord tradingRecord, int endIndex) {
        int count = 0;
        for (Position position : tradingRecord.getPositions()) {
            if (isClosedAt(position, endIndex)) {
                count++;
            }
        }
        return count;
    }

    private static int countOpenPositionsAtOrBefore(TradingRecord tradingRecord, int endIndex) {
        int count = 0;
        for (Position position : tradingRecord.getPositions()) {
            if (isOpenAt(position, endIndex)) {
                count++;
            }
        }
        List<Position> openPositions = tradingRecord.getOpenPositions();
        if (openPositions != null && !openPositions.isEmpty()) {
            for (Position openPosition : openPositions) {
                if (isOpenAt(openPosition, endIndex)) {
                    count++;
                }
            }
            return count;
        }

        Position currentPosition = tradingRecord.getCurrentPosition();
        return currentPosition != null && currentPosition.isOpened() && isOpenAt(currentPosition, endIndex) ? count + 1
                : count;
    }

    private static int countOpenPositionsBetween(TradingRecord tradingRecord, int startIndex, int endIndex) {
        int count = 0;
        for (Position position : tradingRecord.getPositions()) {
            if (isOpenBetween(position, startIndex, endIndex)) {
                count++;
            }
        }
        List<Position> openPositions = tradingRecord.getOpenPositions();
        if (openPositions != null && !openPositions.isEmpty()) {
            for (Position openPosition : openPositions) {
                if (isOpenBetween(openPosition, startIndex, endIndex)) {
                    count++;
                }
            }
            return count;
        }

        Position currentPosition = tradingRecord.getCurrentPosition();
        return currentPosition != null && currentPosition.isOpened()
                && isOpenBetween(currentPosition, startIndex, endIndex) ? count + 1 : count;
    }

    private static boolean isOpenAt(Position position, int endIndex) {
        if (position == null || position.getEntry() == null || position.getEntry().getIndex() > endIndex) {
            return false;
        }
        return position.getExit() == null || position.getExit().getIndex() > endIndex;
    }

    private static boolean isClosedAt(Position position, int endIndex) {
        return position != null && position.getEntry() != null && position.getEntry().getIndex() <= endIndex
                && position.getExit() != null && position.getExit().getIndex() <= endIndex;
    }

    private static boolean isOpenBetween(Position position, int startIndex, int endIndex) {
        return isOpenAt(position, endIndex) && position.getEntry().getIndex() >= startIndex;
    }

}
