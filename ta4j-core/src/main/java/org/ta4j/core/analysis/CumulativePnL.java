/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.stream.Stream;

import org.ta4j.core.*;
import org.ta4j.core.num.Num;

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
    private final EquityCurveMode equityCurveMode;
    /** The window captured when the curve was materialized. */
    private final AnalysisPositionSupport.Window window;
    private final OffsetNumBuffer values;

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
        AnalysisPositionSupport.Curve curve = barSeries.withReadLock(() -> {
            AnalysisPositionSupport.Window captured = AnalysisPositionSupport.captureWindow(this.barSeries, record, 0,
                    requestedFinalIndex, useRecordEnd, useSeriesEnd, true);
            Num zero = this.barSeries.numFactory().zero();
            OffsetNumBuffer buffer = AnalysisPositionSupport.buffer(captured, zero, zero);
            for (Position position : AnalysisPositionSupport.positionsForAnalysis(record, captured.finalIndex(),
                    handling, this.equityCurveMode)) {
                calculatePosition(position, captured.finalIndex(), captured, buffer);
            }
            return new AnalysisPositionSupport.Curve(captured, buffer);
        });
        this.window = curve.window();
        this.values = curve.values();
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
        calculatePosition(position, finalIndex, window, values);
    }

    private void calculatePosition(Position position, int finalIndex, AnalysisPositionSupport.Window captured,
            OffsetNumBuffer buffer) {
        Trade entry = position.getEntry();
        if (entry == null) {
            return;
        }
        int addressableEndIndex = captured.addressableEndIndex();
        int entryIndex = entry.getIndex();
        if (entryIndex > finalIndex || entryIndex > addressableEndIndex) {
            return;
        }
        int endIndex = determineEndIndex(position, finalIndex, addressableEndIndex);
        int seriesBegin = captured.beginIndex();
        if (endIndex < seriesBegin) {
            return;
        }

        boolean isLong = entry.isBuy();
        Num netEntryPrice = entry.getNetPrice();
        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            Num netExit = AnalysisPositionSupport.markToMarket(this, barSeries, position, endIndex, seriesBegin,
                    endIndex - 1, (index, netPrice, previousPrice) -> buffer.add(index,
                            isLong ? netPrice.minus(netEntryPrice) : netEntryPrice.minus(netPrice)))
                    .netPrice();
            Num deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
            buffer.addRange(endIndex, addressableEndIndex, deltaExit);
            return;
        }

        Trade exit = position.getExit();
        if (exit != null && endIndex >= exit.getIndex()) {
            Num holdingCost = position.getHoldingCost(endIndex);
            Num netExit = addCost(exit.getNetPrice(), holdingCost, isLong);
            Num deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
            buffer.addRange(exit.getIndex(), addressableEndIndex, deltaExit);
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
     * {@inheritDoc}
     *
     * @since 0.25.1
     */
    @Override
    public int getBeginIndex() {
        return window.beginIndex();
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.25.1
     */
    @Override
    public int getEndIndex() {
        return window.endIndex();
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

}
