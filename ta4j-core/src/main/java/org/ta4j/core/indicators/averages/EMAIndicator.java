/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Exponential moving average (EMA) indicator.
 *
 * <p>
 * The EMA gives more weight to recent prices and reacts more quickly to price
 * changes than a simple moving average. The smoothing factor is calculated as
 * {@code 2 / (barCount + 1)}.
 *
 * <p>
 * <strong>NaN Handling:</strong> This indicator provides robust NaN handling to
 * prevent contamination of future values. When a NaN value is detected in the
 * input indicator, it is returned immediately. If a previous EMA value is NaN,
 * the indicator resets to the current input value to allow graceful recovery.
 *
 * <p>
 * <strong>Unstable Period:</strong> The indicator returns
 * {@link org.ta4j.core.num.NaN#NaN NaN} for indices within the unstable period
 * (indices less than {@code beginIndex + barCount}). This ensures that the EMA
 * has sufficient data to produce reliable values. After the unstable period,
 * the first calculated value uses the current input value as the initial EMA
 * value.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/e/ema.asp">https://www.investopedia.com/terms/e/ema.asp</a>
 */
public class EMAIndicator extends AbstractEMAIndicator {

    /**
     * Constructor.
     *
     * @param indicator an indicator
     * @param barCount  the EMA time frame
     */
    public EMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator, barCount, (2.0 / (barCount + 1)));
    }

    @Override
    public int getCountOfUnstableBars() {
        return getBarCount();
    }
}
