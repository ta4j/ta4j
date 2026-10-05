/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

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
        this(barSeries, tradingRecord, requestedFinalIndex, equityCurveMode, openPositionHandling, useRecordEnd,
                useSeriesEnd, new ClosePriceIndicator(barSeries));
    }

    private CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, int requestedFinalIndex,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling, boolean useRecordEnd,
            boolean useSeriesEnd, Indicator<Num> markPriceIndicator) {
        this.barSeries = Objects.requireNonNull(barSeries, "barSeries");
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        TradingRecord record = Objects.requireNonNull(tradingRecord);
        OpenPositionHandling handling = Objects.requireNonNull(openPositionHandling);
        FuturesPerformanceSupport.requireMarkSeries(barSeries, markPriceIndicator);
        AnalysisPositionSupport.Curve curve = AnalysisPositionSupport.materialize(this, barSeries, record, 0,
                requestedFinalIndex, useRecordEnd, useSeriesEnd, true, handling, (captured, positions, costs) -> {
                    Num zero = this.barSeries.numFactory().zero();
                    OffsetNumBuffer buffer = AnalysisPositionSupport.buffer(captured, zero, zero);
                    if (FuturesPerformanceSupport.isFutures(record)) {
                        FuturesPerformanceSupport.Cursor cursor = FuturesPerformanceSupport.cursor(barSeries, record,
                                captured.endIndex(),
                                FuturesPerformanceSupport.includesExposure(handling, equityCurveMode),
                                markPriceIndicator);
                        for (long index = captured.beginIndex(); index <= captured.bufferEndIndex(); index++) {
                            buffer.add((int) index, cursor.pnlAt((int) index));
                        }
                    } else
                        for (Position position : positions) {
                            calculatePosition(position, captured.finalIndex(), captured, buffer, costs.get(position));
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
        this(barSeries, FuturesPerformanceSupport.analysisRecord(position), 0, equityCurveMode,
                OpenPositionHandling.MARK_TO_MARKET, false, true);
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
     * @throws IllegalStateException if a bar this curve was materialized from was
     *                               evicted, replaced or updated since
     * @since 0.22.2
     */
    @Override
    public void calculatePosition(Position position, int finalIndex) {
        AnalysisPositionSupport.PricedPosition priced = AnalysisPositionSupport.pricePosition(this, barSeries, position,
                finalIndex, window, true);
        if (priced != null) {
            AnalysisPositionSupport.updateCapturedCurve(barSeries, window, priced, values,
                    staged -> calculatePosition(position, finalIndex, window, staged, priced.holdingCost()));
        }
    }

    private void calculatePosition(Position position, int finalIndex, AnalysisPositionSupport.Window captured,
            OffsetNumBuffer buffer, Num holdingCost) {
        Trade entry = position.getEntry();
        if (entry == null) {
            return;
        }
        int lastCapturedIndex = captured.bufferEndIndex();
        int entryIndex = entry.getIndex();
        if (entryIndex > finalIndex || entryIndex > lastCapturedIndex) {
            return;
        }
        int endIndex = determineEndIndex(position, finalIndex, lastCapturedIndex);
        int seriesBegin = captured.beginIndex();
        boolean isLong = entry.isBuy();
        Num netEntryPrice = entry.getNetPrice();
        if (endIndex < seriesBegin) {
            if (!captured.carriesBeforeWindow(position)) {
                return;
            }
            Trade exit = position.getExit();
            Num netExit = addCost(exit.getNetPrice(), holdingCost, isLong);
            Num deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
            buffer.addRange(seriesBegin, lastCapturedIndex, deltaExit);
            buffer.addBaseline(deltaExit);
            return;
        }

        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            Num basis = AnalysisPositionSupport.valuationBasis(this, barSeries, position, holdingCost, endIndex,
                    seriesBegin);
            Num netExit = AnalysisPositionSupport.markToMarket(this, barSeries, position, holdingCost, endIndex,
                    seriesBegin, endIndex - 1, (index, netPrice, previousPrice) -> buffer.add(index,
                            isLong ? netPrice.minus(basis) : basis.minus(netPrice)))
                    .netPrice();
            Num deltaExit = isLong ? netExit.minus(basis) : basis.minus(netExit);
            buffer.addRange(endIndex, captured.bufferEndIndex(), deltaExit);
            return;
        }

        Trade exit = position.getExit();
        if (exit != null && endIndex >= exit.getIndex()) {
            Num netExit = addCost(exit.getNetPrice(), holdingCost, isLong);
            Num deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
            buffer.addRange(exit.getIndex(), captured.bufferEndIndex(), deltaExit);
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
     * {@inheritDoc}
     *
     * @since 0.25.1
     */
    @Override
    public Num getBaselineValue() {
        return values.baseline();
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
        this(barSeries, tradingRecord, finalIndex, equityCurveMode, openPositionHandling, false, false,
                markPriceIndicator);
    }
}
