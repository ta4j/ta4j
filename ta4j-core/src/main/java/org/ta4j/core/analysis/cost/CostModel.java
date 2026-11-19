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
     * Calculates the cumulative cost at each index of an inclusive range; costs
     * before entry are zero.
     *
     * @param position   the position to evaluate
     * @param firstIndex the first index, inclusive
     * @param finalIndex the last index, inclusive
     * @return cumulative costs; element 0 is at {@code firstIndex}
     * @throws IllegalArgumentException if the range is negative, reversed or too
     *                                  long
     * @since 0.29.1
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