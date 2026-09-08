/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.stream.Stream;

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
    private OffsetNumBuffer values;
    /**
     * The last raw bar index captured when the curve was materialized.
     */
    private int materializedAddressableEndIndex;
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
        this(barSeries, tradingRecord, finalIndex, equityCurveMode, openPositionHandling, false, false);
    }

    private CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, int requestedFinalIndex,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling, boolean useRecordEnd,
            boolean useSeriesEnd) {
        this.barSeries = Objects.requireNonNull(barSeries, "barSeries");
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        TradingRecord record = Objects.requireNonNull(tradingRecord);
        OpenPositionHandling handling = Objects.requireNonNull(openPositionHandling);
        Runnable action = () -> {
            this.materializedAddressableEndIndex = OffsetNumBuffer.addressableEndIndex(this.barSeries);
            int finalIndex = useRecordEnd
                    ? AnalysisPositionSupport.analysisEndIndex(this.barSeries, record, materializedAddressableEndIndex)
                    : useSeriesEnd ? this.barSeries.getEndIndex() : requestedFinalIndex;
            Num zero = this.barSeries.numFactory().zero();
            int endIndex = Math.max(this.barSeries.getEndIndex(),
                    Math.min(finalIndex, this.materializedAddressableEndIndex));
            this.values = endIndex < this.barSeries.getBeginIndex() ? new OffsetNumBuffer(-1, -1, zero, zero)
                    : new OffsetNumBuffer(this.barSeries.getBeginIndex(), endIndex, zero, zero);
            calculate(record, finalIndex, handling);
        };
        barSeries.withReadLock(action);
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
        this(barSeries, new BaseTradingRecord(position), 0, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET, false,
                true);
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
        this(barSeries, tradingRecord, 0, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET, true,
                false);
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
        this(barSeries, tradingRecord, 0, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET, true, false);
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
        this(barSeries, tradingRecord, 0, equityCurveMode, openPositionHandling, true, false);
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
        this(barSeries, tradingRecord, 0, EquityCurveMode.MARK_TO_MARKET, openPositionHandling, true, false);
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
        int analysisEndIndex = materializedAddressableEndIndex;
        int entryIndex = entry.getIndex();
        if (entryIndex > finalIndex || entryIndex > analysisEndIndex) {
            return;
        }
        int endIndex = determineEndIndex(position, finalIndex, analysisEndIndex);
        int seriesBegin = barSeries.getBeginIndex();
        if (endIndex < seriesBegin) {
            return;
        }

        NumFactory numFactory = barSeries.numFactory();
        boolean isLong = entry.isBuy();
        Num netEntryPrice = entry.getNetPrice();
        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            Num averageCostPerPeriod = averageHoldingCostPerPeriod(position, endIndex, numFactory);
            long accruedPeriods = Math.max(0L, (long) seriesBegin - entryIndex);
            if (entryIndex < seriesBegin && endIndex != seriesBegin) {
                // The entry predates the retained window and the first retained
                // bar carries an intermediate mark: anchor its level at the
                // first retained close after accruing every elapsed period
                // before that window.
                Num accruedCost = averageCostPerPeriod.multipliedBy(numFactory.numOf(accruedPeriods));
                Num netIntermediate = addCost(barSeries.getBar(seriesBegin).getClosePrice(), accruedCost, isLong);
                Num seedDelta = isLong ? netIntermediate.minus(netEntryPrice) : netEntryPrice.minus(netIntermediate);
                addValue(seriesBegin, seedDelta);
            }
            long start = Math.max((long) entryIndex + 1, (long) seriesBegin + 1);
            for (long i = start; i < endIndex; i++) {
                accruedPeriods = Math.max(accruedPeriods, i - entryIndex);
                Num accruedCost = averageCostPerPeriod.multipliedBy(numFactory.numOf(accruedPeriods));
                Num close = barSeries.getBar((int) i).getClosePrice();
                Num netIntermediate = addCost(close, accruedCost, isLong);
                Num delta = isLong ? netIntermediate.minus(netEntryPrice) : netEntryPrice.minus(netIntermediate);
                addValue((int) i, delta);
            }
            long exitPeriods = Math.max(0L, (long) endIndex - entryIndex);
            Num accruedExitCost = averageCostPerPeriod.multipliedBy(numFactory.numOf(exitPeriods));
            Num exitRaw = resolveExitPrice(position, endIndex, barSeries);
            Num netExit = addCost(exitRaw, accruedExitCost, isLong);
            Num deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
            addToRange(endIndex, analysisEndIndex, deltaExit);
            return;
        }

        Trade exit = position.getExit();
        if (exit != null && endIndex >= exit.getIndex()) {
            Num holdingCost = position.getHoldingCost(endIndex);
            Num netExit = addCost(exit.getNetPrice(), holdingCost, isLong);
            Num deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
            addToRange(exit.getIndex(), analysisEndIndex, deltaExit);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the cumulative PnL at the given absolute bar index. Indices outside
     * the window materialized by the underlying series resolve to the neutral value
     * zero.
     *
     * @since 0.19
     */
    @Override
    public Num getValue(int index) {
        return values.get(index);
    }

    /**
     * @return values over the captured materialized window, independent of later
     *         changes to the borrowed series bounds
     * @since 0.25.1
     */
    @Override
    public Stream<Num> stream() {
        return values.stream();
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
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Returns the borrowed caller series by contract.")
    public BarSeries getBarSeries() {
        return barSeries;
    }

    /**
     * Returns the number of values captured in the materialized window, unaffected
     * by later changes to the borrowed series.
     *
     * @return the materialized value count
     * @since 0.19
     */
    public int getSize() {
        return values.size();
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
        values.add(index, delta);
    }

    private void addToRange(int startIndex, int endIndex, Num delta) {
        values.addRange(startIndex, endIndex, delta);
    }

}
