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

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Allows to follow the money cash flow involved by a list of positions over a
 * bar series, either marked-to-market or using realized values only. Optionally
 * includes an open position.
 */
public class CashFlow implements Indicator<Num> {

    private final BarSeries barSeries;
    private final List<Num> values;
    private final EquityCurveMode equityCurveMode;

    public CashFlow(BarSeries barSeries, Position position, EquityCurveMode equityCurveMode) {
        this.barSeries = Objects.requireNonNull(barSeries, "barSeries must not be null");
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode, "equityCurveMode must not be null");
        this.values = new ArrayList<>(barSeries.getBarCount());
        this.values.add(barSeries.numFactory().one());

        calculateClosedPosition(position);
        fillToTheEnd(barSeries.getEndIndex());
    }

    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this.barSeries = Objects.requireNonNull(barSeries, "barSeries must not be null");
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode, "equityCurveMode must not be null");
        Objects.requireNonNull(tradingRecord, "tradingRecord must not be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling must not be null");

        this.values = new ArrayList<>(barSeries.getBarCount());
        this.values.add(barSeries.numFactory().one());

        calculateTradingRecord(tradingRecord, finalIndex, openPositionHandling);
        fillToTheEnd(barSeries.getEndIndex());
    }

    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), equityCurveMode, openPositionHandling);
    }

    public CashFlow(BarSeries barSeries, Position position) {
        this(barSeries, position, EquityCurveMode.MARK_TO_MARKET);
    }

    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), equityCurveMode,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), EquityCurveMode.MARK_TO_MARKET,
                openPositionHandling);
    }

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

    public int getSize() {
        return barSeries.getBarCount();
    }

    private void calculateClosedPosition(Position position) {
        Objects.requireNonNull(position, "position must not be null");
        if (position.isOpened()) {
            throw new IllegalArgumentException(
                    "Position is not closed. Final index of observation needs to be provided.");
        }
        calculatePosition(position, position.getExit().getIndex(), equityCurveMode);
    }

    private void calculateTradingRecord(TradingRecord tradingRecord, int finalIndex,
            OpenPositionHandling openPositionHandling) {
        tradingRecord.getPositions().forEach(this::calculateClosedPosition);
        handleLastPosition(tradingRecord, finalIndex, openPositionHandling);
    }

    private void handleLastPosition(TradingRecord tradingRecord, int finalIndex,
            OpenPositionHandling openPositionHandling) {
        var effectiveOpenPositionHandling = equityCurveMode == EquityCurveMode.REALIZED ? OpenPositionHandling.IGNORE
                : openPositionHandling;
        var currentPosition = tradingRecord.getCurrentPosition();
        if (effectiveOpenPositionHandling == OpenPositionHandling.MARK_TO_MARKET && currentPosition != null
                && currentPosition.isOpened()) {
            calculatePosition(currentPosition, finalIndex, EquityCurveMode.MARK_TO_MARKET);
        }
    }

    private void calculatePosition(Position position, int finalIndex, EquityCurveMode calculationMode) {
        var numFactory = barSeries.numFactory();
        var isLongTrade = position.getEntry().isBuy();
        var endIndex = AnalysisUtils.determineEndIndex(position, finalIndex, barSeries.getEndIndex());
        var entryIndex = position.getEntry().getIndex();
        var beginIndexExclusive = entryIndex + 1;

        ensureValuesSizeAtLeast(beginIndexExclusive);

        var zero = numFactory.zero();
        var entryEquity = values.get(entryIndex);
        if (!entryEquity.isGreaterThan(zero)) {
            return;
        }

        var startingIndex = Math.max(beginIndexExclusive, 1);
        var holdingCost = position.getHoldingCost(endIndex);
        var numberOfPeriods = endIndex - entryIndex;
        var effectivePeriodCount = Math.max(1, numberOfPeriods);
        var netEntryPrice = position.getEntry().getNetPrice();

        if (calculationMode == EquityCurveMode.MARK_TO_MARKET) {
            var averageHoldingCostPerPeriod = holdingCost.dividedBy(numFactory.numOf(effectivePeriodCount));

            for (var barIndex = startingIndex; barIndex < endIndex; barIndex++) {
                var intermediateNetPrice = AnalysisUtils.addCost(barSeries.getBar(barIndex).getClosePrice(),
                        averageHoldingCostPerPeriod, isLongTrade);
                var ratio = getIntermediateRatio(isLongTrade, netEntryPrice, intermediateNetPrice);
                values.add(entryEquity.multipliedBy(ratio));
            }

            var exitPrice = position.getExit() != null ? position.getExit().getNetPrice()
                    : barSeries.getBar(endIndex).getClosePrice();

            var netExitPrice = AnalysisUtils.addCost(exitPrice, averageHoldingCostPerPeriod, isLongTrade);
            var ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
            values.add(entryEquity.multipliedBy(ratio));
        } else if (position.getExit() != null && endIndex >= position.getExit().getIndex()) {
            for (var barIndex = startingIndex; barIndex < endIndex; barIndex++) {
                values.add(entryEquity);
            }

            var netExitPrice = AnalysisUtils.addCost(position.getExit().getNetPrice(), holdingCost, isLongTrade);
            var ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
            values.add(entryEquity.multipliedBy(ratio));
        }
    }

    private void ensureValuesSizeAtLeast(int requiredSize) {
        if (requiredSize > values.size()) {
            var lastValue = values.getLast();
            values.addAll(Collections.nCopies(requiredSize - values.size(), lastValue));
        }
    }

    private void fillToTheEnd(int endIndex) {
        if (endIndex >= values.size()) {
            var lastValue = values.getLast();
            values.addAll(Collections.nCopies(endIndex - values.size() + 1, lastValue));
        }
    }

    private static Num getIntermediateRatio(boolean isLongTrade, Num entryPrice, Num exitPrice) {
        if (isLongTrade) {
            return exitPrice.dividedBy(entryPrice);
        }
        return entryPrice.getNumFactory().numOf(2).minus(exitPrice.dividedBy(entryPrice));
    }
}