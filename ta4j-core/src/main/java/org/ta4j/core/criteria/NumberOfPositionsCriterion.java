/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
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

    /**
     * Constructor with {@link #lessIsBetter} = true.
     */
    public NumberOfPositionsCriterion() {
        this(true, PositionStatusFilter.CLOSED);
    }

    /**
     * Constructor.
     *
     * @param lessIsBetter the {@link #lessIsBetter}
     */
    public NumberOfPositionsCriterion(boolean lessIsBetter) {
        this(lessIsBetter, PositionStatusFilter.CLOSED);
    }

    /**
     * Constructor with {@link #lessIsBetter} = true.
     *
     * @param statusFilter position status filter to count
     * @since 0.22.7
     */
    public NumberOfPositionsCriterion(PositionStatusFilter statusFilter) {
        this(true, statusFilter);
    }

    /**
     * Constructor.
     *
     * @param lessIsBetter the {@link #lessIsBetter}
     * @param statusFilter position status filter to count
     * @since 0.22.7
     */
    public NumberOfPositionsCriterion(boolean lessIsBetter, PositionStatusFilter statusFilter) {
        this.lessIsBetter = lessIsBetter;
        this.statusFilter = Objects.requireNonNull(statusFilter, "statusFilter");
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return switch (statusFilter) {
        case CLOSED, ALL -> series.numFactory().one();
        case OPEN -> position != null && position.isOpened() ? series.numFactory().one() : series.numFactory().zero();
        };
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
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
        int closedPositions = tradingRecord.getPositionCount();
        if (statusFilter == PositionStatusFilter.CLOSED) {
            return closedPositions;
        }

        int openPositions = countOpenPositionsAtOrBefore(tradingRecord, tradingRecord.getEndIndex(series));
        return statusFilter == PositionStatusFilter.OPEN ? openPositions : closedPositions + openPositions;
    }

    private TradingRecord projectClosedPositions(BarSeries series, TradingRecord tradingRecord, AnalysisWindow window,
            AnalysisContext context) {
        AtomicReference<TradingRecord> projectedRecordRef = new AtomicReference<>();
        AnalysisContext closedOnlyContext = context.withOpenPositionHandling(OpenPositionHandling.IGNORE);
        new ProjectionCaptureCriterion(projectedRecordRef).calculate(series, tradingRecord, window, closedOnlyContext);
        return Objects.requireNonNull(projectedRecordRef.get(), "projectedRecord");
    }

    private static int countOpenPositionsAtOrBefore(TradingRecord tradingRecord, int endIndex) {
        List<Position> openPositions = tradingRecord.getOpenPositions();
        if (openPositions != null && !openPositions.isEmpty()) {
            int count = 0;
            for (Position openPosition : openPositions) {
                if (isOpenedAtOrBefore(openPosition, endIndex)) {
                    count++;
                }
            }
            return count;
        }

        Position currentPosition = tradingRecord.getCurrentPosition();
        return isOpenedAtOrBefore(currentPosition, endIndex) ? 1 : 0;
    }

    private static int countOpenPositionsBetween(TradingRecord tradingRecord, int startIndex, int endIndex) {
        List<Position> openPositions = tradingRecord.getOpenPositions();
        if (openPositions != null && !openPositions.isEmpty()) {
            int count = 0;
            for (Position openPosition : openPositions) {
                if (isOpenedBetween(openPosition, startIndex, endIndex)) {
                    count++;
                }
            }
            return count;
        }

        Position currentPosition = tradingRecord.getCurrentPosition();
        return isOpenedBetween(currentPosition, startIndex, endIndex) ? 1 : 0;
    }

    private static boolean isOpenedAtOrBefore(Position position, int endIndex) {
        return position != null && position.isOpened() && position.getEntry() != null
                && position.getEntry().getIndex() <= endIndex;
    }

    private static boolean isOpenedBetween(Position position, int startIndex, int endIndex) {
        return isOpenedAtOrBefore(position, endIndex) && position.getEntry().getIndex() >= startIndex;
    }

    private static final class ProjectionCaptureCriterion extends AbstractAnalysisCriterion {
        private final AtomicReference<TradingRecord> projectedRecordRef;

        private ProjectionCaptureCriterion(AtomicReference<TradingRecord> projectedRecordRef) {
            this.projectedRecordRef = Objects.requireNonNull(projectedRecordRef, "projectedRecordRef");
        }

        @Override
        public Num calculate(BarSeries series, Position position) {
            return series.numFactory().zero();
        }

        @Override
        public Num calculate(BarSeries series, TradingRecord tradingRecord) {
            projectedRecordRef.set(tradingRecord);
            return series.numFactory().zero();
        }

        @Override
        public boolean betterThan(Num criterionValue1, Num criterionValue2) {
            return false;
        }
    }
}
