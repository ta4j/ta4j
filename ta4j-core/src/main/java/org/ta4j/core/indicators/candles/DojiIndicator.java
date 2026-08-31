/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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
 * For a positive range factor, a negative prior range from malformed high-low
 * data never qualifies. The explicit zero-factor branch remains an exact
 * zero-body check.
 * <p>
 * A finite body difference outside the subnormal-ratio region is compared by
 * rescaling the body with the positive range factor,
 * {@code |open_i - close_i| / rangeFactor <= priorAverage}. This avoids
 * rounding a body above its inclusive threshold down onto the ratio boundary. A
 * zero body (open equals close) stays zero and qualifies as a doji no matter
 * how small the baseline is. A ratio that would land below the subnormal floor
 * is evaluated with its dividend scaled into the normal range first, so the
 * comparison keeps normal-range relative precision instead of collapsing onto
 * the inclusive boundary. Only when the raw difference overflows the
 * {@link Num} type (finite endpoints farther apart than a representable
 * magnitude) is each operand divided by the baseline before differencing,
 * {@code |open_i / priorAverage - close_i / priorAverage| <= rangeFactor},
 * which preserves the exact ordering under overflow, mirroring DecimalNum's
 * exact arithmetic under DoubleNum.
 *
 * <p>
 * When the restored full-scale prior average itself overflows (an overflowed
 * baseline window), the comparison stays on the preserved half scale:
 * {@code |open_i / 2 - close_i / 2| <= rangeFactor * halfPriorAverage}. This
 * preserves exact ordering even when both restored full-scale body and
 * threshold overflow. An indeterminate half-scale operand conservatively
 * rejects.
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
     * {@code 2^53}: scales a ratio that would land below the subnormal floor into
     * the normal range, so the division keeps its full relative precision.
     */
    private static final double RATIO_SCALE = 0x1p53;

    /**
     * {@code 2^-1022}: the smallest positive normal magnitude; ratios below it are
     * subnormal and round with the coarse subnormal spacing.
     */
    private static final double MIN_NORMAL_MAGNITUDE = 0x1p-1022;

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
        final Bar bar = getBarSeries().getBar(index);
        final Num open = bar.getOpenPrice();
        final Num close = bar.getClosePrice();
        if (open == null || close == null || !Num.isFinite(open) || !Num.isFinite(close)) {
            // Unavailable candle endpoints (for example a NaN open or close)
            // leave the body magnitude undefined: conservatively not a doji.
            return false;
        }
        final NumFactory numFactory = getBarSeries().numFactory();
        final Num bodyMagnitude = open.minus(close).abs();
        if (rangeFactor == 0d) {
            // A zero range factor admits only a body with no magnitude at all.
            // The ratio comparison below would underflow a nonzero subnormal
            // body to zero under DoubleNum and misclassify it as a doji, so
            // compare the raw body magnitude directly; it is zero on every
            // Num implementation exactly when open equals close.
            return !bodyMagnitude.isPositive();
        }
        final Num priorAverage = thresholds.priorAverageRange().getValue(index);
        if (!Num.isFinite(priorAverage)) {
            // Keep both sides on the preserved half scale. Restoring the
            // threshold before comparing makes a body and threshold that both
            // overflow compare equal under DoubleNum even when their exact
            // magnitudes differ.
            final Num two = numFactory.numOf(2);
            final Num halfBodyMagnitude = open.dividedBy(two).minus(close.dividedBy(two)).abs();
            final Num halfThreshold = thresholds.halfPriorAverageRange()
                    .getValue(index)
                    .multipliedBy(numFactory.numOf(rangeFactor));
            if (Num.isNaNOrNull(halfBodyMagnitude) || Num.isNaNOrNull(halfThreshold)) {
                return false;
            }
            return !halfBodyMagnitude.isGreaterThan(halfThreshold);
        }
        if (priorAverage.isNegative()) {
            // A malformed negative range is not a distance threshold.
            return false;
        }
        if (priorAverage.isZero()) {
            // A zero range baseline leaves no scaling reference: only a candle
            // with no body at all can qualify.
            return !bodyMagnitude.isPositive();
        }
        final Num difference = open.minus(close);
        if (Num.isFinite(difference)) {
            // One shared finite scale for both endpoints: a zero body (open
            // equals close) stays zero, so it qualifies as a doji no matter
            // how small the baseline is.
            final Num factor = numFactory.numOf(rangeFactor);
            if (bodyMagnitude.isLessThan(priorAverage.multipliedBy(numFactory.numOf(MIN_NORMAL_MAGNITUDE)))) {
                // The ratio would land below the subnormal floor, where one
                // rounded-down quotient could collapse onto the inclusive
                // boundary below the factor: scale the dividend into the normal
                // range first, so the comparison keeps normal-range relative
                // precision. An overflowed scaled factor (a factor beyond
                // MAX / 2^53) can only exceed the subnormal ratio, so the
                // scaled comparison still yields the doji verdict.
                final Num ratioScale = numFactory.numOf(RATIO_SCALE);
                final Num scaledRatio = bodyMagnitude.multipliedBy(ratioScale).dividedBy(priorAverage);
                return !scaledRatio.isGreaterThan(factor.multipliedBy(ratioScale));
            }
            final Num scaledBodyMagnitude = bodyMagnitude.dividedBy(factor);
            return !scaledBodyMagnitude.isGreaterThan(priorAverage);
        }
        // The raw difference overflows the numeric type although the endpoints
        // are finite. Dividing each operand by the baseline before differencing
        // preserves the exact ordering; a scaled difference that overflows to
        // infinity can only exceed a finite range factor, matching DecimalNum's
        // exact arithmetic.
        final Num scaledBody = open.dividedBy(priorAverage).minus(close.dividedBy(priorAverage)).abs();
        return !scaledBody.isGreaterThan(numFactory.numOf(rangeFactor));
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
