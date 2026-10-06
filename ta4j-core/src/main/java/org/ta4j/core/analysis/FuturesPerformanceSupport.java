/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.BigInteger;
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
import org.ta4j.core.num.DecimalNum;
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
     * a cumulative result rather than a period return. Only executed fills with
     * indices in {@code [0, seriesBegin)} qualify; aggregate trade indices and
     * deferred fills do not establish pre-window activity.
     *
     * @param record       trading record
     * @param seriesBegin  first stored index of the analysed series
     * @param markExposure {@code true} when open exposure is marked to the market
     * @return {@code true} when the first reported value is a cumulative seed
     * @since 0.25.1
     */
    static boolean hasPreWindowActivity(TradingRecord record, int seriesBegin, boolean markExposure) {
        for (Position position : record.getPositions()) {
            if (hasPreWindowActivity(position, seriesBegin, markExposure)) {
                return true;
            }
        }
        List<Position> openPositions = record.getOpenPositions();
        if (!openPositions.isEmpty()) {
            for (Position position : openPositions) {
                if (hasPreWindowActivity(position, seriesBegin, markExposure)) {
                    return true;
                }
            }
        } else {
            Position current = record.getCurrentPosition();
            if (hasPreWindowActivity(current, seriesBegin, markExposure)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the record has an execution or cash flow at the retained head
     * of the analysed series.
     *
     * @param record trading record
     * @param index  retained series head
     * @return {@code true} when activity occurs at {@code index}
     * @since 0.25.1
     */
    static boolean hasActivityAtIndex(TradingRecord record, int index) {
        for (Position position : record.getPositions()) {
            if (hasActivityAtIndex(position, index)) {
                return true;
            }
        }
        List<Position> openPositions = record.getOpenPositions();
        if (!openPositions.isEmpty()) {
            for (Position position : openPositions) {
                if (hasActivityAtIndex(position, index)) {
                    return true;
                }
            }
        } else if (hasActivityAtIndex(record.getCurrentPosition(), index)) {
            return true;
        }
        return false;
    }

    static boolean hasPreWindowActivity(Position position, int seriesBegin, boolean markExposure) {
        if (position == null) {
            return false;
        }
        if (hasPreWindowExecution(position, seriesBegin)) {
            if (markExposure) {
                return true;
            }
            ProfitSum realized = new ProfitSum();
            for (Num component : position.getProfitComponents(seriesBegin - 1, null)) {
                realized.add(component);
            }
            if (!realized.isZero()) {
                return true;
            }
        }
        return hasPreWindowCashFlow(position, seriesBegin);
    }

    static boolean hasActivityAtIndex(Position position, int index) {
        if (position == null) {
            return false;
        }
        if (hasActivityAtIndex(position.getEntry(), index) || hasActivityAtIndex(position.getExit(), index)) {
            return true;
        }
        for (FuturesCashFlow cashFlow : position.getCashFlows()) {
            if (cashFlow.index() == index) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPreWindowCashFlow(Position position, int seriesBegin) {
        for (FuturesCashFlow cashFlow : position.getCashFlows()) {
            if (cashFlow.index() >= 0 && cashFlow.index() < seriesBegin) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasActivityAtIndex(Trade trade, int index) {
        if (trade == null) {
            return false;
        }
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() == index) {
                return true;
            }
        }
        return trade.getFills().isEmpty() && trade.getTime() == null && trade.getIndex() == index;
    }

    private static boolean hasPreWindowExecution(Position position, int seriesBegin) {
        if (position == null) {
            return false;
        }
        return hasPreWindowExecution(position.getEntry(), seriesBegin)
                || hasPreWindowExecution(position.getExit(), seriesBegin);
    }

    private static boolean hasPreWindowExecution(Trade trade, int seriesBegin) {
        if (trade == null) {
            return false;
        }
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() < seriesBegin) {
                return true;
            }
        }
        return false;
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
        boolean usingFallback = capital == null || capital.isZero();
        if (usingFallback) {
            capital = fallbackCapital;
        }
        if (capital == null) {
            throw new IllegalStateException(
                    "native futures account analysis requires an explicit initial capital; configure the trading record initial capital or analyse a single position");
        }
        Num converted = numFactory.numOf(capital.getDelegate());
        if (usingFallback && converted.isZero()) {
            if (!capital.isZero()) {
                throw new IllegalStateException(
                        "native futures fallback capital cannot be represented in analysis factory");
            }
            return converted;
        }
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
        boolean allFillsExecuted = true;
        for (TradeFill fill : fills) {
            if (fill.index() < 0) {
                allFillsExecuted = false;
                continue;
            }
            hasExecutedFill = true;
            Num amount = numFactory.numOf(fill.amount().getDelegate()).abs();
            Num price = numFactory.numOf(fill.price().getDelegate());
            entryNotional = entryNotional.plus(contract.settlementNotional(amount, price));
        }
        return allFillsExecuted ? contract.settlementNotional(entry.getAmount().abs(), entry.getPricePerAsset())
                : hasExecutedFill ? entryNotional : numFactory.zero();
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

    /** Captures components before converting the final account total to Num. */
    static PnLAccumulator pnl(Cursor cursor, AnalysisPositionSupport.Window window, NumFactory factory) {
        PnLAccumulator pnl = new PnLAccumulator(window, factory);
        pnl.add(cursor, null, false);
        return pnl;
    }

    /**
     * Captures normalized native components, including independent entering equity.
     */
    static PnLAccumulator pnl(Cursor cursor, AnalysisPositionSupport.Window window, NumFactory factory, Num capital) {
        PnLAccumulator pnl = new PnLAccumulator(window, factory);
        pnl.add(cursor, capital, false);
        return pnl;
    }

    /** Adds one position, recognizing fills only through its incremental cutoff. */
    static void addPositionPnL(BarSeries series, Position position, int finalIndex,
            AnalysisPositionSupport.Window window, boolean markExposure, Indicator<Num> mark, PnLAccumulator pnl) {
        addPositionPnL(series, position, finalIndex, window, markExposure, mark, pnl, null, false);
    }

    static void addPositionPnL(BarSeries series, Position position, int finalIndex,
            AnalysisPositionSupport.Window window, boolean markExposure, Indicator<Num> mark, PnLAccumulator pnl,
            Num capital) {
        addPositionPnL(series, position, finalIndex, window, markExposure, mark, pnl, capital, false);
    }

    static void addPositionPnL(BarSeries series, Position position, int finalIndex,
            AnalysisPositionSupport.Window window, boolean markExposure, Indicator<Num> mark, PnLAccumulator pnl,
            Num capital, boolean validateArrival) {
        int cutoff = Math.min(finalIndex, window.endIndex());
        Cursor cursor = new Cursor(series, List.of(position), cutoff, markExposure,
                mark == null ? new ClosePriceIndicator(series) : mark);
        pnl.add(cursor, capital, validateArrival);
    }

    /** Per-bar component totals retained across staged incremental updates. */
    static final class PnLAccumulator {
        private final AnalysisPositionSupport.Window window;
        private final NumFactory factory;
        private final List<ProfitSum> sums;
        private ProfitSum baseline;

        PnLAccumulator(AnalysisPositionSupport.Window window, NumFactory factory) {
            this.window = window;
            this.factory = factory;
            this.baseline = new ProfitSum();
            OffsetNumBuffer initial = AnalysisPositionSupport.buffer(window, factory.zero(), factory.zero());
            this.sums = new ArrayList<>(initial.size());
            for (int offset = 0; offset < initial.size(); offset++) {
                sums.add(new ProfitSum());
            }
        }

        private PnLAccumulator(PnLAccumulator previous) {
            this.window = previous.window;
            this.factory = previous.factory;
            this.baseline = previous.baseline == null ? null : new ProfitSum(previous.baseline);
            this.sums = new ArrayList<>(previous.sums.size());
            for (ProfitSum sum : previous.sums) {
                sums.add(sum == null ? null : new ProfitSum(sum));
            }
        }

        PnLAccumulator copy() {
            return new PnLAccumulator(this);
        }

        private void add(Cursor cursor, Num capital, boolean validateArrival) {
            if (!window.isEmpty()) {
                ProfitSum contribution = cursor.profitBefore(window.beginIndex());
                if (baseline == null || contribution == null) {
                    baseline = null;
                } else {
                    if (capital != null)
                        contribution.divide(capital);
                    baseline.add(contribution);
                }
            }
            for (int offset = 0; offset < sums.size(); offset++) {
                ProfitSum contribution = cursor.profitAt((int) ((long) window.beginIndex() + offset));
                ProfitSum sum = sums.get(offset);
                if (sum == null || contribution == null) {
                    sums.set(offset, null);
                } else {
                    if (validateArrival)
                        contribution.value(factory);
                    if (capital != null)
                        contribution.divide(capital);
                    sum.add(contribution);
                }
            }
        }

        /** Retains spot deltas alongside the unrounded native components. */
        void add(OffsetNumBuffer deltas) {
            for (int offset = 0; offset < sums.size(); offset++) {
                Num delta = deltas.get((int) ((long) window.beginIndex() + offset));
                ProfitSum sum = sums.get(offset);
                if (sum == null || !Num.isFinite(delta)) {
                    sums.set(offset, null);
                } else {
                    sum.add(delta);
                }
            }
            if (baseline == null || !Num.isFinite(deltas.baseline())) {
                baseline = null;
            } else {
                baseline.add(deltas.baseline());
            }
        }

        /** A spot ratio scales only the native components already present. */
        void multiply(OffsetNumBuffer factors) {
            for (int offset = 0; offset < sums.size(); offset++) {
                Num factor = factors.get((int) ((long) window.beginIndex() + offset));
                ProfitSum sum = sums.get(offset);
                if (sum == null || !Num.isFinite(factor)) {
                    sums.set(offset, null);
                } else {
                    sum.sum = sum.sum.multiply(factor.bigDecimalValue());
                }
            }
            if (baseline == null || !Num.isFinite(factors.baseline())) {
                baseline = null;
            } else {
                baseline.sum = baseline.sum.multiply(factors.baseline().bigDecimalValue());
            }
        }

        /**
         * Adds a visible base only at final conversion, after component cancellation.
         */
        Num equity(int index, Num base) {
            return equity(index, base, factory.one());
        }

        Num equity(int index, Num base, Num scale) {
            ProfitSum sum = sums.get(index - window.beginIndex());
            if (sum == null || !Num.isFinite(base))
                return NaN.NaN;
            ProfitSum total = new ProfitSum(sum);
            total.add(base);
            total.sum = total.sum.multiply(scale.bigDecimalValue());
            return total.value(factory);
        }

        Num enteringEquity(Num base) {
            if (baseline == null || !Num.isFinite(base))
                return NaN.NaN;
            ProfitSum total = new ProfitSum(baseline);
            total.add(base);
            return total.value(factory);
        }

        Num get(int index) {
            return scaledValue(index, factory.one());
        }

        /** Checks account PnL before its equity base can mask a lossy conversion. */
        void validatePnL(int index, Num capital) {
            scaledValue(index, capital);
        }

        private Num scaledValue(int index, Num scale) {
            if (index < window.beginIndex() || index > window.bufferEndIndex()) {
                return factory.zero();
            }
            ProfitSum sum = sums.get(index - window.beginIndex());
            if (sum == null)
                return NaN.NaN;
            ProfitSum total = new ProfitSum(sum);
            total.sum = total.sum.multiply(scale.bigDecimalValue());
            return total.value(factory);
        }

        OffsetNumBuffer values() {
            OffsetNumBuffer values = AnalysisPositionSupport.buffer(window, factory.zero(), factory.zero());
            // Raw entering carries follow the factory's usual rounding, like spot
            // baselines. Their exact components remain available for later carries.
            values.addBaseline(baseline == null ? NaN.NaN : baseline.roundedValue(factory));
            for (int offset = 0; offset < sums.size(); offset++) {
                int index = (int) ((long) window.beginIndex() + offset);
                values.add(index, get(index));
            }
            return values;
        }
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
     * values through a primitive or silently losing a finite nonzero value.
     *
     * @param numFactory target factory
     * @param value      value, may be {@code null}
     * @return the value in the target factory; {@code null} and NaN inputs remain
     *         unchanged
     * @throws IllegalArgumentException when the converted value is non-finite or a
     *                                  finite nonzero value collapses to zero
     * @since 0.25.1
     */
    static Num toFactory(NumFactory numFactory, Num value) {
        if (Num.isNaNOrNull(value)) {
            return value;
        }
        Num converted = numFactory.numOf(value.getDelegate());
        if (!Num.isFinite(converted)) {
            throw new IllegalArgumentException("value must be finite in analysis number factory");
        }
        if (!value.isZero() && converted.isZero()) {
            throw new IllegalArgumentException("value cannot be represented in analysis number factory");
        }
        return converted;
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
        private final ProfitSum settledRealized;
        private int lastIndex = Integer.MIN_VALUE;
        private int lastMarkIndex = Integer.MIN_VALUE;
        private Num lastMark;

        private Cursor(BarSeries series, List<Position> positions, int finalIndex, boolean markExposure,
                Indicator<Num> markPrice) {
            this.series = series;
            this.positions = positions;
            this.finalIndex = finalIndex;
            this.markExposure = markExposure;
            this.markPrice = markPrice;
            this.numFactory = series.numFactory();
            this.settledRealized = new ProfitSum();
            this.settledPositions = new boolean[positions.size()];
        }

        /**
         * Returns the cumulative futures profit components accounted at {@code index}.
         *
         * @param index logical bar index, must not move backwards
         * @return realized plus selected marked components, or {@code null} when a
         *         required mark is unavailable
         * @since 0.25.1
         */
        private ProfitSum profitAt(int index) {
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
            boolean hasResidualExposure = false;
            for (int i = 0; i < activeCount; i++) {
                if (!settledPositions[i] && hasResidualExposure(positions.get(i), effectiveIndex)) {
                    hasResidualExposure = true;
                    break;
                }
            }
            Num mark = markExposure && hasResidualExposure ? markAt(effectiveIndex) : null;
            ProfitSum total = new ProfitSum(settledRealized);
            for (int i = 0; i < activeCount; i++) {
                if (settledPositions[i]) {
                    continue;
                }
                Position position = positions.get(i);
                if (mark != null && !Num.isFinite(mark)) {
                    return null;
                }
                for (Num component : position.getProfitComponents(effectiveIndex, mark)) {
                    total.add(component);
                }
            }
            return total;
        }

        /**
         * Recognizes only pre-head events, valuing retained exposure at the head mark.
         */
        private ProfitSum profitBefore(int head) {
            int cutoff = Math.min(finalIndex, head - 1);
            ProfitSum total = new ProfitSum();
            for (Position position : positions) {
                if (position.getEntry().getIndex() > cutoff)
                    continue;
                Num mark = markExposure && hasResidualExposure(position, cutoff) ? markAt(head) : null;
                if (mark != null && !Num.isFinite(mark))
                    return null;
                for (Num component : position.getProfitComponents(cutoff, mark)) {
                    total.add(component);
                }
            }
            return total;
        }

        private static boolean hasResidualExposure(Position position, int finalIndex) {
            Trade entry = position.getEntry();
            NumFactory numFactory = entry.getAmount().getNumFactory();
            Num executedEntryAmount = executedAmountAt(entry, finalIndex, numFactory);
            if (!executedEntryAmount.isPositive()) {
                return false;
            }
            Trade exit = position.getExit();
            if (exit == null) {
                return true;
            }
            Num executedExitAmount = executedAmountAt(exit, finalIndex, numFactory);
            return executedEntryAmount.isGreaterThan(executedExitAmount);
        }

        private static Num executedAmountAt(Trade trade, int finalIndex, NumFactory numFactory) {
            Num amount = numFactory.zero();
            for (TradeFill fill : Trade.executionFillsOf(trade)) {
                if (fill.index() >= 0 && fill.index() <= finalIndex) {
                    amount = amount.plus(numFactory.numOf(fill.amount().getDelegate()));
                }
            }
            return amount;
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
                    for (Num component : positions.get(i).getProfitComponents(effectiveIndex, null)) {
                        settledRealized.add(component);
                    }
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
            Trade entry = position.getEntry();
            if (exit == null || !allFillsExecuted(exit, effectiveIndex) || !allFillsExecuted(entry, effectiveIndex)) {
                return false;
            }
            Num entryAmount = entry.getAmount();
            Num exitAmount = entryAmount.getNumFactory().numOf(exit.getAmount().getDelegate());
            if (exitAmount.isLessThan(entryAmount)) {
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
            int seriesBegin = series.getBeginIndex();
            if (seriesEnd < seriesBegin || index < seriesBegin) {
                return NaN.NaN;
            }
            int boundedIndex = Math.min(index, seriesEnd);
            if (boundedIndex != lastMarkIndex) {
                lastMark = toFactory(numFactory, markPrice.getValue(boundedIndex));
                lastMarkIndex = boundedIndex;
            }
            return lastMark;
        }
    }

    /**
     * Adds already validated economic components exactly before converting the
     * account total. Custom TradingRecords can mix position factories, so neither a
     * position's factory nor a rounded lot subtotal defines account precision. The
     * same sum determines pre-window eligibility and cursor settlement.
     */
    private static final class ProfitSum {
        private BigDecimal sum;
        private BigDecimal divisor;

        private ProfitSum() {
            this.sum = BigDecimal.ZERO;
            this.divisor = BigDecimal.ONE;
        }

        private ProfitSum(ProfitSum previous) {
            this.sum = previous.sum;
            this.divisor = previous.divisor;
        }

        private void add(Num component) {
            sum = sum.add(component.bigDecimalValue().multiply(divisor));
        }

        private void add(ProfitSum contribution) {
            if (divisor.compareTo(contribution.divisor) == 0) {
                sum = sum.add(contribution.sum);
            } else if (isZero()) {
                sum = contribution.sum;
                divisor = contribution.divisor;
            } else if (!contribution.isZero()) {
                sum = sum.multiply(contribution.divisor).add(contribution.sum.multiply(divisor));
                divisor = divisor.multiply(contribution.divisor);
                reduce();
            }
        }

        private void divide(Num capital) {
            if (!isZero()) {
                divisor = divisor.multiply(capital.bigDecimalValue());
                reduce();
            }
        }

        private void reduce() {
            int scale = Math.max(sum.scale(), divisor.scale());
            BigInteger numerator = sum.scaleByPowerOfTen(scale).toBigIntegerExact();
            BigInteger denominator = divisor.scaleByPowerOfTen(scale).toBigIntegerExact();
            BigInteger common = numerator.gcd(denominator);
            sum = new BigDecimal(numerator.divide(common));
            divisor = new BigDecimal(denominator.divide(common));
        }

        private boolean isZero() {
            return sum.signum() == 0;
        }

        private Num roundedValue(NumFactory factory) {
            Num one = factory.one();
            MathContext context = one instanceof DecimalNum decimal ? decimal.getMathContext() : MathContext.DECIMAL128;
            return factory.numOf(divisor.compareTo(BigDecimal.ONE) == 0 ? sum : sum.divide(divisor, context));
        }

        private Num value(NumFactory factory) {
            Num value = roundedValue(factory);
            if (!Num.isFinite(value)) {
                throw new IllegalArgumentException("profit total must be finite in analysis number factory");
            }
            if (value.isZero() && !isZero()) {
                throw new IllegalArgumentException("profit total cannot be represented in analysis number factory");
            }
            return value;
        }
    }

}
