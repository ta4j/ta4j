/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.FuturesCashFlow;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Builds futures equity, cash-flow and return curves from native settlement
 * economics.
 *
 * <p>
 * The support consumes already matched {@link Position} snapshots and their
 * as-of-index economic methods. It never matches trades and never spreads an
 * eventual total quantity across earlier bars: at bar {@code i} a position
 * contributes its realized profit up to {@code i}, plus its unrealized
 * mark-to-entry profit when mark-to-market exposure is selected.
 * </p>
 *
 * @since 0.25.1
 */
final class FuturesPerformanceSupport {

    private FuturesPerformanceSupport() {
    }

    /**
     * Returns whether the record carries a native futures contract.
     *
     * @param record trading record
     * @return {@code true} for a futures record
     * @since 0.25.1
     */
    static boolean isFutures(TradingRecord record) {
        return record.getFuturesContract() != null;
    }

    /**
     * Returns whether the position carries a native futures contract.
     *
     * @param position position
     * @return {@code true} for a futures position
     * @since 0.25.1
     */
    static boolean isFutures(Position position) {
        return position.getFuturesContract() != null;
    }

    /**
     * Returns whether the record realized exposure or marked open exposure before
     * the retained head of the analysed series. Such a record seeds the first
     * reported value with the equity accumulated before the head, so that value is
     * a cumulative result rather than a period return.
     *
     * @param record       trading record
     * @param seriesBegin  first stored index of the analysed series
     * @param markExposure {@code true} when open exposure is marked to the market
     * @return {@code true} when the first reported value is a cumulative seed
     * @since 0.25.1
     */
    static boolean hasPreWindowActivity(TradingRecord record, int seriesBegin, boolean markExposure) {
        for (Position position : record.getPositions()) {
            if (position.getEntry() != null && position.getEntry().getIndex() < seriesBegin) {
                return true;
            }
        }
        Position current = record.getCurrentPosition();
        return markExposure && current != null && current.getEntry() != null
                && current.getEntry().getIndex() < seriesBegin;
    }

    /**
     * Validates that a mark price indicator belongs to the analysed series.
     *
     * @param series             analysed bar series
     * @param markPriceIndicator mark price indicator, must not be null
     * @return the shared bar series
     * @since 0.25.1
     */
    static BarSeries requireMarkSeries(BarSeries series, Indicator<Num> markPriceIndicator) {
        Objects.requireNonNull(markPriceIndicator, "markPriceIndicator");
        return IndicatorUtils.requireSameSeries(new ClosePriceIndicator(series), markPriceIndicator);
    }

    /**
     * Returns the account capital used to normalize a futures curve.
     *
     * @param numFactory      factory of the analysed series
     * @param record          trading record
     * @param fallbackCapital unlevered capital of a single-position analysis, may
     *                        be {@code null}
     * @return explicit record capital, else the fallback capital
     * @throws IllegalStateException when neither source supplies capital
     * @since 0.25.1
     */
    static Num accountCapital(NumFactory numFactory, TradingRecord record, Num fallbackCapital) {
        Num capital = record.getInitialCapital();
        if (capital == null) {
            capital = fallbackCapital;
        }
        if (capital == null) {
            throw new IllegalStateException(
                    "native futures account analysis requires an explicit initial capital; configure the trading record initial capital or analyse a single position");
        }
        Num converted = toFactory(numFactory, capital);
        if (!converted.isPositive() || !Num.isFinite(converted)) {
            throw new IllegalStateException("native futures account analysis requires positive finite capital");
        }
        return converted;
    }

    /**
     * Returns the unlevered capital of a single futures position: the settlement
     * notional of its executed entry quantity at the executed entry price. Deferred
     * entry fills, which are not part of the position's executed exposure, are
     * ignored.
     *
     * @param position futures position
     * @return entry settlement notional in the settlement currency
     * @since 0.25.1
     */
    static Num entryNotional(Position position) {
        FuturesContract contract = requireFuturesContract(position);
        Trade entry = position.getEntry();
        List<TradeFill> fills = Trade.executionFillsOf(entry);
        NumFactory numFactory = entry.getPricePerAsset().getNumFactory();
        Num entryNotional = numFactory.zero();
        boolean hasExecutedFill = false;
        for (TradeFill fill : fills) {
            if (fill.index() < 0) {
                continue;
            }
            hasExecutedFill = true;
            Num amount = numFactory.numOf(fill.amount().getDelegate()).abs();
            Num price = numFactory.numOf(fill.price().getDelegate());
            entryNotional = entryNotional.plus(contract.settlementNotional(amount, price));
        }
        return hasExecutedFill ? entryNotional
                : contract.settlementNotional(entry.getAmount().abs(), entry.getPricePerAsset());
    }

