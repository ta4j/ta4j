/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

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
 * begin index through the materialized end, which may extend beyond
 * {@code barSeries.getEndIndex()} when a trailing exit remains addressable in
 * raw storage. {@link #getValue(int)} returns {@link NaN#NaN} outside that
 * materialized range.
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

    /**
     * True when a position entered before the retained window marked the first
     * retained slot against its entry price, giving that slot a defined return even
     * though no prior in-window close exists.
     */
    private boolean firstRetainedSlotSeeded;

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
        this.barSeries = Objects.requireNonNull(barSeries, "barSeries");
        this.representation = Objects.requireNonNull(representation);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        TradingRecord record = Objects.requireNonNull(tradingRecord);
        OpenPositionHandling handling = Objects.requireNonNull(openPositionHandling);
        Materialized materialized = barSeries.withReadLock(() -> {
            AnalysisPositionSupport.Window captured = AnalysisPositionSupport.captureWindow(this.barSeries, record, 0,
                    finalIndex, useRecordEnd, false, true);
            Num initial = this.representation == ReturnRepresentation.LOG ? this.barSeries.numFactory().zero()
                    : this.barSeries.numFactory().one();
            OffsetNumBuffer factors = AnalysisPositionSupport.buffer(captured, initial, NaN.NaN);
            for (Position position : AnalysisPositionSupport.positionsForAnalysis(record, captured.finalIndex(),
                    handling, this.equityCurveMode)) {
                calculatePosition(position, captured.finalIndex(), captured, factors);
            }
            return new Materialized(captured, factors);
        });
        this.window = materialized.window();
        this.returnFactors = materialized.factors();
        this.rawValues = new ArrayList<>(returnFactors.size());
        this.values = new ArrayList<>(returnFactors.size());
        buildReturns();
    }

    private record Materialized(AnalysisPositionSupport.Window window, OffsetNumBuffer factors) {
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
        this(barSeries, new BaseTradingRecord(position), representation, equityCurveMode);
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
     * Returns the first absolute index of the captured return window, independent
     * of later changes to the borrowed series.
     *
     * @return the captured begin index
     * @since 0.25.1
     */
    public int getBeginIndex() {
        return window.beginIndex();
    }

    /**
     * Returns the last absolute index of the analysis window: the trading record's
     * logical end (or explicit final index), extended to a trailing exit beyond the
     * logical series end. Later materialized slots up to the series end carry no
     * position activity and are excluded here. An empty window has an end index
     * below {@link #getBeginIndex()}.
     *
     * @return the captured analysis end index
     * @since 0.25.1
     */
    public int getEndIndex() {
        return window.endIndex();
    }

    /**
     * @return the number of materialized returns, including any trailing exit
     *         return beyond the logical window end. The leading no-prior-close
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
     * @param position   a single position
     * @param finalIndex the index up to which the returns of open positions are
     *                   considered
     * @since 0.22.2
     */
    @Override
    public void calculatePosition(Position position, int finalIndex) {
        calculatePosition(position, finalIndex, window, returnFactors);
    }

    private void calculatePosition(Position position, int finalIndex, AnalysisPositionSupport.Window captured,
            OffsetNumBuffer factors) {
        Trade entry = position.getEntry();
        if (entry == null) {
            return;
        }
        int entryIndex = entry.getIndex();
        int addressableEndIndex = captured.addressableEndIndex();
        if (entryIndex > finalIndex || entryIndex > addressableEndIndex) {
            return;
        }
        int endIndex = determineEndIndex(position, finalIndex, addressableEndIndex);
        int seriesBegin = captured.beginIndex();
        if (endIndex < seriesBegin) {
            return;
        }

        boolean isLongTrade = entry.isBuy();
        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            AnalysisPositionSupport.ExitMark exit = AnalysisPositionSupport.markToMarket(this, barSeries, position,
                    endIndex, seriesBegin, endIndex - 1, (index, netPrice, previousPrice) -> combineReturnAtIndex(index,
                            strategyReturn(calculateReturn(netPrice, previousPrice), isLongTrade), captured, factors));
            combineReturnAtIndex(endIndex,
                    strategyReturn(calculateReturn(exit.netPrice(), exit.previousPrice()), isLongTrade), captured,
                    factors);
            return;
        }

        Trade exit = position.getExit();
        if (exit != null && endIndex >= exit.getIndex()) {
            Num holdingCost = position.getHoldingCost(endIndex);
            Num netExit = addCost(exit.getNetPrice(), holdingCost, isLongTrade);
            combineReturnAtIndex(exit.getIndex(),
                    strategyReturn(calculateReturn(netExit, entry.getNetPrice()), isLongTrade), captured, factors);
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

    /** Signs a position's price return: short positions gain when prices fall. */
    private Num strategyReturn(Num rawReturn, boolean isLongTrade) {
        return isLongTrade ? rawReturn : rawReturn.multipliedBy(barSeries.numFactory().minusOne());
    }

    private void combineReturnAtIndex(int index, Num strategyReturn, AnalysisPositionSupport.Window captured,
            OffsetNumBuffer factors) {
        if (!factors.contains(index)) {
            return;
        }
        if (index == captured.beginIndex()) {
            // Any write into the first retained slot makes it a real return:
            // either an entry predating the window seeded it, or a position
            // entered and exited on the first retained bar itself.
            firstRetainedSlotSeeded = true;
        }
        if (representation == ReturnRepresentation.LOG) {
            factors.add(index, strategyReturn);
        } else {
            factors.multiply(index, toFactor(strategyReturn));
        }
    }

    private void buildReturns() {
        Num one = barSeries.numFactory().one();
        for (int i = 0; i < returnFactors.size(); i++) {
            if (i == 0 && !firstRetainedSlotSeeded) {
                // No prior in-window close exists for the first retained bar
                // unless an entry predating the window seeded its return.
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

}
