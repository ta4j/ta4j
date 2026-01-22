/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core.criteria.pnl;

/** Net profit/loss ratio criterion. */
public class NetProfitLossRatioCriterion extends AbstractProfitLossRatioCriterion {

    public NetProfitLossRatioCriterion() {
        super(new NetAverageProfitCriterion(), new NetAverageLossCriterion());
    }
}
