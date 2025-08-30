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

public final class CumulativePnL implements Indicator<Num> {

    private final BarSeries barSeries;
    private final List<Num> values;

    public CumulativePnL(BarSeries barSeries, Position position) {
        if (position.isOpened()) {
            throw new IllegalArgumentException("Position is not closed. Provide a final index if open.");
        }
        this.barSeries = barSeries;
        this.values = new ArrayList<>(Collections.singletonList(barSeries.numFactory().zero()));
        calculate(position, position.getExit().getIndex());
        fillToTheEnd(barSeries.getEndIndex());
    }

    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries));
    }

    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex) {
        this.barSeries = barSeries;
        this.values = new ArrayList<>(Collections.singletonList(barSeries.numFactory().zero()));

        var positions = tradingRecord.getPositions();
        for (var position : positions) {
            var endIndex = AnalysisUtils.determineEndIndex(position, finalIndex, barSeries.getEndIndex());
            calculate(position, endIndex);
        }
        if (tradingRecord.getCurrentPosition().isOpened()) {
            calculate(tradingRecord.getCurrentPosition(), finalIndex);
        }
        fillToTheEnd(finalIndex);
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

        var periods = Math.max(0, endIndex - entryIndex);
        var holdingCost = position.getHoldingCost(endIndex);
        var averageCostPerPeriod = periods > 0 ? holdingCost.dividedBy(numFactory.numOf(periods)) : zero;
        var netEntryPrice = position.getEntry().getNetPrice();
        var baseAtEntry = values.get(entryIndex);
        var startingIndex = Math.max(begin, 1);

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
    }

    private void fillToTheEnd(int endIndex) {
        if (endIndex >= values.size()) {
            var last = values.getLast();
            values.addAll(Collections.nCopies(barSeries.getEndIndex() - values.size() + 1, last));
        }
    }

}
