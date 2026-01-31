/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

/** Gross profit/loss ratio criterion. */
public class GrossProfitLossRatioCriterion extends AbstractProfitLossRatioCriterion {

    public GrossProfitLossRatioCriterion() {
        super(new GrossAverageProfitCriterion(), new GrossAverageLossCriterion());
    }
}
