package org.ta4j.core.rules;

import org.ta4j.core.CustomPositionData;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

/**
 * Stop loss rule.
 *
 * Satisfied when the underlying indicator price reaches the stop loss price carried by
 * position's custom data object.
 *
 * See {@link CustomPositionData}.
 */
public class FixedPriceStopLossRule extends AbstractRule {

    /** the indicator whose value will be compared position's stop loss price. */
    private final AbstractIndicator<Num> indicator;

    /**
     * Constructor.
     *
     * @param indicator the price indicator whose value will be used to be compared with position's stop loss price.
     */
    public FixedPriceStopLossRule(AbstractIndicator<Num> indicator) {
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
                    && customPositionData.getStopLossPrice() != null) {

                Num stopLossValue =
                        currentPosition.getCustomPositionData().getStopLossPrice();

                Num currentPrice = indicator.getValue(index);

                if (currentPosition.getEntry().isBuy()) {
                    satisfied = isBuyStopSatisfied(stopLossValue, currentPrice);
                } else {
                    satisfied = isSellStopSatisfied(stopLossValue, currentPrice);
                }
            }
        }

        traceIsSatisfied(index, satisfied);
        return satisfied;
    }

    private boolean isSellStopSatisfied(Num stopLossValue, Num currentPrice) {
        return currentPrice.isGreaterThanOrEqual(stopLossValue);
    }

    private boolean isBuyStopSatisfied(Num stopLossValue, Num currentPrice) {
        return currentPrice.isLessThanOrEqual(stopLossValue);
    }
}
