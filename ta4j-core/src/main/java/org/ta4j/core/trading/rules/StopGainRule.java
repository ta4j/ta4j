package org.ta4j.core.trading.rules;

import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * A stop-gain rule.
 * </p>
 * Satisfied when the close price reaches the gain threshold.
 */
public class StopGainRule extends AbstractRule {

    /**
     * The close price indicator
     */
    private final ClosePriceIndicator closePrice;

    /**
     * The gain ratio threshold (e.g. 1.03 for 3%)
     */
    private final Num gainRatioThreshold;


    /**
     * Constructor.
     *
     * @param closePrice     the close price indicator
     * @param gainPercentage the gain percentage
     */
    public StopGainRule(ClosePriceIndicator closePrice, Number gainPercentage) {
        this(closePrice, closePrice.numOf(gainPercentage));
    }

    /**
     * Constructor.
     *
     * @param closePrice     the close price indicator
     * @param gainPercentage the gain percentage
     */
    public StopGainRule(ClosePriceIndicator closePrice, Num gainPercentage) {
        this.closePrice = closePrice;
        Num HUNDRED = closePrice.numOf(100);
        this.gainRatioThreshold = HUNDRED.plus(gainPercentage).dividedBy(HUNDRED);
    }


    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history or no trade opened, no gain
        if (tradingRecord != null) {
            Trade currentTrade = tradingRecord.getCurrentTrade();
            if (currentTrade.isOpened()) {
                Num entryPrice = currentTrade.getEntry().getPrice();
                Num currentPrice = closePrice.getValue(index);
                Num threshold = entryPrice.multipliedBy(gainRatioThreshold);
                if (currentTrade.getEntry().isBuy()) {
                    satisfied = currentPrice.isGreaterThanOrEqual(threshold);
                } else {
                    satisfied = currentPrice.isLessThanOrEqual(threshold);
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
