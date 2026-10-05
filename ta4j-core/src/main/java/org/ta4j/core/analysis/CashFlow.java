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
    private boolean initialReturnEligible;
    private boolean preWindowFuturesActivity;
    private boolean firstBarFuturesActivity;
    private final Num futuresCapital;
    private final Indicator<Num> futuresMark;
    private final boolean markFuturesExposure;
    private FuturesPerformanceSupport.PnLAccumulator futuresPnL;

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
        this(barSeries, position, new ClosePriceIndicator(barSeries), equityCurveMode);
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
        this(barSeries, tradingRecord, startIndex, requestedFinalIndex, equityCurveMode, openPositionHandling,
                useRecordEnd, useSeriesEnd, padToSeriesEnd, new ClosePriceIndicator(barSeries), null);
    }

    private CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int startIndex, int requestedFinalIndex,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling, boolean useRecordEnd,
            boolean useSeriesEnd, boolean padToSeriesEnd, Indicator<Num> markPriceIndicator, Num fallbackCapital) {
        this.barSeries = Objects.requireNonNull(barSeries, "barSeries");
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        TradingRecord record = Objects.requireNonNull(tradingRecord);
        OpenPositionHandling handling = Objects.requireNonNull(openPositionHandling);
        FuturesPerformanceSupport.requireMarkSeries(barSeries, markPriceIndicator);
        boolean futures = FuturesPerformanceSupport.isFutures(record);
        Materialized curve = AnalysisPositionSupport.materialize(this, barSeries, record, startIndex,
                requestedFinalIndex, useRecordEnd, useSeriesEnd, padToSeriesEnd, handling,
                (captured, positions, costs) -> {
                    Num one = this.barSeries.numFactory().one();
                    OffsetNumBuffer buffer = AnalysisPositionSupport.buffer(captured, one, one);
                    FuturesPerformanceSupport.PnLAccumulator pnl = null;
                    if (futures) {
                        pnl = fillFuturesValues(record, captured, buffer, markPriceIndicator, handling,
                                fallbackCapital);
                    } else
                        for (Position position : positions) {
                            calculatePosition(position, captured.finalIndex(), captured, buffer, costs.get(position));
                        }
                    return new Materialized(captured, buffer, pnl);
                });
        this.window = curve.window();
        this.values = curve.values();
        this.futuresPnL = curve.pnl();
        this.futuresCapital = record.getInitialCapital() == null ? fallbackCapital : record.getInitialCapital();
        this.futuresMark = markPriceIndicator;
        this.markFuturesExposure = FuturesPerformanceSupport.includesExposure(handling, equityCurveMode);
        this.preWindowFuturesActivity = futures
                && FuturesPerformanceSupport.hasPreWindowActivity(record, window.beginIndex(), markFuturesExposure);
        this.firstBarFuturesActivity = futures
                && FuturesPerformanceSupport.hasActivityAtIndex(record, window.beginIndex());
        this.initialReturnEligible = futures && !window.isEmpty() && !preWindowFuturesActivity
                && firstBarFuturesActivity;
    }

    private record Materialized(AnalysisPositionSupport.Window window, OffsetNumBuffer values,
            FuturesPerformanceSupport.PnLAccumulator pnl) {
    }

    /**
     * Calculates the cash flow for a single position (including accrued cashflow
     * for open positions).
     *
     * @param position   a single position
     * @param finalIndex index up until cash flow of open positions is considered
     * @throws IllegalStateException if a bar this curve was materialized from was
     *                               evicted, replaced or updated since
     * @since 0.22.2
     */
    @Override
    public void calculatePosition(Position position, int finalIndex) {
        AnalysisPositionSupport.PricedPosition priced = AnalysisPositionSupport.pricePosition(this, barSeries, position,
                finalIndex, window, true);
        if (priced != null && FuturesPerformanceSupport.isFutures(position)) {
            TradingRecord single = FuturesPerformanceSupport.analysisRecord(position);
            Num capital = FuturesPerformanceSupport.accountCapital(barSeries.numFactory(), single,
                    futuresCapital == null ? FuturesPerformanceSupport.fallbackCapital(position) : futuresCapital);
            if (capital.isZero())
                return;
            boolean preWindow = preWindowFuturesActivity || FuturesPerformanceSupport.hasPreWindowActivity(position,
                    window.beginIndex(), markFuturesExposure);
            boolean firstActivity = firstBarFuturesActivity
                    || FuturesPerformanceSupport.hasActivityAtIndex(position, window.beginIndex());
            boolean initialReturn = !preWindow && firstActivity;
            boolean nativeCurve = futuresPnL != null;
            FuturesPerformanceSupport.PnLAccumulator pnl = futuresPnL == null
                    ? new FuturesPerformanceSupport.PnLAccumulator(window, barSeries.numFactory())
                    : futuresPnL.copy();
            AnalysisPositionSupport.updateCapturedCurve(barSeries, window, priced, values, staged -> {
                Num one = barSeries.numFactory().one();
                FuturesPerformanceSupport.addPositionPnL(barSeries, position, finalIndex, window, markFuturesExposure,
                        futuresMark, pnl);
                OffsetNumBuffer result = AnalysisPositionSupport.buffer(window, one, one);
                for (long index = window.beginIndex(); index <= window.bufferEndIndex(); index++) {
                    Num equity = nativeCurve ? capital.plus(pnl.get((int) index)).dividedBy(capital)
                            : staged.get((int) index).plus(pnl.get((int) index).dividedBy(capital));
                    result.multiply((int) index, equity);
                }
                if (!initialReturn && !window.isEmpty())
                    result.multiplyBaseline(result.get(window.beginIndex()));
                staged.replaceWith(result);
            }, true);
            if (nativeCurve) {
                futuresPnL = pnl;
            }
            preWindowFuturesActivity = preWindow;
            firstBarFuturesActivity = firstActivity;
            initialReturnEligible = initialReturn;
            return;
        }
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
        int windowStartIndex = captured.beginIndex();
        int windowEndIndex = captured.bufferEndIndex();
        int entryIndex = entry.getIndex();
        if (entryIndex > finalIndex || entryIndex > windowEndIndex) {
            return;
        }
        int endIndex = determineEndIndex(position, finalIndex, windowEndIndex);
        if (windowStartIndex > windowEndIndex) {
            return;
        }
        boolean isLongTrade = entry.isBuy();
        Num netEntryPrice = entry.getNetPrice();
        if (endIndex < windowStartIndex) {
            if (!captured.carriesBeforeWindow(position)) {
                return;
            }
            Num entryEquity = buffer.get(windowStartIndex);
            if (!entryEquity.isGreaterThan(barSeries.numFactory().zero())) {
                return;
            }
            Trade exit = position.getExit();
            Num netExitPrice = addCost(exit.getNetPrice(), holdingCost, isLongTrade);
            Num ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
            buffer.multiplyRange(windowStartIndex, windowEndIndex, ratio);
            buffer.multiplyBaseline(ratio);
            return;
        }

        Num entryEquity = buffer.get(Math.max(entryIndex, windowStartIndex));
        if (!entryEquity.isGreaterThan(barSeries.numFactory().zero())) {
            return;
        }
        int ratioIndex = endIndex;
        // A same-bar ratio moves to the next bar only inside the logical analysis
        // window; the padded buffer can extend past it.
        if (ratioIndex == entryIndex && entryIndex < captured.endIndex()) {
            ratioIndex = entryIndex + 1;
        }
        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            Num basis = AnalysisPositionSupport.valuationBasis(this, barSeries, position, holdingCost, endIndex,
                    windowStartIndex);
            Num netExitPrice = AnalysisPositionSupport.markToMarket(this, barSeries, position, holdingCost, endIndex,
                    windowStartIndex, windowEndIndex, (index, netPrice, previousPrice) -> buffer.multiply(index,
                            getIntermediateRatio(isLongTrade, basis, netPrice)))
                    .netPrice();
            Num ratio = getIntermediateRatio(isLongTrade, basis, netExitPrice);
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
     * {@inheritDoc}
     *
     * @since 0.25.1
     */
    @Override
    public Num getBaselineValue() {
        return values.baseline();
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

    /**
     * Constructor valuing open exposure at an explicit mark price.
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
     * @param finalIndex           index up until cash flows of open positions are
     *                             considered
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.25.1
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, Indicator<Num> markPriceIndicator, int finalIndex,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, 0, finalIndex, equityCurveMode, openPositionHandling, false, false, true,
                markPriceIndicator, null);
    }

    /**
     * Constructor materializing only a bounded logical window on the original
     * series and valuing open exposure at an explicit mark price. Discarded bars
     * are not stored and retain the neutral value of one.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param markPriceIndicator   mark price indicator on the same series
     * @param startIndex           first logical bar index to materialize
     * @param finalIndex           last logical bar index to materialize and to
     *                             consider for open positions
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.25.1
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, Indicator<Num> markPriceIndicator, int startIndex,
            int finalIndex, EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, startIndex, finalIndex, equityCurveMode, openPositionHandling, false, false,
                false, markPriceIndicator, null);
    }

    /**
     * Constructor for cash flows of a position using an explicit mark price.
     *
     * @param barSeries          the bar series
     * @param position           a single position
     * @param markPriceIndicator mark price indicator on the same series
     * @param equityCurveMode    the calculation mode
     * @since 0.25.1
     */
    public CashFlow(BarSeries barSeries, Position position, Indicator<Num> markPriceIndicator,
            EquityCurveMode equityCurveMode) {
        this(barSeries, FuturesPerformanceSupport.analysisRecord(position), 0, 0, equityCurveMode,
                OpenPositionHandling.MARK_TO_MARKET, false, true, true, markPriceIndicator,
                FuturesPerformanceSupport.fallbackCapital(position));
    }

    /**
     * Whether the first retained bar includes a futures return from initial
     * capital.
     *
     * @return whether the cash flow has an initial futures return
     * @since 0.25.1
     */
    public boolean hasInitialReturn() {
        return initialReturnEligible;
    }

    private FuturesPerformanceSupport.PnLAccumulator fillFuturesValues(TradingRecord record,
            AnalysisPositionSupport.Window captured, OffsetNumBuffer buffer, Indicator<Num> markPrice,
            OpenPositionHandling handling, Num fallbackCapital) {
        NumFactory factory = barSeries.numFactory();
        if (captured.isEmpty())
            return new FuturesPerformanceSupport.PnLAccumulator(captured, factory);
        Num capital = FuturesPerformanceSupport.accountCapital(factory, record, fallbackCapital);
        if (capital.isZero())
            return new FuturesPerformanceSupport.PnLAccumulator(captured, factory);
        boolean markExposure = FuturesPerformanceSupport.includesExposure(handling, equityCurveMode);
        FuturesPerformanceSupport.Cursor cursor = FuturesPerformanceSupport.cursor(barSeries, record,
                captured.endIndex(), markExposure, markPrice);
        FuturesPerformanceSupport.PnLAccumulator pnl = FuturesPerformanceSupport.pnl(cursor, captured, factory);
        boolean initial = !FuturesPerformanceSupport.hasPreWindowActivity(record, captured.beginIndex(), markExposure)
                && FuturesPerformanceSupport.hasActivityAtIndex(record, captured.beginIndex());
        for (long index = captured.beginIndex(); index <= captured.bufferEndIndex(); index++) {
            Num equity = capital.plus(pnl.get((int) index)).dividedBy(capital);
            buffer.multiply((int) index, equity);
            if (index == captured.beginIndex() && !initial)
                buffer.multiplyBaseline(equity);
        }
        return pnl;
    }
}
