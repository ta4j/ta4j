/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
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
        var seriesEnd = barSeries.getEndIndex();
        var size = Math.max(seriesEnd + 1, 0);
        var one = barSeries.numFactory().one();
        var zero = barSeries.numFactory().zero();
        var initial = representation == ReturnRepresentation.LOG ? zero : one;
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
        var entry = position.getEntry();
        if (entry == null) {
            return;
        }
        var entryIndex = entry.getIndex();
        var seriesEnd = barSeries.getEndIndex();
        if (entryIndex > finalIndex || entryIndex > seriesEnd) {
            return;
        }
        var endIndex = determineEndIndex(position, finalIndex, seriesEnd);
        var seriesBegin = barSeries.getBeginIndex();
        if (endIndex < seriesBegin) {
            return;
        }

        var numFactory = barSeries.numFactory();
        var minusOne = numFactory.minusOne();
        var isLongTrade = entry.isBuy();
        var start = Math.max(entryIndex + 1, seriesBegin + 1);

        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            var avgCost = averageHoldingCostPerPeriod(position, endIndex, numFactory);
            var lastPrice = entry.getNetPrice();
            for (var i = start; i < endIndex; i++) {
                var bar = barSeries.getBar(i);
                var intermediateNetPrice = addCost(bar.getClosePrice(), avgCost, isLongTrade);
                var rawReturn = calculateReturn(intermediateNetPrice, lastPrice);
                var strategyReturn = isLongTrade ? rawReturn : rawReturn.multipliedBy(minusOne);
                combineReturnAtIndex(i, strategyReturn);
                lastPrice = bar.getClosePrice();
            }
            var exitPrice = resolveExitPrice(position, endIndex, barSeries);
            var rawReturn = calculateReturn(addCost(exitPrice, avgCost, isLongTrade), lastPrice);
            var strategyReturn = isLongTrade ? rawReturn : rawReturn.multipliedBy(minusOne);
            combineReturnAtIndex(endIndex, strategyReturn);
            return;
        }

        var exit = position.getExit();
        if (exit != null && endIndex >= exit.getIndex()) {
            var holdingCost = position.getHoldingCost(endIndex);
            var netExit = addCost(exit.getNetPrice(), holdingCost, isLongTrade);
            var rawReturn = calculateReturn(netExit, entry.getNetPrice());
            var strategyReturn = isLongTrade ? rawReturn : rawReturn.multipliedBy(minusOne);
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
        var one = barSeries.numFactory().one();
        return xNew.dividedBy(xOld).minus(one);
    }

    private Num toFactor(Num strategyReturn) {
        var one = barSeries.numFactory().one();
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
        var one = barSeries.numFactory().one();
        for (var i = 1; i < rawValues.size(); i++) {
            if (representation == ReturnRepresentation.LOG) {
                var logReturn = returnFactors.get(i);
                rawValues.set(i, logReturn);
                values.set(i, logReturn);
            } else {
                var factor = returnFactors.get(i);
                var rawReturn = factor.minus(one);
                rawValues.set(i, rawReturn);
                values.set(i, representation.toRepresentationFromRateOfReturn(rawReturn));
            }
        }
    }
}
