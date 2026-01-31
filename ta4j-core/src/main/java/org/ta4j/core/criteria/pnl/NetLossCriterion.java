/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

/**
 * Net loss criterion.
 */
public class NetLossCriterion extends AbstractPnlCriterion {

    @Override
    protected Num calculatePosition(Position position) {
        return loss(netPnL(position));
    }

}
