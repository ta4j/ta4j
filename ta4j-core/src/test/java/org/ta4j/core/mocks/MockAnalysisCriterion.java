package org.ta4j.core.mocks;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

import java.util.List;

public class MockAnalysisCriterion implements AnalysisCriterion {

    TimeSeries series;
    List<Num> values;

    /**
     * Constructor.
     * 
     * @param series TimeSeries of the AnalysisCriterion
     * @param values AnalysisCriterion values
     */
    public MockAnalysisCriterion(TimeSeries series, List<Num> values) {
        this.series = series;
        this.values = values;
    }

    /**
     * Gets the final criterion value.
     * 
     * @param series TimeSeries is ignored
     * @param trade is ignored
     */
    public double calculate(TimeSeries series, Trade trade) {
        return values.get(values.size() - 1).doubleValue();
    }

    /**
     * Gets the final criterion value.
     * 
     * @param series TimeSeries is ignored
     * @param tradingRecord is ignored
     */
    public double calculate(TimeSeries series, TradingRecord tradingRecord) {
        return values.get(values.size() - 1).doubleValue();
    }

    /**
     * Compares two criterion values and returns true if first value is greater
     * than second value, false otherwise.
     * 
     * @param criterionValue1 first value
     * @param criterionValue2 second value
     * @return boolean indicating first value is greater than second value
     */
    public boolean betterThan(double criterionValue1, double criterionValue2) {
        return (criterionValue1 > criterionValue2);
    }

}
