/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.Objects;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;

/**
 * Shared base for criteria that require {@link EquityCurveMode} and
 * {@link OpenPositionHandling} configuration.
 *
 * <p>
 * Note that {@link EquityCurveMode#REALIZED} ignores open positions regardless
 * of the requested {@link OpenPositionHandling}. Use
 * {@link EquityCurveMode#MARK_TO_MARKET} when you want
 * {@link OpenPositionHandling#MARK_TO_MARKET} to reflect open trades.
 *
 * @since 0.22.2
 */
public abstract class AbstractEquityCurveSettingsCriterion extends AbstractAnalysisCriterion {

    protected final EquityCurveMode equityCurveMode;
    protected final OpenPositionHandling openPositionHandling;

    protected AbstractEquityCurveSettingsCriterion() {
        this(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    protected AbstractEquityCurveSettingsCriterion(EquityCurveMode equityCurveMode) {
        this(equityCurveMode, OpenPositionHandling.MARK_TO_MARKET);
    }

    protected AbstractEquityCurveSettingsCriterion(OpenPositionHandling openPositionHandling) {
        this(EquityCurveMode.MARK_TO_MARKET, openPositionHandling);
    }

    protected AbstractEquityCurveSettingsCriterion(EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        this.openPositionHandling = Objects.requireNonNull(openPositionHandling);
    }
}
