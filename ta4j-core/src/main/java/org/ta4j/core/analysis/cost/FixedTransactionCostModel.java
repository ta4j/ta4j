/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import java.util.Objects;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
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
        Trade entry = position.getEntry();
        if (entry != null && entry.getFuturesContract() != null) {
            return sumExecutedFillCosts(position, currentIndex);
        }
        final var numFactory = position.getEntry().getPricePerAsset().getNumFactory();
        Num multiplier = numFactory.one();
        if (position.isClosed()) {
            multiplier = numFactory.numOf(2);
        }
        return numFactory.numOf(feePerTrade).multipliedBy(multiplier);
    }

    /**
     * <b>Note:</b> A partially executed native trade charges only the fills already
     * executed no later than {@code currentIndex}.
     *
     * <p>
     * The fixed fee is applied once per executed fill, because the venue charges
     * per contract execution rather than per entry/exit trade.
     * </p>
     */
    private Num sumExecutedFillCosts(Position position, int currentIndex) {
        Trade entry = position.getEntry();
        Num total = entry.getPricePerAsset().getNumFactory().zero();
        total = total.plus(sumFillCosts(entry, currentIndex));
        Trade exit = position.getExit();
        if (exit != null) {
            total = total.plus(sumFillCosts(exit, currentIndex));
        }
        return total;
    }

    private Num sumFillCosts(Trade trade, int currentIndex) {
        Num total = trade.getPricePerAsset().getNumFactory().zero();
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() < 0 || fill.index() > currentIndex) {
                continue;
            }
            Num fillCost = feePerTrade == 0d || !fill.hasRecordedFees() ? calculate(fill) : fill.fee();
            total = total.plus(total.getNumFactory().numOf(fillCost.getDelegate()));
        }
        return total;
    }

    /**
     * @return the transaction cost of the single {@code position}
     */
    @Override
    public Num calculate(Position position) {
        Trade entry = position.getEntry();
        if (entry != null && entry.getFuturesContract() != null) {
            return sumExecutedFillCosts(position, Integer.MAX_VALUE);
        }
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

    /**
     * Charges {@link #feePerTrade} once per native execution fill.
     *
     * @param fill the execution fill
     * @return the trading cost of {@code fill}
     * @since 0.25.1
     */
    @Override
    public Num calculate(TradeFill fill) {
        Objects.requireNonNull(fill, "fill");
        if (fill.futuresContract() == null) {
            return calculate(fill.price(), fill.amount());
        }
        return fill.price().getNumFactory().numOf(feePerTrade);
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
