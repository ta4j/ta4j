/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

/**
 * Gross profit and loss criterion.
 */
public class GrossProfitLossCriterion extends AbstractPnlCriterion {

    @Override
    protected Num calculatePosition(Position position) {
        return grossPnL(position);
    }

}
