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
import org.ta4j.core.named.NamedAssetRegistry;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.AnalysisCriterionSerialization;
import org.ta4j.core.serialization.ComponentDescriptor;

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
     * Serializes this criterion into canonical descriptor JSON.
     *
     * @return JSON representation
     * @throws IllegalArgumentException if this criterion has constructor state that
     *                                  cannot be represented safely
     * @since 0.23.1
     */
    default String toJson() {
        return AnalysisCriterionSerialization.toJson(this);
    }

    /**
     * Converts this criterion into a structured descriptor.
     *
     * @return component descriptor for this criterion
     * @throws IllegalArgumentException if this criterion has constructor state that
     *                                  cannot be represented safely
     * @since 0.23.1
     */
    default ComponentDescriptor toDescriptor() {
        return AnalysisCriterionSerialization.describe(this);
    }

    /**
     * Renders this criterion as a compact named shorthand expression using ta4j's
     * default named asset registry.
     *
     * @return compact shorthand expression
     * @throws IllegalArgumentException if no registered shorthand can represent the
     *                                  criterion without dropping constructor state
     * @since 0.23.1
     */
    default String toExpression() {
        return AnalysisCriterionSerialization.toExpression(this);
    }

    /**
     * Renders this criterion as a compact named shorthand expression using the
     * supplied named asset registry.
     *
     * @param registry named asset registry
     * @return compact shorthand expression
     * @throws IllegalArgumentException if no registered shorthand can represent the
     *                                  criterion without dropping constructor state
     * @since 0.23.1
     */
    default String toExpression(NamedAssetRegistry registry) {
        return AnalysisCriterionSerialization.toExpression(this, registry);
    }

    /**
     * Reconstructs an analysis criterion from canonical descriptor JSON.
     *
     * @param json JSON payload
     * @return reconstructed criterion
     * @since 0.23.1
     */
    static AnalysisCriterion fromJson(String json) {
        return AnalysisCriterionSerialization.fromJson(json);
    }

    /**
     * Reconstructs an analysis criterion from compact named shorthand using ta4j's
     * default named asset registry.
     *
     * @param expression shorthand expression
     * @return reconstructed criterion
     * @since 0.23.1
     */
    static AnalysisCriterion fromExpression(String expression) {
        return AnalysisCriterionSerialization.fromExpression(expression);
    }

    /**
     * Reconstructs an analysis criterion from compact named shorthand using the
     * supplied named asset registry.
     *
     * @param expression shorthand expression
     * @param registry   named asset registry
     * @return reconstructed criterion
     * @since 0.23.1
     */
    static AnalysisCriterion fromExpression(String expression, NamedAssetRegistry registry) {
        return AnalysisCriterionSerialization.fromExpression(expression, registry);
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
        if (source.getFuturesContract() != null) {
            return projectFuturesTradingRecord(series, source, start, end, hasBars, context);
        }
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
            List<Position> openPositions = openPositionsForMarkToMarket(source, end);
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
            projectedRecord.operate(entry);
            projectedRecord.operate(exit);
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

    /**
     * Projects a native futures trading record onto a window by copying the
     * already-matched positions selected by the inclusion policy.
     *
     * <p>
     * Closed positions keep their recorded entry and exit, and their cash-flow
     * allocations are trimmed to the window end. Open positions selected for
     * {@link OpenPositionHandling#MARK_TO_MARKET} are closed with a synthetic
     * contract-carrying exit at the window end, which turns their prior variation
     * margin into the marked payoff without charging any fee. Their entries and
     * exits are never replayed, so the projection cannot rematch overlapping lots.
     * </p>
     */
    private static TradingRecord projectFuturesTradingRecord(BarSeries series, TradingRecord source, int start, int end,
            boolean hasBars, AnalysisContext context) {
        CostModel holdingCostModel = Objects.requireNonNullElseGet(source.getHoldingCostModel(), ZeroCostModel::new);
        List<Position> includedPositions = new ArrayList<>();
        if (hasBars) {
            PositionInclusionPolicy inclusionPolicy = context.positionInclusionPolicy();
            for (Position position : source.getPositions()) {
                if (!includeClosedPosition(position, start, end, inclusionPolicy)) {
                    continue;
                }
                for (Position trimmedPosition : trimFuturesPositionToWindow(position, end)) {
                    if (trimmedPosition.isClosed()) {
                        includedPositions.add(trimmedPosition);
                    } else if (context.openPositionHandling() == OpenPositionHandling.MARK_TO_MARKET) {
                        Position syntheticPosition = createMarkToMarketFuturesPosition(series, trimmedPosition, end,
                                holdingCostModel);
                        if (syntheticPosition != null
                                && includeClosedPosition(syntheticPosition, start, end, inclusionPolicy)) {
                            includedPositions.add(syntheticPosition);
                        }
                    }
                }
            }
            if (context.openPositionHandling() == OpenPositionHandling.MARK_TO_MARKET) {
                List<Position> positionsToMark = futuresPositionsForMarkToMarket(source, end);
                for (Position positionToMark : positionsToMark) {
                    if (positionToMark.isClosed()) {
                        if (includeClosedPosition(positionToMark, start, end, inclusionPolicy)) {
                            includedPositions.add(positionToMark);
                        }
                        continue;
                    }
                    Position syntheticPosition = createMarkToMarketFuturesPosition(series, positionToMark, end,
                            holdingCostModel);
                    if (syntheticPosition != null
                            && includeClosedPosition(syntheticPosition, start, end, inclusionPolicy)) {
                        includedPositions.add(syntheticPosition);
                    }
                }
            }
        }
        return BaseTradingRecord.projectedFutures(source, includedPositions, start, end);
    }

    /**
     * Closes an open futures position at the mark of the window end with a
     * synthetic exit that carries the same contract.
     *
     * <p>
     * {@link Position} rejects an entry that references a futures contract next to
     * an exit that does not, so the synthetic exit is built from a contract-aware
     * fill with no fees: the recorded fee total of the projected record must stay
     * limited to the fees the run actually charged.
     * </p>
     */
    private static Position createMarkToMarketFuturesPosition(BarSeries series, Position currentPosition,
            int windowEndIndex, CostModel holdingCostModel) {
        if (currentPosition == null || !currentPosition.isOpened()) {
            return null;
        }
        Trade entryTrade = currentPosition.getEntry();
        if (entryTrade == null || entryTrade.getIndex() > windowEndIndex) {
            return null;
        }
        List<TradeFill> retainedEntryFills = FuturesPositionAccounting.executedFills(entryTrade, windowEndIndex);
        if (retainedEntryFills.isEmpty()) {
            return null;
        }
        Trade projectedEntryTrade = Trade.fromFills(entryTrade.getType(), retainedEntryFills,
                entryTrade.getCostModel());
        FuturesContract contract = currentPosition.getFuturesContract();
        if (contract == null) {
            return null;
        }
        Bar windowEndBar = series.getBar(windowEndIndex);
        Num closePrice = windowEndBar.getClosePrice();
        CostModel transactionCostModel = entryTrade.getCostModel();
        TradeFill fill = TradeFill.builder()
                .index(windowEndIndex)
                .time(windowEndBar.getEndTime())
                .price(closePrice)
                .amount(projectedEntryTrade.getAmount())
                .side(projectedEntryTrade.isBuy() ? ExecutionSide.SELL : ExecutionSide.BUY)
                .futuresContract(contract)
                .fees(List.of())
                .build();
        Trade syntheticExit = Trade.fromFill(fill, transactionCostModel);
        return new Position(projectedEntryTrade, syntheticExit, transactionCostModel, holdingCostModel,
                currentPosition.getCashFlows());
    }

    private static List<Position> openPositionsForMarkToMarket(TradingRecord source, int windowEndIndex) {
        List<Position> openPositions = source.getOpenPositions();
        if (!openPositions.isEmpty()) {
            return openPositionsWithinWindow(openPositions, windowEndIndex);
        }
        Position currentPosition = source.getCurrentPosition();
        if (currentPosition == null || !currentPosition.isOpened()) {
            return List.of();
        }
        return List.of(currentPosition);
    }

    private static List<Position> futuresPositionsForMarkToMarket(TradingRecord source, int windowEndIndex) {
        List<Position> positions = new ArrayList<>(openPositionsForMarkToMarket(source, windowEndIndex));
        for (Position closedPosition : source.getPositions()) {
            Trade entry = closedPosition.getEntry();
            Trade exit = closedPosition.getExit();
            if (entry == null || exit == null || entry.getIndex() > windowEndIndex
                    || exit.getIndex() <= windowEndIndex) {
                continue;
            }
            positions.addAll(trimFuturesPositionToWindow(closedPosition, windowEndIndex));
        }
        return positions;
    }

    private static List<Position> openPositionsWithinWindow(List<Position> openPositions, int windowEndIndex) {
        List<Position> positions = new ArrayList<>();
        for (Position openPosition : openPositions) {
            if (openPosition == null || !openPosition.isOpened()) {
                continue;
            }
            if (openPosition.getEntry().getIndex() > windowEndIndex) {
                continue;
            }
            positions.add(openPosition);
        }
        return positions;
    }

    private static List<Position> trimFuturesPositionToWindow(Position position, int end) {
        Trade entry = position.getEntry();
        List<TradeFill> allEntryFills = Trade.executionFillsOf(entry);
        List<TradeFill> retainedEntryFills = FuturesPositionAccounting.executedFills(entry, end);
        Trade exit = position.getExit();
        List<TradeFill> allExitFills = exit == null ? List.of() : Trade.executionFillsOf(exit);
        List<TradeFill> retainedExitFills = exit == null ? List.of()
                : FuturesPositionAccounting.executedFills(exit, end);
        if (retainedEntryFills.size() == allEntryFills.size()
                && (exit == null || retainedExitFills.size() == allExitFills.size())
                && (exit == null || FuturesValidation.numEquals(
                        totalFillAmount(retainedEntryFills, entry.getAmount().getNumFactory()),
                        totalFillAmount(retainedExitFills, exit.getAmount().getNumFactory())))) {
            return List.of(position);
        }
        if (retainedEntryFills.isEmpty()) {
            return List.of();
        }
        CostModel transactionCostModel = position.getTransactionCostModel();
        CostModel holdingCostModel = position.getHoldingCostModel();
        Trade retainedEntry = Trade.fromFills(entry.getType(), retainedEntryFills, entry.getCostModel());
        if (exit == null || retainedExitFills.isEmpty()) {
            return List
                    .of(new Position(retainedEntry, transactionCostModel, holdingCostModel, position.getCashFlows()));
        }
        Trade retainedExit = Trade.fromFills(exit.getType(), retainedExitFills, exit.getCostModel());
        Num retainedEntryAmount = retainedEntry.getAmount();
        Num retainedExitAmount = retainedEntryAmount.getNumFactory().numOf(retainedExit.getAmount().getDelegate());
        if (retainedExitAmount.isGreaterThan(retainedEntryAmount)) {
            throw new IllegalArgumentException("retained exit amount cannot exceed retained entry amount");
        }
        if (FuturesValidation.numEquals(retainedExitAmount, retainedEntryAmount)) {
            return List.of(new Position(retainedEntry, retainedExit, transactionCostModel, holdingCostModel,
                    position.getCashFlows()));
        }

        NumFactory quantityFactory = retainedEntryAmount.getNumFactory();
        List<TradeFill> closedEntryFills = new ArrayList<>();
        List<TradeFill> openEntryFills = new ArrayList<>();
        List<Num> closedAmounts = new ArrayList<>();
        Num remainingClosedAmount = retainedExitAmount;
        for (TradeFill fill : retainedEntryFills) {
            Num fillAmount = quantityFactory.numOf(fill.amount().getDelegate());
            Num closedAmount = quantityFactory.zero();
            if (remainingClosedAmount.isPositive()) {
                closedAmount = fillAmount.isLessThanOrEqual(remainingClosedAmount) ? fillAmount : remainingClosedAmount;
                remainingClosedAmount = remainingClosedAmount.minus(closedAmount);
            }
            Num openAmount = fillAmount.minus(closedAmount);
            if (closedAmount.isPositive()) {
                closedEntryFills.add(resizeFill(fill, closedAmount, fillAmount));
            }
            if (openAmount.isPositive()) {
                openEntryFills.add(resizeFill(fill, openAmount, fillAmount));
            }
            closedAmounts.add(closedAmount);
        }
        if (remainingClosedAmount.isPositive() || closedEntryFills.isEmpty() || openEntryFills.isEmpty()) {
            throw new IllegalStateException("could not split a partially closed futures position");
        }
        // A cash flow belongs to the entry fill that was live when it was recorded,
        // so the split scales each fill's own allocation rather than the aggregate.
        List<List<FuturesCashFlow>> cashFlowSlices = FuturesPositionAccounting
                .allocateCashFlowsByFill(position.getCashFlows(), retainedEntryFills, quantityFactory);
        List<FuturesCashFlow> closedCashFlows = new ArrayList<>();
        List<FuturesCashFlow> openCashFlows = new ArrayList<>();
        for (int i = 0; i < retainedEntryFills.size(); i++) {
            Num fillAmount = quantityFactory.numOf(retainedEntryFills.get(i).amount().getDelegate());
            Num closedAmount = closedAmounts.get(i);
            for (FuturesCashFlow cashFlow : cashFlowSlices.get(i)) {
                Num closedPortion = scaleValue(cashFlow.amount(), closedAmount, fillAmount);
                Num closedSettlement = scaleValue(cashFlow.settlementAmount(), closedAmount, fillAmount);
                closedCashFlows
                        .add(cashFlow.toBuilder().amount(closedPortion).settlementAmount(closedSettlement).build());
                openCashFlows.add(cashFlow.toBuilder()
                        .amount(cashFlow.amount().minus(closedPortion))
                        .settlementAmount(cashFlow.settlementAmount().minus(closedSettlement))
                        .build());
            }
        }
        Position closedPosition = new Position(Trade.fromFills(entry.getType(), closedEntryFills, entry.getCostModel()),
                retainedExit, transactionCostModel, holdingCostModel, closedCashFlows);
        Position openPosition = new Position(Trade.fromFills(entry.getType(), openEntryFills, entry.getCostModel()),
                transactionCostModel, holdingCostModel, openCashFlows);
        return List.of(closedPosition, openPosition);
    }

    private static Num totalFillAmount(List<TradeFill> fills, NumFactory factory) {
        Num total = factory.zero();
        for (TradeFill fill : fills) {
            total = total.plus(factory.numOf(fill.amount().getDelegate()));
        }
        return total;
    }

    private static TradeFill resizeFill(TradeFill fill, Num amount, Num originalAmount) {
        TradeFill.Builder builder = fill.toBuilder().amount(amount);
        if (fill.hasRecordedFees()) {
            builder.fees(fill.fees()
                    .stream()
                    .map(fee -> fee.toBuilder()
                            .amount(scaleValue(fee.amount(), amount, originalAmount))
                            .settlementAmount(fee.settlementAmount() == null ? null
                                    : scaleValue(fee.settlementAmount(), amount, originalAmount))
                            .build())
                    .toList());
        } else if (fill.fee() != null) {
            builder.fee(scaleValue(fill.fee(), amount, originalAmount));
        }
        return builder.build();
    }

    private static Num scaleValue(Num value, Num amount, Num totalAmount) {
        NumFactory factory = value.getNumFactory();
        Num ratio = factory.numOf(amount.getDelegate()).dividedBy(factory.numOf(totalAmount.getDelegate()));
        return value.multipliedBy(ratio);
    }

    private static boolean includeClosedPosition(Position position, int start, int end,
            PositionInclusionPolicy positionInclusionPolicy) {
        if (position == null || !position.isClosed()) {
            return false;
        }
        // A closed position is judged by the executions it actually made: an
        // aggregate trade index only reports its earliest fill.
        int entryStart = firstExecutedFillIndex(position.getEntry());
        int exitStart = firstExecutedFillIndex(position.getExit());
        int exitEnd = lastExecutedFillIndex(position.getExit());
        return switch (positionInclusionPolicy) {
        case EXIT_IN_WINDOW -> exitStart <= end && exitEnd >= start;
        case FULLY_CONTAINED -> entryStart >= start && exitEnd <= end;
        };
    }

    private static int firstExecutedFillIndex(Trade trade) {
        int earliest = Integer.MAX_VALUE;
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0) {
                earliest = Math.min(earliest, fill.index());
            }
        }
        return earliest == Integer.MAX_VALUE ? trade.getIndex() : earliest;
    }

    private static int lastExecutedFillIndex(Trade trade) {
        int latest = Integer.MIN_VALUE;
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0) {
                latest = Math.max(latest, fill.index());
            }
        }
        return latest == Integer.MIN_VALUE ? trade.getIndex() : latest;
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
