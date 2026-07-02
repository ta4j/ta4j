/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.AnalysisContext;
import org.ta4j.core.analysis.AnalysisWindow;
import org.ta4j.core.num.Num;
import org.ta4j.core.utils.DeprecationNotifier;

/**
 * @deprecated This class was moved to
 *             {@link org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion}
 *             and is scheduled for removal in 0.24.0.
 */
@Deprecated(since = "0.19", forRemoval = true)
public class MaximumDrawdownCriterion extends AbstractEquityCurveSettingsCriterion {

    private final org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion delegate;

    public MaximumDrawdownCriterion() {
        super();
        this.delegate = new org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion();
        DeprecationNotifier.warnOnce(MaximumDrawdownCriterion.class,
                "org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion", "0.24.0");
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return delegate.calculate(series, position);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        return delegate.calculate(series, tradingRecord);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord, AnalysisWindow window,
            AnalysisContext context) {
        return delegate.calculate(series, tradingRecord, window, context);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return delegate.betterThan(criterionValue1, criterionValue2);
    }
}
