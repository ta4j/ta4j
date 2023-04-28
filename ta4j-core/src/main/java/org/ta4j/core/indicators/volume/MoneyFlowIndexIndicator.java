/**
 * The MIT License (MIT)
 * <p>
 * Copyright (c) 2017-2023 Ta4j Organization & respective
 * authors (see AUTHORS)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
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
 * https://school.stockcharts.com/doku.php?id=technical_indicators:money_flow_index_mfi
 */
public class MoneyFlowIndexIndicator extends CachedIndicator<Num> {

    private final PreviousValueIndicator previousTypicalPrice;
    private final TypicalPriceIndicator typicalPrice;
    private final VolumeIndicator volume;
    private final int barCount;

    public MoneyFlowIndexIndicator(BarSeries series, int barCount) {
        super(series);

        this.typicalPrice = new TypicalPriceIndicator(series);
        this.previousTypicalPrice = new PreviousValueIndicator(this.typicalPrice);
        this.volume = new VolumeIndicator(series);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        if (index < this.getUnstableBars()) {
            return NaN;
        }

        Num sumOfPositiveMoneyFlowVolume = zero();
        Num sumOfNegativeMoneyFlowVolume = zero();

        int startIndex = Math.max(0, index - barCount + 1);
        for (int i = startIndex; i <= index; i++) {
            Num currentTypicalPriceValue = typicalPrice.getValue(i);
            Num previousTypicalPriceValue = previousTypicalPrice.getValue(i);
            Num currentVolume = volume.getValue(i);

            Num rawMoneyFlowValue = currentTypicalPriceValue.multipliedBy(currentVolume);

            if (currentTypicalPriceValue.isGreaterThan(previousTypicalPriceValue)) {
                sumOfPositiveMoneyFlowVolume = sumOfPositiveMoneyFlowVolume.plus(rawMoneyFlowValue);
            } else if (currentTypicalPriceValue.isLessThan(previousTypicalPriceValue)) {
                sumOfNegativeMoneyFlowVolume = sumOfNegativeMoneyFlowVolume.plus(rawMoneyFlowValue);
            }
        }

        Num moneyFlowRatio = sumOfPositiveMoneyFlowVolume.max(one()).dividedBy(sumOfNegativeMoneyFlowVolume.max(one()));
        Num moneyFlowIndex = hundred().minus((hundred().dividedBy((one().plus(moneyFlowRatio)))));

        return moneyFlowIndex;
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
