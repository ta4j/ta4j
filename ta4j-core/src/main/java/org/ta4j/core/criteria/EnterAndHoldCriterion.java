/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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

import java.math.BigDecimal;
import java.util.Objects;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.ReturnCriterion;
import org.ta4j.core.num.Num;

/**
 * Enter and hold criterion.
 *
 * <p>
 * Calculates the {@link AnalysisCriterion criterion} from an enter-and-hold
 * strategy:
 *
 * <ul>
 * <li>For {@link #tradeType} = {@link TradeType#BUY}: Buy with the close price
 * of the first bar and sell with the close price of the last bar.
 * <li>For {@link #tradeType} = {@link TradeType#SELL}: Sell with the close
 * price of the first bar and buy with the close price of the last bar.
 * <li>If {@code barSeries} is empty, it returns {@code 1} because the
 * investment hasn't changed and is still 100%.
 * </ul>
 *
 * @see <a href=
 *      "http://en.wikipedia.org/wiki/Buy_and_hold">http://en.wikipedia.org/wiki/Buy_and_hold</a>
 */
public class EnterAndHoldCriterion extends AbstractAnalysisCriterion {

    /** The amount to be used to hold the entry position */
    private final BigDecimal amount;
    private final TradeType tradeType;
    private final AnalysisCriterion criterion;

    /**
     * The {@link ReturnCriterion} (with base) from a buy-and-hold strategy with an
     * {@link #amount} of {@code 1}.
     */
    public static EnterAndHoldCriterion EnterAndHoldReturnCriterion() {
        return new EnterAndHoldCriterion(TradeType.BUY, new ReturnCriterion());
    }

    /**
     * Constructor for buy-and-hold strategy with an {@link #amount} of {@code 1}.
     *
     * @param criterion the {@link AnalysisCriterion criterion} to calculate
     * @throws IllegalArgumentException if {@code criterion} is an instance of
     *                                  {@code EnterAndHoldCriterion} or
     *                                  {@code VersusEnterAndHoldCriterion}
     */
    public EnterAndHoldCriterion(AnalysisCriterion criterion) {
        this(TradeType.BUY, criterion);
    }

    /**
     * Constructor with an {@link #amount} of {@code 1}.
     *
     * @param tradeType the {@link TradeType} used to open the position
     * @param criterion the {@link AnalysisCriterion criterion} to calculate
     * @throws IllegalArgumentException if {@code criterion} is an instance of
     *                                  {@code EnterAndHoldCriterion} or
     *                                  {@code VersusEnterAndHoldCriterion}
     */
    public EnterAndHoldCriterion(TradeType tradeType, AnalysisCriterion criterion) {
        this(tradeType, criterion, BigDecimal.ONE);
    }

    /**
     * Constructor.
     *
     * @param tradeType the {@link TradeType} used to open the position
     * @param criterion the {@link AnalysisCriterion criterion} to calculate
     * @param amount    the amount to be used to hold the entry position
     * @throws IllegalArgumentException if {@code criterion} is an instance of
     *                                  {@code EnterAndHoldCriterion} or
     *                                  {@code VersusEnterAndHoldCriterion}
     * @throws NullPointerException     if {@code amount} is {@code null}
     */
    public EnterAndHoldCriterion(TradeType tradeType, AnalysisCriterion criterion, BigDecimal amount) {
        if (criterion instanceof EnterAndHoldCriterion) {
            throw new IllegalArgumentException("Criterion cannot be an instance of EnterAndHoldCriterion.");
        }
        if (criterion instanceof VersusEnterAndHoldCriterion) {
            throw new IllegalArgumentException("Criterion cannot be an instance of VersusEnterAndHoldCriterion.");
        }
        this.tradeType = tradeType;
        this.criterion = criterion;
        this.amount = Objects.requireNonNull(amount);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var beginIndex = position.getEntry().getIndex();
        var endIndex = series.getEndIndex();
        return criterion.calculate(series, createEnterAndHoldTrade(series, beginIndex, endIndex));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (series.isEmpty()) {
            return series.numFactory().one();
        }
        var beginIndex = tradingRecord.getStartIndex(series);
        var endIndex = tradingRecord.getEndIndex(series);
        return criterion.calculate(series, createEnterAndHoldTradingRecord(series, beginIndex, endIndex));
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterion.betterThan(criterionValue1, criterionValue2);
    }

    private Position createEnterAndHoldTrade(BarSeries series, int beginIndex, int endIndex) {
        var position = new Position(tradeType);
        var entryAmount = series.numFactory().numOf(amount);
        position.operate(beginIndex, series.getBar(beginIndex).getClosePrice(), entryAmount);
        position.operate(endIndex, series.getBar(endIndex).getClosePrice(), entryAmount);
        return position;
    }

    private TradingRecord createEnterAndHoldTradingRecord(BarSeries series, int beginIndex, int endIndex) {
        var fakeRecord = new BaseTradingRecord(tradeType);
        var entryAmount = series.numFactory().numOf(amount);
        fakeRecord.enter(beginIndex, series.getBar(beginIndex).getClosePrice(), entryAmount);
        fakeRecord.exit(endIndex, series.getBar(endIndex).getClosePrice(), entryAmount);
        return fakeRecord;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " of " + criterion.getClass().getSimpleName();
    }
}
