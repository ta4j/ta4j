/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
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
