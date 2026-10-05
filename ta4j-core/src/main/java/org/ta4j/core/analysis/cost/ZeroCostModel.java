/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

/**
 * With this cost model there are no trading costs.
 */
public class ZeroCostModel extends FixedTransactionCostModel {

    private static final double ZERO_FEE_PER_TRADE = 0.0;

    /**
     * Constructor with {@code feePerTrade = 0}.
     *
     * @see FixedTransactionCostModel
     */
    public ZeroCostModel() {
        super(ZERO_FEE_PER_TRADE);
    }

    /**
     * Returns zero regardless of recorded execution fees, which belong to the
     * transaction-cost leg and must not become an additional holding charge.
     *
     * @since 0.26.1
     */
    @Override
    public Num calculate(Position position, int currentIndex) {
        return position.getEntry().getPricePerAsset().getNumFactory().zero();
    }

    /**
     * Returns zero holding or modeled transaction cost for the position.
     *
     * @since 0.26.1
     */
    @Override
    public Num calculate(Position position) {
        return calculate(position, 0);
    }
}
