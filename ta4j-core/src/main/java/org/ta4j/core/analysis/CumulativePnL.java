/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * An {@link org.ta4j.core.Indicator} implementation that computes the
 * cumulative profit and loss (PnL) series of one or more trading positions over
 * a given {@link BarSeries}.
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
    private final EquityCurveMode equityCurveMode;

    /**
     * Constructor for a trading record with a specified final index.
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
        this.barSeries = Objects.requireNonNull(barSeries);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        var seriesEnd = barSeries.getEndIndex();
        var size = Math.max(seriesEnd + 1, 0);
        this.values = new ArrayList<>(Collections.nCopies(size, barSeries.numFactory().zero()));
        calculate(Objects.requireNonNull(tradingRecord), finalIndex, Objects.requireNonNull(openPositionHandling));
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
     * Calculates the cumulative PnL for a single position.
     *
     * @param position   the position
     * @param finalIndex the final index to calculate up to
     * @since 0.22.2
     */
    @Override
    public void calculatePosition(Position position, int finalIndex) {
        var entry = position.getEntry();
        if (entry == null) {
            return;
        }
        var seriesEnd = barSeries.getEndIndex();
        var entryIndex = entry.getIndex();
        if (entryIndex > finalIndex || entryIndex > seriesEnd) {
            return;
        }
        var endIndex = determineEndIndex(position, finalIndex, seriesEnd);
        var seriesBegin = barSeries.getBeginIndex();
        if (endIndex < seriesBegin) {
            return;
        }

        var numFactory = barSeries.numFactory();
        var isLong = entry.isBuy();
        var netEntryPrice = entry.getNetPrice();

        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            var averageCostPerPeriod = averageHoldingCostPerPeriod(position, endIndex, numFactory);
            var start = Math.max(entryIndex + 1, seriesBegin + 1);
            for (var i = start; i < endIndex; i++) {
                var close = barSeries.getBar(i).getClosePrice();
                var netIntermediate = addCost(close, averageCostPerPeriod, isLong);
                var delta = isLong ? netIntermediate.minus(netEntryPrice) : netEntryPrice.minus(netIntermediate);
                addValue(i, delta);
            }
            var exitRaw = resolveExitPrice(position, endIndex, barSeries);
            var netExit = addCost(exitRaw, averageCostPerPeriod, isLong);
            var deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
            addToRange(endIndex, seriesEnd, deltaExit);
            return;
        }

        var exit = position.getExit();
        if (exit != null && endIndex >= exit.getIndex()) {
            var holdingCost = position.getHoldingCost(endIndex);
            var netExit = addCost(exit.getNetPrice(), holdingCost, isLong);
            var deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
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
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public BarSeries getBarSeries() {
        return barSeries;
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
        var start = Math.max(0, startIndex);
        var end = Math.min(endIndex, values.size() - 1);
        if (start > end) {
            return;
        }
        for (var i = start; i <= end; i++) {
            values.set(i, values.get(i).plus(delta));
        }
    }

}
