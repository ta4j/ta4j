/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Money Flow Index (MFI) indicator.
 * <p>
 * MFI is a volume-weighted version of RSI that shows shifts in buying and
 * selling pressure. It uses both price and volume to measure buying and selling
 * pressure. For more information, @see: <a href=
 * "https://school.stockcharts.com/doku.php?id=technical_indicators:money_flow_index_mfi"></a>
 * </p>
 */
public class MoneyFlowIndexIndicator extends CachedIndicator<Num> {

    private final PreviousValueIndicator previousTypicalPrice;
    private final TypicalPriceIndicator typicalPrice;
    private final VolumeIndicator volume;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public MoneyFlowIndexIndicator(BarSeries series, int barCount) {
        super(series);

        // Calculating typical price and volume for the series
        this.typicalPrice = new TypicalPriceIndicator(series);
        this.previousTypicalPrice = new PreviousValueIndicator(this.typicalPrice);
        this.volume = new VolumeIndicator(series);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        // Return NaN for unstable bars
        if (index < this.getCountOfUnstableBars()) {
            return NaN;
        }

        final var numFactory = getBarSeries().numFactory();
        Num sumOfPositiveMoneyFlowVolume = numFactory.zero();
        Num sumOfNegativeMoneyFlowVolume = numFactory.zero();

        // Start from the first bar or the start of the window
        int startIndex = Math.max(0, index - barCount + 1);
        for (int i = startIndex; i <= index; i++) {
            Num currentTypicalPriceValue = typicalPrice.getValue(i);
            Num previousTypicalPriceValue = previousTypicalPrice.getValue(i);
            Num currentVolume = volume.getValue(i);

            Num rawMoneyFlowValue = currentTypicalPriceValue.multipliedBy(currentVolume);

            // If the typical price is increasing, we add to the positive flow
            if (currentTypicalPriceValue.isGreaterThan(previousTypicalPriceValue)) {
                sumOfPositiveMoneyFlowVolume = sumOfPositiveMoneyFlowVolume.plus(rawMoneyFlowValue);
            }
            // If the typical price is decreasing, we add to the negative flow
            else if (currentTypicalPriceValue.isLessThan(previousTypicalPriceValue)) {
                sumOfNegativeMoneyFlowVolume = sumOfNegativeMoneyFlowVolume.plus(rawMoneyFlowValue);
            }
        }

        // Calculate money flow ratio and index
        Num moneyFlowRatio = sumOfPositiveMoneyFlowVolume.max(numFactory.one())
                .dividedBy(sumOfNegativeMoneyFlowVolume.max(numFactory.one()));

        // Calculate MFI. max function is used to prevent division by zero.
        return numFactory.hundred().minus((numFactory.hundred().dividedBy((numFactory.one().plus(moneyFlowRatio)))));
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
