package org.ta4j.sparx.rules;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AbstractRule;

public class CrossedUpHighestPriceRule extends AbstractRule {

    private final AbstractIndicator<Num> priceIndicator;
    private final HighestValueIndicator highestValueIndicator;
    private final int barCount;

    public CrossedUpHighestPriceRule(AbstractIndicator<Num> priceIndicator, int barCount) {
        this.priceIndicator = priceIndicator;
        this.barCount = barCount;
        this.highestValueIndicator = new HighestValueIndicator(priceIndicator, barCount);
    }

    @Override
    public boolean isSatisfied(int i, TradingRecord tradingRecord) {
        if (i != 0 && i >= barCount
                && !priceIndicator.getValue(i).isNaN()
                && !highestValueIndicator.getValue(i - 1).isNaN()) {
            Num lastClosingPrice = priceIndicator.getValue(i);
            Num highestClosingPriceFromYesterday = highestValueIndicator.getValue(i - 1);
            return lastClosingPrice.isGreaterThan(highestClosingPriceFromYesterday);
        }
        return false;
    }
}
