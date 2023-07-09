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
package org.ta4j.core.criteria;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Versus "enter and hold" criterion, returned in decimal format.
 *
 * Compares the value of a provided {@link AnalysisCriterion criterion} with the
 * value of an "enter and hold". The "enter and hold"-strategy is done as
 * follows:
 *
 * <ul>
 * <li>For {@link #tradeType} = {@link TradeType#BUY}: Buy with the close price
 * of the first bar and sell with the close price of the last bar.
 * <li>For {@link #tradeType} = {@link TradeType#SELL}: Sell with the close
 * price of the first bar and buy with the close price of the last bar.
 * </ul>
 */
public class VersusEnterAndHoldCriterion extends AbstractAnalysisCriterion {

    private final TradeType tradeType;
    private final AnalysisCriterion criterion;

    /**
     * Constructor for buy-and-hold strategy.
     *
     * @param criterion the criterion to be compared
     */
    public VersusEnterAndHoldCriterion(AnalysisCriterion criterion) {
        this(TradeType.BUY, criterion);
    }

    /**
     * Constructor.
     *
     * @param tradeType the {@link TradeType} used to open the position
     * @param criterion the criterion to be compared
     */
    public VersusEnterAndHoldCriterion(TradeType tradeType, AnalysisCriterion criterion) {
        this.tradeType = tradeType;
        this.criterion = criterion;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        int beginIndex = position.getEntry().getIndex();
        int endIndex = series.getEndIndex();
        TradingRecord fakeRecord = createEnterAndHoldTradingRecord(series, beginIndex, endIndex);
        return criterion.calculate(series, position).dividedBy(criterion.calculate(series, fakeRecord));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        int beginIndex = tradingRecord.getStartIndex(series);
        int endIndex = tradingRecord.getEndIndex(series);
        TradingRecord fakeRecord = createEnterAndHoldTradingRecord(series, beginIndex, endIndex);
        return criterion.calculate(series, tradingRecord).dividedBy(criterion.calculate(series, fakeRecord));
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    private TradingRecord createEnterAndHoldTradingRecord(BarSeries series, int beginIndex, int endIndex) {
        TradingRecord fakeRecord = new BaseTradingRecord(tradeType);
        fakeRecord.enter(beginIndex, series.getBar(beginIndex).getClosePrice(), series.one());
        fakeRecord.exit(endIndex, series.getBar(endIndex).getClosePrice(), series.one());
        return fakeRecord;
    }

}
