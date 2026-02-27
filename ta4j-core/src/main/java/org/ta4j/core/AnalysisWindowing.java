/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.AnalysisContext.MissingHistoryPolicy;
import org.ta4j.core.AnalysisContext.OpenPositionPolicy;
import org.ta4j.core.AnalysisContext.PositionInclusionPolicy;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;

/**
 * Utilities for resolving and projecting analysis windows.
 */
final class AnalysisWindowing {

    private AnalysisWindowing() {
    }

    /**
     * Window resolved against a concrete bar series.
     *
     * @param startIndexInclusive start index used for record scoping
     * @param endIndexInclusive   end index used for record scoping
     * @param hasBars             true if at least one bar matches the requested
     *                            window
     */
    record ResolvedWindow(int startIndexInclusive, int endIndexInclusive, boolean hasBars) {
    }

    /**
     * Resolves the requested analysis window to concrete series indices.
     *
     * @param series  target series
     * @param window  requested window
     * @param context resolution context
     * @return resolved window
     */
    static ResolvedWindow resolve(BarSeries series, AnalysisWindow window, AnalysisContext context) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(context, "context");

        if (series.isEmpty()) {
            return new ResolvedWindow(0, -1, false);
        }

        int availableStart = series.getBeginIndex();
        int availableEnd = series.getEndIndex();
        var requested = requestedRange(series, window, context, availableStart, availableEnd);

        if (context.missingHistoryPolicy() == MissingHistoryPolicy.STRICT
                && (requested.lowerBoundBeforeAvailable() || requested.upperBoundAfterAvailable())) {
            throw unavailableHistoryException(requested, availableStart, availableEnd);
        }

        int resolvedStart = requested.startIndexInclusive();
        int resolvedEnd = requested.endIndexInclusive();

        if (context.missingHistoryPolicy() == MissingHistoryPolicy.CLAMP) {
            resolvedStart = Math.max(resolvedStart, availableStart);
            resolvedEnd = Math.min(resolvedEnd, availableEnd);
        }

        if (context.missingHistoryPolicy() == MissingHistoryPolicy.STRICT
                && (resolvedStart < availableStart || resolvedEnd > availableEnd)) {
            throw unavailableHistoryException(requested, availableStart, availableEnd);
        }

