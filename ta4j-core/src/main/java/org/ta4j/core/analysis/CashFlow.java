/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
import org.ta4j.core.num.Num;

/**
 * The cash flow.
 * 现金流量。
 *
 * This class allows to follow the money cash flow involved by a list of positions over a bar series.
 * 此类允许跟踪条形系列上的头寸列表所涉及的资金现金流。
 */
public class CashFlow implements Indicator<Num> {

    /**
     * The bar series
     * * bar系列
     */
    private final BarSeries barSeries;

    /**
     * The cash flow values
     * * 现金流值
     */
    private List<Num> values;

    /**
     * Constructor for cash flows of a closed position.
     * 平仓现金流的构造函数。
     *
     * @param barSeries the bar series
     *                  bar系列
     * @param position  a single position
     *                  单一职位
     */
    public CashFlow(BarSeries barSeries, Position position) {
        this.barSeries = barSeries;
        values = new ArrayList<>(Collections.singletonList(numOf(1)));
        calculate(position);
        fillToTheEnd();
    }

    /**
     * Constructor for cash flows of closed positions of a trading record.
     * 交易记录的已平仓头寸的现金流构造函数。
     *
     * @param barSeries     the bar series
     *                      酒吧系列
     * @param tradingRecord the trading record
     *                      交易记录
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord) {
        this.barSeries = barSeries;
        values = new ArrayList<>(Collections.singletonList(numOf(1)));
        calculate(tradingRecord);

        fillToTheEnd();
    }

    /**
     * Constructor.
     * 构造函数。
     *
     * @param barSeries     the bar series
     *                      酒吧系列
     * @param tradingRecord the trading record
     *                      交易记录
     * @param finalIndex    index up until cash flows of open positions are  considered
     *                      索引直到考虑未平仓头寸的现金流
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex) {
        this.barSeries = barSeries;
        values = new ArrayList<>(Collections.singletonList(numOf(1)));
        calculate(tradingRecord, finalIndex);

        fillToTheEnd();
    }

    /**
     * @param index the bar index
     *              条形索引
     * @return the cash flow value at the index-th position
     * 第索引位置的现金流值
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
     * 酒吧系列的尺寸
     */
    public int getSize() {
        return barSeries.getBarCount();
    }

    /**
     * Calculates the cash flow for a single closed position.
     * * 计算单个平仓头寸的现金流。
     *
     * @param position a single position
     *                 单一职位
     */
    private void calculate(Position position) {
        if (position.isOpened()) {
            throw new IllegalArgumentException(
                    "Position is not closed. Final index of observation needs to be provided. 持仓未平仓。 需要提供最终的观察指标。");
        }
        calculate(position, position.getExit().getIndex());
    }

    /**
     * Calculates the cash flow for a single position (including accrued cashflow for open positions).
     * 计算单个头寸的现金流（包括未平仓头寸的应计现金流）。
     *
     * @param position   a single position
     *                   单一职位
     * @param finalIndex index up until cash flow of open positions is considered
     *                   索引直到考虑未平仓头寸的现金流
     */
    private void calculate(Position position, int finalIndex) {
        boolean isLongTrade = position.getEntry().isBuy();
        int endIndex = determineEndIndex(position, finalIndex, barSeries.getEndIndex());
        final int entryIndex = position.getEntry().getIndex();
        int begin = entryIndex + 1;
        if (begin > values.size()) {
            Num lastValue = values.get(values.size() - 1);
            values.addAll(Collections.nCopies(begin - values.size(), lastValue));
        }
        // Trade is not valid if net balance at the entryIndex is negative
        // 如果 entryIndex 的净余额为负，则交易无效
        if (values.get(values.size() - 1).isGreaterThan(values.get(0).numOf(0))) {
            int startingIndex = Math.max(begin, 1);

            int nPeriods = endIndex - entryIndex;
            Num holdingCost = position.getHoldingCost(endIndex);
            Num avgCost = holdingCost.dividedBy(holdingCost.numOf(nPeriods));

            // Add intermediate cash flows during position
            // 在持仓期间添加中间现金流
            Num netEntryPrice = position.getEntry().getNetPrice();
            for (int i = startingIndex; i < endIndex; i++) {
                Num intermediateNetPrice = addCost(barSeries.getBar(i).getClosePrice(), avgCost, isLongTrade);
                Num ratio = getIntermediateRatio(isLongTrade, netEntryPrice, intermediateNetPrice);
                values.add(values.get(entryIndex).multipliedBy(ratio));
            }

            // add net cash flow at exit position
            // 在退出位置添加净现金流
            Num exitPrice;
            if (position.getExit() != null) {
                exitPrice = position.getExit().getNetPrice();
            } else {
                exitPrice = barSeries.getBar(endIndex).getClosePrice();
            }
            Num ratio = getIntermediateRatio(isLongTrade, netEntryPrice, addCost(exitPrice, avgCost, isLongTrade));
            values.add(values.get(entryIndex).multipliedBy(ratio));
        }
    }

