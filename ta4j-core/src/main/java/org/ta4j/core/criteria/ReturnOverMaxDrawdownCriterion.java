/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import org.ta4j.core.utils.DeprecationNotifier;

/**
 * @deprecated This class was moved to
 *             {@link org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion}
 *             and is scheduled for removal in 0.24.0.
 */
@Deprecated(since = "0.19", forRemoval = true)
public class ReturnOverMaxDrawdownCriterion extends AbstractEquityCurveSettingsCriterion {

    private final org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion delegate;

    public ReturnOverMaxDrawdownCriterion() {
        super();
        this.delegate = new org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion();
        DeprecationNotifier.warnOnce(ReturnOverMaxDrawdownCriterion.class,
                "org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion", "0.24.0");
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
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return delegate.getReturnRepresentation();
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return delegate.betterThan(criterionValue1, criterionValue2);
    }
}
