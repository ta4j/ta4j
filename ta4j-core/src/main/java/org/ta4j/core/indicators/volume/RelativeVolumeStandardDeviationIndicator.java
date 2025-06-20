/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.statistics.SampleType;
import org.ta4j.core.num.Num;

/**
 * Relative Volume Standard Deviation Indicator. This class is an indicator that
 * calculates the standard deviation of the relative volume.
 * <p>
 * Relative Volume (often times called RVOL) is an indicator that tells traders
 * how current trading volume is compared to past trading volume over a given
 * period.
 * <p>
 * It is calculated as the ratio of the current volume to the average volume for
 * the same period. The standard deviation of the relative volume is then
 * calculated to understand volatility.
 *
 * @see <a href=
 *      "https://www.tradingview.com/script/Eize4T9L-Relative-Volume-Standard-Deviation/">Relative
 *      Volume Standard Deviation</a>
 */
public class RelativeVolumeStandardDeviationIndicator extends CachedIndicator<Num> {

    private final StandardDeviationIndicator volumeStandardDeviation;
    private final SMAIndicator averageVolume;
    private final VolumeIndicator volume;
    private final int barCount;

    /**
     * Constructor.
     *
     * @param series     the bar series
     * @param barCount   the time frame
     * @param sampleType sample/population
     */
    public RelativeVolumeStandardDeviationIndicator(final BarSeries series, final int barCount,
            final SampleType sampleType) {
        super(series);
        this.barCount = barCount;
        this.volume = new VolumeIndicator(series);
        this.averageVolume = new SMAIndicator(this.volume, barCount);
        this.volumeStandardDeviation = sampleType.isSample()
                ? StandardDeviationIndicator.ofSample(this.volume, barCount)
                : StandardDeviationIndicator.ofPopulation(this.volume, barCount);
    }

    public static RelativeVolumeStandardDeviationIndicator ofSample(final BarSeries series, final int barCount) {
        return new RelativeVolumeStandardDeviationIndicator(series, barCount, SampleType.SAMPLE);
    }

    public static RelativeVolumeStandardDeviationIndicator ofPopulation(final BarSeries series, final int barCount) {
        return new RelativeVolumeStandardDeviationIndicator(series, barCount, SampleType.POPULATION);
    }

    @Override
    protected Num calculate(final int index) {
        // If the index is less than the required unstable bars, return NaN
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        // Calculate the relative volume standard deviation
        return this.volume.getValue(index)
                .minus(this.averageVolume.getValue(index))
                .dividedBy(this.volumeStandardDeviation.getValue(index));
    }

    @Override
    public int getCountOfUnstableBars() {
        return this.barCount;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + this.barCount;
    }
}
