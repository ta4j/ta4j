/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

/** Net profit/loss ratio criterion. */
public class NetProfitLossRatioCriterion extends AbstractProfitLossRatioCriterion {

    public NetProfitLossRatioCriterion() {
        super(new NetAverageProfitCriterion(), new NetAverageLossCriterion());
    }
}
