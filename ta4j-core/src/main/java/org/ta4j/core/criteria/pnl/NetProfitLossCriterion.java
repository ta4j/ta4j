/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

/**
 * Net profit and loss criterion.
 */
public class NetProfitLossCriterion extends AbstractPnlCriterion {

    @Override
    protected Num calculatePosition(Position position) {
        return netPnL(position);
    }

}
