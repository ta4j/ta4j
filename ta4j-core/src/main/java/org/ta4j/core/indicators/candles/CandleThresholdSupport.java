/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Shared, causal threshold evaluation for the candlestick pattern indicators of
 * this package.
 *
 * <p>
 * Every predicate compares the candle at {@code index} against a baseline
 * computed exclusively from the {@code averagePeriod} candles
 * <em>preceding</em> it: the baseline of an indicator {@code m} at
 * {@code index} is
 *
 * <pre>
 * average(m[index - averagePeriod] ... m[index - 1])
 * </pre>
 *
 * implemented as
 * {@code new PreviousValueIndicator(new SMAIndicator(m, averagePeriod))}. The
 * candle under evaluation therefore never influences its own baseline, which
 * keeps the pattern evaluation causal (look-ahead free).
 *
 * <p>
 * Recommended threshold profile (documented defaults):
 *
 * <table>
 * <caption>Threshold profile</caption>
 * <tr>
 * <th>Predicate</th>
 * <th>Condition</th>
 * <th>Baseline</th>
 * <th>Comparison</th>
 * <th>Factor</th>
 * </tr>
 * <tr>
 * <td>{@link #isLongBody(int)}</td>
 * <td>body</td>
 * <td>prior average body</td>
 * <td>{@code >}</td>
 * <td>{@value #LONG_BODY_FACTOR}</td>
 * </tr>
 * <tr>
 * <td>{@link #isShortBody(int)}</td>
 * <td>body</td>
 * <td>prior average body</td>
 * <td>{@code <}</td>
 * <td>{@value #SHORT_BODY_FACTOR}</td>
 * </tr>
 * <tr>
 * <td>{@link #isDoji(int)}</td>
 * <td>body</td>
 * <td>prior average range</td>
 * <td>{@code <=}</td>
 * <td>{@value #DOJI_RANGE_FACTOR}</td>
 * </tr>
 * <tr>
 * <td>{@link #isLongShadow(int, Indicator)}</td>
 * <td>shadow</td>
 * <td>prior average body</td>
 * <td>{@code >}</td>
 * <td>{@value #LONG_SHADOW_FACTOR}</td>
 * </tr>
 * <tr>
 * <td>{@link #isShortShadow(int, Indicator)}</td>
 * <td>shadow</td>
 * <td>prior average range</td>
 * <td>{@code <=}</td>
 * <td>{@value #SHORT_SHADOW_RANGE_FACTOR}</td>
 * </tr>
 * <tr>
 * <td>{@link #isNear(int, Indicator, Indicator)}</td>
 * <td>{@code |first - second|}</td>
 * <td>prior average range</td>
 * <td>{@code <=}</td>
 * <td>{@value #NEAR_RANGE_FACTOR}</td>
 * </tr>
 * </table>
 *
 * <p>
 * A threshold is only {@linkplain #isValid(int) valid} from the index at which
 * a full preceding window exists: {@code index >= series.getBeginIndex() +
 * averagePeriod}. Every predicate returns {@code false} below that boundary, so
 * callers of this package can treat {@code false} uniformly as "not confirmed
 * yet".
 *
 * <p>
 * The profile follows the baseline-relative conventions documented in
 * Bulkowski's <em>Encyclopedia of Candlestick Charts</em>;
 * {@code averagePeriod} remains tunable per series while the factors are
 * package constants so the whole package shares one recommended profile.
 *
 * <p>
 * Instances are derived from the series and hold indicator caches; public
 * pattern indicators should keep their instance in a {@code transient} field,
 * like any other derived indicator field.
 */
final class CandleThresholdSupport {

    /** Default number of preceding candles averaged into a baseline. */
    static final int DEFAULT_AVERAGE_PERIOD = 5;

    /** Factor against the prior average body for {@link #isLongBody(int)}. */
    static final double LONG_BODY_FACTOR = 1.0;

    /** Factor against the prior average body for {@link #isShortBody(int)}. */
    static final double SHORT_BODY_FACTOR = 0.5;

    /** Factor against the prior average range for {@link #isDoji(int)}. */
    static final double DOJI_RANGE_FACTOR = 0.1;

    /**
     * Factor against the prior average body for
     * {@link #isLongShadow(int, Indicator)}.
     */
    static final double LONG_SHADOW_FACTOR = 2.0;

    /**
     * Factor against the prior average range for
     * {@link #isShortShadow(int, Indicator)}.
     */
    static final double SHORT_SHADOW_RANGE_FACTOR = 0.1;

    /**
     * Factor against the prior average range for
     * {@link #isNear(int, Indicator, Indicator)}.
     */
    static final double NEAR_RANGE_FACTOR = 0.1;

    private final BarSeries series;
    private final int averagePeriod;
    private final Indicator<Num> body;
    private final Indicator<Num> range;
    private final Indicator<Num> priorAverageBody;
    private final Indicator<Num> priorAverageRange;
    private final Num longBodyFactor;
    private final Num shortBodyFactor;
    private final Num dojiRangeFactor;
    private final Num longShadowFactor;
    private final Num shortShadowRangeFactor;
    private final Num nearRangeFactor;

    /**
     * Constructor with the {@link #DEFAULT_AVERAGE_PERIOD default average period}.
     *
     * @param series the bar series to evaluate
     * @throws NullPointerException     if {@code series} is null
     * @throws IllegalArgumentException if {@code averagePeriod} is below 1
     */
    CandleThresholdSupport(BarSeries series) {
        this(series, DEFAULT_AVERAGE_PERIOD);
    }

    /**
     * Constructor with a custom average period.
     *
     * @param series        the bar series to evaluate
     * @param averagePeriod the number of preceding candles averaged into each
     *                      baseline
     * @throws NullPointerException     if {@code series} is null
     * @throws IllegalArgumentException if {@code averagePeriod} is below 1
     */
    CandleThresholdSupport(BarSeries series, int averagePeriod) {
        this.series = Objects.requireNonNull(series, "series must not be null");
        if (averagePeriod < 1) {
            throw new IllegalArgumentException("averagePeriod must be at least 1, but was: " + averagePeriod);
        }
        this.averagePeriod = averagePeriod;
        this.body = new CandleBodyIndicator(series);
        this.range = new CandleRangeIndicator(series);
        this.priorAverageBody = new PreviousValueIndicator(new SMAIndicator(body, averagePeriod));
        this.priorAverageRange = new PreviousValueIndicator(new SMAIndicator(range, averagePeriod));
        final NumFactory numFactory = series.numFactory();
        this.longBodyFactor = numFactory.numOf(LONG_BODY_FACTOR);
        this.shortBodyFactor = numFactory.numOf(SHORT_BODY_FACTOR);
        this.dojiRangeFactor = numFactory.numOf(DOJI_RANGE_FACTOR);
        this.longShadowFactor = numFactory.numOf(LONG_SHADOW_FACTOR);
        this.shortShadowRangeFactor = numFactory.numOf(SHORT_SHADOW_RANGE_FACTOR);
        this.nearRangeFactor = numFactory.numOf(NEAR_RANGE_FACTOR);
    }

    /**
     * The shifted rolling average of the preceding {@code averagePeriod} candle
     * bodies, the baseline against which body-dependent thresholds are evaluated at
     * each index.
     *
     * @return the prior average body baseline
     */
    Indicator<Num> priorAverageBody() {
        return priorAverageBody;
    }

    /**
     * The shifted rolling average of the preceding {@code averagePeriod} candle
     * ranges, the baseline against which range-dependent thresholds are evaluated
     * at each index.
     *
     * @return the prior average range baseline
     */
    Indicator<Num> priorAverageRange() {
        return priorAverageRange;
    }

    /**
     * Whether a full preceding window exists at {@code index}, i.e.
     * {@code index >= series.getBeginIndex() + averagePeriod}.
     *
     * @param index the candle index
     * @return {@code true} if every predicate can be evaluated at {@code index}
     */
    boolean isValid(int index) {
        return index >= series.getBeginIndex() + averagePeriod;
    }

    /**
     * Whether the candle body is greater than {@value #LONG_BODY_FACTOR} times the
     * prior average body.
     *
     * @param index the candle index
     * @return {@code true} for a long body, {@code false} below the warm-up
     *         boundary or for a short body
     */
    boolean isLongBody(int index) {
        return isValid(index)
                && body.getValue(index).isGreaterThan(priorAverageBody.getValue(index).multipliedBy(longBodyFactor));
    }

    /**
     * Whether the candle body is less than {@value #SHORT_BODY_FACTOR} times the
     * prior average body.
     *
     * @param index the candle index
     * @return {@code true} for a short body, {@code false} below the warm-up
     *         boundary or for a long body
     */
    boolean isShortBody(int index) {
        return isValid(index)
                && body.getValue(index).isLessThan(priorAverageBody.getValue(index).multipliedBy(shortBodyFactor));
    }

    /**
     * Whether the candle body is at most {@value #DOJI_RANGE_FACTOR} of the prior
     * average range, the classic doji neighborhood.
     *
     * @param index the candle index
     * @return {@code true} for a doji-like body, {@code false} below the warm-up
     *         boundary or for a substantial body
     */
    boolean isDoji(int index) {
        return isValid(index)
                && !body.getValue(index).isGreaterThan(priorAverageRange.getValue(index).multipliedBy(dojiRangeFactor));
    }

    /**
     * Whether the given shadow measurement is greater than
     * {@value #LONG_SHADOW_FACTOR} times the prior average body.
     *
     * @param index  the candle index
     * @param shadow the shadow measurement to evaluate (upper or lower)
     * @return {@code true} for a long shadow, {@code false} below the warm-up
     *         boundary or for a short shadow
     */
    boolean isLongShadow(int index, Indicator<Num> shadow) {
        return isValid(index) && shadow.getValue(index)
                .isGreaterThan(priorAverageBody.getValue(index).multipliedBy(longShadowFactor));
    }

    /**
     * Whether the given shadow measurement is at most
     * {@value #SHORT_SHADOW_RANGE_FACTOR} of the prior average range.
     *
     * @param index  the candle index
     * @param shadow the shadow measurement to evaluate (upper or lower)
     * @return {@code true} for a short shadow, {@code false} below the warm-up
     *         boundary or for a long shadow
     */
    boolean isShortShadow(int index, Indicator<Num> shadow) {
        return isValid(index) && !shadow.getValue(index)
                .isGreaterThan(priorAverageRange.getValue(index).multipliedBy(shortShadowRangeFactor));
    }

    /**
     * Whether the absolute difference between the two measurements is at most
     * {@value #NEAR_RANGE_FACTOR} of the prior average range.
     *
     * @param index  the candle index
     * @param first  the first measurement
     * @param second the second measurement
     * @return {@code true} when the two measurements are near each other,
     *         {@code false} below the warm-up boundary or when they diverge
     */
    boolean isNear(int index, Indicator<Num> first, Indicator<Num> second) {
        return isValid(index) && !first.getValue(index)
                .minus(second.getValue(index))
                .abs()
                .isGreaterThan(priorAverageRange.getValue(index).multipliedBy(nearRangeFactor));
    }
}
