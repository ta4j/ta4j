/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ta4j.core.*;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * A {@link PerformanceIndicator} implementation that computes the cumulative
 * profit and loss (PnL) series of one or more trading positions over a given
 * {@link BarSeries}.
 * <p>
 * The cumulative PnL is calculated incrementally from the start of the
 * {@code BarSeries}, taking into account realized and unrealized gains/losses,
 * trading costs, and position direction (long or short). Each index in the
 * series represents the total PnL up to that bar. The calculation mode can be
 * configured to mark open positions to market or to only realize PnL at exits.
 * </p>
 *
 * @since 0.19
 */
public final class CumulativePnL implements PerformanceIndicator {

    private final BarSeries barSeries;
    private final List<Num> values;
    private final AtomicBoolean frozen = new AtomicBoolean();

    /**
     * Whether a sweep already composed positions into {@link #values}. Once data is
     * present, further public calculations fall back to per-position application so
     * the addition order (and therefore the finite-precision results) matches the
     * legacy recipe exactly.
     */
    private volatile boolean materialized;

    private final EquityCurveMode equityCurveMode;

    /**
     * Constructor for a trading record with a specified final index. Takes a single
     * defensive snapshot of the given bar series so the calculated values stay
     * isolated from later mutations of the caller's series; {@link #getBarSeries()}
     * returns that same snapshot for every call.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param finalIndex           the final index to calculate up to
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this.barSeries = snapshotSeries(barSeries);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        int seriesEnd = this.barSeries.getEndIndex();
        int size = Math.max(seriesEnd + 1, 0);
        this.values = new ArrayList<>(Collections.nCopies(size, this.barSeries.numFactory().zero()));
        sweep(Objects.requireNonNull(tradingRecord), finalIndex, Objects.requireNonNull(openPositionHandling));
    }

    /**
     * Constructor for a single closed position.
     *
     * @param barSeries       the bar series
     * @param position        the closed position
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, Position position, EquityCurveMode equityCurveMode) {
        this(barSeries, new BaseTradingRecord(position), barSeries.getEndIndex(), equityCurveMode);
    }

    /**
     * Constructor for a trading record with a specified final index.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param finalIndex      the final index to calculate up to
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex,
            EquityCurveMode equityCurveMode) {
        this(barSeries, tradingRecord, finalIndex, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor for a single closed position.
     *
     * @param barSeries the bar series
     * @param position  the closed position
     * @since 0.19
     */
    public CumulativePnL(BarSeries barSeries, Position position) {
        this(barSeries, position, EquityCurveMode.MARK_TO_MARKET);
    }

