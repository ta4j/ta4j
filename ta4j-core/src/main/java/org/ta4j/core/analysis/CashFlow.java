/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2019 Ta4j Organization & respective
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
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The cash flow.
 *
 * This class allows to follow the money cash flow involved by a list of trades
 * over a bar series.
 */
public class CashFlow implements Indicator<Num> {

    /**
     * The bar series
     */
    private final BarSeries barSeries;

    /**
     * The cash flow values
     */
    private List<Num> values;

    /**
     * Constructor for cash flows of a closed trade.
     *
     * @param barSeries the bar series
     * @param trade     a single trade
     */
    public CashFlow(BarSeries barSeries, Trade trade) {
        this.barSeries = barSeries;
        values = new ArrayList<>(Collections.singletonList(numOf(1)));
        calculate(trade);
        fillToTheEnd();
    }

    /**
     * Constructor for cash flows of closed trades of a trading record.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord) {
        this.barSeries = barSeries;
        values = new ArrayList<>(Collections.singletonList(numOf(1)));
        calculate(tradingRecord);

        fillToTheEnd();
    }

    /**
     * Constructor.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     * @param finalIndex    index up until cash flows of open trades are considered
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex) {
        this.barSeries = barSeries;
        values = new ArrayList<>(Collections.singletonList(numOf(1)));
        calculate(tradingRecord, finalIndex);

        fillToTheEnd();
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
    public BarSeries getBarSeries() {
        return barSeries;
    }

    @Override
    public Num numOf(Number number) {
        return barSeries.numOf(number);
    }

    /**
     * @return the size of the bar series
     */
    public int getSize() {
        return barSeries.getBarCount();
    }

    /**
     * Calculates the cash flow for a single closed trade.
     *
     * @param trade a single trade
     */
    private void calculate(Trade trade) {
        if (trade.isOpened()) {
            throw new IllegalArgumentException("Trade is not closed. Final index of observation needs to be provided.");
        }
        calculate(trade, trade.getExit().getIndex());
    }

    /**
     * Calculates the cash flow for a single trade (including accrued cashflow for
     * open trades).
     *
     * @param trade      a single trade
     * @param finalIndex index up until cash flow of open trades is considered
     */
    private void calculate(Trade trade, int finalIndex) {
        boolean isLongTrade = trade.getEntry().isBuy();
        int endIndex = determineEndIndex(trade, finalIndex, barSeries.getEndIndex());
        final int entryIndex = trade.getEntry().getIndex();
        int begin = entryIndex + 1;
        if (begin > values.size()) {
            Num lastValue = values.get(values.size() - 1);
            values.addAll(Collections.nCopies(begin - values.size(), lastValue));
        }
        // Trade is not valid if net balance at the entryIndex is negative
        if (values.get(values.size() - 1).isGreaterThan(values.get(0).numOf(0))) {
            int startingIndex = Math.max(begin, 1);

            int nPeriods = endIndex - entryIndex;
            Num holdingCost = trade.getHoldingCost(endIndex);
            Num avgCost = holdingCost.dividedBy(holdingCost.numOf(nPeriods));

            // Add intermediate cash flows during trade
            Num netEntryPrice = trade.getEntry().getNetPrice();
            for (int i = startingIndex; i < endIndex; i++) {
                Num intermediateNetPrice = addCost(barSeries.getBar(i).getClosePrice(), avgCost, isLongTrade);
                Num ratio = getIntermediateRatio(isLongTrade, netEntryPrice, intermediateNetPrice);
                values.add(values.get(entryIndex).multipliedBy(ratio));
            }

            // add net cash flow at exit trade
            Num exitPrice;
            if (trade.getExit() != null) {
                exitPrice = trade.getExit().getNetPrice();
            } else {
                exitPrice = barSeries.getBar(endIndex).getClosePrice();
            }
            Num ratio = getIntermediateRatio(isLongTrade, netEntryPrice, addCost(exitPrice, avgCost, isLongTrade));
            values.add(values.get(entryIndex).multipliedBy(ratio));
        }
    }

    /**
     * Calculates the ratio of intermediate prices.
     *
     * @param isLongTrade true, if the entry order type is BUY
     * @param entryPrice  price ratio denominator
     * @param exitPrice   price ratio numerator
     */
    private static Num getIntermediateRatio(boolean isLongTrade, Num entryPrice, Num exitPrice) {
        Num ratio;
        if (isLongTrade) {
            ratio = exitPrice.dividedBy(entryPrice);
        } else {
            ratio = entryPrice.numOf(2).minus(exitPrice.dividedBy(entryPrice));
        }
        return ratio;
    }

    /**
     * Calculates the cash flow for the closed trades of a trading record.
     *
     * @param tradingRecord the trading record
     */
    private void calculate(TradingRecord tradingRecord) {
        // For each trade...
        tradingRecord.getTrades().forEach(this::calculate);
    }

    /**
     * Calculates the cash flow for all trades of a trading record, including
     * accrued cash flow of an open trade.
     *
     * @param tradingRecord the trading record
     * @param finalIndex    index up until cash flows of open trades are considered
     */
    private void calculate(TradingRecord tradingRecord, int finalIndex) {
        calculate(tradingRecord);

        // Add accrued cash flow of open trade
        if (tradingRecord.getCurrentTrade().isOpened()) {
            calculate(tradingRecord.getCurrentTrade(), finalIndex);
        }
    }

    /**
     * Adjusts (intermediate) price to incorporate trading costs.
     *
     * @param rawPrice    the gross asset price
     * @param holdingCost share of the holding cost per period
     * @param isLongTrade true, if the entry order type is BUY
     */
    static Num addCost(Num rawPrice, Num holdingCost, boolean isLongTrade) {
        Num netPrice;
        if (isLongTrade) {
            netPrice = rawPrice.minus(holdingCost);
        } else {
            netPrice = rawPrice.plus(holdingCost);
        }
        return netPrice;
    }

    /**
     * Fills with last value till the end of the series.
     */
    private void fillToTheEnd() {
        if (barSeries.getEndIndex() >= values.size()) {
            Num lastValue = values.get(values.size() - 1);
            values.addAll(Collections.nCopies(barSeries.getEndIndex() - values.size() + 1, lastValue));
        }
    }

    /**
     * Determines the the valid final index to be considered.
     *
     * @param trade      the trade
     * @param finalIndex index up until cash flows of open trades are considered
     * @param maxIndex   maximal valid index
     */
    static int determineEndIndex(Trade trade, int finalIndex, int maxIndex) {
        int idx = finalIndex;
        // After closing of trade, no further accrual necessary
        if (trade.getExit() != null) {
            idx = Math.min(trade.getExit().getIndex(), finalIndex);
        }
        // Accrual at most until maximal index of asset data
        if (idx > maxIndex) {
            idx = maxIndex;
        }
        return idx;
    }
}