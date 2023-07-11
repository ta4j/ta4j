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
package org.ta4j.core.indicators.bollinger;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

/**
 * %B indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_perce">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_perce</a>
 */
public class PercentBIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final BollingerBandsUpperIndicator bbu;
    private final BollingerBandsLowerIndicator bbl;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator} (usually {@code ClosePriceIndicator})
     * @param barCount  the time frame
     * @param k         the K multiplier (usually 2.0)
     */
    public PercentBIndicator(Indicator<Num> indicator, int barCount, double k) {
        super(indicator);
        this.indicator = indicator;
        BollingerBandsMiddleIndicator bbm = new BollingerBandsMiddleIndicator(new SMAIndicator(indicator, barCount));
        StandardDeviationIndicator sd = new StandardDeviationIndicator(indicator, barCount);
        this.bbu = new BollingerBandsUpperIndicator(bbm, sd, numOf(k));
        this.bbl = new BollingerBandsLowerIndicator(bbm, sd, numOf(k));
    }

    @Override
    protected Num calculate(int index) {
        Num value = indicator.getValue(index);
        Num upValue = bbu.getValue(index);
        Num lowValue = bbl.getValue(index);
        return value.minus(lowValue).dividedBy(upValue.minus(lowValue));
    }

    @Override
    public int getUnstableBars() {
        return 0;
    }
}
