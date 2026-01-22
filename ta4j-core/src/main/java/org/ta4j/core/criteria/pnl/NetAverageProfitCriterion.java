/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.criteria.NumberOfWinningPositionsCriterion;

/**
 * Average net profit criterion.
 */
public class NetAverageProfitCriterion extends AbstractAveragePnlCriterion {

    public NetAverageProfitCriterion() {
        super(new NetProfitCriterion(), new NumberOfWinningPositionsCriterion());
    }

}
