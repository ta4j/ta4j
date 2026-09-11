/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.*;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

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
        this(barSeries, tradingRecord, new ClosePriceIndicator(barSeries), finalIndex, equityCurveMode,
                openPositionHandling);
    }

    /**
     * Constructor for a futures trading record valuing open exposure at an explicit
     * mark price.
     *
     * <p>
     * The mark price indicator is validated against the analysed series and is
     * consumed by native futures records only; closing prices remain the documented
     * backtest mark proxy otherwise.
     * </p>
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param markPriceIndicator   mark price indicator on the same series
     * @param finalIndex           the final index to calculate up to
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.25.1
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, Indicator<Num> markPriceIndicator,
            int finalIndex, EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        TradingRecord record = Objects.requireNonNull(tradingRecord);
        OpenPositionHandling handling = Objects.requireNonNull(openPositionHandling);
        FuturesPerformanceSupport.requireMarkSeries(Objects.requireNonNull(barSeries),
                Objects.requireNonNull(markPriceIndicator));
        this.barSeries = snapshotSeries(barSeries);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        int seriesEnd = this.barSeries.getEndIndex();
        this.values = new ArrayList<>(
                Collections.nCopies(Math.max(seriesEnd + 1, 0), this.barSeries.numFactory().zero()));
        if (FuturesPerformanceSupport.isFutures(record)) {
            fillFuturesValues(record, markPriceIndicator, finalIndex, handling);
            return;
        }
        calculate(record, finalIndex, handling);
    }

    /**
     * Fills the curve with the absolute settlement P&amp;L of a native futures
     * record. Futures P&amp;L is already an account-level amount, so no
     * normalization by account capital is applied.
     *
     * @param tradingRecord      the futures trading record
     * @param markPriceIndicator mark price indicator on the analysed series
     * @param finalIndex         index up until open position P&amp;L is considered
     * @param handling           how to handle open positions
     */
    private void fillFuturesValues(TradingRecord tradingRecord, Indicator<Num> markPriceIndicator, int finalIndex,
            OpenPositionHandling handling) {
        int seriesEnd = barSeries.getEndIndex();
        if (seriesEnd < 0) {
            return;
        }
        boolean markExposure = FuturesPerformanceSupport.includesExposure(handling, equityCurveMode);
        int effectiveFinalIndex = Math.min(tradingRecord.getEndIndex(barSeries), finalIndex);
        FuturesPerformanceSupport.Cursor cursor = FuturesPerformanceSupport.cursor(barSeries, tradingRecord,
                Math.min(effectiveFinalIndex, seriesEnd), markExposure, markPriceIndicator);
        for (int barIndex = 0; barIndex <= seriesEnd; barIndex++) {
            values.set(barIndex, cursor.pnlAt(barIndex));
        }
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
        this(barSeries, FuturesPerformanceSupport.analysisRecord(position), barSeries.getEndIndex(), equityCurveMode);
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
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public BarSeries getBarSeries() {
        return snapshotSeries(barSeries);
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

    private static BarSeries snapshotSeries(final BarSeries barSeries) {
        return Objects.requireNonNull(barSeries).snapshot();
    }

}
