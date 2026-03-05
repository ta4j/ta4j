/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.AnalysisContext;
import org.ta4j.core.analysis.AnalysisWindow;
import org.ta4j.core.analysis.AnalysisContext.MissingHistoryPolicy;
import org.ta4j.core.analysis.AnalysisContext.PositionInclusionPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.num.Num;

/**
 * An analysis criterion. It can be used to:
 *
 * <ul>
 * <li>analyze the performance of a {@link Strategy strategy}
 * <li>compare several {@link Strategy strategies} together
 * </ul>
 */
public interface AnalysisCriterion {

    /** Filter to differentiate between winning or losing positions. */
    enum PositionFilter {
        /** Consider only winning positions. */
        PROFIT,
        /** Consider only losing positions. */
        LOSS;
    }

    /**
     * @param series   the bar series, not null
     * @param position the position, not null
     * @return the criterion value for the position
     */
    Num calculate(BarSeries series, Position position);

    /**
     * @param series        the bar series, not null
     * @param tradingRecord the trading record, not null
     * @return the criterion value for the positions
     */
    Num calculate(BarSeries series, TradingRecord tradingRecord);

    /**
     * Calculates this criterion over a specific analysis window using
     * {@link AnalysisContext#defaults() default context options}.
     *
     * <p>
     * Examples:
     * </p>
     * <ul>
     * <li>Past 7 days:
     * {@code criterion.calculate(series, record, AnalysisWindow.lookbackDuration(Duration.ofDays(7)))}</li>
     * <li>Past 30 days:
     * {@code criterion.calculate(series, record, AnalysisWindow.lookbackDuration(Duration.ofDays(30)))}</li>
     * <li>Explicit date range:
     * {@code criterion.calculate(series, record, AnalysisWindow.timeRange(Instant.parse("2026-02-10T00:00:00Z"), Instant.parse("2026-02-14T00:00:00Z")))}</li>
     * </ul>
     *
     * @param series        the bar series, not null
     * @param tradingRecord the trading record, not null
     * @param window        the requested analysis window, not null
     * @return the criterion value for the window
     * @since 0.22.4
     */
    default Num calculate(BarSeries series, TradingRecord tradingRecord, AnalysisWindow window) {
        return calculate(series, tradingRecord, window, AnalysisContext.defaults());
    }

    /**
     * Calculates this criterion over a specific analysis window.
     *
     * <p>
     * Window boundaries follow:
     * </p>
     * <ul>
     * <li>bar indices: start inclusive, end inclusive</li>
     * <li>time windows: start inclusive, end exclusive (bar membership is based on
     * bar end time)</li>
     * </ul>
     *
     * <p>
     * On constrained or moving series (for example when
     * {@link BarSeries#setMaximumBarCount(int)} removed historical bars), missing
     * history is handled according to
     * {@link AnalysisContext#missingHistoryPolicy()}:
     * </p>
     * <ul>
     * <li>{@link AnalysisContext.MissingHistoryPolicy#STRICT}: fails when requested
     * history is unavailable</li>
     * <li>{@link AnalysisContext.MissingHistoryPolicy#CLAMP}: intersects requested
     * range with available logical indices</li>
     * </ul>
     *
     * @param series        the bar series, not null
     * @param tradingRecord the trading record, not null
     * @param window        the requested analysis window, not null
     * @param context       window resolution and projection options, not null
     * @return the criterion value for the window
     * @since 0.22.4
     */
    default Num calculate(BarSeries series, TradingRecord tradingRecord, AnalysisWindow window,
            AnalysisContext context) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(tradingRecord, "tradingRecord");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(context, "context");

        if (series.isEmpty()) {
            return calculate(series, tradingRecord);
        }