    /**
     * Constructor for a trading record.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     * @since 0.19
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor for a trading record.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), equityCurveMode,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor for a trading record.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), equityCurveMode, openPositionHandling);
    }

    /**
     * Constructor for a trading record.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     * @param finalIndex    the final index to calculate up to
     * @since 0.19
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex) {
        this(barSeries, tradingRecord, finalIndex, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor for a trading record.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), EquityCurveMode.MARK_TO_MARKET,
                openPositionHandling);
    }

    /**
     * Calculates the cumulative PnL for all positions of the trading record in a
     * single forward sweep over the series.
     *
     * <p>
     * Positions are processed in analysis order while a running sum
     * {@code realized} carries each closed position's exit delta forward. This
     * reproduces, per bar index, the exact addition sequence of per-position
     * processing (held-bar deltas followed by exit deltas), but avoids re-adding
     * the flat tail after every position: complexity is O(series + held bars)
     * instead of O(positions &times; series).
     *
     * @param tradingRecord        the trading record
     * @param finalIndex           index up until values of open positions are
     *                             considered
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    @Override
    public void calculate(TradingRecord tradingRecord, int finalIndex, OpenPositionHandling openPositionHandling) {
        if (frozen.get()) {
            throw new UnsupportedOperationException("equity curves exposed by EquityBundle are immutable");
        }
        Objects.requireNonNull(tradingRecord);
        Objects.requireNonNull(openPositionHandling);
        if (materialized) {
            // Composing a combined sum onto already-materialized cells would
            // reassociate the per-position addition order, which changes
            // finite-precision results; apply each position separately instead.
            PerformanceIndicator.super.calculate(tradingRecord, finalIndex, openPositionHandling);
            return;
        }
        sweep(tradingRecord, finalIndex, openPositionHandling);
    }

    private void sweep(TradingRecord tradingRecord, int finalIndex, OpenPositionHandling openPositionHandling) {
        if (values.isEmpty()) {
            return;
        }
        OpenPositionHandling effectiveOpenPositionHandling = equityCurveMode == EquityCurveMode.REALIZED
                ? OpenPositionHandling.IGNORE
                : openPositionHandling;
        List<Position> positions = AnalysisPositionSupport.positionsForAnalysis(tradingRecord, finalIndex,
                effectiveOpenPositionHandling, equityCurveMode);
        int seriesBegin = barSeries.getBeginIndex();
        int seriesEnd = barSeries.getEndIndex();
        NumFactory numFactory = barSeries.numFactory();
        Num realized = numFactory.zero();
        int cursor = Math.max(seriesBegin, 0);

        for (int p = 0; p < positions.size(); p++) {
            Position position = positions.get(p);
            Trade entry = position == null ? null : position.getEntry();
            if (entry == null) {
                continue;
            }
            int entryIndex = entry.getIndex();
            if (entryIndex > finalIndex || entryIndex > seriesEnd) {
                continue;
            }
            int endIndex = determineEndIndex(position, finalIndex, seriesEnd);
            if (endIndex < seriesBegin) {
                continue;
            }
            boolean isLongTrade = entry.isBuy();
            Num netEntryPrice = entry.getNetPrice();

            if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
                Num averageCostPerPeriod = averageHoldingCostPerPeriod(position, endIndex, numFactory);
                int start = Math.max(entryIndex + 1, seriesBegin + 1);
                for (int i = start; i < endIndex; i++) {
                    cursor = fillRange(cursor, i, realized);
                    Num close = barSeries.getBar(i).getClosePrice();
                    Num netIntermediate = addCost(close, averageCostPerPeriod, isLongTrade);
                    Num delta = isLongTrade ? netIntermediate.minus(netEntryPrice)
                            : netEntryPrice.minus(netIntermediate);
                    addValue(i, delta);
                }
                Num exitRaw = resolveExitPrice(position, endIndex, barSeries);
                Num netExit = addCost(exitRaw, averageCostPerPeriod, isLongTrade);
                Num deltaExit = isLongTrade ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
                if (endIndex < cursor) {
                    // A later-iterated position may close at an earlier bar
                    // than the sweep cursor. The realized delta applies from
                    // its exit bar onward, so accumulate it across every
                    // already-materialized cell instead of rewinding.
                    addToRange(endIndex, cursor - 1, deltaExit);
                } else {
                    cursor = fillRange(cursor, endIndex, realized);
                    addValue(endIndex, deltaExit);
                    cursor = endIndex + 1;
                }
                realized = realized.plus(deltaExit);
                continue;
            }

            Trade exit = position.getExit();
            if (exit != null && endIndex >= exit.getIndex()) {
                Num holdingCost = position.getHoldingCost(endIndex);
                Num netExit = addCost(exit.getNetPrice(), holdingCost, isLongTrade);
                Num deltaExit = isLongTrade ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
                int exitIndex = exit.getIndex();
                if (exitIndex < cursor) {
                    // A later-iterated position may close at an earlier bar
                    // than the sweep cursor. The realized delta applies from
                    // its exit bar onward, so accumulate it across every
                    // already-materialized cell instead of rewinding.
                    addToRange(exitIndex, cursor - 1, deltaExit);
                } else {
                    cursor = fillRange(cursor, exitIndex, realized);
                    addValue(exitIndex, deltaExit);
                    cursor = exitIndex + 1;
                }
                realized = realized.plus(deltaExit);
            }
        }
        fillRange(cursor, seriesEnd, realized);
        if (!positions.isEmpty()) {
            materialized = true;
        }
    }

    /**
     * Marks this curve immutable after {@link EquityBundle} fully materialized it,
     * so the shared cached instance cannot be altered through the public
     * accumulating operations.
     */
    void freeze() {
        this.frozen.set(true);
    }

