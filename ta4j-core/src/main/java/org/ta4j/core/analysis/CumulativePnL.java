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
import org.ta4j.core.*;
import org.ta4j.core.num.Num;

/**
 * An {@link Indicator} implementation that computes the cumulative profit and
 * loss (PnL) series of one or more trading positions over a given
 * {@link BarSeries}.
 * <p>
 * The cumulative PnL is calculated incrementally from the start of the
 * {@code BarSeries}, taking into account realized and unrealized gains/losses,
 * trading costs, and position direction (long or short). Each index in the
 * series represents the total PnL up to that bar. The calculation mode can be
 * configured to mark open positions to market or to only realize PnL at exits.
 * </p>
 *
 * @since 0.19
 */
public final class CumulativePnL implements PerformanceIndicator {

    private final BarSeries barSeries;
    private final List<Num> values;
    private final EquityCurveMode equityCurveMode;

    /**
     * Constructor for a trading record with a specified final index.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param finalIndex           the final index to calculate up to
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle the last open position
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this.barSeries = Objects.requireNonNull(barSeries);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        var aZero = Collections.singletonList(barSeries.numFactory().zero());
        this.values = new ArrayList<>(aZero);

        calculate(Objects.requireNonNull(tradingRecord), finalIndex, Objects.requireNonNull(openPositionHandling));
        fillToTheEnd(finalIndex);
    }

    /**
     * Constructor for a single closed position.
     *
     * @param barSeries       the bar series
     * @param position        the closed position
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, Position position, EquityCurveMode equityCurveMode) {
        this(barSeries, new BaseTradingRecord(position), barSeries.getEndIndex(), equityCurveMode);
    }

    /**
     * Constructor for a trading record with a specified final index.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param finalIndex      the final index to calculate up to
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex,
            EquityCurveMode equityCurveMode) {
        this(barSeries, tradingRecord, finalIndex, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor for a single closed position.
     *
     * @param barSeries the bar series
     * @param position  the closed position
     * @since 0.19
     */
    public CumulativePnL(BarSeries barSeries, Position position) {
        this(barSeries, position, EquityCurveMode.MARK_TO_MARKET);
    }

    /**
     * Constructor for a trading record.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     * @since 0.19
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor for a trading record.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), equityCurveMode,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor for a trading record.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle the last open position
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), equityCurveMode, openPositionHandling);
    }

    /**
     * Constructor for a trading record with a specified final index.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     * @param finalIndex    the final index to calculate up to
     * @since 0.19
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex) {
        this(barSeries, tradingRecord, finalIndex, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor for a trading record.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param openPositionHandling how to handle the last open position
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), EquityCurveMode.MARK_TO_MARKET,
                openPositionHandling);
    }

    /**
     * Calculates the cumulative PnL for a single position.
     *
     * @param position   the position
     * @param finalIndex the final index to calculate up to
     * @since 0.22.2
     */
    @Override
    public void calculatePosition(Position position, int finalIndex) {
        var numFactory = barSeries.numFactory();
        var entry = position.getEntry();
        var isLong = entry.isBuy();
        var entryIndex = entry.getIndex();
        var endIndex = determineEndIndex(position, finalIndex, barSeries.getEndIndex());
        var begin = entryIndex + 1;

        padToSize(values, begin, values.getLast());

        var baseAtEntry = values.get(entryIndex);
        var startingIndex = Math.max(begin, 1);
        var holdingCost = position.getHoldingCost(endIndex);
        var averageCostPerPeriod = averageHoldingCostPerPeriod(position, endIndex, numFactory);
        var netEntryPrice = entry.getNetPrice();
        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            for (var i = startingIndex; i < endIndex; i++) {
                var close = barSeries.getBar(i).getClosePrice();
                var netIntermediate = addCost(close, averageCostPerPeriod, isLong);
                var delta = isLong ? netIntermediate.minus(netEntryPrice) : netEntryPrice.minus(netIntermediate);
                values.add(baseAtEntry.plus(delta));
            }
            var exitRaw = resolveExitPrice(position, endIndex, barSeries);
            var netExit = addCost(exitRaw, averageCostPerPeriod, isLong);
            var deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
            values.add(baseAtEntry.plus(deltaExit));
        } else {
            var exit = position.getExit();
            if (exit != null && endIndex >= exit.getIndex()) {
                for (var i = startingIndex; i < endIndex; i++) {
                    values.add(baseAtEntry);
                }
                var exitRaw = exit.getNetPrice();
                var netExit = addCost(exitRaw, holdingCost, isLong);
                var deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
                values.add(baseAtEntry.plus(deltaExit));
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public Num getValue(int index) {
        return values.get(index);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19
     */
    @Override
    public BarSeries getBarSeries() {
        return barSeries;
    }

    /**
     * Returns the number of bars in the underlying series.
     *
     * @return the bar count
     * @since 0.19
     */
    public int getSize() {
        return barSeries.getBarCount();
    }

    @Override
    public EquityCurveMode getEquityCurveMode() {
        return equityCurveMode;
    }

    private void fillToTheEnd(int endIndex) {
        if (endIndex >= values.size()) {
            padToEndIndex(values, barSeries.getEndIndex(), values.getLast());
        }
    }

}
