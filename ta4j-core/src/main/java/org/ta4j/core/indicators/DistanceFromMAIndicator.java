/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.ATMAIndicator;
import org.ta4j.core.indicators.averages.AbstractEMAIndicator;
import org.ta4j.core.indicators.averages.DMAIndicator;
import org.ta4j.core.indicators.averages.DoubleEMAIndicator;
import org.ta4j.core.indicators.averages.EDMAIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.HMAIndicator;
import org.ta4j.core.indicators.averages.JMAIndicator;
import org.ta4j.core.indicators.averages.KAMAIndicator;
import org.ta4j.core.indicators.averages.KiJunV2Indicator;
import org.ta4j.core.indicators.averages.LSMAIndicator;
import org.ta4j.core.indicators.averages.LWMAIndicator;
import org.ta4j.core.indicators.averages.MCGinleyMAIndicator;
import org.ta4j.core.indicators.averages.MMAIndicator;
import org.ta4j.core.indicators.averages.SGMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.averages.SMMAIndicator;
import org.ta4j.core.indicators.averages.TMAIndicator;
import org.ta4j.core.indicators.averages.TripleEMAIndicator;
import org.ta4j.core.indicators.averages.VIDYAIndicator;
import org.ta4j.core.indicators.averages.WMAIndicator;
import org.ta4j.core.indicators.averages.WildersMAIndicator;
import org.ta4j.core.indicators.averages.ZLEMAIndicator;
import org.ta4j.core.indicators.averages.VWMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Distance From Moving Average
 *
 * <pre>
 * (close - MA) / MA
 * </pre>
 *
 * @see <a href=
 *      "https://school.stockcharts.com/doku.php?id=technical_indicators:distance_from_ma">
 *      https://school.stockcharts.com/doku.php?id=technical_indicators:distance_from_ma
 *      </a>
 */
public class DistanceFromMAIndicator extends CachedIndicator<Num> {

    private static final Set<Class<?>> supportedMovingAverages = new HashSet<>(
            Arrays.asList(EMAIndicator.class, DoubleEMAIndicator.class, TripleEMAIndicator.class, SMAIndicator.class,
                    WMAIndicator.class, ZLEMAIndicator.class, HMAIndicator.class, KAMAIndicator.class,
                    LWMAIndicator.class, AbstractEMAIndicator.class, MMAIndicator.class, WildersMAIndicator.class,
                    DMAIndicator.class, EDMAIndicator.class, JMAIndicator.class, TMAIndicator.class,
                    ATMAIndicator.class, MCGinleyMAIndicator.class, SMMAIndicator.class, SGMAIndicator.class,
                    LSMAIndicator.class, KiJunV2Indicator.class, VIDYAIndicator.class, VWMAIndicator.class));

    private final Indicator<Num> movingAverage;

    /**
     * Constructor.
     *
     * @param series        the bar series
     * @param movingAverage the moving average
     */
    public DistanceFromMAIndicator(BarSeries series, Indicator<Num> movingAverage) {
        super(series);
        if (!(supportedMovingAverages.contains(movingAverage.getClass()))) {
            throw new IllegalArgumentException(
                    "Passed indicator must be a moving average based indicator. " + movingAverage.toString());
        }
        this.movingAverage = movingAverage;
    }

    @Override
    protected Num calculate(int index) {
        Bar currentBar = getBarSeries().getBar(index);
        Num closePrice = currentBar.getClosePrice();
        Num maValue = movingAverage.getValue(index);
        return (closePrice.minus(maValue)).dividedBy(maValue);
    }

    @Override
    public int getCountOfUnstableBars() {
        return movingAverage.getCountOfUnstableBars();
    }
}
