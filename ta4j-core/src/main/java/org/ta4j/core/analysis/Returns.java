/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.*;
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

    /**
     * The bar series.
     */
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
     * @param openPositionHandling how to handle the last open position
     * @since 0.22.2
     */
    public Returns(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex,
            ReturnRepresentation representation, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this.barSeries = Objects.requireNonNull(barSeries);
        this.representation = Objects.requireNonNull(representation);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        // at index 0, there is no return
        var aNan = Collections.singletonList(NaN.NaN);
        rawValues = new ArrayList<>(aNan);
        values = new ArrayList<>(aNan);
        calculate(Objects.requireNonNull(tradingRecord), finalIndex, Objects.requireNonNull(openPositionHandling));
        fillToTheEnd(barSeries.getEndIndex());
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
     * @param openPositionHandling how to handle the last open position
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
     * @param openPositionHandling how to handle the last open position
     * @since 0.22.2
     */
    public Returns(BarSeries barSeries, TradingRecord tradingRecord, ReturnRepresentation representation,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), representation, equityCurveMode,
                openPositionHandling);
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
        boolean isLongTrade = entry.isBuy();
        Num minusOne = barSeries.numFactory().minusOne();
        int endIndex = determineEndIndex(position, finalIndex, barSeries.getEndIndex());
        final int entryIndex = entry.getIndex();
        int begin = entryIndex + 1;
        if (begin > rawValues.size()) {
            Num zero = barSeries.numFactory().zero();
            padToSize(rawValues, begin, zero);
            padToSize(values, begin, zero);
        }

        int startingIndex = Math.max(begin, 1);
        int nPeriods = endIndex - entryIndex;
        Num holdingCost = position.getHoldingCost(endIndex);
        if (nPeriods == 0) {
            return;
        }
        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            Num avgCost = averageHoldingCostPerPeriod(position, endIndex, getBarSeries().numFactory());

            // returns are per period (iterative). Base price needs to be updated
            // accordingly
            Num lastPrice = entry.getNetPrice();
            for (int i = startingIndex; i < endIndex; i++) {
                var bar = barSeries.getBar(i);
                Num intermediateNetPrice = addCost(bar.getClosePrice(), avgCost, isLongTrade);
                Num rawReturn = calculateReturn(intermediateNetPrice, lastPrice);
                Num strategyReturn;
                if (entry.isBuy()) {
                    strategyReturn = rawReturn;
                } else {
                    strategyReturn = rawReturn.multipliedBy(minusOne);
                }
                rawValues.add(strategyReturn);
                // Format the return according to the configured representation
                addValue(strategyReturn);
                // update base price
                lastPrice = bar.getClosePrice();
            }

            var exitPrice = resolveExitPrice(position, endIndex, barSeries);
            Num rawReturn = calculateReturn(addCost(exitPrice, avgCost, isLongTrade), lastPrice);
            Num strategyReturn;
            if (entry.isBuy()) {
                strategyReturn = rawReturn;
            } else {
                strategyReturn = rawReturn.multipliedBy(minusOne);
            }
            rawValues.add(strategyReturn);
            addValue(strategyReturn);
        } else {
            Num zero = barSeries.numFactory().zero();
            for (int i = startingIndex; i < endIndex; i++) {
                rawValues.add(zero);
                addValue(zero);
            }
            var exit = position.getExit();
            if (exit != null && endIndex >= exit.getIndex()) {
                Num entryPrice = entry.getNetPrice();
                Num exitPrice = exit.getNetPrice();
                Num netExit = addCost(exitPrice, holdingCost, isLongTrade);
                Num rawReturn = calculateReturn(netExit, entryPrice);
                Num strategyReturn = entry.isBuy() ? rawReturn : rawReturn.multipliedBy(minusOne);
                rawValues.add(strategyReturn);
                addValue(strategyReturn);
            } else {
                rawValues.add(zero);
                addValue(zero);
            }
        }
    }

    /**
     * @return the return rates (formatted according to the configured
     *         representation)
     */
    public List<Num> getValues() {
        // Values are already formatted during calculate()
        return values;
    }

    /**
     * @param index the bar index
     * @return the return rate value at the index-th position (formatted according
     *         to the configured representation)
     */
    @Override
    public Num getValue(int index) {
        // Values are already formatted during calculate()
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

    private void addValue(Num strategyReturn) {
        // Format the return according to the configured representation
        if (representation == ReturnRepresentation.LOG) {
            // Log returns are returned as-is (no conversion needed)
            values.add(strategyReturn);
        } else {
            // Raw return is already in DECIMAL format (arithmetic return)
            values.add(representation.toRepresentationFromRateOfReturn(strategyReturn));
        }
    }

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
        } else {
            // r_i = P_i/P_(i-1) - 1 (arithmetic return, which is DECIMAL format)
            var one = barSeries.numFactory().one();
            return xNew.dividedBy(xOld).minus(one);
        }
    }

    /**
     * Pads {@link #rawValues} and {@link #values} with zeros up until
     * {@code endIndex}.
     *
     * @param endIndex the end index
     */
    private void fillToTheEnd(int endIndex) {
        if (endIndex >= rawValues.size()) {
            var zero = barSeries.numFactory().zero();
            padToEndIndex(rawValues, endIndex, zero);
            padToEndIndex(values, endIndex, zero);
        }
    }

}