        if (requested.empty() || resolvedStart > resolvedEnd) {
            int anchor = Math.min(Math.max(resolvedStart, availableStart), availableEnd);
            return new ResolvedWindow(anchor, anchor, false);
        }
        return new ResolvedWindow(resolvedStart, resolvedEnd, true);
    }

    private static IllegalArgumentException unavailableHistoryException(RequestedRange requested, int availableStart,
            int availableEnd) {
        String message = String.format("Requested window [%d, %d] is outside available series range [%d, %d]",
                requested.startIndexInclusive(), requested.endIndexInclusive(), availableStart, availableEnd);
        return new IllegalArgumentException(message);
    }

    private static RequestedRange requestedRange(BarSeries series, AnalysisWindow window, AnalysisContext context,
            int availableStart, int availableEnd) {
        return switch (window) {
        case AnalysisWindow.BarRange barRange -> {
            int start = barRange.startIndexInclusive();
            int end = barRange.endIndexInclusive();
            yield new RequestedRange(start, end, false, start < availableStart, end > availableEnd);
        }
        case AnalysisWindow.LookbackBars lookbackBars ->
            requestedLookbackBars(series, lookbackBars, context, availableStart, availableEnd);
        case AnalysisWindow.TimeRange timeRange -> requestedTimeRange(series, timeRange.startInclusive(),
                timeRange.endExclusive(), availableStart, availableEnd);
        case AnalysisWindow.LookbackDuration lookbackDuration ->
            requestedLookbackDuration(series, lookbackDuration, context, availableStart, availableEnd);
        };
    }

    private static RequestedRange requestedLookbackBars(BarSeries series, AnalysisWindow.LookbackBars lookbackBars,
            AnalysisContext context, int availableStart, int availableEnd) {
        Instant asOf = context.asOf();
        Instant availableStartTime = series.getBar(availableStart).getEndTime();
        Instant availableEndExclusive = series.getBar(availableEnd).getEndTime().plusNanos(1);
        boolean lowerOut = asOf != null && asOf.isBefore(availableStartTime);
        boolean upperOut = asOf != null && asOf.isAfter(availableEndExclusive);

        int anchor = asOf == null ? availableEnd : findLastIndexAtOrBefore(series, asOf, availableStart, availableEnd);
        if (anchor < 0) {
            return new RequestedRange(availableStart, availableStart - 1, true, true, upperOut);
        }

        int start = anchor - lookbackBars.barCount() + 1;
        int end = anchor;
        lowerOut = lowerOut || start < availableStart;
        upperOut = upperOut || end > availableEnd;
        return new RequestedRange(start, end, false, lowerOut, upperOut);
    }

    private static RequestedRange requestedLookbackDuration(BarSeries series,
            AnalysisWindow.LookbackDuration lookbackDuration, AnalysisContext context, int availableStart,
            int availableEnd) {
        Instant endExclusive = context.asOf() != null ? context.asOf()
                : series.getBar(availableEnd).getEndTime().plusNanos(1);
        Instant startInclusive = endExclusive.minus(lookbackDuration.duration());
        return requestedTimeRange(series, startInclusive, endExclusive, availableStart, availableEnd);
    }

    private static RequestedRange requestedTimeRange(BarSeries series, Instant startInclusive, Instant endExclusive,
            int availableStart, int availableEnd) {
        Instant availableStartTime = series.getBar(availableStart).getEndTime();
        Instant availableEndExclusive = series.getBar(availableEnd).getEndTime().plusNanos(1);
        boolean lowerOut = startInclusive.isBefore(availableStartTime);
        boolean upperOut = endExclusive.isAfter(availableEndExclusive);

        int start = findFirstIndexAtOrAfter(series, startInclusive, availableStart, availableEnd);
        int end = findLastIndexBefore(series, endExclusive, availableStart, availableEnd);
        boolean empty = start < 0 || end < 0 || start > end;
        if (empty) {
            return new RequestedRange(availableStart, availableStart - 1, true, lowerOut, upperOut);
        }
        return new RequestedRange(start, end, false, lowerOut, upperOut);
    }

    private static int findLastIndexAtOrBefore(BarSeries series, Instant asOf, int availableStart, int availableEnd) {
        for (int i = availableEnd; i >= availableStart; i--) {
            if (!series.getBar(i).getEndTime().isAfter(asOf)) {
                return i;
            }
        }
        return -1;
    }

    private static int findFirstIndexAtOrAfter(BarSeries series, Instant startInclusive, int availableStart,
            int availableEnd) {
        for (int i = availableStart; i <= availableEnd; i++) {
            if (!series.getBar(i).getEndTime().isBefore(startInclusive)) {
                return i;
            }
        }
        return -1;
    }

    private static int findLastIndexBefore(BarSeries series, Instant endExclusive, int availableStart,
            int availableEnd) {
        for (int i = availableEnd; i >= availableStart; i--) {
            if (series.getBar(i).getEndTime().isBefore(endExclusive)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Builds a read-only projected trading record scoped to a resolved window.
     *
     * @param series   series used for mark-to-market operations
     * @param source   source trading record
     * @param resolved resolved analysis window
     * @param context  projection context
     * @return projected read-only trading record
     */
    static TradingRecord projectTradingRecord(BarSeries series, TradingRecord source, ResolvedWindow resolved,
            AnalysisContext context) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(resolved, "resolved");
        Objects.requireNonNull(context, "context");

        var txModel = Objects.requireNonNullElseGet(source.getTransactionCostModel(), ZeroCostModel::new);
        var holdingModel = Objects.requireNonNullElseGet(source.getHoldingCostModel(), ZeroCostModel::new);
        var start = resolved.startIndexInclusive();
        var end = resolved.endIndexInclusive();
        var inclusionPolicy = context.positionInclusionPolicy();

        var positions = new ArrayList<Position>();
        if (resolved.hasBars()) {
            for (Position position : source.getPositions()) {
                if (includeClosedPosition(position, start, end, inclusionPolicy)) {
                    positions.add(position);
                }
            }
        }

        var currentPosition = new Position(source.getStartingType(), txModel, holdingModel);
        if (resolved.hasBars() && context.openPositionPolicy() == OpenPositionPolicy.MARK_TO_MARKET_AT_WINDOW_END) {
            Position synthetic = createMarkToMarketPosition(series, source.getCurrentPosition(), end, holdingModel);
            if (synthetic != null && includeClosedPosition(synthetic, start, end, inclusionPolicy)) {
                positions.add(synthetic);
                positions.sort(Comparator.comparingInt(p -> p.getExit().getIndex()));
            }
        }

        return new WindowedTradingRecord(source.getName(), source.getStartingType(), start, end, txModel, holdingModel,
                positions, currentPosition);
    }

    private static Position createMarkToMarketPosition(BarSeries series, Position current, int windowEndIndex,
            CostModel holdingModel) {
        if (current == null || !current.isOpened()) {
            return null;
        }
        Trade entry = current.getEntry();
        if (entry == null || entry.getIndex() > windowEndIndex) {
            return null;
        }

        var amount = entry.getAmount();
        var closePrice = series.getBar(windowEndIndex).getClosePrice();
        var transactionCostModel = entry.getCostModel();
        Trade exit = entry.isBuy() ? Trade.sellAt(windowEndIndex, closePrice, amount, transactionCostModel)
                : Trade.buyAt(windowEndIndex, closePrice, amount, transactionCostModel);

        return new Position(entry, exit, transactionCostModel, holdingModel);
    }

    private static boolean includeClosedPosition(Position position, int start, int end,
            PositionInclusionPolicy positionInclusionPolicy) {
        if (position == null || !position.isClosed()) {
            return false;
        }
        int entry = position.getEntry().getIndex();
        int exit = position.getExit().getIndex();
        return switch (positionInclusionPolicy) {
        case EXIT_IN_WINDOW -> exit >= start && exit <= end;
        case FULLY_CONTAINED -> entry >= start && exit <= end;
        };
    }

    /**
     * Requested raw range before strict/clamp normalization.
     *
     * @param startIndexInclusive       requested start index
     * @param endIndexInclusive         requested end index
     * @param empty                     true when no bars match the requested range
     * @param lowerBoundBeforeAvailable true when lower bound is before available
     *                                  history
     * @param upperBoundAfterAvailable  true when upper bound is after available
     *                                  history
     */
    private record RequestedRange(int startIndexInclusive, int endIndexInclusive, boolean empty,
            boolean lowerBoundBeforeAvailable, boolean upperBoundAfterAvailable) {
    }
}
