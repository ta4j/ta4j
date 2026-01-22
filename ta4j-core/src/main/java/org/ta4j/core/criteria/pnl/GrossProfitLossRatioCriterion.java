/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core.criteria.pnl;

/** Gross profit/loss ratio criterion. */
public class GrossProfitLossRatioCriterion extends AbstractProfitLossRatioCriterion {

    public GrossProfitLossRatioCriterion() {
        super(new GrossAverageProfitCriterion(), new GrossAverageLossCriterion());
    }
}
