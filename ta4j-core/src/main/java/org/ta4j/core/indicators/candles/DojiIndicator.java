/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Doji indicator.
 *
 * <p>
 * A candle at index {@code i} is a doji when its body magnitude is at most
 * {@code rangeFactor} times the average high-low range of the
 * {@code averagePeriod} candles immediately preceding it:
 *
 * <pre>
 * |close_i - open_i| &lt;= rangeFactor * average(range[i-averagePeriod] ... range[i-1])
 * </pre>
 *
 * The comparison is <em>inclusive</em>: a body exactly equal to the threshold
 * (including a zero body against a zero range baseline) is still a doji.
 * 
 * <p>
 * The body is compared in scaled form,
 * {@code |open_i / priorAverage - close_i / priorAverage| <= rangeFactor},
 * which is equivalent to the product form above. Scaling each operand before
 * differencing keeps the comparison decidable when the raw body magnitude
 * overflows the {@link Num} type: a finite scaled ratio preserves the exact
 * ordering, while a scaled difference that overflows to positive infinity can
 * only exceed a finite range factor, mirroring DecimalNum's exact arithmetic
 * under DoubleNum.
 *
 * 
 * <p>
 * This indicator evaluates only candle geometry; it does not evaluate trend or
 * direction context. A doji is traditionally interpreted as a sign of market
 * indecision, but whether it carries reversal meaning depends on the context
 * the caller composes around it.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#doji">
 *      http://stockcharts.com/school/doku.php?id=chart_school:chart_analysis:introduction_to_candlesticks#doji</a>
 */
public class DojiIndicator extends CandlePatternIndicator {

    /** The number of preceding candles averaged into the range baseline. */
    private final int averagePeriod;

    /**
     * The factor applied to the prior average range to obtain the doji body
     * threshold.
     */
    private final double rangeFactor;

    /**
     * Constructor with the recommended defaults: a 5-candle range baseline and a
     * range factor of 0.1.
     *
     * @param series the bar series
     * @since 0.24.2
     */
    public DojiIndicator(BarSeries series) {
        super(validateConfiguration(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD,
                CandleThresholdSupport.DOJI_RANGE_FACTOR),
                CandleThresholdSupport.forSeries(series, CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD));
        this.averagePeriod = CandleThresholdSupport.DEFAULT_AVERAGE_PERIOD;
        this.rangeFactor = CandleThresholdSupport.DOJI_RANGE_FACTOR;
    }

    /**
     * Constructor with a custom average period and range factor.
     *
     * @param series        the bar series
     * @param averagePeriod the number of preceding candles averaged into the range
     *                      baseline; must be at least 1
     * @param rangeFactor   the factor applied to the prior average range; must be
     *                      finite and non-negative; a signed zero is normalized to
     *                      positive zero so {@code == 0} factors behave identically
     *                      on every {@link Num} implementation
     * @throws IllegalArgumentException if {@code averagePeriod} is below 1 or
     *                                  {@code rangeFactor} is not finite or is
     *                                  negative
     */
    public DojiIndicator(BarSeries series, int averagePeriod, double rangeFactor) {
        super(validateConfiguration(series, averagePeriod, rangeFactor),
                CandleThresholdSupport.forSeries(series, averagePeriod));
        this.averagePeriod = averagePeriod;
        // Normalize signed zero: DoubleNum's Double.compare orders 0.0 above -0.0,
        // which would flip the inclusive doji threshold for a zero body.
        this.rangeFactor = rangeFactor == 0d ? 0d : rangeFactor;
    }

    @Override
    protected Boolean calculate(int index) {
        if (!thresholds.isValid(index)) {
            return false;
        }
        final var bar = getBarSeries().getBar(index);
        final Num open = bar.getOpenPrice();
        final Num close = bar.getClosePrice();
        if (open == null || close == null || !Num.isFinite(open) || !Num.isFinite(close)) {
            // Unavailable candle endpoints (for example a NaN open or close)
            // leave the body magnitude undefined: conservatively not a doji.
            return false;
        }
        final Num priorAverage = thresholds.priorAverageRange().getValue(index);
        if (!Num.isFinite(priorAverage)) {
            // A non-finite prior average (e.g. a DoubleNum SMA accumulator that
            // overflowed while summing the baseline window) leaves the correct
            // threshold unrepresentable: conservatively not a doji.
            return false;
        }
        if (priorAverage.isZero()) {
            // A zero range baseline leaves no scaling reference: only a candle
            // with no body at all can qualify.
            return !open.minus(close).abs().isPositive();
        }
        // Compare the body against the threshold in scaled form, dividing each
        // operand by the baseline before differencing (see class Javadoc). The
        // raw body magnitude can overflow the numeric type even when the ratio
        // is representable; a scaled difference that overflows to positive
        // infinity can only exceed a finite range factor, matching DecimalNum's
        // exact arithmetic.
        final Num scaledBody = open.dividedBy(priorAverage).minus(close.dividedBy(priorAverage)).abs();
        return !scaledBody.isGreaterThan(getBarSeries().numFactory().numOf(rangeFactor));
    }

    @Override
    public int getCountOfUnstableBars() {
        return averagePeriod;
    }

    private static BarSeries validateConfiguration(BarSeries series, int averagePeriod, double rangeFactor) {
        Objects.requireNonNull(series, "series must not be null");
        if (averagePeriod < 1) {
            throw new IllegalArgumentException("averagePeriod must be at least 1, but was: " + averagePeriod);
        }
        if (!Double.isFinite(rangeFactor) || rangeFactor < 0d) {
            throw new IllegalArgumentException("rangeFactor must be finite and >= 0, but was: " + rangeFactor);
        }
        return series;
    }
}