    /**
     * Adds {@code value} to every cell of the inclusive range {@code [from, to]}
     * and returns the next unmaterialized index ({@code max(from, to) + 1}).
     * Untouched cells hold zero, so composition equals replacement on a freshly
     * constructed curve, while a repeated {@link #calculate} invocation accumulates
     * on top of the data already present instead of discarding it.
     */
    private int fillRange(int from, int to, Num value) {
        if (from > to || to < 0 || from >= values.size()) {
            return from;
        }
        int end = Math.min(to, values.size() - 1);
        for (int i = Math.max(from, 0); i <= end; i++) {
            values.set(i, values.get(i).plus(value));
        }
        return to + 1;
    }

    /**
     * Calculates the cumulative PnL for a single position.
     *
     * @param position   the position
     * @param finalIndex the final index to calculate up to
     * @since 0.22.2
     */
    @Override
    public void calculatePosition(Position position, int finalIndex) {
        if (frozen.get()) {
            throw new UnsupportedOperationException("equity curves exposed by EquityBundle are immutable");
        }
        Trade entry = position.getEntry();
        if (entry == null) {
            return;
        }
        int seriesEnd = barSeries.getEndIndex();
        int entryIndex = entry.getIndex();
        if (entryIndex > finalIndex || entryIndex > seriesEnd) {
            return;
        }
        int endIndex = determineEndIndex(position, finalIndex, seriesEnd);
        int seriesBegin = barSeries.getBeginIndex();
        if (endIndex < seriesBegin) {
            return;
        }

        NumFactory numFactory = barSeries.numFactory();
        boolean isLong = entry.isBuy();
        Num netEntryPrice = entry.getNetPrice();

        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            Num averageCostPerPeriod = averageHoldingCostPerPeriod(position, endIndex, numFactory);
            int start = Math.max(entryIndex + 1, seriesBegin + 1);
            for (int i = start; i < endIndex; i++) {
                Num close = barSeries.getBar(i).getClosePrice();
                Num netIntermediate = addCost(close, averageCostPerPeriod, isLong);
                Num delta = isLong ? netIntermediate.minus(netEntryPrice) : netEntryPrice.minus(netIntermediate);
                addValue(i, delta);
            }
            Num exitRaw = resolveExitPrice(position, endIndex, barSeries);
            Num netExit = addCost(exitRaw, averageCostPerPeriod, isLong);
            Num deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
            addToRange(endIndex, seriesEnd, deltaExit);
            return;
        }

        Trade exit = position.getExit();
        if (exit != null && endIndex >= exit.getIndex()) {
            Num holdingCost = position.getHoldingCost(endIndex);
            Num netExit = addCost(exit.getNetPrice(), holdingCost, isLong);
            Num deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
            addToRange(exit.getIndex(), seriesEnd, deltaExit);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public Num getValue(int index) {
        return values.get(index);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    /**
     * Returns the defensive series snapshot taken at construction time. The same
     * instance is returned for every call; later mutations of the original series
     * are not visible through it.
     *
     * @since 0.19
     */
    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "getBarSeries intentionally returns the single "
            + "construction-time defensive snapshot; the indicator is the sole owner of this instance and the "
            + "stable-identity contract is documented on this class and pinned by tests")
    public BarSeries getBarSeries() {
        return barSeries;
    }

    private static BarSeries snapshotSeries(final BarSeries barSeries) {
        BarSeries series = Objects.requireNonNull(barSeries);
        return new BaseBarSeriesBuilder().withName(series.getName())
                .withNumFactory(series.numFactory())
                .withBars(series.getBarData())
                .withBeginIndex(Math.max(0, series.getBeginIndex()))
                .withMaxBarCount(series.getMaximumBarCount())
                .build();
    }

    /**
     * Returns the number of bars in the underlying series.
     *
     * @return the bar count
     * @since 0.19
     */
    public int getSize() {
        return barSeries.getBarCount();
    }

    /**
     * @return the equity curve mode used for this cumulative PnL
     * @since 0.22.2
     */
    @Override
    public EquityCurveMode getEquityCurveMode() {
        return equityCurveMode;
    }

    private void addValue(int index, Num delta) {
        if (index < 0 || index >= values.size()) {
            return;
        }
        values.set(index, values.get(index).plus(delta));
    }

    private void addToRange(int startIndex, int endIndex, Num delta) {
        if (values.isEmpty()) {
            return;
        }
        int start = Math.max(0, startIndex);
        int end = Math.min(endIndex, values.size() - 1);
        if (start > end) {
            return;
        }
        for (int i = start; i <= end; i++) {
            values.set(i, values.get(i).plus(delta));
        }
    }

}
