/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.num.Num;
import org.ta4j.core.Position;

/**
 * Net profit criterion.
 */
public class NetProfitCriterion extends AbstractPnlCriterion {

    @Override
    protected Num calculatePosition(Position position) {
        return profit(netPnL(position));
    }

}
