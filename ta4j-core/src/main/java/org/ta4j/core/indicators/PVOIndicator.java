package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.VolumeIndicator;

public class PVOIndicator extends PPOIndicator {

   /**
    * Percentage Volume Oscillator (PVO):
    * ((12-day EMA of Volume - 26-day EMA of Volume)/26-day EMA of Volume) x 100
    *
    * @see <a href=
    *      "https://school.stockcharts.com/doku.php?id=technical_indicators:percentage_volume_oscillator_pvo">
    *       https://school.stockcharts.com/doku.php?id=technical_indicators:percentage_volume_oscillator_pvo
    *  </a>
    */

    public PVOIndicator(BarSeries series) {
        super(new VolumeIndicator(series));
    }

    public PVOIndicator(BarSeries series, int volumeBarCount) {
        super(new VolumeIndicator(series, volumeBarCount));
    }

    public PVOIndicator(BarSeries series, int shortBarCount, int longBarCount) {
        super(new VolumeIndicator(series), shortBarCount, longBarCount);
    }

    public PVOIndicator(BarSeries series, int volumeBarCount, int shortBarCount, int longBarCount) {
        super(new VolumeIndicator(series, volumeBarCount), shortBarCount, longBarCount);
    }

}
