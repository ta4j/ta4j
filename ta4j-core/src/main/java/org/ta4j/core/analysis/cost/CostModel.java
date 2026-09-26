/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import java.util.List;
import java.util.Objects;
import org.ta4j.core.Position;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradeFee;
import org.ta4j.core.num.Num;

/**
 * With the {@code CostModel}, we can include trading costs that may be incurred
 * when opening or closing a position.
 */
public interface CostModel {

    /**
     * @param position   the position
     * @param finalIndex the index up to which open positions are considered
     * @return the trading cost of the single {@code position}
     */
    Num calculate(Position position, int finalIndex);

    /**
     * @param position the position
     * @return the trading cost of the single {@code position}
     */
    Num calculate(Position position);

    /**
     * @param price  the trade price per asset
     * @param amount the trade amount (i.e. the number of traded assets)
     * @return the trading cost for the traded {@code amount}
     */
    Num calculate(Num price, Num amount);

    /**
     * Returns the trading cost of a single native execution fill.
     *
     * <p>
     * The default preserves historic spot behavior by delegating to
     * {@link #calculate(Num, Num)} with the fill price and amount. A futures fill
     * settles in the contract settlement currency, which the spot formulation
     * cannot express, so models that support futures override this method; the
     * default refuses instead of silently treating contracts as base assets.
     * </p>
     *
     * @param fill the execution fill
     * @return the trading cost of {@code fill}
     * @throws UnsupportedOperationException when {@code fill} is a futures fill and
     *                                       this model does not override the method
     * @since 0.25.1
     */
    default Num calculate(TradeFill fill) {
        Objects.requireNonNull(fill, "fill");
        if (fill.futuresContract() != null) {
            throw new UnsupportedOperationException(
                    getClass().getName() + " cannot model futures fills; override calculate(TradeFill)");
        }
        return calculate(fill.price(), fill.amount());
    }

    /**
     * Returns the itemized fee components of a single native execution fill in the
     * contract settlement currency.
     *
     * <p>
     * The default wraps {@link #calculate(TradeFill)} into a single commission
     * component. Models that know the fee provenance or split charges override this
     * method.
     * </p>
     *
     * @param fill the futures execution fill
     * @return the fee components of {@code fill}
     * @throws IllegalArgumentException when {@code fill} is not a futures fill
     * @since 0.25.1
     */
    default List<TradeFee> calculateFees(TradeFill fill) {
        Objects.requireNonNull(fill, "fill");
        if (fill.futuresContract() == null) {
            throw new IllegalArgumentException("fee components are only defined for futures fills");
        }
        return List.of(TradeFee.builder()
                .type(TradeFee.Type.COMMISSION)
                .amount(calculate(fill))
                .currency(fill.futuresContract().settlementCurrency())
                .build());
    }

    /**
     * Evaluates if two models are equal.
     *
     * @param otherModel
     * @return true if {@code this} and {@code otherModel} are equal
     */
    boolean equals(CostModel otherModel);
}