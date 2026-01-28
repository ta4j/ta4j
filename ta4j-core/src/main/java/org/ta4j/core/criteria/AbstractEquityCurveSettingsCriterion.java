/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
 * Note that {@link EquityCurveMode#REALIZED} ignores open positions regardless of
 * the requested {@link OpenPositionHandling}. Use
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
