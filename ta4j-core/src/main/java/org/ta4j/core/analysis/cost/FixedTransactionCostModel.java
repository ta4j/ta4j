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
package org.ta4j.core.analysis.cost;

import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;

/**
 * With this cost model, the trading costs for opening or closing a position are
 * accrued through a constant fee per trade (i.e. a fixed fee per transaction).
 */
public class FixedTransactionCostModel implements CostModel {

    /** The fixed fee per {@link Trade trade}. */
    private final double feePerTrade;

    /**
     * Constructor for a fixed fee trading cost model.
     *
     * <pre>
     * Cost of opened {@link Position position}: (fixedFeePerTrade * 1) 
     * Cost of closed {@link Position position}: (fixedFeePerTrade * 2)
     * </pre>
     *
     * @param feePerTrade the fixed fee per {@link Trade trade}
     */
    public FixedTransactionCostModel(double feePerTrade) {
        this.feePerTrade = feePerTrade;
    }

    /**
     * @param position     the position
     * @param currentIndex the current bar index (irrelevant for
     *                     {@code FixedTransactionCostModel})
     * @return the transaction cost of the single {@code position}
     */
    @Override
    public Num calculate(Position position, int currentIndex) {
        Num pricePerAsset = position.getEntry().getPricePerAsset();
        Num multiplier = pricePerAsset.one();
        if (position.isClosed()) {
            multiplier = pricePerAsset.numOf(2);
        }
        return pricePerAsset.numOf(feePerTrade).multipliedBy(multiplier);
    }

    /**
     * @return the transaction cost of the single {@code position}
     */
    @Override
    public Num calculate(Position position) {
        return this.calculate(position, 0);
    }

    /**
     * <b>Note:</b> Both {@code price} and {@code amount} are irrelevant as the fee
     * in {@code FixedTransactionCostModel} is always the same.
     *
     * @return {@link #feePerTrade}
     */
    @Override
    public Num calculate(Num price, Num amount) {
        return price.numOf(feePerTrade);
    }

    @Override
    public boolean equals(CostModel otherModel) {
        boolean equality = false;
        if (this.getClass().equals(otherModel.getClass())) {
            equality = ((FixedTransactionCostModel) otherModel).feePerTrade == this.feePerTrade;
        }
        return equality;
    }
}
