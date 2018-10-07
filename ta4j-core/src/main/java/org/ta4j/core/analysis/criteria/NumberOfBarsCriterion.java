package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

import java.util.Objects;

/**
 * Number of bars criterion.
 * </p>
 * Returns the number of bars during the provided trade(s).
 */
public class NumberOfBarsCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        Num nBars = series.numOf(0);
        for (Trade trade : tradingRecord.getTrades()) {
            nBars = nBars.plus(calculate(series, trade));
        }
        return nBars;
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        Objects.requireNonNull(series, "Series must be not null");

        return
            (series.numOf(1).plus(series.numOf(trade.getExit().getIndex())))
            .minus(series.numOf(trade.getEntry().getIndex()));
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }
}
