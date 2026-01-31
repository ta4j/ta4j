/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Base class for average profit and loss criteria.
 */
public abstract class AbstractAveragePnlCriterion extends AbstractAnalysisCriterion {

    private final AnalysisCriterion pnlCriterion;
    private final AnalysisCriterion positionsCriterion;

    protected AbstractAveragePnlCriterion(AnalysisCriterion pnlCriterion, AnalysisCriterion positionsCriterion) {
        this.pnlCriterion = pnlCriterion;
        this.positionsCriterion = positionsCriterion;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var zero = series.numFactory().zero();
        var count = positionsCriterion.calculate(series, position);
        if (count.isZero()) {
            return zero;
        }
        var pnl = pnlCriterion.calculate(series, position);
        if (pnl.isZero()) {
            return zero;
        }
        return pnl.dividedBy(count);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var zero = series.numFactory().zero();
        var count = positionsCriterion.calculate(series, tradingRecord);
        if (count.isZero()) {
            return zero;
        }
        var pnl = pnlCriterion.calculate(series, tradingRecord);
        if (pnl.isZero()) {
            return zero;
        }
        return pnl.dividedBy(count);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
