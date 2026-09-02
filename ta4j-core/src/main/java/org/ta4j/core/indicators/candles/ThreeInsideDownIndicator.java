/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * Three inside down candle indicator.
 *
 * <p>
 * The harami baseline averages the preceding
 * {@link CandleThresholdSupport#DEFAULT_AVERAGE_PERIOD} candle bodies by
 * default; use {@link #ThreeInsideDownIndicator(BarSeries, int)} to tune it.
 *
 * <p>
 * Matches the documented three-candle three-inside-down morphology at
 * {@code index} for the candles at {@code index - 2} (first), {@code index - 1}
 * (second), and {@code index} (third):
 *
 * <pre>
 * a bearish harami completes at index - 1 over the first two candles:
 *     BearishHaramiIndicator(index - 1)
 * third is bearish and its close confirms strictly beyond the first open:
 *     close(third) &lt; open(first)
 * </pre>
 *
 * <p>
 * The harami condition reuses {@link BearishHaramiIndicator} with the same
 * {@code averagePeriod}, so a long bullish first body, a short contained second
 * body, and the shared adaptive body thresholds are all evaluated there. The
 * third-candle confirmation close and the bearish direction are strict, and a
 * third candle with non-finite open or close never matches.
 *
 * <p>
 * The default constructor uses a five-candle baseline ({@code averagePeriod} =
 * 5).
 *
 * <p>
 * This indicator is stable after {@code averagePeriod + 2} bars, i.e. the
 * harami's unstable count plus one for the confirming third candle. Before that
 * boundary, or when the retained history does not reach back to the first
 * candle, it returns {@code false}.
 *
 * <p>
 * Traditionally interpreted as a bearish reversal signal after an uptrend. This
 * indicator does not evaluate the preceding trend or any direction context; the
 * reversal context must be composed explicitly by the caller.
 *
 * @see <a href="https://www.investopedia.com/terms/t/three-inside-updown.asp">
 *      https://www.investopedia.com/terms/t/three-inside-updown.asp</a>
 * @since 0.22.2
 */
public class ThreeInsideDownIndicator extends CandlePatternIndicator {

    private final int averagePeriod;

    private final transient BearishHaramiIndicator harami;

    /**
     * Constructor with the default average period.
     *
     * @param series the bar series
     */
    public ThreeInsideDownIndicator(final BarSeries series) {
        super(CandleThresholdSupport.validateSeriesAndAveragePeriod(series,
                CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD),
                CandleThresholdSupport.forSeries(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD));

        this.averagePeriod = CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD;
        this.harami = new BearishHaramiIndicator(getBarSeries(), averagePeriod);
    }

    /**
     * Constructor with a custom average period.
     *
     * @param series        the bar series
     * @param averagePeriod the number of preceding candles averaged into each body
     *                      baseline
     * @throws IllegalArgumentException if {@code averagePeriod} is below 1 or above
     *                                  {@link CandleThresholdSupport#MAX_AVERAGE_PERIOD}
     * @since 0.24.2
     */
    public ThreeInsideDownIndicator(final BarSeries series, final int averagePeriod) {
        super(CandleThresholdSupport.validateSeriesAndAveragePeriod(series, averagePeriod),
                CandleThresholdSupport.forSeries(series, averagePeriod));

        this.averagePeriod = averagePeriod;
        this.harami = new BearishHaramiIndicator(getBarSeries(), averagePeriod);
    }

    @Override
    int latestBaselineIndex(final int index) {
        return index - 2;
    }

    @Override
    protected Boolean calculate(int index) {
        BarSeries series = getBarSeries();
        if (index < getCountOfUnstableBars() || index - 2 < series.getBeginIndex()) {
            return false;
        }
        Bar firstBar = series.getBar(index - 2);
        Bar thirdBar = series.getBar(index);

        return harami.getValue(index - 1) && Num.isFinite(thirdBar.getOpenPrice())
                && Num.isFinite(thirdBar.getClosePrice())
                && thirdBar.getClosePrice().isLessThan(firstBar.getOpenPrice()) && thirdBar.isBearish();
    }

    @Override
    public int getCountOfUnstableBars() {
        return harami.getCountOfUnstableBars() + 1;
    }
}
