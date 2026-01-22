/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.criteria.NumberOfLosingPositionsCriterion;

/**
 * Average gross loss criterion.
 */
public class GrossAverageLossCriterion extends AbstractAveragePnlCriterion {

    public GrossAverageLossCriterion() {
        super(new GrossLossCriterion(), new NumberOfLosingPositionsCriterion());
    }
}
