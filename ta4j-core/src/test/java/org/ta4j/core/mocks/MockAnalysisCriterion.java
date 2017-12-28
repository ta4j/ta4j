package org.ta4j.core.mocks;

import java.util.List;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;

public class MockAnalysisCriterion implements AnalysisCriterion {

    TimeSeries series;
    List<Decimal> values;

    public MockAnalysisCriterion(TimeSeries series, List<Decimal> values) {
        this.series = series;
        this.values = values;
    }
    @Override
    public double calculate(TimeSeries series, Trade trade) {
        return values.get(values.size() - 1).doubleValue();
    }

    @Override
    public double calculate(TimeSeries series, TradingRecord tradingRecord) {
        return values.get(values.size() - 1).doubleValue();
    }

    @Override
    public boolean betterThan(double criterionValue1, double criterionValue2) {
        return (criterionValue1 > criterionValue2);
    }

}