    /**
     * Calculates the ratio of intermediate prices.
     * 计算中间价格的比率。
     *
     * @param isLongTrade true, if the entry trade type is BUY
     *                    true，如果入场交易类型是 BUY
     * @param entryPrice  price ratio denominator
     *                    价格比分母
     * @param exitPrice   price ratio numerator
     *                    价格比率分子
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
     * Calculates the cash flow for the closed positions of a trading record.
     * 计算交易记录的已平仓头寸的现金流。
     *
     * @param tradingRecord the trading record
     *                      交易记录
     */
    private void calculate(TradingRecord tradingRecord) {
        // For each position...
        // 对于每个位置...
        tradingRecord.getPositions().forEach(this::calculate);
    }

    /**
     * Calculates the cash flow for all positions of a trading record, including accrued cash flow of an open position.
     * * 计算交易记录中所有头寸的现金流，包括未平仓头寸的应计现金流。
     *
     * @param tradingRecord the trading record
     *                      交易记录
     * @param finalIndex    index up until cash flows of open positions are  considered
     *                      索引直到考虑未平仓头寸的现金流
     */
    private void calculate(TradingRecord tradingRecord, int finalIndex) {
        calculate(tradingRecord);

        // Add accrued cash flow of open position
        // 添加未平仓头寸的应计现金流
        if (tradingRecord.getCurrentPosition().isOpened()) {
            calculate(tradingRecord.getCurrentPosition(), finalIndex);
        }
    }

    /**
     * Adjusts (intermediate) price to incorporate trading costs.
     * * 调整（中间）价格以纳入交易成本。
     *
     * @param rawPrice    the gross asset price
     *                    总资产价格
     * @param holdingCost share of the holding cost per period
     *                    每期持有成本的份额
     * @param isLongTrade true, if the entry trade type is BUY
     *                    true，如果入场交易类型是 BUY
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
     * * 填充最后一个值，直到系列结束。
     */
    private void fillToTheEnd() {
        if (barSeries.getEndIndex() >= values.size()) {
            Num lastValue = values.get(values.size() - 1);
            values.addAll(Collections.nCopies(barSeries.getEndIndex() - values.size() + 1, lastValue));
        }
    }

    /**
     * Determines the the valid final index to be considered.
     * 确定要考虑的有效最终索引。
     *
     * @param position   the position
     *                   位置
     * @param finalIndex index up until cash flows of open positions are considered
     *                   索引直到考虑未平仓头寸的现金流
     * @param maxIndex   maximal valid index
     *                   最大有效指数
     */
    static int determineEndIndex(Position position, int finalIndex, int maxIndex) {
        int idx = finalIndex;
        // After closing of position, no further accrual necessary
        // 平仓后，无需进一步计提
        if (position.getExit() != null) {
            idx = Math.min(position.getExit().getIndex(), finalIndex);
        }
        // Accrual at most until maximal index of asset data
        // 最多累积直到资产数据的最大索引
        if (idx > maxIndex) {
            idx = maxIndex;
        }
        return idx;
    }
}