    /**
     * Returns the unlevered normalization capital of a single position.
     *
     * @param position position
     * @return the entry settlement notional of a futures position, {@code null} for
     *         a spot position
     * @since 0.25.1
     */
    static Num fallbackCapital(Position position) {
        return isFutures(position) ? entryNotional(position) : null;
    }

    /**
     * Returns the analysis-local record of a single position.
     *
     * <p>
     * A futures position is adopted as-is so that its recorded cash flows survive;
     * the analysis-local record carries no account capital.
     * </p>
     *
     * @param position single position
     * @return analysis-local trading record
     * @since 0.25.1
     */
    static TradingRecord analysisRecord(Position position) {
        Objects.requireNonNull(position, "position");
        if (position.getFuturesContract() == null) {
            return new BaseTradingRecord(position);
        }
        return new BaseTradingRecord(List.of(position));
    }

    /**
     * Returns whether mark-to-market price exposure is selected.
     *
     * @param openPositionHandling open position handling
     * @param equityCurveMode      equity curve mode
     * @return {@code true} when unsold price exposure is included
     * @since 0.25.1
     */
    static boolean includesExposure(OpenPositionHandling openPositionHandling, EquityCurveMode equityCurveMode) {
        return equityCurveMode != EquityCurveMode.REALIZED
                && openPositionHandling == OpenPositionHandling.MARK_TO_MARKET;
    }

