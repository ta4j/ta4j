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
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.ReturnRepresentationPolicy;
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
        this.barSeries = Objects.requireNonNull(barSeries);
        this.representation = Objects.requireNonNull(representation);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        int seriesEnd = barSeries.getEndIndex();
        int size = Math.max(seriesEnd + 1, 0);
        Num one = barSeries.numFactory().one();
        Num zero = barSeries.numFactory().zero();
        Num initial = representation == ReturnRepresentation.LOG ? zero : one;
        returnFactors = new ArrayList<>(Collections.nCopies(size, initial));
        rawValues = new ArrayList<>(Collections.nCopies(size, zero));
        values = new ArrayList<>(Collections.nCopies(size, zero));
        calculate(Objects.requireNonNull(tradingRecord), finalIndex, Objects.requireNonNull(openPositionHandling));
        buildReturns();
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
        return values;
    }

    /**
     * @param index the bar index
     * @return the return rate value at the index-th position (formatted according
     *         to the configured representation)
     */
    @Override
    public Num getValue(int index) {
        return values.get(index);
    }

    /**
     * @return the raw return rates (before formatting)
     */
    public List<Num> getRawValues() {
        return rawValues;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    @Override
    public BarSeries getBarSeries() {
        return barSeries;
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
        if (index < 0 || index >= returnFactors.size()) {
            return;
        }
        if (representation == ReturnRepresentation.LOG) {
            returnFactors.set(index, returnFactors.get(index).plus(strategyReturn));
        } else {
            returnFactors.set(index, returnFactors.get(index).multipliedBy(toFactor(strategyReturn)));
        }
    }

    private void buildReturns() {
        if (rawValues.isEmpty()) {
            return;
        }
        rawValues.set(0, NaN.NaN);
        values.set(0, NaN.NaN);
        Num one = barSeries.numFactory().one();
        for (int i = 1; i < rawValues.size(); i++) {
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
}
