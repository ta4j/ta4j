/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

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
        if (index < this.getUnstableBars()) {
            return NaN;
        }

        Num sumOfPositiveMoneyFlowVolume = zero();
        Num sumOfNegativeMoneyFlowVolume = zero();

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
        Num moneyFlowRatio = sumOfPositiveMoneyFlowVolume.max(one()).dividedBy(sumOfNegativeMoneyFlowVolume.max(one()));

        // Calculate MFI. max function is used to prevent division by zero.
        return hundred().minus((hundred().dividedBy((one().plus(moneyFlowRatio)))));
    }

    @Override
    public int getUnstableBars() {
        return barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
