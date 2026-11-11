/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.DecimalNum;
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
    private final transient RunningTotalIndicator positiveTotal;
    private final transient RunningTotalIndicator negativeTotal;
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
        this.positiveTotal = new RunningTotalIndicator(moneyFlow(series, true), barCount);
        this.negativeTotal = new RunningTotalIndicator(moneyFlow(series, false), barCount);
    }

    @Override
    protected Num calculate(int index) {
        // Return NaN for unstable bars
        if (index < this.getCountOfUnstableBars()) {
            return NaN;
        }

        final var numFactory = getBarSeries().numFactory();
        Num sumOfPositiveMoneyFlowVolume;
        Num sumOfNegativeMoneyFlowVolume;

        // Start from the first bar or the start of the window
        int startIndex = Math.max(0, index - barCount + 1);
        if (barCount > 1 && startIndex >= getBarSeries().getBeginIndex()
                && (!(numFactory.zero() instanceof DecimalNum decimal)
                        || decimal.getMathContext().getPrecision() >= 16)) {
            sumOfPositiveMoneyFlowVolume = positiveTotal.getValue(index);
            sumOfNegativeMoneyFlowVolume = negativeTotal.getValue(index);
        } else {
            sumOfPositiveMoneyFlowVolume = numFactory.zero();
            sumOfNegativeMoneyFlowVolume = numFactory.zero();
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
        }

        // Calculate money flow ratio and index
        Num moneyFlowRatio = sumOfPositiveMoneyFlowVolume.max(numFactory.one())
                .dividedBy(sumOfNegativeMoneyFlowVolume.max(numFactory.one()));

        // Calculate MFI. max function is used to prevent division by zero.
        return numFactory.hundred().minus((numFactory.hundred().dividedBy((numFactory.one().plus(moneyFlowRatio)))));
    }

    private Indicator<Num> moneyFlow(BarSeries series, boolean positive) {
        return new CachedIndicator<Num>(series) {
            @Override
            protected Num calculate(int index) {
                Num current = typicalPrice.getValue(index);
                Num previous = previousTypicalPrice.getValue(index);
                Num raw = current.multipliedBy(volume.getValue(index));
                boolean matches = positive ? current.isGreaterThan(previous) : current.isLessThan(previous);
                return matches ? raw : getBarSeries().numFactory().zero();
            }

            @Override
            public int getCountOfUnstableBars() {
                return previousTypicalPrice.getCountOfUnstableBars();
            }
        };
    }

    @Override
    public int getCountOfUnstableBars() {
        return previousTypicalPrice.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
