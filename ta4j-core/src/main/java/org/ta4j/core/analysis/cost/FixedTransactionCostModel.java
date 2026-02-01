/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import org.ta4j.core.Position;
import org.ta4j.core.TradeView;
import org.ta4j.core.num.Num;

/**
 * With this cost model, the trading costs for opening or closing a position are
 * accrued through a constant fee per trade (i.e. a fixed fee per transaction).
 */
public class FixedTransactionCostModel implements CostModel {

    /** The fixed fee per {@link TradeView trade}. */
    private final double feePerTrade;

    /**
     * Constructor for a fixed fee trading cost model.
     *
     * <pre>
     * Cost of opened {@link Position position}: (fixedFeePerTrade * 1)
     * Cost of closed {@link Position position}: (fixedFeePerTrade * 2)
     * </pre>
     *
     * @param feePerTrade the fixed fee per {@link TradeView trade}
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
        final var numFactory = position.getEntry().getPricePerAsset().getNumFactory();
        Num multiplier = numFactory.one();
        if (position.isClosed()) {
            multiplier = numFactory.numOf(2);
        }
        return numFactory.numOf(feePerTrade).multipliedBy(multiplier);
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
        return price.getNumFactory().numOf(feePerTrade);
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
