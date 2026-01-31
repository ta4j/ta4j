/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

/**
 * Gross loss criterion.
 */
public class GrossLossCriterion extends AbstractPnlCriterion {

    @Override
    protected Num calculatePosition(Position position) {
        return loss(grossPnL(position));
    }

}