        int[] resolvedWindow = resolveWindow(series, window, context);
        int windowStartIndex = resolvedWindow[0];
        int windowEndIndex = resolvedWindow[1];
        boolean hasBars = resolvedWindow[2] == 1;
        TradingRecord projectedRecord = projectTradingRecord(series, tradingRecord, windowStartIndex, windowEndIndex,
                hasBars, context);
        return calculate(series, projectedRecord);
    }

    private static int[] resolveWindow(BarSeries series, AnalysisWindow window, AnalysisContext context) {
        int availableStart = series.getBeginIndex();
        int availableEnd = series.getEndIndex();

        int requestedStart;
        int requestedEnd;
        boolean requestedEmpty;
        boolean lowerBoundBeforeAvailable;
        boolean upperBoundAfterAvailable;

        switch (window) {
        case AnalysisWindow.BarRange barRange:
            requestedStart = barRange.startIndexInclusive();
            requestedEnd = barRange.endIndexInclusive();
            requestedEmpty = false;
            lowerBoundBeforeAvailable = requestedStart < availableStart;
            upperBoundAfterAvailable = requestedEnd > availableEnd;
            break;
        case AnalysisWindow.LookbackBars lookbackBars:
            Instant lookbackBarsAsOf = context.asOf();
            Instant availableStartTimeForBars = series.getBar(availableStart).getEndTime();
            Instant availableEndExclusiveForBars = series.getBar(availableEnd).getEndTime().plusNanos(1);
            lowerBoundBeforeAvailable = lookbackBarsAsOf != null
                    && lookbackBarsAsOf.isBefore(availableStartTimeForBars);
            upperBoundAfterAvailable = lookbackBarsAsOf != null
                    && lookbackBarsAsOf.isAfter(availableEndExclusiveForBars);

            int lookbackBarsAnchor = lookbackBarsAsOf == null ? availableEnd
                    : findLastIndexAtOrBefore(series, lookbackBarsAsOf, availableStart, availableEnd);
            if (lookbackBarsAnchor < 0) {
                requestedStart = availableStart;
                requestedEnd = availableStart - 1;
                requestedEmpty = true;
                lowerBoundBeforeAvailable = true;
                break;
            }

            requestedStart = lookbackBarsAnchor - lookbackBars.barCount() + 1;
            requestedEnd = lookbackBarsAnchor;
            requestedEmpty = false;
            lowerBoundBeforeAvailable = lowerBoundBeforeAvailable || requestedStart < availableStart;
            upperBoundAfterAvailable = upperBoundAfterAvailable || requestedEnd > availableEnd;
            break;
        case AnalysisWindow.TimeRange timeRange:
            requestedStart = findFirstIndexAtOrAfter(series, timeRange.startInclusive(), availableStart, availableEnd);
            requestedEnd = findLastIndexBefore(series, timeRange.endExclusive(), availableStart, availableEnd);
            requestedEmpty = requestedStart < 0 || requestedEnd < 0 || requestedStart > requestedEnd;
            lowerBoundBeforeAvailable = timeRange.startInclusive().isBefore(series.getBar(availableStart).getEndTime());
            upperBoundAfterAvailable = timeRange.endExclusive()
                    .isAfter(series.getBar(availableEnd).getEndTime().plusNanos(1));
            if (requestedEmpty) {
                requestedStart = availableStart;
                requestedEnd = availableStart - 1;
            }
            break;
        case AnalysisWindow.LookbackDuration lookbackDuration:
            Instant endExclusive = context.asOf() != null ? context.asOf()
                    : series.getBar(availableEnd).getEndTime().plusNanos(1);
            Instant startInclusive = endExclusive.minus(lookbackDuration.duration());
            requestedStart = findFirstIndexAtOrAfter(series, startInclusive, availableStart, availableEnd);
            requestedEnd = findLastIndexBefore(series, endExclusive, availableStart, availableEnd);
            requestedEmpty = requestedStart < 0 || requestedEnd < 0 || requestedStart > requestedEnd;
            lowerBoundBeforeAvailable = startInclusive.isBefore(series.getBar(availableStart).getEndTime());
            upperBoundAfterAvailable = endExclusive.isAfter(series.getBar(availableEnd).getEndTime().plusNanos(1));
            if (requestedEmpty) {
                requestedStart = availableStart;
                requestedEnd = availableStart - 1;
            }
            break;
        }

        if (context.missingHistoryPolicy() == MissingHistoryPolicy.STRICT
                && (lowerBoundBeforeAvailable || upperBoundAfterAvailable)) {
            throw unavailableHistoryException(requestedStart, requestedEnd, availableStart, availableEnd);
        }

        int resolvedStart = requestedStart;
        int resolvedEnd = requestedEnd;
        if (context.missingHistoryPolicy() == MissingHistoryPolicy.CLAMP) {
            resolvedStart = Math.max(resolvedStart, availableStart);
            resolvedEnd = Math.min(resolvedEnd, availableEnd);
        }

        if (context.missingHistoryPolicy() == MissingHistoryPolicy.STRICT
                && (resolvedStart < availableStart || resolvedEnd > availableEnd)) {
            throw unavailableHistoryException(requestedStart, requestedEnd, availableStart, availableEnd);
        }

        if (requestedEmpty || resolvedStart > resolvedEnd) {
            int anchor = Math.min(Math.max(resolvedStart, availableStart), availableEnd);
            return new int[] { anchor, anchor, 0 };
        }
        return new int[] { resolvedStart, resolvedEnd, 1 };
    }

    private static IllegalArgumentException unavailableHistoryException(int requestedStart, int requestedEnd,
            int availableStart, int availableEnd) {
        String message = String.format("Requested window [%d, %d] is outside available series range [%d, %d]",
                requestedStart, requestedEnd, availableStart, availableEnd);
        return new IllegalArgumentException(message);
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

    private static TradingRecord projectTradingRecord(BarSeries series, TradingRecord source, int start, int end,
            boolean hasBars, AnalysisContext context) {
        CostModel transactionCostModel = Objects.requireNonNullElseGet(source.getTransactionCostModel(),
                ZeroCostModel::new);
        CostModel holdingCostModel = Objects.requireNonNullElseGet(source.getHoldingCostModel(), ZeroCostModel::new);
        BaseTradingRecord projectedRecord = new BaseTradingRecord(source.getStartingType(), start, end,
                transactionCostModel, holdingCostModel);
        if (!hasBars) {
            return projectedRecord;
        }

        PositionInclusionPolicy inclusionPolicy = context.positionInclusionPolicy();
        List<Position> includedPositions = new ArrayList<>();
        for (Position position : source.getPositions()) {
            if (includeClosedPosition(position, start, end, inclusionPolicy)) {
                includedPositions.add(position);
            }
        }

        if (context.openPositionHandling() == OpenPositionHandling.MARK_TO_MARKET) {
            List<Position> openPositions = openPositionsForMarkToMarket(source, end, transactionCostModel,
                    holdingCostModel);
            for (Position openPosition : openPositions) {
                Position syntheticPosition = createMarkToMarketPosition(series, openPosition, end, holdingCostModel);
                if (syntheticPosition != null
                        && includeClosedPosition(syntheticPosition, start, end, inclusionPolicy)) {
                    includedPositions.add(syntheticPosition);
                }
            }
        }

        includedPositions.sort(Comparator.comparingInt(position -> position.getExit().getIndex()));
        for (Position position : includedPositions) {
            Trade entry = position.getEntry();
            Trade exit = position.getExit();
            projectedRecord.operate(entry.getIndex(), entry.getPricePerAsset(), entry.getAmount());
            projectedRecord.operate(exit.getIndex(), exit.getPricePerAsset(), exit.getAmount());
        }
        return projectedRecord;
    }

    private static Position createMarkToMarketPosition(BarSeries series, Position currentPosition, int windowEndIndex,
            CostModel holdingCostModel) {
        if (currentPosition == null || !currentPosition.isOpened()) {
            return null;
        }

        Trade entryTrade = currentPosition.getEntry();
        if (entryTrade == null || entryTrade.getIndex() > windowEndIndex) {
            return null;
        }

        Num amount = entryTrade.getAmount();
        Num closePrice = series.getBar(windowEndIndex).getClosePrice();
        CostModel transactionCostModel = entryTrade.getCostModel();
        Trade syntheticExit = entryTrade.isBuy()
                ? Trade.sellAt(windowEndIndex, closePrice, amount, transactionCostModel)
                : Trade.buyAt(windowEndIndex, closePrice, amount, transactionCostModel);
        return new Position(entryTrade, syntheticExit, transactionCostModel, holdingCostModel);
    }

    private static List<Position> openPositionsForMarkToMarket(TradingRecord source, int windowEndIndex,
            CostModel transactionCostModel, CostModel holdingCostModel) {
        if (source instanceof PositionLedger positionLedger) {
            return openPositionsFromLedger(positionLedger, windowEndIndex, transactionCostModel, holdingCostModel);
        }
        Position currentPosition = source.getCurrentPosition();
        if (currentPosition == null || !currentPosition.isOpened()) {
            return List.of();
        }
        return List.of(currentPosition);
    }

    private static List<Position> openPositionsFromLedger(PositionLedger positionLedger, int windowEndIndex,
            CostModel transactionCostModel, CostModel holdingCostModel) {
        List<Position> positions = new ArrayList<>();
        for (OpenPosition openPosition : positionLedger.getOpenPositions()) {
            for (PositionLot lot : openPosition.lots()) {
                if (lot.entryIndex() > windowEndIndex) {
                    continue;
                }
                TradeFill entryFill = new TradeFill(lot.entryIndex(), lot.entryTime(), lot.entryPrice(), lot.amount(),
                        lot.fee(), lot.side(), lot.orderId(), lot.correlationId());
                Trade entryTrade;
                if (entryFill.price().isNaN()) {
                    entryTrade = lot.side() == ExecutionSide.BUY
                            ? Trade.buyAt(lot.entryIndex(), entryFill.price(), lot.amount(), transactionCostModel)
                            : Trade.sellAt(lot.entryIndex(), entryFill.price(), lot.amount(), transactionCostModel);
                } else {
                    entryTrade = Trade.fromFills(lot.side().toTradeType(), List.of(entryFill), transactionCostModel);
                }
                positions.add(new Position(entryTrade, transactionCostModel, holdingCostModel));
            }
        }
        return positions;
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
     * @param manager    the bar series manager with entry type of BUY
     * @param strategies a list of strategies
     * @return the best strategy (among the provided ones) according to the
     *         criterion
     */
    default Strategy chooseBest(BarSeriesManager manager, List<Strategy> strategies) {
        return chooseBest(manager, TradeType.BUY, strategies);
    }

    /**
     * @param manager    the bar series manager
     * @param tradeType  the entry type (BUY or SELL) of the first trade in the
     *                   trading session
     * @param strategies a list of strategies
     * @return the best strategy (among the provided ones) according to the
     *         criterion
     */
    default Strategy chooseBest(BarSeriesManager manager, TradeType tradeType, List<Strategy> strategies) {
        Strategy bestStrategy = strategies.getFirst();
        Num bestCriterionValue = calculate(manager.getBarSeries(), manager.run(bestStrategy));

        for (int i = 1; i < strategies.size(); i++) {
            Strategy currentStrategy = strategies.get(i);
            Num currentCriterionValue = calculate(manager.getBarSeries(), manager.run(currentStrategy, tradeType));

            if (betterThan(currentCriterionValue, bestCriterionValue)) {
                bestStrategy = currentStrategy;
                bestCriterionValue = currentCriterionValue;
            }
        }

        return bestStrategy;
    }

    /**
     * @param criterionValue1 the first value
     * @param criterionValue2 the second value
     * @return true if the first value is better than (according to the criterion)
     *         the second one, false otherwise
     */
    boolean betterThan(Num criterionValue1, Num criterionValue2);
}
