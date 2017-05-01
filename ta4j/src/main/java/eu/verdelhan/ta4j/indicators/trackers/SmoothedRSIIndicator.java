package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.helpers.*;

/**
 * Relative strength index indicator.
 * <p>
 * This calculation of RSI is based on accumulative moving average
 * as described in Wilder's original paper from 1978
 *
 * <p>See reference
 * <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:relative_strength_index_rsi">
 * RSI calculation</a>.
 *
 * @see RSIIndicator
 * @since 0.9
 */
public class SmoothedRSIIndicator extends CachedIndicator<Decimal> {

    private static final Integer SMOOTH_MIN_TICKS = 150;

    private RelativeStrengthIndexCalculation rsiCalculation;
    private final int timeFrame;

    public SmoothedRSIIndicator(Indicator<Decimal> indicator, int timeFrame) {
        super(indicator);
        this.timeFrame = timeFrame;
        rsiCalculation = new RelativeStrengthIndexCalculation(
            new SmoothedAverageGainIndicator(indicator, timeFrame),
            new SmoothedAverageLossIndicator(indicator, timeFrame)
        );
    }

    @Override
    protected Decimal calculate(int index) {
        if (index < SMOOTH_MIN_TICKS) {
            log.warn(
                "Requesting index : {}. Smoothed RSI needs {} ticks before calculated index in data series to get the best results",
                index,
                SMOOTH_MIN_TICKS);
        }
        return rsiCalculation.getValue(index);
    }

    @Override
    public String toString() {
        return getClass().getName() + " timeFrame: " + timeFrame;
    }
}
