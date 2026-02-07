/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.candles.RealBodyIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.indicators.numeric.UnaryOperationIndicator;
import org.ta4j.core.num.Num;

/**
 * IntraDay Momentum Index Indicator.
 * <p>
 * The IntraDay Momentum Index is a measure of the security's strength of trend.
 * It uses the difference between the open and close prices of each bar to
 * calculate momentum. For more information, check: <a href=
 * "https://library.tradingtechnologies.com/trade/chrt-ti-intraday-momentum-index.html"></a>
 * </p>
 */
public class IntraDayMomentumIndexIndicator extends CachedIndicator<Num> {
    private final SMAIndicator averageCloseOpenDiff;
    private final SMAIndicator averageOpenCloseDiff;

    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public IntraDayMomentumIndexIndicator(final BarSeries series, final int barCount) {
        super(series);

        // Calculate the real body of the bars (close - open)
        final var realBody = new RealBodyIndicator(series);

        // Transform the real body into close-open and open-close differences
        final var closeOpenDiff = BinaryOperationIndicator.max(realBody, 0);
        final var openCloseDiff = UnaryOperationIndicator.abs(BinaryOperationIndicator.min(realBody, 0));

        // Calculate the SMA of the differences
        this.averageCloseOpenDiff = new SMAIndicator(closeOpenDiff, barCount);
        this.averageOpenCloseDiff = new SMAIndicator(openCloseDiff, barCount);

        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        // Return NaN for unstable bars
        if (index < this.getCountOfUnstableBars()) {
            return NaN;
        }

        // Calculate the average values of the differences
        Num avgCloseOpenValue = this.averageCloseOpenDiff.getValue(index);
        Num avgOpenCloseValue = this.averageOpenCloseDiff.getValue(index);

        // Calculate the momentum index
        return avgCloseOpenValue.dividedBy((avgCloseOpenValue.plus(avgOpenCloseValue)))
                .multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(averageCloseOpenDiff.getCountOfUnstableBars(), averageOpenCloseDiff.getCountOfUnstableBars());
    }
}
