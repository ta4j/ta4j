package org.ta4j.core.rules;

import org.ta4j.core.CustomPositionData;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

/**
 * Stop gain rule.
 *
 * Satisfied when the underlying indicator price reaches the take profit price carried by
 * position's custom data object.
 *
 * See {@link CustomPositionData}.
 */
public class FixedPriceStopGainRule extends AbstractRule {

    /** the indicator whose value will be compared position's take profit price. */
    private final AbstractIndicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator the price indicator whose value will be used to be compared with position's take profit price.
     */
    public FixedPriceStopGainRule(AbstractIndicator<Num> indicator) {
        this.indicator = indicator;
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history or no position opened, no loss
        if (tradingRecord != null) {
            Position currentPosition = tradingRecord.getCurrentPosition();
            CustomPositionData customPositionData = currentPosition.getCustomPositionData();

            if (currentPosition.isOpened()
                    && customPositionData != null
                    && customPositionData.getTakeProfitPrice() != null) {

                Num takeProfitPrice = customPositionData.getTakeProfitPrice();
                Num currentPrice = indicator.getValue(index);

                if (currentPosition.getEntry().isBuy()) {
                    satisfied = isBuyGainSatisfied(takeProfitPrice, currentPrice);
                } else {
                    satisfied = isSellGainSatisfied(takeProfitPrice, currentPrice);
                }
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private boolean isSellGainSatisfied(Num takeProfitPrice, Num currentPrice) {
        return currentPrice.isLessThanOrEqual(takeProfitPrice);
    }

    private boolean isBuyGainSatisfied(Num takeProfitPrice,  Num currentPrice) {
        return currentPrice.isGreaterThanOrEqual(takeProfitPrice);
    }
}
