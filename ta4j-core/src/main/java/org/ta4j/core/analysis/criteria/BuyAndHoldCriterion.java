package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Buy and hold criterion.
 * </p>
 * @see <a href="http://en.wikipedia.org/wiki/Buy_and_hold">http://en.wikipedia.org/wiki/Buy_and_hold</a>
 */
public class BuyAndHoldCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        return series.getBar(series.getEndIndex()).getClosePrice().dividedBy(series.getBar(series.getBeginIndex()).getClosePrice());
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        int entryIndex = trade.getEntry().getIndex();
        int exitIndex = trade.getExit().getIndex();

        if (trade.getEntry().isBuy()) {
            return series.getBar(exitIndex).getClosePrice().dividedBy(series.getBar(entryIndex).getClosePrice());
        } else {
            return series.getBar(entryIndex).getClosePrice().dividedBy(series.getBar(exitIndex).getClosePrice());
        }
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
