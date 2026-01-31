/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.VolumeIndicator;

/**
 * Percentage Volume Oscillator (PVO) indicator.
 *
 * <pre>
 * ((12-day EMA of Volume - 26-day EMA of Volume) / 26-day EMA of Volume) x 100
 * </pre>
 *
 * @see <a href=
 *      "https://school.stockcharts.com/doku.php?id=technical_indicators:percentage_volume_oscillator_pvo">
 *      https://school.stockcharts.com/doku.php?id=technical_indicators:percentage_volume_oscillator_pvo
 *      </a>
 */
public class PVOIndicator extends PPOIndicator {

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code shortBarCount} = 12
     * <li>{@code longBarCount} = 26
     * </ul>
     *
     * @param series the bar series {@link BarSeries}
     */
    public PVOIndicator(BarSeries series) {
        super(new VolumeIndicator(series));
    }

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code shortBarCount} = 12
     * <li>{@code longBarCount} = 26
     * </ul>
     *
     * @param series         the bar series {@link BarSeries}.
     * @param volumeBarCount the bar count for the {@link VolumeIndicator}
     */
    public PVOIndicator(BarSeries series, int volumeBarCount) {
        super(new VolumeIndicator(series, volumeBarCount));
    }

    /**
     * @param series        the bar series {@link BarSeries}.
     * @param shortBarCount PPO short time frame.
     * @param longBarCount  PPO long time frame.
     */
    public PVOIndicator(BarSeries series, int shortBarCount, int longBarCount) {
        super(new VolumeIndicator(series), shortBarCount, longBarCount);
    }

    /**
     * @param series         the bar series {@link BarSeries}.
     * @param volumeBarCount Volume Indicator bar count.
     * @param shortBarCount  PPO short time frame.
     * @param longBarCount   PPO long time frame.
     */
    public PVOIndicator(BarSeries series, int volumeBarCount, int shortBarCount, int longBarCount) {
        super(new VolumeIndicator(series, volumeBarCount), shortBarCount, longBarCount);
    }

}
