/*
 * SPDX-License-Identifier: MIT
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
