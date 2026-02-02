/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.Position;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.Num;

/** Net profit and loss percentage criterion. */
public class NetProfitLossPercentageCriterion extends AbstractProfitLossPercentageCriterion {

    public NetProfitLossPercentageCriterion() {
        super();
    }

    public NetProfitLossPercentageCriterion(ReturnRepresentation representation) {
        super(representation);
    }

    @Override
    protected Num profit(Position position) {
        return position.getProfit();
    }
}
