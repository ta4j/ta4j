/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;

/**
 * Kairi Relative Index (KRI) indicator by LazyBear.
 *
 * @see <a href=
 *      "https://www.tradingview.com/script/xzRPAboO-Indicator-Kairi-Relative-Index-KRI/">TradingView</a>
 */
public class KRIIndicator extends AbstractIndicator<Num> {
    private final Indicator<Num> kriIndicator;
    private final int barCount;

    public KRIIndicator(BarSeries series, int barCount) {
        this(new ClosePriceIndicator(series), barCount);
    }

    public KRIIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator.getBarSeries());

        final var smaIndicator = new SMAIndicator(indicator, barCount);
        final var difference = BinaryOperationIndicator.difference(indicator, smaIndicator);
        final var quotient = BinaryOperationIndicator.quotient(difference, smaIndicator);
        this.kriIndicator = BinaryOperationIndicator.product(quotient, 100);
        this.barCount = barCount;
    }

    @Override
    public Num getValue(int index) {
        return kriIndicator.getValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }
}
