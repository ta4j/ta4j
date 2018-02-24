/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.volume;


import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.CloseLocationValueIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Chaikin Money Flow (CMF) indicator.
 * <p></p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_money_flow_cmf">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_money_flow_cmf"</a>
 * @see <a href="http://www.fmlabs.com/reference/default.htm?url=ChaikinMoneyFlow.htm">
 *     http://www.fmlabs.com/reference/default.htm?url=ChaikinMoneyFlow.htm</a>
 */
public class ChaikinMoneyFlowIndicator extends CachedIndicator<Num> {

    private TimeSeries series;

    private CloseLocationValueIndicator clvIndicator;

    private VolumeIndicator volumeIndicator;

    private int barCount;

    public ChaikinMoneyFlowIndicator(TimeSeries series, int barCount) {
        super(series);
        this.series = series;
        this.barCount = barCount;
        this.clvIndicator = new CloseLocationValueIndicator(series);
        this.volumeIndicator = new VolumeIndicator(series, barCount);
    }

    @Override
    protected Num calculate(int index) {
        int startIndex = Math.max(0, index - barCount + 1);
        Num sumOfMoneyFlowVolume = numOf(0);
        for (int i = startIndex; i <= index; i++) {
            sumOfMoneyFlowVolume = sumOfMoneyFlowVolume.plus(getMoneyFlowVolume(i));
        }
        Num sumOfVolume = volumeIndicator.getValue(index);

        return sumOfMoneyFlowVolume.dividedBy(sumOfVolume);
    }

    /**
     * @param index the bar index
     * @return the money flow volume for the i-th period/bar
     */
    private Num getMoneyFlowVolume(int index) {
        return clvIndicator.getValue(index).multipliedBy(series.getBar(index).getVolume());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
