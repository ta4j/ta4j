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
public final class CumulativePnL implements Indicator<Num> {

    private final BarSeries barSeries;
    private final List<Num> values;
    private final EquityCurveMode equityCurveMode;

    /**
     * Constructor for a single closed position.
     *
     * @param barSeries the bar series
     * @param position  the closed position
     *
     * @since 0.19
     */
    public CumulativePnL(BarSeries barSeries, Position position) {
        this(barSeries, position, EquityCurveMode.MARK_TO_MARKET);
    }

    /**
     * Constructor for a single closed position.
     *
     * @param barSeries       the bar series
     * @param position        the closed position
     * @param equityCurveMode the calculation mode
     *
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, Position position, EquityCurveMode equityCurveMode) {
        if (position.isOpened()) {
            throw new IllegalArgumentException("Position is not closed. Provide a final index if open.");
        }
        this.barSeries = Objects.requireNonNull(barSeries);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        var aZero = Collections.singletonList(barSeries.numFactory().zero());
        this.values = new ArrayList<>(aZero);
        calculate(position, position.getExit().getIndex());
        fillToTheEnd(barSeries.getEndIndex());
    }

    /**
     * Constructor for a trading record with a specified final index.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param finalIndex      the final index to calculate up to
     * @param equityCurveMode the calculation mode
     *
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex,
            EquityCurveMode equityCurveMode) {
        this.barSeries = Objects.requireNonNull(barSeries);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        var aZero = Collections.singletonList(barSeries.numFactory().zero());
        this.values = new ArrayList<>(aZero);

        var positions = tradingRecord.getPositions();
        for (var position : positions) {
            var endIndex = AnalysisUtils.determineEndIndex(position, finalIndex, barSeries.getEndIndex());
            calculate(position, endIndex);
        }
        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET && tradingRecord.getCurrentPosition().isOpened()) {
            calculate(tradingRecord.getCurrentPosition(), finalIndex);
        }
        fillToTheEnd(finalIndex);
    }

    /**
     * Constructor for a trading record.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     *
     * @since 0.19
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), EquityCurveMode.MARK_TO_MARKET);
    }

    /**
     * Constructor for a trading record.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param equityCurveMode the calculation mode
     *
     * @since 0.22.2
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), equityCurveMode);
    }

    /**
     * Constructor for a trading record with a specified final index.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     * @param finalIndex    the final index to calculate up to
     *
     * @since 0.19
     */
    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex) {
        this(barSeries, tradingRecord, finalIndex, EquityCurveMode.MARK_TO_MARKET);
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
     *
     * @since 0.19
     */
    public int getSize() {
        return barSeries.getBarCount();
    }

    private void calculate(Position position, int finalIndex) {
        var numFactory = barSeries.numFactory();
        var zero = numFactory.zero();
        var isLong = position.getEntry().isBuy();
        var entryIndex = position.getEntry().getIndex();
        var endIndex = AnalysisUtils.determineEndIndex(position, finalIndex, barSeries.getEndIndex());
        var begin = entryIndex + 1;

        if (begin > values.size()) {
            var last = values.getLast();
            values.addAll(Collections.nCopies(begin - values.size(), last));
        }

        var baseAtEntry = values.get(entryIndex);
        var startingIndex = Math.max(begin, 1);

        var periods = Math.max(0, endIndex - entryIndex);
        var holdingCost = position.getHoldingCost(endIndex);
        var averageCostPerPeriod = periods > 0 ? holdingCost.dividedBy(numFactory.numOf(periods)) : zero;
        var netEntryPrice = position.getEntry().getNetPrice();

        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            for (var i = startingIndex; i < endIndex; i++) {
                var close = barSeries.getBar(i).getClosePrice();
                var netIntermediate = AnalysisUtils.addCost(close, averageCostPerPeriod, isLong);
                var delta = isLong ? netIntermediate.minus(netEntryPrice) : netEntryPrice.minus(netIntermediate);
                values.add(baseAtEntry.plus(delta));
            }

            var exitRaw = Objects.nonNull(position.getExit()) ? position.getExit().getNetPrice()
                    : barSeries.getBar(endIndex).getClosePrice();
            var netExit = AnalysisUtils.addCost(exitRaw, averageCostPerPeriod, isLong);
            var deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
            values.add(baseAtEntry.plus(deltaExit));
        } else if (position.getExit() != null && endIndex >= position.getExit().getIndex()) {
            for (var i = startingIndex; i < endIndex; i++) {
                values.add(baseAtEntry);
            }
            var exitRaw = position.getExit().getNetPrice();
            var netExit = AnalysisUtils.addCost(exitRaw, holdingCost, isLong);
            var deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
            values.add(baseAtEntry.plus(deltaExit));
        }
    }

    private void fillToTheEnd(int endIndex) {
        if (endIndex >= values.size()) {
            var last = values.getLast();
            values.addAll(Collections.nCopies(barSeries.getEndIndex() - values.size() + 1, last));
        }
    }

}
