/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.stream.Stream;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Allows to follow the money cash flow involved by a list of positions over a
 * bar series, either marked to market or using realized values only.
 */
public class CashFlow implements PerformanceIndicator {

    /**
     * The bar series.
     */
    private final BarSeries barSeries;

    /**
     * The (accrued) cash flow sequence (without trading costs).
     */
    private final OffsetNumBuffer values;

    /**
     * The window captured when the cash flow was materialized.
     */
    private final AnalysisPositionSupport.Window window;

    /**
     * The equity curve calculation mode.
     */
    private final EquityCurveMode equityCurveMode;

    /**
     * Constructor.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param finalIndex           index up until cash flows of open positions are
     *                             considered
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, 0, finalIndex, equityCurveMode, openPositionHandling, false, false, true);
    }

    /**
     * Constructor materializing only a bounded logical window on the original
     * series.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param startIndex           first absolute bar index to materialize
     * @param finalIndex           last absolute bar index to materialize and to
     *                             consider for open positions
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.22.5
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int startIndex, int finalIndex,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, startIndex, finalIndex, equityCurveMode, openPositionHandling, false, false,
                false);
    }

    /**
     * Constructor for cash flows of a closed position.
     *
     * @param barSeries       the bar series
     * @param position        a single position
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, Position position, EquityCurveMode equityCurveMode) {
        this(barSeries, new BaseTradingRecord(position), 0, 0, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET,
                false, true, true);
    }

    /**
     * Constructor.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param finalIndex      index up until cash flows of open positions are
     *                        considered
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex, EquityCurveMode equityCurveMode) {
        this(barSeries, tradingRecord, finalIndex, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor for cash flows of a closed position.
     *
     * @param barSeries the bar series
     * @param position  a single position
     */
    public CashFlow(BarSeries barSeries, Position position) {
        this(barSeries, position, EquityCurveMode.MARK_TO_MARKET);
    }

    /**
     * Constructor for cash flows of closed positions of a trading record.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord) {
        this(barSeries, tradingRecord, 0, 0, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET, true,
                false, true);
    }

    /**
     * Constructor.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode) {
        this(barSeries, tradingRecord, 0, 0, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET, true, false, true);
    }

    /**
     * Constructor.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, 0, 0, equityCurveMode, openPositionHandling, true, false, true);
    }

    /**
     * Constructor.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     * @param finalIndex    index up until cash flows of open positions are
     *                      considered
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex) {
        this(barSeries, tradingRecord, finalIndex, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, 0, 0, EquityCurveMode.MARK_TO_MARKET, openPositionHandling, true, false, true);
    }

    private CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int startIndex, int requestedFinalIndex,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling, boolean useRecordEnd,
            boolean useSeriesEnd, boolean padToSeriesEnd) {
        this.barSeries = Objects.requireNonNull(barSeries, "barSeries");
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        TradingRecord record = Objects.requireNonNull(tradingRecord);
        OpenPositionHandling handling = Objects.requireNonNull(openPositionHandling);
        AnalysisPositionSupport.Curve curve = barSeries.withReadLock(() -> {
            AnalysisPositionSupport.Window captured = AnalysisPositionSupport.captureWindow(this.barSeries, record,
                    startIndex, requestedFinalIndex, useRecordEnd, useSeriesEnd, padToSeriesEnd);
            Num one = this.barSeries.numFactory().one();
            OffsetNumBuffer buffer = AnalysisPositionSupport.buffer(captured, one, one);
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
     * Calculates the cash flow for a single position (including accrued cashflow
     * for open positions).
     *
     * @param position   a single position
     * @param finalIndex index up until cash flow of open positions is considered
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
        int windowStartIndex = captured.beginIndex();
        int windowEndIndex = captured.bufferEndIndex();
        if (windowStartIndex > windowEndIndex || endIndex < windowStartIndex) {
            return;
        }

        boolean isLongTrade = entry.isBuy();
        Num netEntryPrice = entry.getNetPrice();
        Num entryEquity = buffer.get(Math.max(entryIndex, windowStartIndex));
        if (!entryEquity.isGreaterThan(barSeries.numFactory().zero())) {
            return;
        }
        int ratioIndex = endIndex;
        if (ratioIndex == entryIndex && entryIndex < barSeries.getEndIndex()) {
            ratioIndex = entryIndex + 1;
        }

        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            Num netExitPrice = AnalysisPositionSupport.markToMarket(this, barSeries, position, endIndex,
                    windowStartIndex, windowEndIndex, (index, netPrice, previousPrice) -> buffer.multiply(index,
                            getIntermediateRatio(isLongTrade, netEntryPrice, netPrice)))
                    .netPrice();
            Num ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
            if (ratioIndex <= windowEndIndex) {
                buffer.multiply(ratioIndex, ratio);
            }
            if (ratioIndex < windowEndIndex) {
                // ratioIndex + 1 must stay representable: skip the empty
                // successor range instead of letting the increment overflow.
                buffer.multiplyRange(ratioIndex + 1, windowEndIndex, ratio);
            }
            return;
        }

        Trade exit = position.getExit();
        if (exit != null && endIndex >= exit.getIndex()) {
            Num holdingCost = position.getHoldingCost(endIndex);
            Num netExitPrice = addCost(exit.getNetPrice(), holdingCost, isLongTrade);
            Num ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
            buffer.multiplyRange(Math.max(ratioIndex, windowStartIndex), windowEndIndex, ratio);
        }
    }

    /**
     * @param index the bar index
     * @return the cash flow value at the index-th position, or the neutral value
     *         one for indices outside the materialized window
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

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Returns the borrowed caller series by contract.")
    public BarSeries getBarSeries() {
        return barSeries;
    }

    /**
     * Returns the first absolute index of the captured curve, independent of later
     * changes to the borrowed series.
     *
     * @return the captured begin index
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
     * @return the number of values captured in the materialized window, unaffected
     *         by later changes to the borrowed series
     */
    public int getSize() {
        return values.size();
    }

    /**
     * @return the equity curve mode used for this cash flow
     * @since 0.22.2
     */
    @Override
    public EquityCurveMode getEquityCurveMode() {
        return equityCurveMode;
    }

    private static Num getIntermediateRatio(boolean isLongTrade, Num entryPrice, Num exitPrice) {
        if (isLongTrade) {
            return exitPrice.dividedBy(entryPrice);
        }
        return entryPrice.getNumFactory().numOf(2).minus(exitPrice.dividedBy(entryPrice));
    }
}
