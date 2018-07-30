package org.ta4j.core.analysis.criteria;

import org.ta4j.core.Order;
import org.ta4j.core.PriceType;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

public abstract class AbstractBacktestingCriterion extends AbstractAnalysisCriterion {

    protected PriceType priceType;

    public AbstractBacktestingCriterion(PriceType priceType) {
        this.priceType = priceType;
    }

    protected Num getPrice(TimeSeries series, Order order) {
        if (priceType == PriceType.OPEN) {
            return series.getBar(order.getIndex()).getOpenPrice();
        }
        if (priceType == PriceType.HIGH) {
            return series.getBar(order.getIndex()).getMaxPrice();
        }
        if (priceType == PriceType.LOW) {
            return series.getBar(order.getIndex()).getMinPrice();
        }
        return series.getBar(order.getIndex()).getClosePrice();
    }
}
