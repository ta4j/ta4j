/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.Position;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.Num;

/** Gross profit and loss percentage criterion. */
public class GrossProfitLossPercentageCriterion extends AbstractProfitLossPercentageCriterion {

    public GrossProfitLossPercentageCriterion() {
        super();
    }

    public GrossProfitLossPercentageCriterion(ReturnRepresentation representation) {
        super(representation);
    }

    @Override
    protected Num profit(Position position) {
        return position.getGrossProfit();
    }
}
