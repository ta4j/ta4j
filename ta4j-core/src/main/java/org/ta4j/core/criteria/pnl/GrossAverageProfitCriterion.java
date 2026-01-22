/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.criteria.NumberOfWinningPositionsCriterion;

/**
 * Average gross profit criterion.
 */
public class GrossAverageProfitCriterion extends AbstractAveragePnlCriterion {

    public GrossAverageProfitCriterion() {
        super(new GrossProfitCriterion(), new NumberOfWinningPositionsCriterion());
    }

}
