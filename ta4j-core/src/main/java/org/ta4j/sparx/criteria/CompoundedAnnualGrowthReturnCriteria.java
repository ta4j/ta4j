package org.ta4j.sparx.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.pnl.ReturnCriterion;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

public class CompoundedAnnualGrowthReturnCriteria extends AbstractAnalysisCriterion {
    @Override
    public Num calculate(BarSeries series, Position position) {
        Num grossReturn = new ReturnCriterion().calculate(series, position);
        double cagr = calculateCagr(series, grossReturn);
        return DoubleNum.valueOf(cagr);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num grossReturn = new ReturnCriterion().calculate(series, tradingRecord);
        double cagr = calculateCagr(series, grossReturn);
        return DoubleNum.valueOf(cagr);
    }

    private double calculateCagr(BarSeries series, Num grossReturn) {
        int holdingPeriod = getHoldingPeriod(series);
        return Math.pow(grossReturn.doubleValue(), (1.0 / holdingPeriod)) - 1;
    }

    private int getHoldingPeriod(BarSeries series) {
        int startingYear = series.getFirstBar().getEndTime().getYear();
        int endingYear = series.getLastBar().getEndTime().getYear();
        return endingYear - startingYear;
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
