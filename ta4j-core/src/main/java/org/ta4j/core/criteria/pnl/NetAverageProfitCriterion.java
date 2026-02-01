/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.criteria.NumberOfWinningPositionsCriterion;

/**
 * Average net profit criterion.
 */
public class NetAverageProfitCriterion extends AbstractAveragePnlCriterion {

    public NetAverageProfitCriterion() {
        super(new NetProfitCriterion(), new NumberOfWinningPositionsCriterion());
    }

}
