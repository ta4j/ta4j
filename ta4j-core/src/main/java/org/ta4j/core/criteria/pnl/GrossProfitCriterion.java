/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.num.Num;
import org.ta4j.core.Position;

/**
 * Gross profit criterion.
 */
public class GrossProfitCriterion extends AbstractPnlCriterion {

    @Override
    protected Num calculatePosition(Position position) {
        return profit(grossPnL(position));
    }

}
