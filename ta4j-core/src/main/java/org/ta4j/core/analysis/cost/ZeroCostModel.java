/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core.analysis.cost;

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

}
