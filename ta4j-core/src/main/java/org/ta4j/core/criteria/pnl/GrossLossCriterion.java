/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
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
