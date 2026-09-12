/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.ReturnRepresentationPolicy;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Allows to compute the return rate of a price time-series.
 * <p>
 * Returns are calculated and formatted according to the specified
 * {@link ReturnRepresentation}. Use {@link ReturnRepresentation#LOG} for log
 * returns, or {@link ReturnRepresentation#DECIMAL},
 * {@link ReturnRepresentation#MULTIPLICATIVE}, or
 * {@link ReturnRepresentation#PERCENTAGE} for arithmetic returns in different
 * formats.
 * <p>
 * The default representation (when not explicitly specified) is obtained from
 * {@link ReturnRepresentationPolicy#getDefaultRepresentation()}.
 *
 * @see ReturnRepresentation
 * @see ReturnRepresentationPolicy
 */
public class Returns implements PerformanceIndicator {

    private final ReturnRepresentation representation;
    private final EquityCurveMode equityCurveMode;

    /** The bar series. */
    private final BarSeries barSeries;

    /** The first logical bar index stored in the internal buffers. */
    private final int seriesBegin;

    /**
     * The raw return rates (before formatting).
     * <p>
     * Stores log returns if {@code representation == LOG}, otherwise stores
     * arithmetic returns in DECIMAL format (0-based, e.g., 0.12 for +12%). Used by
     * {@link #getRawValues()} for statistical calculations.
     */
    private final List<Num> rawValues;

    /**
     * The formatted return rates (according to the configured representation).
     * <p>
     * Values are formatted during calculation using
     * {@link ReturnRepresentation#toRepresentationFromRateOfReturn(Num)} for
     * arithmetic returns, or returned as-is for log returns.
     */
    private final List<Num> values;

    private final List<Num> returnFactors;

    /**
     * Whether the first stored bar index reports a return. A windowed futures
     * series reports the first bar's return measured from the account capital;
     * every other layout keeps a placeholder value at the first stored position.
     */
    private final boolean firstBarReported;

    /**
     * Constructor.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param finalIndex           the index up to which the returns of open
     *                             positions are considered
     * @param representation       the return representation (determines both
     *                             calculation method and output format)
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public Returns(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex,
            ReturnRepresentation representation, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, new ClosePriceIndicator(barSeries), finalIndex, representation, equityCurveMode,
                openPositionHandling, null);
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
     * @param finalIndex           the index up to which the returns of open
     *                             positions are considered
     * @param representation       the return representation (determines both
     *                             calculation method and output format)
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.25.1
     */
    public Returns(BarSeries barSeries, TradingRecord tradingRecord, Indicator<Num> markPriceIndicator, int finalIndex,
            ReturnRepresentation representation, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, markPriceIndicator, finalIndex, representation, equityCurveMode,
                openPositionHandling, null);
    }

    /**
     * Canonical constructor. Derives the return factors of a native futures record
     * from consecutive equity ratios and keeps the incremental position-combination
     * path for every other record.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param markPriceIndicator   mark price indicator on the same series
     * @param finalIndex           the index up to which the returns of open
     *                             positions are considered
     * @param representation       the return representation (determines both
     *                             calculation method and output format)
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @param fallbackCapital      unlevered capital of a single position analysis,
     *                             may be {@code null}
     */
    private Returns(BarSeries barSeries, TradingRecord tradingRecord, Indicator<Num> markPriceIndicator, int finalIndex,
            ReturnRepresentation representation, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling, Num fallbackCapital) {
        TradingRecord record = Objects.requireNonNull(tradingRecord);
        OpenPositionHandling handling = Objects.requireNonNull(openPositionHandling);
        FuturesPerformanceSupport.requireMarkSeries(Objects.requireNonNull(barSeries),
                Objects.requireNonNull(markPriceIndicator));
        this.barSeries = snapshotSeries(barSeries);
        this.seriesBegin = this.barSeries.getBeginIndex();
        this.representation = Objects.requireNonNull(representation);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        int size = this.barSeries.getBarCount();
        Num one = this.barSeries.numFactory().one();
        Num zero = this.barSeries.numFactory().zero();
        Num initial = representation == ReturnRepresentation.LOG ? zero : one;
        returnFactors = new ArrayList<>(Collections.nCopies(Math.max(size, 0), initial));
        rawValues = new ArrayList<>(Collections.nCopies(Math.max(size, 0), zero));
        values = new ArrayList<>(Collections.nCopies(Math.max(size, 0), zero));
        this.firstBarReported = FuturesPerformanceSupport.isFutures(record) && this.seriesBegin > 0;
        if (FuturesPerformanceSupport.isFutures(record)) {
            fillFuturesReturnFactors(record, markPriceIndicator, finalIndex, handling, fallbackCapital);
        } else {
            calculate(record, finalIndex, handling);
        }
        buildReturns();
    }

    /**
     * Fills the return factors with the ratio of consecutive futures equity values,
     * normalized by the record account capital.
     *
     * <p>
     * Bar {@code 0} has no reported return; the first reported return is measured
     * from the account capital, so the cumulative product of the reported returns
     * equals the account growth from the initial capital.
     * </p>
     *
     * <p>
     * A previous equity of zero or less makes the subsequent return undefined and
     * is reported as {@link NaN#NaN}; a positive previous equity still reports the
     * actual arithmetic loss when the current equity falls to zero or below.
     * </p>
     *
     * @param tradingRecord   the futures trading record
     * @param markPrice       mark price indicator on the analysed series
     * @param finalIndex      index up until open position P&amp;L is considered
     * @param handling        how to handle open positions
     * @param fallbackCapital unlevered capital of a single position analysis, may
     *                        be {@code null}
     */
    private void fillFuturesReturnFactors(TradingRecord tradingRecord, Indicator<Num> markPrice, int finalIndex,
            OpenPositionHandling handling, Num fallbackCapital) {
        int seriesEnd = barSeries.getEndIndex();
        if (seriesEnd < 1) {
            return;
        }
        NumFactory numFactory = barSeries.numFactory();
        Num capital = FuturesPerformanceSupport.accountCapital(numFactory, tradingRecord, fallbackCapital);
        boolean markExposure = FuturesPerformanceSupport.includesExposure(handling, equityCurveMode);
        int effectiveFinalIndex = Math.min(tradingRecord.getEndIndex(barSeries), finalIndex);
        FuturesPerformanceSupport.Cursor cursor = FuturesPerformanceSupport.cursor(barSeries, tradingRecord,
                Math.min(effectiveFinalIndex, seriesEnd), markExposure, markPrice);
        int firstBar = Math.max(1, seriesBegin);
        if (firstBar > seriesEnd) {
            return;
        }
        Num previousEquity = firstBar == 1 ? capital : capital.plus(cursor.pnlAt(firstBar - 1));
        for (int barIndex = firstBar; barIndex <= seriesEnd; barIndex++) {
            Num equity = capital.plus(cursor.pnlAt(barIndex));
            returnFactors.set(barIndex - seriesBegin, returnFactor(previousEquity, equity));
            previousEquity = equity;
        }
    }

    /**
     * Converts consecutive equity values into the factor expected by
     * {@link #buildReturns()}.
     *
     * @param previousEquity equity at the previous bar
     * @param equity         equity at the current bar
     * @return log return when the representation is
     *         {@link ReturnRepresentation#LOG}, the equity ratio otherwise
     */
    private Num returnFactor(Num previousEquity, Num equity) {
        if (!previousEquity.isPositive() || !Num.isFinite(previousEquity) || !Num.isFinite(equity)) {
            return NaN.NaN;
        }
        Num ratio = equity.dividedBy(previousEquity);
        if (representation == ReturnRepresentation.LOG) {
            return ratio.isPositive() ? ratio.log() : NaN.NaN;
        }
        return ratio;
    }

    /**
     * Constructor with default representation from
     * {@link ReturnRepresentationPolicy#getDefaultRepresentation()}.
     *
     * @param barSeries the bar series
     * @param position  a single position
     */
    public Returns(BarSeries barSeries, Position position) {
        this(barSeries, position, ReturnRepresentationPolicy.getDefaultRepresentation(),
                EquityCurveMode.MARK_TO_MARKET);
    }

    /**
     * Constructor with default representation from
     * {@link ReturnRepresentationPolicy#getDefaultRepresentation()}.
     *
     * @param barSeries       the bar series
     * @param position        a single position
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public Returns(BarSeries barSeries, Position position, EquityCurveMode equityCurveMode) {
        this(barSeries, position, ReturnRepresentationPolicy.getDefaultRepresentation(), equityCurveMode);
    }

    /**
     * Constructor.
     *
     * @param barSeries      the bar series
     * @param position       a single position
     * @param representation the return representation (determines both calculation
     *                       method and output format)
     */
    public Returns(BarSeries barSeries, Position position, ReturnRepresentation representation) {
        this(barSeries, position, representation, EquityCurveMode.MARK_TO_MARKET);
    }

    /**
     * Constructor.
     *
     * @param barSeries       the bar series
     * @param position        a single position
     * @param representation  the return representation (determines both calculation
     *                        method and output format)
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public Returns(BarSeries barSeries, Position position, ReturnRepresentation representation,
            EquityCurveMode equityCurveMode) {
        this(barSeries, FuturesPerformanceSupport.analysisRecord(position), new ClosePriceIndicator(barSeries),
                barSeries.getEndIndex(), representation, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET,
                FuturesPerformanceSupport.fallbackCapital(position));
    }

    /**
     * Constructor.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param representation  the return representation (determines both calculation
     *                        method and output format)
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public Returns(BarSeries barSeries, TradingRecord tradingRecord, ReturnRepresentation representation,
            EquityCurveMode equityCurveMode) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), representation, equityCurveMode,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor with default representation from
     * {@link ReturnRepresentationPolicy#getDefaultRepresentation()}.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     */
    public Returns(BarSeries barSeries, TradingRecord tradingRecord) {
        this(barSeries, tradingRecord, ReturnRepresentationPolicy.getDefaultRepresentation(),
                EquityCurveMode.MARK_TO_MARKET);
    }

    /**
     * Constructor with default representation from
     * {@link ReturnRepresentationPolicy#getDefaultRepresentation()}.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public Returns(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode) {
        this(barSeries, tradingRecord, ReturnRepresentationPolicy.getDefaultRepresentation(), equityCurveMode);
    }

    /**
     * Constructor.
     *
     * @param barSeries      the bar series
     * @param tradingRecord  the trading record
     * @param representation the return representation (determines both calculation
     *                       method and output format)
     */
    public Returns(BarSeries barSeries, TradingRecord tradingRecord, ReturnRepresentation representation) {
        this(barSeries, tradingRecord, representation, EquityCurveMode.MARK_TO_MARKET);
    }

    /**
     * Constructor.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param representation       the return representation (determines both
     *                             calculation method and output format)
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public Returns(BarSeries barSeries, TradingRecord tradingRecord, ReturnRepresentation representation,
            OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), representation,
                EquityCurveMode.MARK_TO_MARKET, openPositionHandling);
    }

    /**
     * Constructor.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param representation       the return representation (determines both
     *                             calculation method and output format)
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public Returns(BarSeries barSeries, TradingRecord tradingRecord, ReturnRepresentation representation,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), representation, equityCurveMode,
                openPositionHandling);
    }

    /**
     * @return the return rates (formatted according to the configured
     *         representation)
     */
    public List<Num> getValues() {
        return absoluteValues(values);
    }

    /**
     * @param index the bar index
     * @return the return rate value at the index-th position (formatted according
     *         to the configured representation)
     */
    @Override
    public Num getValue(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("index must not be negative: " + index);
        }
        if (index < seriesBegin) {
            return index == 0 ? NaN.NaN : barSeries.numFactory().zero();
        }
        int offset = index - seriesBegin;
        if (offset >= values.size()) {
            throw new IndexOutOfBoundsException("index is outside the series window: " + index);
        }
        return values.get(offset);
    }

    /**
     * @return the raw return rates (before formatting)
     */
    public List<Num> getRawValues() {
        return absoluteValues(rawValues);
    }

    /**
     * @return whether the first stored bar index reports a return. A windowed
     *         futures series reports the first bar's return from the account
     *         capital; every other layout keeps a placeholder value at the first
     *         stored position.
     * @since 0.25.1
     */
    public boolean hasFirstBarReturn() {
        return firstBarReported;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    @Override
    public BarSeries getBarSeries() {
        return snapshotSeries(barSeries);
    }

    /**
     * @return the size of the return series.
     */
    public int getSize() {
        return barSeries.getBarCount() - 1;
    }

    /**
     * Calculates the returns for a single position.
     *
     * @param position   a single position
     * @param finalIndex the index up to which the returns of open positions are
     *                   considered
     * @since 0.22.2
     */
    @Override
    public void calculatePosition(Position position, int finalIndex) {
        Trade entry = position.getEntry();
        if (entry == null) {
            return;
        }
        int entryIndex = entry.getIndex();
        int seriesEnd = barSeries.getEndIndex();
        if (entryIndex > finalIndex || entryIndex > seriesEnd) {
            return;
        }
        int endIndex = determineEndIndex(position, finalIndex, seriesEnd);
        int seriesBegin = barSeries.getBeginIndex();
        if (endIndex < seriesBegin) {
            return;
        }

        NumFactory numFactory = barSeries.numFactory();
        Num minusOne = numFactory.minusOne();
        boolean isLongTrade = entry.isBuy();
        int start = Math.max(entryIndex + 1, seriesBegin + 1);

        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            Num avgCost = averageHoldingCostPerPeriod(position, endIndex, numFactory);
            Num lastPrice = entry.getNetPrice();
            for (int i = start; i < endIndex; i++) {
                Bar bar = barSeries.getBar(i);
                Num intermediateNetPrice = addCost(bar.getClosePrice(), avgCost, isLongTrade);
                Num rawReturn = calculateReturn(intermediateNetPrice, lastPrice);
                Num strategyReturn = isLongTrade ? rawReturn : rawReturn.multipliedBy(minusOne);
                combineReturnAtIndex(i, strategyReturn);
                lastPrice = intermediateNetPrice;
            }
            Num exitPrice = resolveExitPrice(position, endIndex, barSeries);
            Num rawReturn = calculateReturn(addCost(exitPrice, avgCost, isLongTrade), lastPrice);
            Num strategyReturn = isLongTrade ? rawReturn : rawReturn.multipliedBy(minusOne);
            combineReturnAtIndex(endIndex, strategyReturn);
            return;
        }

        Trade exit = position.getExit();
        if (exit != null && endIndex >= exit.getIndex()) {
            Num holdingCost = position.getHoldingCost(endIndex);
            Num netExit = addCost(exit.getNetPrice(), holdingCost, isLongTrade);
            Num rawReturn = calculateReturn(netExit, entry.getNetPrice());
            Num strategyReturn = isLongTrade ? rawReturn : rawReturn.multipliedBy(minusOne);
            combineReturnAtIndex(exit.getIndex(), strategyReturn);
        }
    }

    /**
     * @return the equity curve mode used for this return series
     * @since 0.22.2
     */
    @Override
    public EquityCurveMode getEquityCurveMode() {
        return equityCurveMode;
    }

    /**
     * Calculates the raw return between two prices.
     *
     * @param xNew the new price
     * @param xOld the old price
     * @return the raw return (log return if representation is LOG, arithmetic
     *         return otherwise)
     */
    private Num calculateReturn(Num xNew, Num xOld) {
        if (representation == ReturnRepresentation.LOG) {
            // r_i = ln(P_i/P_(i-1))
            return (xNew.dividedBy(xOld)).log();
        }
        // r_i = P_i/P_(i-1) - 1 (arithmetic return, which is DECIMAL format)
        Num one = barSeries.numFactory().one();
        return xNew.dividedBy(xOld).minus(one);
    }

    private Num toFactor(Num strategyReturn) {
        Num one = barSeries.numFactory().one();
        return strategyReturn.plus(one);
    }

    private void combineReturnAtIndex(int index, Num strategyReturn) {
        int offset = index - seriesBegin;
        if (offset < 0 || offset >= returnFactors.size()) {
            return;
        }
        if (representation == ReturnRepresentation.LOG) {
            returnFactors.set(offset, returnFactors.get(offset).plus(strategyReturn));
        } else {
            returnFactors.set(offset, returnFactors.get(offset).multipliedBy(toFactor(strategyReturn)));
        }
    }

    private void buildReturns() {
        if (rawValues.isEmpty()) {
            return;
        }
        Num one = barSeries.numFactory().one();
        int start = seriesBegin == 0 ? 1 : 0;
        if (seriesBegin == 0) {
            rawValues.set(0, NaN.NaN);
            values.set(0, NaN.NaN);
        }
        for (int i = start; i < rawValues.size(); i++) {
            if (representation == ReturnRepresentation.LOG) {
                Num logReturn = returnFactors.get(i);
                rawValues.set(i, logReturn);
                values.set(i, logReturn);
            } else {
                Num factor = returnFactors.get(i);
                Num rawReturn = factor.minus(one);
                rawValues.set(i, rawReturn);
                values.set(i, representation.toRepresentationFromRateOfReturn(rawReturn));
            }
        }
    }

    private List<Num> absoluteValues(List<Num> stored) {
        int size = barSeries.getBarCount();
        if (seriesBegin > Integer.MAX_VALUE - size) {
            throw new IllegalStateException(
                    "series window is too large to materialize absolute indices: " + seriesBegin);
        }
        int absoluteSize = seriesBegin + size;
        List<Num> absolute = new ArrayList<>(absoluteSize);
        for (int i = 0; i < seriesBegin; i++) {
            absolute.add(i == 0 ? NaN.NaN : barSeries.numFactory().zero());
        }
        absolute.addAll(stored);
        return List.copyOf(absolute);
    }

    private static BarSeries snapshotSeries(final BarSeries barSeries) {
        BarSeries series = Objects.requireNonNull(barSeries);
        if (series.getBarCount() == 0) {
            return new BaseBarSeriesBuilder().withName(series.getName())
                    .withNumFactory(series.numFactory())
                    .withMaxBarCount(series.getMaximumBarCount())
                    .build();
        }
        return new BaseBarSeriesBuilder().withName(series.getName())
                .withNumFactory(series.numFactory())
                .withBars(series.getBarData())
                .withBeginIndex(series.getBeginIndex())
                .withMaxBarCount(series.getMaximumBarCount())
                .build();
    }
}
