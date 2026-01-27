/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
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
public class Returns implements Indicator<Num> {

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
     *
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
     *
     * @since 0.22.2
     */
    public Returns(BarSeries barSeries, Position position, ReturnRepresentation representation,
            EquityCurveMode equityCurveMode) {
        this.barSeries = Objects.requireNonNull(barSeries);
        this.representation = Objects.requireNonNull(representation);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        // at index 0, there is no return
        var aNan = Collections.singletonList(NaN.NaN);
        rawValues = new ArrayList<>(aNan);
        values = new ArrayList<>(aNan);
        calculate(Objects.requireNonNull(position), barSeries.getEndIndex());
        fillToTheEnd(barSeries.getEndIndex());
    }

    /**
     * Constructor.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param representation  the return representation (determines both calculation
     *                        method and output format)
     * @param equityCurveMode the calculation mode
     *
     * @since 0.22.2
     */
    public Returns(BarSeries barSeries, TradingRecord tradingRecord, ReturnRepresentation representation,
            EquityCurveMode equityCurveMode) {
        this.barSeries = Objects.requireNonNull(barSeries);
        this.representation = Objects.requireNonNull(representation);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        // at index 0, there is no return
        var aNan = Collections.singletonList(NaN.NaN);
        rawValues = new ArrayList<>(aNan);
        values = new ArrayList<>(aNan);
        calculate(Objects.requireNonNull(tradingRecord));
        fillToTheEnd(tradingRecord.getEndIndex(barSeries));
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
     *
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

    /**
     * Calculates the cash flow for a single position (including accrued cashflow
     * for open positions).
     *
     * @param position   a single position
     * @param finalIndex the index up to which the cash flow of open positions is
     *                   considered
     */
    public void calculate(Position position, int finalIndex) {
        boolean isLongTrade = position.getEntry().isBuy();
        Num minusOne = barSeries.numFactory().numOf(-1);
        int endIndex = AnalysisUtils.determineEndIndex(position, finalIndex, barSeries.getEndIndex());
        final int entryIndex = position.getEntry().getIndex();
        int begin = entryIndex + 1;
        if (begin > rawValues.size()) {
            int paddingSize = begin - rawValues.size();
            Num zero = barSeries.numFactory().zero();
            rawValues.addAll(Collections.nCopies(paddingSize, zero));
            values.addAll(Collections.nCopies(paddingSize, zero));
        }

        int startingIndex = Math.max(begin, 1);
        int nPeriods = endIndex - entryIndex;
        Num holdingCost = position.getHoldingCost(endIndex);

        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            Num avgCost = holdingCost.dividedBy(getBarSeries().numFactory().numOf(nPeriods));

            // returns are per period (iterative). Base price needs to be updated
            // accordingly
            Num lastPrice = position.getEntry().getNetPrice();
            for (int i = startingIndex; i < endIndex; i++) {
                Num intermediateNetPrice = AnalysisUtils.addCost(barSeries.getBar(i).getClosePrice(), avgCost,
                        isLongTrade);
                Num rawReturn = calculateReturn(intermediateNetPrice, lastPrice);

                Num strategyReturn;
                if (position.getEntry().isBuy()) {
                    strategyReturn = rawReturn;
                } else {
                    strategyReturn = rawReturn.multipliedBy(minusOne);
                }
                rawValues.add(strategyReturn);
                // Format the return according to the configured representation
                addValue(strategyReturn);
                // update base price
                lastPrice = barSeries.getBar(i).getClosePrice();
            }

            // add net return at exit position
            Num exitPrice;
            if (position.getExit() != null) {
                exitPrice = position.getExit().getNetPrice();
            } else {
                exitPrice = barSeries.getBar(endIndex).getClosePrice();
            }

            Num rawReturn = calculateReturn(AnalysisUtils.addCost(exitPrice, avgCost, isLongTrade), lastPrice);
            Num strategyReturn;
            if (position.getEntry().isBuy()) {
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
            if (position.getExit() != null && endIndex >= position.getExit().getIndex()) {
                Num entryPrice = position.getEntry().getNetPrice();
                Num exitPrice = position.getExit().getNetPrice();
                Num netExit = AnalysisUtils.addCost(exitPrice, holdingCost, isLongTrade);
                Num rawReturn = calculateReturn(netExit, entryPrice);
                Num strategyReturn = position.getEntry().isBuy() ? rawReturn : rawReturn.multipliedBy(minusOne);
                rawValues.add(strategyReturn);
                addValue(strategyReturn);
            } else {
                rawValues.add(zero);
                addValue(zero);
            }
        }
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

    /**
     * Calculates the returns for a trading record.
     *
     * @param tradingRecord the trading record
     */
    private void calculate(TradingRecord tradingRecord) {
        int endIndex = tradingRecord.getEndIndex(getBarSeries());
        // For each position...
        tradingRecord.getPositions().forEach(p -> calculate(p, endIndex));
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
            var paddingSize = barSeries.getEndIndex() - rawValues.size() + 1;
            var zero = barSeries.numFactory().zero();
            var c = Collections.nCopies(paddingSize, zero);
            rawValues.addAll(c);
            values.addAll(c);
        }
    }
}
