package org.ta4j.sparx.rules;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AbstractRule;

public class CrossedDownLowestPriceRule extends AbstractRule {

    private final AbstractIndicator<Num> priceIndicator;
    private final int barCount;
    private final LowestValueIndicator lowestValueIndicator;

    public CrossedDownLowestPriceRule(AbstractIndicator<Num> priceIndicator, int barCount) {
        this.priceIndicator = priceIndicator;
        this.barCount = barCount;
        this.lowestValueIndicator = new LowestValueIndicator(priceIndicator, barCount);
    }

    @Override
    public boolean isSatisfied(int i, TradingRecord tradingRecord) {
        if (i != 0 && i >= barCount && !priceIndicator.getValue(i).isNaN() && !lowestValueIndicator.getValue(i - 1).isNaN()) {
            Num lastPrice = priceIndicator.getValue(i);
            Num lowestPrice = lowestValueIndicator.getValue(i - 1);
            return lastPrice.isLessThan(lowestPrice);
        }
        return false;
    }
}
