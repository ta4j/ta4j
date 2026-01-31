/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.CloseLocationValueIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Chaikin Money Flow (CMF) indicator.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_money_flow_cmf">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:chaikin_money_flow_cmf"</a>
 * @see <a href=
 *      "http://www.fmlabs.com/reference/default.htm?url=ChaikinMoneyFlow.htm">
 *      http://www.fmlabs.com/reference/default.htm?url=ChaikinMoneyFlow.htm</a>
 */
public class ChaikinMoneyFlowIndicator extends CachedIndicator<Num> {

    private final CloseLocationValueIndicator clvIndicator;
    private final VolumeIndicator volumeIndicator;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public ChaikinMoneyFlowIndicator(BarSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
        this.clvIndicator = new CloseLocationValueIndicator(series);
        this.volumeIndicator = new VolumeIndicator(series, barCount);
    }

    @Override
    protected Num calculate(int index) {
        int startIndex = Math.max(0, index - barCount + 1);
        Num sumOfMoneyFlowVolume = getBarSeries().numFactory().zero();
        for (int i = startIndex; i <= index; i++) {
            sumOfMoneyFlowVolume = sumOfMoneyFlowVolume.plus(getMoneyFlowVolume(i));
        }
        Num sumOfVolume = volumeIndicator.getValue(index);

        return sumOfMoneyFlowVolume.dividedBy(sumOfVolume);
    }

    @Override
    public int getCountOfUnstableBars() {
        return barCount;
    }

    /**
     * @param index the bar index
     * @return the money flow volume for the i-th period/bar
     */
    private Num getMoneyFlowVolume(int index) {
        return clvIndicator.getValue(index).multipliedBy(getBarSeries().getBar(index).getVolume());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