    /**
     * Creates an ordered profit cursor over the futures positions of a record.
     *
     * @param series       analysed bar series
     * @param record       trading record
     * @param finalIndex   last index whose executions and cash flows are recognized
     * @param markExposure {@code true} to include unrealized price exposure
     * @param markPrice    mark price indicator, {@code null} to value at close
     *                     prices
     * @return cursor that must be consumed with non-decreasing indices
     * @since 0.25.1
     */
    static Cursor cursor(BarSeries series, TradingRecord record, int finalIndex, boolean markExposure,
            Indicator<Num> markPrice) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(record, "record");
        return new Cursor(series, positions(record, finalIndex), finalIndex, markExposure,
                markPrice == null ? new ClosePriceIndicator(series) : markPrice);
    }

    /**
     * Returns the futures positions that are live up to {@code finalIndex}.
     *
     * @param record     trading record
     * @param finalIndex last recognized index
     * @return closed positions entered up to the index, followed by open positions
     * @since 0.25.1
     */
    static List<Position> positions(TradingRecord record, int finalIndex) {
        List<Position> positions = new ArrayList<>();
        for (Position position : record.getPositions()) {
            addPosition(positions, position, finalIndex);
        }
        for (Position position : AnalysisPositionSupport.openPositions(record, finalIndex)) {
            addPosition(positions, position, finalIndex);
        }
        positions.sort(Comparator.comparingInt(position -> position.getEntry().getIndex()));
        return positions;
    }

    /**
     * Converts a value into the analysed factory without round-tripping non-finite
     * values through a primitive.
     *
     * @param numFactory target factory
     * @param value      value, may be {@code null}
     * @return the value in the target factory, {@link NaN#NaN} for a missing or NaN
     *         value
     * @since 0.25.1
     */
    static Num toFactory(NumFactory numFactory, Num value) {
        if (value == null || value.isNaN()) {
            return NaN.NaN;
        }
        return numFactory.numOf(value.getDelegate());
    }

    private static void addPosition(List<Position> positions, Position position, int finalIndex) {
        if (position == null || position.getEntry() == null) {
            return;
        }
        if (position.getEntry().getIndex() > finalIndex) {
            return;
        }
        positions.add(position);
    }

    private static FuturesContract requireFuturesContract(Position position) {
        FuturesContract contract = position.getFuturesContract();
        if (contract == null) {
            throw new IllegalArgumentException("position is not a futures position");
        }
        return contract;
    }

    /**
     * Ordered profit cursor: each position is activated at its entry index and
     * contributes realized profit, plus unrealized profit when mark-to-market
     * exposure is selected.
     *
     * @since 0.25.1
     */
    static final class Cursor {

        private final BarSeries series;
        private final List<Position> positions;
        private final int finalIndex;
        private final boolean markExposure;
        private final Indicator<Num> markPrice;
        private final NumFactory numFactory;
        private int activeCount;
        private final boolean[] settledPositions;
        private Num settledRealized;
        private int lastIndex = Integer.MIN_VALUE;

        private Cursor(BarSeries series, List<Position> positions, int finalIndex, boolean markExposure,
                Indicator<Num> markPrice) {
            this.series = series;
            this.positions = positions;
            this.finalIndex = finalIndex;
            this.markExposure = markExposure;
            this.markPrice = markPrice;
            this.numFactory = series.numFactory();
            this.settledRealized = numFactory.zero();
            this.settledPositions = new boolean[positions.size()];
        }

        /**
         * Returns the cumulative futures profit accounted at {@code index}.
         *
         * @param index logical bar index, must not move backwards
         * @return realized profit, plus unrealized profit while mark-to-market exposure
         *         is selected
         * @since 0.25.1
         */
        Num pnlAt(int index) {
            if (index < lastIndex) {
                throw new IllegalArgumentException(
                        "cursor index must not move backwards: " + lastIndex + " -> " + index);
            }
            lastIndex = index;
            int effectiveIndex = Math.min(index, finalIndex);
            while (activeCount < positions.size()
                    && positions.get(activeCount).getEntry().getIndex() <= effectiveIndex) {
                activeCount++;
            }
            settle(effectiveIndex);
            Num mark = markExposure && activeCount > 0 ? markAt(effectiveIndex) : null;
            Num total = settledRealized;
            for (int i = 0; i < activeCount; i++) {
                if (settledPositions[i]) {
                    continue;
                }
                Position position = positions.get(i);
                total = total.plus(toFactory(numFactory, position.getRealizedProfit(effectiveIndex)));
                if (mark != null) {
                    total = total.plus(toFactory(numFactory, position.getUnrealizedProfit(mark, effectiveIndex)));
                }
            }
            return total;
        }

        /**
         * Folds the leading exhausted positions into the running total.
         *
         * <p>
         * A position whose entry and exit executions and cash flows are all accounted
         * at {@code effectiveIndex} can no longer change its realized profit, so it is
         * measured once here instead of on every later bar.
         * </p>
         *
         * @param effectiveIndex last bar accounted by the current cursor step
         */
        private void settle(int effectiveIndex) {
            for (int i = 0; i < activeCount; i++) {
                if (!settledPositions[i] && isSettled(positions.get(i), effectiveIndex)) {
                    settledRealized = settledRealized
                            .plus(toFactory(numFactory, positions.get(i).getRealizedProfit(effectiveIndex)));
                    settledPositions[i] = true;
                }
            }
        }

        /**
         * Returns whether {@code position} is fully accounted at
         * {@code effectiveIndex}.
         *
         * @param position       measured position
         * @param effectiveIndex last bar accounted by the current cursor step
         * @return {@code true} when no later bar can change the realized profit
         */
        private static boolean isSettled(Position position, int effectiveIndex) {
            Trade exit = position.getExit();
            if (exit == null || !allFillsExecuted(exit, effectiveIndex)
                    || !allFillsExecuted(position.getEntry(), effectiveIndex)) {
                return false;
            }
            for (FuturesCashFlow cashFlow : position.getCashFlows()) {
                if (cashFlow.index() > effectiveIndex) {
                    return false;
                }
            }
            return true;
        }

        private static boolean allFillsExecuted(Trade trade, int effectiveIndex) {
            for (TradeFill fill : Trade.executionFillsOf(trade)) {
                if (fill.index() < 0 || fill.index() > effectiveIndex) {
                    return false;
                }
            }
            return true;
        }

        private Num markAt(int index) {
            int seriesEnd = series.getEndIndex();
            if (seriesEnd < series.getBeginIndex()) {
                return NaN.NaN;
            }
            int boundedIndex = Math.max(series.getBeginIndex(), Math.min(index, seriesEnd));
            return toFactory(numFactory, markPrice.getValue(boundedIndex));
        }
    }
}
