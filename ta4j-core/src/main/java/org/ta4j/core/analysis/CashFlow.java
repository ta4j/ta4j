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
import java.util.List;
import java.util.Objects;
import org.ta4j.core.*;
import org.ta4j.core.num.Num;

/**
 * Allows to follow the money cash flow involved by a list of positions over a
 * bar series, either marked to market or using realized values only.
 */
public class CashFlow implements Indicator<Num>, PerformanceIndicator {

    /**
     * The bar series.
     */
    private final BarSeries barSeries;

    /**
     * The (accrued) cash flow sequence (without trading costs).
     */
    private final List<Num> values;

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
     * @param openPositionHandling how to handle the last open position
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this.barSeries = Objects.requireNonNull(barSeries);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        var aOne = List.of(barSeries.numFactory().one());
        values = new ArrayList<>(aOne);

        calculate(Objects.requireNonNull(tradingRecord), finalIndex, Objects.requireNonNull(openPositionHandling));
        fillToTheEnd(barSeries.getEndIndex());
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
        this(barSeries, new BaseTradingRecord(position), barSeries.getEndIndex(), equityCurveMode);
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
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
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
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), equityCurveMode,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle the last open position
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), equityCurveMode, openPositionHandling);
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
     * @param openPositionHandling how to handle the last open position
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), EquityCurveMode.MARK_TO_MARKET,
                openPositionHandling);
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
        var numFactory = barSeries.numFactory();
        var entry = position.getEntry();
        var isLongTrade = entry.isBuy();
        var endIndex = determineEndIndex(position, finalIndex, barSeries.getEndIndex());
        var entryIndex = entry.getIndex();
        var beginIndexExclusive = entryIndex + 1;

        ensureValuesSizeAtLeast(beginIndexExclusive);

        var entryEquity = values.get(entryIndex);
        if (!entryEquity.isGreaterThan(numFactory.zero())) {
            return;
        }

        var startingIndex = Math.max(beginIndexExclusive, 1);
        var holdingCost = position.getHoldingCost(endIndex);
        var netEntryPrice = entry.getNetPrice();
        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            var averageHoldingCostPerPeriod = averageHoldingCostPerPeriod(position, endIndex, numFactory);
            for (var barIndex = startingIndex; barIndex < endIndex; barIndex++) {
                var closePrice = barSeries.getBar(barIndex).getClosePrice();
                var intermediateNetPrice = addCost(closePrice, averageHoldingCostPerPeriod, isLongTrade);
                var ratio = getIntermediateRatio(isLongTrade, netEntryPrice, intermediateNetPrice);
                values.add(entryEquity.multipliedBy(ratio));
            }
            var exitPrice = resolveExitPrice(position, endIndex, barSeries);
            var netExitPrice = addCost(exitPrice, averageHoldingCostPerPeriod, isLongTrade);
            var ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
            values.add(entryEquity.multipliedBy(ratio));
        } else {
            var exit = position.getExit();
            if (exit != null && endIndex >= exit.getIndex()) {
                for (var barIndex = startingIndex; barIndex < endIndex; barIndex++) {
                    values.add(entryEquity);
                }
                var netExitPrice = addCost(exit.getNetPrice(), holdingCost, isLongTrade);
                var ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
                values.add(entryEquity.multipliedBy(ratio));
            }
        }
    }

    private void ensureValuesSizeAtLeast(int requiredSize) {
        padToSize(values, requiredSize, values.getLast());
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
     * Calculates the ratio of intermediate prices.
     *
     * @param isLongTrade true, if the entry trade type is BUY
     * @param entryPrice  price ratio denominator
     * @param exitPrice   price ratio numerator
     */
    private static Num getIntermediateRatio(boolean isLongTrade, Num entryPrice, Num exitPrice) {
        if (isLongTrade) {
            return exitPrice.dividedBy(entryPrice);
        }
        return entryPrice.getNumFactory().numOf(2).minus(exitPrice.dividedBy(entryPrice));
    }

    @Override
    public EquityCurveMode getEquityCurveMode() {
        return equityCurveMode;
    }

    /**
     * Pads {@link #values} with its last value up until {@code endIndex}.
     *
     * @param endIndex the end index
     */
    private void fillToTheEnd(int endIndex) {
        padToEndIndex(values, endIndex, values.getLast());
    }
}
