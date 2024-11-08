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

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Versus "enter and hold" criterion, returned in decimal format.
 *
 * <p>
 * Compares the value of a provided {@link AnalysisCriterion criterion} with the
 * value of an {@link EnterAndHoldCriterion}.
 */
public class VersusEnterAndHoldCriterion extends AbstractAnalysisCriterion {

    private final AnalysisCriterion criterion;
    private final EnterAndHoldCriterion enterAndHoldCriterion;

    /**
     * Constructor with an entry amount of {@code 1}.
     *
     * @param criterion the criterion to be compared to
     *                  {@link EnterAndHoldCriterion}
     */
    public VersusEnterAndHoldCriterion(AnalysisCriterion criterion) {
        this(TradeType.BUY, criterion);
    }

    /**
     * Constructor with an entry amount of {@code 1}.
     *
     * @param tradeType the {@link TradeType} used to open the position
     * @param criterion the criterion to be compared to
     *                  {@link EnterAndHoldCriterion}
     */
    public VersusEnterAndHoldCriterion(TradeType tradeType, AnalysisCriterion criterion) {
        this.criterion = criterion;
        this.enterAndHoldCriterion = new EnterAndHoldCriterion(tradeType, criterion, BigDecimal.ONE);
    }

    /**
     * Constructor.
     *
     * @param tradeType the {@link TradeType} used to open the position
     * @param criterion the criterion to be compared to
     *                  {@link EnterAndHoldCriterion}
     * @param amount    the amount to be used to hold the entry position; if
     *                  {@code null} then {@code 1} is used.
     * @throws NullPointerException if {@code amount} is {@code null}
     */
    public VersusEnterAndHoldCriterion(TradeType tradeType, AnalysisCriterion criterion, BigDecimal amount) {
        this.criterion = criterion;
        this.enterAndHoldCriterion = new EnterAndHoldCriterion(tradeType, criterion, amount);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return criterion.calculate(series, position).dividedBy(enterAndHoldCriterion.calculate(series, position));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (series.isEmpty()) {
            return series.numFactory().one();
        }
        return criterion.calculate(series, tradingRecord)
                .dividedBy(enterAndHoldCriterion.calculate(series, tradingRecord));
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    @Override
    public String toString() {
        return criterion.getClass().getSimpleName() + " vs. " + enterAndHoldCriterion.getClass().getSimpleName();
    }

}
