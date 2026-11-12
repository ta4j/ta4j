/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import java.util.Arrays;
import java.util.Objects;

import org.ta4j.core.Position;
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
     * Calculates cumulative costs from entry through each index in an inclusive
     * range. The result at offset zero corresponds to {@code firstIndex}; values
     * before entry are zero. A closed position is treated as open before its exit
     * and as closed at and after its exit, so models that use the actual exit for
     * closed positions still respect earlier observation indices. For a position
     * without an entry, the indexed result at {@code firstIndex} is repeated.
     *
     * <p>
     * This default implementation calls the indexed overload for each applicable
     * index. Models can override it with a more efficient calculation.
     *
     * @param position   the position to evaluate
     * @param firstIndex the first observation index, inclusive
     * @param finalIndex the last observation index, inclusive
     * @return cumulative costs, ordered by observation index
     * @throws NullPointerException     if {@code position} is null
     * @throws IllegalArgumentException if {@code firstIndex} is negative,
     *                                  {@code finalIndex} precedes it, or the
     *                                  inclusive range length exceeds
     *                                  {@link Integer#MAX_VALUE}
     * @since 0.28.1
     */
    default Num[] calculate(Position position, int firstIndex, int finalIndex) {
        Objects.requireNonNull(position, "position");
        long length = (long) finalIndex - firstIndex + 1;
        if (firstIndex < 0 || finalIndex < firstIndex || length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid index range");
        }
        Num[] costs = new Num[(int) length];
        if (position.getEntry() == null) {
            Arrays.fill(costs, calculate(position, firstIndex));
            return costs;
        }
        int entryIndex = position.getEntry().getIndex();
        Position openPosition = position;
        if (position.isClosed() && firstIndex < position.getExit().getIndex()) {
            openPosition = new Position(position.getEntry(), position.getTransactionCostModel(),
                    position.getHoldingCostModel());
        }
        Num zero = position.getEntry().getPricePerAsset().getNumFactory().zero();
        for (int offset = 0; offset < costs.length; offset++) {
            int index = firstIndex + offset;
            if (index < entryIndex) {
                costs[offset] = zero;
            } else {
                Position observed = position.isClosed() && index >= position.getExit().getIndex() ? position
                        : openPosition;
                costs[offset] = calculate(observed, index);
            }
        }
        return costs;
    }

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
     * Evaluates if two models are equal.
     *
     * @param otherModel
     * @return true if {@code this} and {@code otherModel} are equal
     */
    boolean equals(CostModel otherModel);
}