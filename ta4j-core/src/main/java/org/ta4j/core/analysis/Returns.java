/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Allows to compute the return rate of a price time-series.
 */
public class Returns implements Indicator<Num> {

    public enum ReturnType {
        LOG {
            @Override
            public Num calculate(Num xNew, Num xOld) {
                // r_i = ln(P_i/P_(i-1))
                return (xNew.dividedBy(xOld)).log();
            }
        },
        ARITHMETIC {
            @Override
            public Num calculate(Num xNew, Num xOld) {
                // r_i = P_i/P_(i-1) - 1
                return xNew.dividedBy(xOld).minus(one);
            }
        };

        /**
         * @return the single return rate
         */
        public abstract Num calculate(Num xNew, Num xOld);
    }

    private final ReturnType type;

    /** The bar series. */
    private final BarSeries barSeries;

    /** The return rates. */
    private final List<Num> values;

    /** Unit element for efficient arithmetic return computation. */
    private static Num one;

    /**
     * Constructor.
     *
     * @param barSeries the bar series
     * @param position  a single position
     * @param type      the ReturnType
     */
    public Returns(BarSeries barSeries, Position position, ReturnType type) {
        one = barSeries.one();
        this.barSeries = barSeries;
        this.type = type;
        // at index 0, there is no return
        values = new ArrayList<>(Collections.singletonList(NaN.NaN));
        calculate(position, barSeries.getEndIndex());

        fillToTheEnd(barSeries.getEndIndex());
    }

    /**
     * Constructor.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     * @param type          the ReturnType
     */
    public Returns(BarSeries barSeries, TradingRecord tradingRecord, ReturnType type) {
        one = barSeries.one();
        this.barSeries = barSeries;
        this.type = type;
        // at index 0, there is no return
        values = new ArrayList<>(Collections.singletonList(NaN.NaN));
        calculate(tradingRecord);

        fillToTheEnd(tradingRecord.getEndIndex(barSeries));
    }

    /**
     * @return the return rates
     */
    public List<Num> getValues() {
        return values;
    }

    /**
     * @param index the bar index
     * @return the return rate value at the index-th position
     */
    @Override
    public Num getValue(int index) {
        return values.get(index);
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }

    @Override
    public BarSeries getBarSeries() {
        return barSeries;
    }

    @Override
    public Num numOf(Number number) {
        return barSeries.numOf(number);
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
        Num minusOne = barSeries.numOf(-1);
        int endIndex = CashFlow.determineEndIndex(position, finalIndex, barSeries.getEndIndex());
        final int entryIndex = position.getEntry().getIndex();
        int begin = entryIndex + 1;
        if (begin > values.size()) {
            values.addAll(Collections.nCopies(begin - values.size(), barSeries.zero()));
        }

        int startingIndex = Math.max(begin, 1);
        int nPeriods = endIndex - entryIndex;
        Num holdingCost = position.getHoldingCost(endIndex);
        Num avgCost = holdingCost.dividedBy(holdingCost.numOf(nPeriods));

        // returns are per period (iterative). Base price needs to be updated
        // accordingly
        Num lastPrice = position.getEntry().getNetPrice();
        for (int i = startingIndex; i < endIndex; i++) {
            Num intermediateNetPrice = CashFlow.addCost(barSeries.getBar(i).getClosePrice(), avgCost, isLongTrade);
            Num assetReturn = type.calculate(intermediateNetPrice, lastPrice);

            Num strategyReturn;
            if (position.getEntry().isBuy()) {
                strategyReturn = assetReturn;
            } else {
                strategyReturn = assetReturn.multipliedBy(minusOne);
            }
            values.add(strategyReturn);
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

        Num strategyReturn;
        Num assetReturn = type.calculate(CashFlow.addCost(exitPrice, avgCost, isLongTrade), lastPrice);
        if (position.getEntry().isBuy()) {
            strategyReturn = assetReturn;
        } else {
            strategyReturn = assetReturn.multipliedBy(minusOne);
        }
        values.add(strategyReturn);
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
     * Pads {@link #values} with zeros up until {@code endIndex}.
     * 
     * @param endIndex the end index
     */
    private void fillToTheEnd(int endIndex) {
        if (endIndex >= values.size()) {
            values.addAll(Collections.nCopies(barSeries.getEndIndex() - values.size() + 1, barSeries.zero()));
        }
    }
}