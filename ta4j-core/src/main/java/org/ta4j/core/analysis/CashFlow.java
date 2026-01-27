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
import org.ta4j.core.num.Num;

/**
 * Allows to follow the money cash flow involved by a list of positions over a
 * bar series, either marked to market or using realized values only.
 */
public class CashFlow implements Indicator<Num> {

    /** The bar series. */
    private final BarSeries barSeries;

    /** The (accrued) cash flow sequence (without trading costs). */
    private final List<Num> values;

    /** The equity curve calculation mode. */
    private final EquityCurveMode equityCurveMode;

    /**
     * Constructor for cash flows of a closed position.
     *
     * @param barSeries       the bar series
     * @param position        a single position
     * @param equityCurveMode the calculation mode
     *
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, Position position, EquityCurveMode equityCurveMode) {
        this.barSeries = Objects.requireNonNull(barSeries);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        var aOne = Collections.singletonList(barSeries.numFactory().one());
        values = new ArrayList<>(aOne);

        calculate(Objects.requireNonNull(position));
        fillToTheEnd(barSeries.getEndIndex());
    }

    /**
     * Constructor.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param finalIndex      index up until cash flows of open positions are
     *                        considered
     * @param equityCurveMode the calculation mode
     *
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex, EquityCurveMode equityCurveMode) {
        this.barSeries = Objects.requireNonNull(barSeries);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        var aOne = Collections.singletonList(barSeries.numFactory().one());
        values = new ArrayList<>(aOne);

        calculate(Objects.requireNonNull(tradingRecord), finalIndex);
        fillToTheEnd(finalIndex);
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
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), EquityCurveMode.MARK_TO_MARKET);
    }

    /**
     * Constructor.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param equityCurveMode the calculation mode
     *
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), equityCurveMode);
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
        this(barSeries, tradingRecord, finalIndex, EquityCurveMode.MARK_TO_MARKET);
    }

    /**
     * @param index the bar index
     * @return the cash flow value at the index-th position
     */
    @Override
    public Num getValue(int index) {
        return values.get(index);
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
     * @return the size of the bar series
     */
    public int getSize() {
        return barSeries.getBarCount();
    }

    /**
     * Calculates the cash flow for a single closed position.
     *
     * @param position a single position
     */
    private void calculate(Position position) {
        if (position.isOpened()) {
            throw new IllegalArgumentException(
                    "Position is not closed. Final index of observation needs to be provided.");
        }
        calculate(position, position.getExit().getIndex());
    }

    /**
     * Calculates the cash flow for a single position (including accrued cashflow
     * for open positions).
     *
     * @param position   a single position
     * @param finalIndex index up until cash flow of open positions is considered
     */
    private void calculate(Position position, int finalIndex) {
        var numFactory = barSeries.numFactory();
        var isLongTrade = position.getEntry().isBuy();
        var endIndex = AnalysisUtils.determineEndIndex(position, finalIndex, barSeries.getEndIndex());
        var entryIndex = position.getEntry().getIndex();
        var begin = entryIndex + 1;

        if (begin > values.size()) {
            var lastValue = values.getLast();
            values.addAll(Collections.nCopies(begin - values.size(), lastValue));
        }

        var zero = numFactory.zero();
        var entryEquity = values.get(entryIndex);
        if (!entryEquity.isGreaterThan(zero)) {
            return;
        }

        var startingIndex = Math.max(begin, 1);
        var holdingCost = position.getHoldingCost(endIndex);
        var nPeriods = endIndex - entryIndex;
        var effectivePeriods = Math.max(1, nPeriods);
        var netEntryPrice = position.getEntry().getNetPrice();

        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            var avgCost = holdingCost.dividedBy(numFactory.numOf(effectivePeriods));
            for (var i = startingIndex; i < endIndex; i++) {
                var intermediateNetPrice = AnalysisUtils.addCost(barSeries.getBar(i).getClosePrice(), avgCost,
                        isLongTrade);
                var ratio = getIntermediateRatio(isLongTrade, netEntryPrice, intermediateNetPrice);
                values.add(values.get(entryIndex).multipliedBy(ratio));
            }
            var exitPrice = position.getExit() != null ? position.getExit().getNetPrice()
                    : barSeries.getBar(endIndex).getClosePrice();
            var netExitPrice = AnalysisUtils.addCost(exitPrice, avgCost, isLongTrade);
            var ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
            values.add(values.get(entryIndex).multipliedBy(ratio));
        } else if (position.getExit() != null && endIndex >= position.getExit().getIndex()) {
            var entryValue = values.get(entryIndex);
            for (var i = startingIndex; i < endIndex; i++) {
                values.add(entryValue);
            }
            var netExitPrice = AnalysisUtils.addCost(position.getExit().getNetPrice(), holdingCost, isLongTrade);
            var ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
            values.add(entryValue.multipliedBy(ratio));
        }
    }

    /**
     * Calculates the ratio of intermediate prices.
     *
     * @param isLongTrade true, if the entry trade type is BUY
     * @param entryPrice  price ratio denominator
     * @param exitPrice   price ratio numerator
     */
    private static Num getIntermediateRatio(boolean isLongTrade, Num entryPrice, Num exitPrice) {
        Num ratio;
        if (isLongTrade) {
            ratio = exitPrice.dividedBy(entryPrice);
        } else {
            ratio = entryPrice.getNumFactory().numOf(2).minus(exitPrice.dividedBy(entryPrice));
        }

        return ratio;
    }

    /**
     * Calculates the cash flow for the closed positions of a trading record.
     *
     * @param tradingRecord the trading record
     */
    private void calculate(TradingRecord tradingRecord) {
        // For each position...
        tradingRecord.getPositions().forEach(this::calculate);
    }

    /**
     * Calculates the cash flow for all positions of a trading record, including
     * accrued cash flow of an open position.
     *
     * @param tradingRecord the trading record
     * @param finalIndex    index up until cash flows of open positions are
     *                      considered
     */
    private void calculate(TradingRecord tradingRecord, int finalIndex) {
        calculate(tradingRecord);

        // Add accrued cash flow of open position
        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET && tradingRecord.getCurrentPosition().isOpened()) {
            calculate(tradingRecord.getCurrentPosition(), finalIndex);
        }
    }

    /**
     * Pads {@link #values} with its last value up until {@code endIndex}.
     *
     * @param endIndex the end index
     */
    private void fillToTheEnd(int endIndex) {
        if (endIndex >= values.size()) {
            var lastValue = values.getLast();
            values.addAll(Collections.nCopies(endIndex - values.size() + 1, lastValue));
        }
    }

}
