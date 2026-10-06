/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.ReturnRepresentationPolicy;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

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
 * <p>
 * The return values are materialized positionally from the captured series
 * begin index through the logical series end captured with them. Positions are
 * never priced after that end: one still open there is marked at its last close
 * rather than at an exit that happened later. {@link #getValue(int)} returns
 * {@link NaN#NaN} outside that materialized range.
 *
 * <p>
 * Native period factors compare normalized entering and current equity before
 * rounding either amount or rescaling by account capital.
 *
 * @see ReturnRepresentation
 * @see ReturnRepresentationPolicy
 */
public class Returns implements PerformanceIndicator {

    private final ReturnRepresentation representation;
    private final EquityCurveMode equityCurveMode;

    /** The bar series. */
    private final BarSeries barSeries;

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

    private final OffsetNumBuffer returnFactors;
    private FuturesPerformanceSupport.PnLAccumulator futuresPnL;
    private OffsetNumBuffer spotReturnFactors;
    private boolean spotFirstRetainedSlotSeeded;
    private boolean spotDefinesHeadPeriod;
    private final Num futuresCapital;
    private final Indicator<Num> futuresMark;
    private final boolean markFuturesExposure;
    private boolean preWindowFuturesActivity;
    private boolean firstBarFuturesActivity;

    /**
     * True when a position entered before the retained window marked the first
     * retained slot against its entry price, giving that slot a defined return even
     * though no prior in-window close exists.
     */
    private boolean firstRetainedSlotSeeded;
    private boolean seededFirstBarReturn;

    /**
     * The window captured when the return buffers were materialized. Later rolling
     * advances of the borrowed series must not rebase lookups, or old returns leak
     * onto never-calculated bars.
     */
    private final AnalysisPositionSupport.Window window;

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
        this(barSeries, tradingRecord, finalIndex, representation, equityCurveMode, openPositionHandling, false);
    }

    private Returns(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex,
            ReturnRepresentation representation, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling, boolean useRecordEnd) {
        this(barSeries, tradingRecord, finalIndex, representation, equityCurveMode, openPositionHandling, useRecordEnd,
                new ClosePriceIndicator(barSeries), null);
    }

    private Returns(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex,
            ReturnRepresentation representation, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling, boolean useRecordEnd, Indicator<Num> markPriceIndicator,
            Num fallbackCapital) {
        this.barSeries = Objects.requireNonNull(barSeries, "barSeries");
        this.representation = Objects.requireNonNull(representation);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        TradingRecord record = Objects.requireNonNull(tradingRecord);
        OpenPositionHandling handling = Objects.requireNonNull(openPositionHandling);
        FuturesPerformanceSupport.requireMarkSeries(barSeries, markPriceIndicator);
        boolean futures = FuturesPerformanceSupport.isFutures(record);
        Materialized materialized = AnalysisPositionSupport.materialize(this, barSeries, record, 0, finalIndex,
                useRecordEnd, false, true, handling, (captured, positions, costs) -> {
                    Num initial = this.representation == ReturnRepresentation.LOG ? this.barSeries.numFactory().zero()
                            : this.barSeries.numFactory().one();
                    OffsetNumBuffer factors = AnalysisPositionSupport.buffer(captured, initial, NaN.NaN);
                    boolean seeded = false;
                    Num zero = this.barSeries.numFactory().zero();
                    FuturesPerformanceSupport.PnLAccumulator pnl = null;
                    if (futures) {
                        Num capital = captured.isEmpty() ? zero
                                : FuturesPerformanceSupport.accountCapital(barSeries.numFactory(), record,
                                        fallbackCapital);
                        pnl = FuturesPerformanceSupport
                                .pnl(FuturesPerformanceSupport.cursor(barSeries, record, captured.endIndex(),
                                        FuturesPerformanceSupport.includesExposure(handling, equityCurveMode),
                                        markPriceIndicator), captured, barSeries.numFactory(), capital);
                        seeded = publishReturnFactors(pnl, capital,
                                FuturesPerformanceSupport.hasActivityAtIndex(record, captured.beginIndex()), false,
                                AnalysisPositionSupport.buffer(captured, initial, NaN.NaN), captured, factors);
                    } else
                        for (Position position : positions) {
                            seeded |= calculatePosition(position, captured.finalIndex(), captured, factors,
                                    costs.get(position));
                        }
                    return new Materialized(captured, factors, pnl, seeded);
                });
        this.window = materialized.window();
        this.returnFactors = materialized.factors();
        this.futuresPnL = materialized.pnl() == null
                ? new FuturesPerformanceSupport.PnLAccumulator(window, barSeries.numFactory())
                : materialized.pnl();
        Num spotInitial = representation == ReturnRepresentation.LOG ? barSeries.numFactory().zero()
                : barSeries.numFactory().one();
        this.spotReturnFactors = futures ? AnalysisPositionSupport.buffer(window, spotInitial, NaN.NaN)
                : returnFactors.copy();
        this.spotFirstRetainedSlotSeeded = !futures && materialized.firstRetainedSlotSeeded();
        this.spotDefinesHeadPeriod = spotFirstRetainedSlotSeeded;
        this.futuresCapital = record.getInitialCapital() == null ? fallbackCapital : record.getInitialCapital();
        this.futuresMark = markPriceIndicator;
        this.markFuturesExposure = FuturesPerformanceSupport.includesExposure(handling, equityCurveMode);
        this.preWindowFuturesActivity = futures
                && FuturesPerformanceSupport.hasPreWindowActivity(record, window.beginIndex(), markFuturesExposure);
        this.firstBarFuturesActivity = futures
                && FuturesPerformanceSupport.hasActivityAtIndex(record, window.beginIndex());
        this.firstRetainedSlotSeeded = materialized.firstRetainedSlotSeeded();
        this.seededFirstBarReturn = futures && firstRetainedSlotSeeded
                && FuturesPerformanceSupport.hasPreWindowActivity(record, window.beginIndex(),
                        FuturesPerformanceSupport.includesExposure(handling, equityCurveMode));
        this.rawValues = new ArrayList<>(returnFactors.size());
        this.values = new ArrayList<>(returnFactors.size());
        buildReturns();
    }

    /**
     * One materialization attempt's factors; the seeding flag travels with them so
     * a discarded attempt cannot leave it set.
     */
    private record Materialized(AnalysisPositionSupport.Window window, OffsetNumBuffer factors,
            FuturesPerformanceSupport.PnLAccumulator pnl, boolean firstRetainedSlotSeeded) {
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
        this(barSeries, FuturesPerformanceSupport.analysisRecord(position), 0, representation, equityCurveMode,
                OpenPositionHandling.MARK_TO_MARKET, true, new ClosePriceIndicator(barSeries),
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
        this(barSeries, tradingRecord, 0, representation, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET, true);
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
        this(barSeries, tradingRecord, 0, representation, EquityCurveMode.MARK_TO_MARKET, openPositionHandling, true);
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
        this(barSeries, tradingRecord, 0, representation, equityCurveMode, openPositionHandling, true);
    }

    /**
     * @return the return rates (formatted according to the configured
     *         representation)
     */
    public List<Num> getValues() {
        return List.copyOf(values);
    }

    /**
     * @param index the bar index
     * @return the return rate value at the index-th position (formatted according
     *         to the configured representation), or {@link NaN#NaN} outside the
     *         materialized range captured by this instance
     */
    @Override
    public Num getValue(int index) {
        long position = (long) index - window.beginIndex();
        if (position < 0 || position >= values.size()) {
            return NaN.NaN;
        }
        return values.get((int) position);
    }

    /**
     * @return formatted values over the captured materialized window, independent
     *         of later changes to the borrowed series bounds
     * @since 0.25.1
     */
    @Override
    public Stream<Num> stream() {
        return values.stream();
    }

    /**
     * @return the raw return rates (before formatting)
     */
    public List<Num> getRawValues() {
        return List.copyOf(rawValues);
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
     * @return the number of materialized returns. The leading no-prior-close
     *         placeholder is only excluded when the first slot carries no real
     *         return.
     */
    public int getSize() {
        if (returnFactors.size() == 0) {
            return 0;
        }
        return returnFactors.size() - (firstRetainedSlotSeeded ? 0 : 1);
    }

    /**
     * Calculates the returns for a single position.
     *
     * <p>
     * Accepted spot period factors remain composed with the native account's return
     * factors across later updates, including when an ordinary record supplied the
     * constructor. A retained-head period established by a spot return keeps that
     * meaning when later native activity arrives. Each update composes the retained
     * spot factors with normalized native periods once; rounding a previous
     * combined view does not become input to a later update. With no native
     * contribution, the established spot-only composition is preserved.
     * </p>
     *
     * @param position   a single position
     * @param finalIndex the index up to which the returns of open positions are
     *                   considered
     * @throws IllegalStateException if a bar this curve was materialized from was
     *                               evicted, replaced or updated since
     * @since 0.22.2
     */
    @Override
    public void calculatePosition(Position position, int finalIndex) {
        AnalysisPositionSupport.PricedPosition priced = AnalysisPositionSupport.pricePosition(this, barSeries, position,
                finalIndex, window, FuturesPerformanceSupport.isFutures(position));
        if (priced == null) {
            return;
        }
        if (FuturesPerformanceSupport.isFutures(position)) {
            Num capital = FuturesPerformanceSupport.accountCapital(barSeries.numFactory(),
                    FuturesPerformanceSupport.analysisRecord(position),
                    futuresCapital == null ? FuturesPerformanceSupport.fallbackCapital(position) : futuresCapital);
            boolean preWindow = preWindowFuturesActivity || FuturesPerformanceSupport.hasPreWindowActivity(position,
                    window.beginIndex(), markFuturesExposure);
            boolean firstActivity = firstBarFuturesActivity
                    || FuturesPerformanceSupport.hasActivityAtIndex(position, window.beginIndex());
            FuturesPerformanceSupport.PnLAccumulator pnl = futuresPnL.copy();
            boolean[] firstReported = new boolean[1];
            AnalysisPositionSupport.updateCapturedCurve(barSeries, window, priced, returnFactors, staged -> {
                FuturesPerformanceSupport.addPositionPnL(barSeries, position, finalIndex, window, markFuturesExposure,
                        futuresMark, pnl, capital);
                firstReported[0] = publishReturnFactors(pnl, capital, firstActivity, spotDefinesHeadPeriod,
                        spotReturnFactors, window, staged);
            }, true);
            futuresPnL = pnl;
            firstRetainedSlotSeeded = firstReported[0] || spotFirstRetainedSlotSeeded;
            seededFirstBarReturn = firstReported[0] && preWindow && !spotDefinesHeadPeriod;
            preWindowFuturesActivity = preWindow;
            firstBarFuturesActivity = firstActivity;
            rawValues.clear();
            values.clear();
            buildReturns();
            return;
        }
        boolean[] seeded = new boolean[1];
        OffsetNumBuffer spotFactors = spotReturnFactors.copy();
        AnalysisPositionSupport.updateCapturedCurve(barSeries, window, priced, returnFactors, staged -> {
            Num initial = representation == ReturnRepresentation.LOG ? barSeries.numFactory().zero()
                    : barSeries.numFactory().one();
            OffsetNumBuffer contribution = AnalysisPositionSupport.buffer(window, initial, NaN.NaN);
            seeded[0] = calculatePosition(position, finalIndex, window, contribution, priced.holdingCost());
            for (long index = window.beginIndex(); index <= window.bufferEndIndex(); index++) {
                Num factor = contribution.get((int) index);
                if (representation == ReturnRepresentation.LOG) {
                    spotFactors.add((int) index, factor);
                } else {
                    spotFactors.multiply((int) index, factor);
                }
            }
            // Spot changes do not alter the already-validated native components.
            // Recompose both retained representations instead of chaining the
            // rounded combined view into the next spot publication.
            publishReturnFactors(futuresPnL, null, firstBarFuturesActivity,
                    spotDefinesHeadPeriod || seeded[0] && !firstRetainedSlotSeeded, spotFactors, window, staged);
        });
        spotReturnFactors = spotFactors;
        if (seeded[0] && !firstRetainedSlotSeeded)
            spotDefinesHeadPeriod = true;
        spotFirstRetainedSlotSeeded |= seeded[0];
        // Reached only once the staged factors were verified and published.
        firstRetainedSlotSeeded |= seeded[0];
        rawValues.clear();
        values.clear();
        buildReturns();
    }

    /**
     * Combines a position's returns into {@code factors}.
     *
     * @return whether a return was written into the first retained slot
     */
    private boolean calculatePosition(Position position, int finalIndex, AnalysisPositionSupport.Window captured,
            OffsetNumBuffer factors, Num holdingCost) {
        Trade entry = position.getEntry();
        if (entry == null) {
            return false;
        }
        // Priced only through the last captured bar, even when a later final index
        // is requested of a bounded curve.
        int lastCapturedIndex = captured.bufferEndIndex();
        int entryIndex = entry.getIndex();
        if (entryIndex > finalIndex || entryIndex > lastCapturedIndex) {
            return false;
        }
        int endIndex = determineEndIndex(position, finalIndex, lastCapturedIndex);
        int seriesBegin = captured.beginIndex();
        if (endIndex < seriesBegin) {
            return false;
        }

        boolean isLongTrade = entry.isBuy();
        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            boolean[] seeded = new boolean[1];
            AnalysisPositionSupport.ExitMark exit = AnalysisPositionSupport.markToMarket(this, barSeries, position,
                    holdingCost, endIndex, seriesBegin, endIndex - 1,
                    (index, netPrice, previousPrice) -> seeded[0] |= combineReturnAtIndex(index,
                            strategyReturn(calculateReturn(netPrice, previousPrice), isLongTrade), captured, factors));
            return combineReturnAtIndex(endIndex,
                    strategyReturn(calculateReturn(exit.netPrice(), exit.previousPrice()), isLongTrade), captured,
                    factors) | seeded[0];
        }

        Trade exit = position.getExit();
        if (exit != null && endIndex >= exit.getIndex()) {
            Num netExit = addCost(exit.getNetPrice(), holdingCost, isLongTrade);
            return combineReturnAtIndex(exit.getIndex(),
                    strategyReturn(calculateReturn(netExit, entry.getNetPrice()), isLongTrade), captured, factors);
        }
        return false;
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

    /** Signs a position's price return: short positions gain when prices fall. */
    private Num strategyReturn(Num rawReturn, boolean isLongTrade) {
        return isLongTrade ? rawReturn : rawReturn.multipliedBy(barSeries.numFactory().minusOne());
    }

    /**
     * Combines one return into {@code factors}.
     *
     * @return whether the return was written into the first retained slot, which
     *         makes that slot a real return: a position that exits on the first
     *         retained bar (entered there or, valued at that bar's close, before
     *         the window)
     */
    private boolean combineReturnAtIndex(int index, Num strategyReturn, AnalysisPositionSupport.Window captured,
            OffsetNumBuffer factors) {
        if (!factors.contains(index)) {
            return false;
        }
        if (representation == ReturnRepresentation.LOG) {
            factors.add(index, strategyReturn);
        } else {
            factors.multiply(index, toFactor(strategyReturn));
        }
        return index == captured.beginIndex();
    }

    private void buildReturns() {
        Num one = barSeries.numFactory().one();
        for (int i = 0; i < returnFactors.size(); i++) {
            if (i == 0 && !firstRetainedSlotSeeded) {
                // No prior in-window close exists for the first retained bar;
                // positions held into the window are valued from its close.
                rawValues.add(NaN.NaN);
                values.add(NaN.NaN);
            } else if (representation == ReturnRepresentation.LOG) {
                Num logReturn = returnFactors.at(i);
                rawValues.add(logReturn);
                values.add(logReturn);
            } else {
                Num rawReturn = returnFactors.at(i).minus(one);
                rawValues.add(rawReturn);
                values.add(representation.toRepresentationFromRateOfReturn(rawReturn));
            }
        }
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
        this(barSeries, tradingRecord, finalIndex, representation, equityCurveMode, openPositionHandling, false,
                markPriceIndicator, null);
    }

    /**
     * Publishes one composition of normalized native periods and retained spot
     * factors. A null capital reuses native components already validated before a
     * spot-only update; native construction and mutations validate with their
     * account capital before publication. A zero fallback capital belongs to a
     * position with no executed exposure: its zero contribution leaves the accepted
     * normalized components available for the same publication.
     */
    private boolean publishReturnFactors(FuturesPerformanceSupport.PnLAccumulator pnl, Num capital,
            boolean firstActivity, boolean spotHeadPeriod, OffsetNumBuffer spotFactors,
            AnalysisPositionSupport.Window captured, OffsetNumBuffer target) {
        if (captured.isEmpty())
            return false;
        Num initial = representation == ReturnRepresentation.LOG ? barSeries.numFactory().zero()
                : barSeries.numFactory().one();
        OffsetNumBuffer factors = AnalysisPositionSupport.buffer(captured, initial, NaN.NaN);
        boolean firstReported = captured.beginIndex() > 0 && (firstActivity || spotHeadPeriod);
        // A spot return that first defined this slot remains a period sample;
        // later native activity must not replace it with a historical capital seed.
        int previousIndex = captured.beginIndex() - 1;
        for (long index = captured.beginIndex(); index <= captured.bufferEndIndex(); index++) {
            if (capital != null && !capital.isZero())
                pnl.validatePnL((int) index, capital);
            if (index > captured.beginIndex() || firstReported) {
                Num ratio = pnl.equityRatio((int) index, previousIndex,
                        index == captured.beginIndex() && spotHeadPeriod);
                if (representation == ReturnRepresentation.LOG) {
                    factors.add((int) index, ratio.isPositive() ? ratio.log() : NaN.NaN);
                } else {
                    factors.multiply((int) index, ratio);
                }
            }
            // At absolute index zero preserve the established capital-based next sample;
            // a retained head without activity is instead the first prior equity value.
            if (index > captured.beginIndex() || captured.beginIndex() > 0)
                previousIndex = (int) index;
            if (representation == ReturnRepresentation.LOG)
                factors.add((int) index, spotFactors.get((int) index));
            else
                factors.multiply((int) index, spotFactors.get((int) index));
        }
        target.replaceWith(factors);
        return firstReported;
    }

    /**
     * @return whether the captured first slot carries a return rather than a
     *         placeholder
     * @since 0.25.1
     */
    public boolean hasFirstBarReturn() {
        return firstRetainedSlotSeeded;
    }

    /**
     * Returns whether the value reported at the first stored bar is the cumulative
     * equity accumulated since the account capital instead of a period return. Risk
     * criteria omit such a seed, because it repeats results realized before the
     * retained head of the series.
     *
     * @return {@code true} when the first reported value is a cumulative seed
     * @since 0.25.1
     */
    public boolean hasSeededFirstBarReturn() {
        return seededFirstBarReturn;
    }
}
