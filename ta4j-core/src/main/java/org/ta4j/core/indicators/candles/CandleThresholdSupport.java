/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.NaN;
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
 * implemented as {@code new PreviousValueIndicator(new PriorAverageIndicator(m,
 * averagePeriod))}, where {@code m} is the half-scale source (half body or half
 * range). {@link PriorAverageIndicator} averages half-scale values, so its
 * intermediate sums stay finite whenever the full-scale mean is representable,
 * and returns the half-scale mean; when the SMA accumulator still overflows
 * into a non-finite value, the same window is re-averaged as an incremental
 * mean. Full-scale views are derived only where a consumer still expects raw
 * magnitudes (shadow comparisons and the baseline accessors) by doubling the
 * half-scale mean. Body, doji, and near comparisons additionally consult raw
 * full-scale baselines whenever both operands are finite: halving two adjacent
 * subnormal magnitudes can collapse them onto the same half-scale value, so the
 * raw comparison preserves the strict ordering that
 * {@link org.ta4j.core.num.DecimalNum} observes. For the one-tenth factors the
 * comparison is cross-multiplied ({@code body * 10 <= range}) because the
 * factor itself is not exactly representable in binary floating point;
 * non-finite raw operands fall back to the half-scale path. The candle under
 * evaluation therefore never influences its own baseline, which keeps the
 * pattern evaluation causal (look-ahead free).
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
 * Instances are interned per {@code (series, averagePeriod)} pair through
 * {@link #forSeries(BarSeries, int)} so that every pattern indicator composed
 * over the same series shares the same cached primitives and baselines instead
 * of each building a duplicate cache of identical values. The intern table
 * compares series by identity and holds both keys and values weakly: an entry
 * only survives while at least one pattern indicator still references it, and
 * it is collected once the series itself becomes unreachable.
 *
 * <p>
 * Public pattern indicators should keep their instance in a {@code transient}
 * field, like any other derived indicator field.
 */
final class CandleThresholdSupport {

    /**
     * A weak, identity-keyed intern table. Keys are compared by referent identity,
     * not {@code equals}, so custom {@link BarSeries} implementations that override
     * {@link Object#equals(Object)} still map to their own entry. Lookup keys are
     * built without a reference queue so they are never enqueued; only stored keys
     * enter the queue and are removed from the map once their referent is
     * collected.
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    private static final class WeakIdentityInternTable<K, V> {

        private final ReferenceQueue<K> queue = new ReferenceQueue<>();
        private final Map<IdentityKey<K>, V> entries = new HashMap<>();

        synchronized V get(K key) {
            purgeClearedKeys();
            return entries.get(new IdentityKey<>(key, null));
        }

        synchronized void put(K key, V value) {
            purgeClearedKeys();
            entries.put(new IdentityKey<>(key, queue), value);
        }

        private void purgeClearedKeys() {
            IdentityKey<?> cleared;
            while ((cleared = (IdentityKey<?>) queue.poll()) != null) {
                entries.remove(cleared);
            }
        }
    }

    /**
     * A weak reference whose equality and hash code are derived from the identity
     * of its referent, captured while the referent is still alive.
     *
     * @param <K> the referent type
     */
    private static final class IdentityKey<K> extends WeakReference<K> {

        private final int identityHash;

        IdentityKey(K referent, ReferenceQueue<K> queue) {
            super(referent, queue);
            this.identityHash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (!(other instanceof IdentityKey<?>)) {
                return false;
            }
            final K referent = get();
            return referent != null && referent == ((IdentityKey<?>) other).get();
        }
    }

    /** Interned supports, keyed by series identity and weak on both sides. */
    private static final WeakIdentityInternTable<BarSeries, Map<Integer, WeakReference<CandleThresholdSupport>>> INTERNED_SUPPORTS = new WeakIdentityInternTable<>();

    /** Receives collected per-period support references for lazy cleanup. */
    private static final ReferenceQueue<CandleThresholdSupport> SUPPORT_QUEUE = new ReferenceQueue<>();

    /**
     * A weak reference to an interned support that remembers the average-period key
     * it is stored under and the map that owns the entry, so
     * {@link #forSeries(BarSeries, int)} can drop an entry whose value has been
     * collected without scanning the whole period map. The owner is referenced
     * weakly: it dies together with the series it belongs to.
     */
    private static final class SupportReference extends WeakReference<CandleThresholdSupport> {

        private final int averagePeriod;
        private final WeakReference<Map<Integer, WeakReference<CandleThresholdSupport>>> owner;

        SupportReference(CandleThresholdSupport support, int averagePeriod,
                Map<Integer, WeakReference<CandleThresholdSupport>> owner) {
            super(support, SUPPORT_QUEUE);
            this.averagePeriod = averagePeriod;
            this.owner = new WeakReference<>(owner);
        }
    }

    /**
     * Returns the interned support for the given series and average period,
     * creating it on first use. All callers with the same
     * {@code (series, averagePeriod)} pair share one instance, so the cached
     * primitives and baselines are computed only once per series.
     *
     * @param series        the bar series to evaluate
     * @param averagePeriod the number of preceding candles averaged into each
     *                      baseline
     * @return the shared support for the series and period
     * @throws NullPointerException     if {@code series} is null
     * @throws IllegalArgumentException if {@code averagePeriod} is outside the
     *                                  supported range
     */
    static CandleThresholdSupport forSeries(BarSeries series, int averagePeriod) {
        Objects.requireNonNull(series, "series must not be null");
        if (averagePeriod < 1) {
            throw new IllegalArgumentException("averagePeriod must be at least 1, but was: " + averagePeriod);
        }
        synchronized (INTERNED_SUPPORTS) {
            Map<Integer, WeakReference<CandleThresholdSupport>> byPeriod = INTERNED_SUPPORTS.get(series);
            if (byPeriod == null) {
                byPeriod = new HashMap<>();
                INTERNED_SUPPORTS.put(series, byPeriod);
            }
            removeCollectedPeriods();
            final WeakReference<CandleThresholdSupport> existing = byPeriod.get(averagePeriod);
            CandleThresholdSupport support = existing == null ? null : existing.get();
            if (support == null) {
                support = new CandleThresholdSupport(series, averagePeriod);
                byPeriod.put(averagePeriod, new SupportReference(support, averagePeriod, byPeriod));
            }
            return support;
        }
    }

    /**
     * Returns the interned period entries recorded for the given series.
     * <p>
     * Package-private seam: lets package-local tests verify that collected support
     * references are removed from their owning map without reaching into private
     * state.
     *
     * @param series the series whose interned periods are requested
     * @return the interned period entries, or {@code null} when the series has none
     */
    static Map<Integer, WeakReference<CandleThresholdSupport>> internedPeriods(BarSeries series) {
        synchronized (INTERNED_SUPPORTS) {
            return INTERNED_SUPPORTS.get(series);
        }
    }

    /**
     * Removes the period entries whose support values have been collected, as
     * reported by {@link #SUPPORT_QUEUE}. Each collected reference knows its own
     * owning map, so entries of other series are cleaned from that owner rather
     * than left behind. The removal is keyed by the exact collected reference, so a
     * replacement stored since collection is never dropped.
     */
    private static void removeCollectedPeriods() {
        SupportReference collected;
        while ((collected = (SupportReference) SUPPORT_QUEUE.poll()) != null) {
            final Map<Integer, WeakReference<CandleThresholdSupport>> owner = collected.owner.get();
            if (owner != null) {
                owner.remove(collected.averagePeriod, collected);
            }
        }
    }

    /** Default number of preceding candles averaged into a baseline. */
    static final int DEFAULT_AVERAGE_PERIOD = 5;

    /**
     * Maximum supported average period. Two is subtracted from
     * {@link Integer#MAX_VALUE} so that the warm-up arithmetic of every pattern
     * indicator, which adds up to two bars, cannot overflow.
     */
    static final int MAX_AVERAGE_PERIOD = Integer.MAX_VALUE - 2;

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

    /**
     * Multiplicative scale applied to the measured magnitude instead of multiplying
     * the baseline by {@link #DOJI_RANGE_FACTOR} or {@link #NEAR_RANGE_FACTOR}.
     * Both factors are one tenth, which is not exactly representable in binary
     * floating point, whereas scaling by ten is exact for every subnormal
     * magnitude, so the scaled comparison preserves the ordering between adjacent
     * subnormal magnitudes.
     */
    static final double RANGE_SCALE = 10.0;

    private final BarSeries series;
    private final int averagePeriod;
    private final Indicator<Num> body;
    private final Indicator<Num> range;
    private final Indicator<Num> upperShadow;
    private final Indicator<Num> lowerShadow;
    private final Indicator<Num> halfBody;
    private final Indicator<Num> halfRange;
    private final Indicator<Num> halfUpperShadow;
    private final Indicator<Num> halfLowerShadow;
    private final Indicator<Num> halfPriorAverageBody;
    private final Indicator<Num> halfPriorAverageRange;
    private final Indicator<Num> priorAverageBody;
    private final Indicator<Num> priorAverageRange;
    private final Indicator<Num> rawPriorAverageBody;
    private final Indicator<Num> rawPriorAverageRange;
    private final Num longBodyFactor;
    private final Num shortBodyFactor;
    private final Num dojiRangeFactor;
    private final Num longShadowFactor;
    private final Num shortShadowRangeFactor;
    private final Num nearRangeFactor;
    private final Num restoreFactor;
    private final Num rangeScale;

    /**
     * Constructor with the {@link #DEFAULT_AVERAGE_PERIOD default average period}.
     *
     * @param series the bar series to evaluate
     * @throws NullPointerException     if {@code series} is null
     * @throws IllegalArgumentException if {@code averagePeriod} is outside the
     *                                  supported range
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
     * @throws IllegalArgumentException if {@code averagePeriod} is outside the
     *                                  supported range
     */
    CandleThresholdSupport(BarSeries series, int averagePeriod) {
        this.series = validateSeriesAndAveragePeriod(series, averagePeriod);
        this.averagePeriod = averagePeriod;
        this.body = new CandleBodyIndicator(this.series);
        this.range = new CandleRangeIndicator(this.series);
        this.upperShadow = new UpperShadowIndicator(this.series);
        this.lowerShadow = new LowerShadowIndicator(this.series);
        this.halfBody = new HalfBodyIndicator(this.series);
        this.halfRange = new HalfRangeIndicator(this.series);
        this.halfUpperShadow = new HalfUpperShadowIndicator(this.series);
        this.halfLowerShadow = new HalfLowerShadowIndicator(this.series);
        this.halfPriorAverageBody = new PreviousValueIndicator(new PriorAverageIndicator(halfBody, averagePeriod));
        this.halfPriorAverageRange = new PreviousValueIndicator(new PriorAverageIndicator(halfRange, averagePeriod));
        this.priorAverageBody = new DoubledIndicator(halfPriorAverageBody);
        this.priorAverageRange = new DoubledIndicator(halfPriorAverageRange);
        final NumFactory numFactory = this.series.numFactory();
        // Raw full-scale rolling baselines: used only to preserve strict ordering
        // between adjacent subnormal magnitudes that the half-scale path below
        // collapses. Their windows can overflow, which is fine because every
        // comparison falls back to the half-scale path when a raw operand is
        // non-finite.
        this.rawPriorAverageBody = new PreviousValueIndicator(new PriorAverageIndicator(body, averagePeriod));
        this.rawPriorAverageRange = new PreviousValueIndicator(new PriorAverageIndicator(range, averagePeriod));
        this.longBodyFactor = numFactory.numOf(LONG_BODY_FACTOR);
        this.shortBodyFactor = numFactory.numOf(SHORT_BODY_FACTOR);
        this.dojiRangeFactor = numFactory.numOf(DOJI_RANGE_FACTOR);
        this.longShadowFactor = numFactory.numOf(LONG_SHADOW_FACTOR);
        this.shortShadowRangeFactor = numFactory.numOf(SHORT_SHADOW_RANGE_FACTOR);
        this.nearRangeFactor = numFactory.numOf(NEAR_RANGE_FACTOR);
        this.restoreFactor = numFactory.numOf(2);
        this.rangeScale = numFactory.numOf(RANGE_SCALE);
    }

    /**
     * Validates the series and average period shared by every construction path.
     *
     * @param series        the bar series to evaluate
     * @param averagePeriod the number of preceding candles averaged into each
     *                      baseline
     * @return the validated series
     * @throws NullPointerException     if {@code series} is null
     * @throws IllegalArgumentException if {@code averagePeriod} is below 1 or above
     *                                  {@link #MAX_AVERAGE_PERIOD}, which bounds
     *                                  the warm-up window and intermediate sums
     */
    static BarSeries validateSeriesAndAveragePeriod(BarSeries series, int averagePeriod) {
        final BarSeries validatedSeries = Objects.requireNonNull(series, "series must not be null");
        if (averagePeriod < 1 || averagePeriod > MAX_AVERAGE_PERIOD) {
            throw new IllegalArgumentException(
                    "averagePeriod must be in [1, " + MAX_AVERAGE_PERIOD + "], but was: " + averagePeriod);
        }
        return validatedSeries;
    }

    /**
     * Validates the series, average period, and penetration fraction shared by the
     * piercing/dark-cloud and star pattern construction paths.
     *
     * @param series        the bar series to evaluate
     * @param averagePeriod the number of preceding candles averaged into each
     *                      baseline
     * @param penetration   the fraction of the first body the second close must
     *                      penetrate
     * @return the validated series
     * @throws NullPointerException     if {@code series} is null
     * @throws IllegalArgumentException if {@code averagePeriod} is outside the
     *                                  supported range or {@code penetration} is
     *                                  not finite or is outside (0, 1]
     */
    static BarSeries validateSeriesAndAveragePeriodAndPenetration(BarSeries series, int averagePeriod,
            double penetration) {
        BarSeries validatedSeries = validateSeriesAndAveragePeriod(series, averagePeriod);
        if (!Double.isFinite(penetration) || penetration <= 0 || penetration > 1) {
            throw new IllegalArgumentException("penetration must be finite and in (0, 1], but was: " + penetration);
        }
        return validatedSeries;
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
     * The cached body magnitude primitive shared with every pattern indicator
     * interned over the same series.
     *
     * @return the body magnitude indicator
     */
    Indicator<Num> bodyIndicator() {
        return body;
    }

    /**
     * The cached candle range primitive shared with every pattern indicator
     * interned over the same series.
     *
     * @return the candle range indicator
     */
    Indicator<Num> rangeIndicator() {
        return range;
    }

    /**
     * The cached upper shadow primitive shared with every pattern indicator
     * interned over the same series.
     *
     * @return the upper shadow indicator
     */
    Indicator<Num> upperShadow() {
        return upperShadow;
    }

    /**
     * The cached lower shadow primitive shared with every pattern indicator
     * interned over the same series.
     *
     * @return the lower shadow indicator
     */
    Indicator<Num> lowerShadow() {
        return lowerShadow;
    }

    /**
     * The half-scale shifted rolling average of the preceding {@code averagePeriod}
     * candle ranges. Consumers apply a reducing factor to this value before
     * restoring full scale, so a raw mean that would overflow stays decidable.
     *
     * @return the half-scale prior average range indicator
     * @since 0.24.2
     */
    Indicator<Num> halfPriorAverageRange() {
        return halfPriorAverageRange;
    }

    /**
     * Whether a full preceding window exists at {@code index}, i.e.
     * {@code index >= series.getBeginIndex() + averagePeriod}, evaluated in
     * {@code long} arithmetic so a large begin index cannot overflow.
     *
     * @param index the candle index
     * @return {@code true} if every predicate can be evaluated at {@code index}
     */
    boolean isValid(int index) {
        return index >= (long) series.getBeginIndex() + averagePeriod;
    }

    /**
     * Whether the candle body is greater than {@value #LONG_BODY_FACTOR} times the
     * prior average body.
     *
     * <p>
     * The raw body is compared against the raw-scale prior average body whenever
     * both are finite, so two adjacent subnormal magnitudes stay strictly ordered
     * instead of collapsing onto the same half-scale value. Otherwise the
     * comparison is performed at half scale: the half body is measured against the
     * half-scale prior average body, so two magnitudes whose raw values would both
     * overflow still keep a decidable strict ordering across {@link NumFactory}s.
     * An unavailable (NaN) measurement is never classified.
     *
     * @param index the candle index
     * @return {@code true} for a long body, {@code false} below the warm-up
     *         boundary, for a short body, or for an unavailable measurement
     */
    boolean isLongBody(int index) {
        if (!isValid(index)) {
            return false;
        }
        final Num bodyValue = halfBody.getValue(index);
        final Num baseline = halfPriorAverageBody.getValue(index).multipliedBy(longBodyFactor);
        if (Num.isNaNOrNull(bodyValue) || Num.isNaNOrNull(baseline)) {
            return false;
        }
        final Num rawBody = body.getValue(index);
        final Num rawBaseline = rawPriorAverageBody.getValue(index).multipliedBy(longBodyFactor);
        if (Num.isFinite(rawBody) && Num.isFinite(rawBaseline)) {
            return rawBody.isGreaterThan(rawBaseline);
        }
        return bodyValue.isGreaterThan(baseline);
    }

    /**
     * Whether the candle body is less than {@value #SHORT_BODY_FACTOR} times the
     * prior average body.
     *
     * <p>
     * The comparison {@code body < factor * priorBody} is evaluated as
     * {@code body / factor < priorBody} on the raw-scale operands whenever both are
     * finite: dividing by the power-of-two factor doubles the body exactly, whereas
     * multiplying an odd subnormal baseline by the factor can round it onto the
     * measured body and lose the strict ordering. Otherwise the comparison is
     * performed at half scale: the half body is measured against the half-scale
     * prior average body, so a finite half body stays decidable against a baseline
     * whose raw mean would overflow. An unavailable (NaN) measurement is never
     * classified.
     *
     * @param index the candle index
     * @return {@code true} for a short body, {@code false} below the warm-up
     *         boundary, for a long body, or for an unavailable measurement
     */
    boolean isShortBody(int index) {
        if (!isValid(index)) {
            return false;
        }
        final Num bodyValue = halfBody.getValue(index);
        final Num baseline = halfPriorAverageBody.getValue(index).multipliedBy(shortBodyFactor);
        if (Num.isNaNOrNull(bodyValue) || Num.isNaNOrNull(baseline)) {
            return false;
        }
        final Num rawBody = body.getValue(index);
        final Num rawBaseline = rawPriorAverageBody.getValue(index);
        if (Num.isFinite(rawBody) && Num.isFinite(rawBaseline)) {
            return rawBody.dividedBy(shortBodyFactor).isLessThan(rawBaseline);
        }
        return bodyValue.isLessThan(baseline);
    }

    /**
     * Whether the candle body is at most {@value #DOJI_RANGE_FACTOR} of the prior
     * average range, the classic doji neighborhood.
     *
     * <p>
     * The comparison {@code body <= factor * range} is evaluated as
     * {@code body * 10 <= range} on the raw-scale operands whenever both are
     * finite: the factor is one tenth, which is not exactly representable in binary
     * floating point, so scaling the measured body keeps two adjacent subnormal
     * magnitudes strictly ordered instead of collapsing onto the same half-scale
     * value. Otherwise the comparison is performed at half scale: the half body is
     * measured against the half-scale prior average range, so an overflowed body or
     * baseline stays decidable across {@link NumFactory}s. An unavailable (NaN)
     * measurement is never classified.
     *
     * @param index the candle index
     * @return {@code true} for a doji-like body, {@code false} below the warm-up
     *         boundary, for a substantial body, or for an unavailable measurement
     */
    boolean isDoji(int index) {
        if (!isValid(index)) {
            return false;
        }
        final Num bodyValue = halfBody.getValue(index);
        final Num baseline = halfPriorAverageRange.getValue(index).multipliedBy(dojiRangeFactor);
        if (Num.isNaNOrNull(bodyValue) || Num.isNaNOrNull(baseline)) {
            return false;
        }
        final Num rawBody = body.getValue(index);
        final Num rawBaseline = rawPriorAverageRange.getValue(index);
        if (Num.isFinite(rawBody) && Num.isFinite(rawBaseline)) {
            final Num scaledBody = rawBody.multipliedBy(rangeScale);
            if (Num.isFinite(scaledBody)) {
                return !scaledBody.isGreaterThan(rawBaseline);
            }
        }
        return !bodyValue.isGreaterThan(baseline);
    }

    /**
     * Whether the given shadow measurement is greater than
     * {@value #LONG_SHADOW_FACTOR} times the prior average body.
     *
     * <p>
     * The comparison {@code shadow > factor * baseline} is evaluated as
     * {@code shadow > baseline * factor} on the raw-scale operands whenever the
     * shadow is finite, so two adjacent subnormal magnitudes stay strictly ordered
     * instead of collapsing onto the same half-scale quotient; a baseline whose
     * doubled value overflows is definitively larger than any finite shadow, so the
     * answer is false. A shadow whose raw magnitude overflowed into infinity from
     * finite source prices is rebuilt exactly at half scale from the bar prices
     * (via the interned half-scale shadow primitive matching {@code shadow}) and
     * compared against the baseline, which stays finite for representable data; a
     * non-finite measurement from any other shadow indicator keeps the historical
     * decidable contract of being longer than any finite baseline. An unavailable
     * (NaN) measurement is never classified; an overflowed shadow from non-finite
     * source prices (missing data) is rejected: a shadow derived from an
     * unavailable price never qualifies as long.
     *
     * @param index  the candle index
     * @param shadow the shadow measurement to evaluate (upper or lower)
     * @return {@code true} for a long shadow, {@code false} below the warm-up
     *         boundary, for a short shadow, or for an unavailable measurement
     */
    boolean isLongShadow(int index, Indicator<Num> shadow) {
        if (!isValid(index)) {
            return false;
        }
        final Num shadowValue = shadow.getValue(index);
        final Num baseline = priorAverageBody.getValue(index);
        if (Num.isNaNOrNull(shadowValue) || Num.isNaNOrNull(baseline)) {
            return false;
        }
        if (Num.isFinite(shadowValue)) {
            final Num doubledBaseline = baseline.multipliedBy(longShadowFactor);
            if (!Num.isFinite(doubledBaseline)) {
                // Two times an overflowed baseline exceeds any finite shadow.
                return false;
            }
            return shadowValue.isGreaterThan(doubledBaseline);
        }
        if (!hasFiniteSourcePrices(index)) {
            // An infinite shadow from an unavailable or non-finite price is
            // missing data, not an overflowed finite magnitude.
            return false;
        }
        // Overflowed raw magnitude from finite source prices: rebuild the exact
        // half-scale shadow and compare it against the full-scale baseline.
        final Indicator<Num> halfShadow = shadow == upperShadow ? halfUpperShadow
                : shadow == lowerShadow ? halfLowerShadow : null;
        if (halfShadow == null) {
            // A non-finite measurement from an arbitrary shadow indicator: a
            // magnitude that overflowed from finite operands stays longer than
            // any finite baseline (a negative infinity compares false, a NaN
            // was rejected above).
            return shadowValue.isGreaterThan(baseline);
        }
        return halfShadow.getValue(index).isGreaterThan(baseline);
    }

    /**
     * Whether all four source prices of the candle at {@code index} are finite,
     * i.e. the bar carries no missing or non-finite OHLC value.
     *
     * @param index the candle index
     * @return {@code true} when open, close, high, and low are all finite
     */
    private boolean hasFiniteSourcePrices(int index) {
        final Bar bar = this.series.getBar(index);
        return Num.isFinite(bar.getOpenPrice()) && Num.isFinite(bar.getClosePrice()) && Num.isFinite(bar.getHighPrice())
                && Num.isFinite(bar.getLowPrice());
    }

    /**
     * Whether the given shadow measurement is at most
     * {@value #SHORT_SHADOW_RANGE_FACTOR} of the prior average range.
     *
     * <p>
     * The comparison {@code shadow <= factor * range} is evaluated as
     * {@code shadow * 10 <= range} on the raw-scale operands whenever both are
     * finite: the factor is one tenth, which is not exactly representable in binary
     * floating point, so scaling the measured shadow keeps two adjacent subnormal
     * magnitudes strictly ordered instead of collapsing onto the same half-scale
     * value. Otherwise the factor is applied to the half-scale prior average range
     * before the baseline is restored to full scale, so the threshold stays finite
     * when the raw mean would overflow and a finite shadow stays decidable across
     * {@link NumFactory}s. An unavailable (NaN) measurement is never classified; an
     * overflowed shadow from non-finite source prices (missing data) is rejected: a
     * shadow derived from an unavailable price never qualifies as short. An
     * overflowed magnitude from finite source prices stays decidable, so it can
     * never qualify as a short shadow.
     *
     * @param index  the candle index
     * @param shadow the shadow measurement to evaluate (upper or lower)
     * @return {@code true} for a short shadow, {@code false} below the warm-up
     *         boundary, for a long shadow, or for an unavailable measurement
     */
    boolean isShortShadow(int index, Indicator<Num> shadow) {
        if (!isValid(index)) {
            return false;
        }
        final Num shadowValue = shadow.getValue(index);
        final Num baseline = halfPriorAverageRange.getValue(index)
                .multipliedBy(shortShadowRangeFactor)
                .multipliedBy(restoreFactor);
        if (Num.isNaNOrNull(shadowValue) || Num.isNaNOrNull(baseline)) {
            return false;
        }
        if (!Num.isFinite(shadowValue) && !hasFiniteSourcePrices(index)) {
            // An infinite shadow from an unavailable or non-finite price is
            // missing data, not an overflowed finite magnitude.
            return false;
        }
        if (Num.isFinite(shadowValue)) {
            final Num rawBaseline = rawPriorAverageRange.getValue(index);
            if (Num.isFinite(rawBaseline)) {
                final Num scaledShadow = shadowValue.multipliedBy(rangeScale);
                if (Num.isFinite(scaledShadow)) {
                    return !scaledShadow.isGreaterThan(rawBaseline);
                }
            }
        }
        return !shadowValue.isGreaterThan(baseline);
    }

    /**
     * Whether the absolute difference between the two measurements is at most
     * {@value #NEAR_RANGE_FACTOR} of the prior average range.
     *
     * <p>
     * Both the difference and the baseline are computed at half scale, so a raw
     * difference or mean that would overflow stays decidable across
     * {@link NumFactory}s, and a subnormal difference is not erased by the halving.
     *
     * @param index  the candle index
     * @param first  the first measurement
     * @param second the second measurement
     * @return {@code true} when the two measurements are near each other,
     *         {@code false} below the warm-up boundary, when they diverge, or for a
     *         missing or non-finite measurement
     */
    boolean isNear(int index, Indicator<Num> first, Indicator<Num> second) {
        if (!isValid(index)) {
            return false;
        }
        final Num firstValue = first.getValue(index);
        final Num secondValue = second.getValue(index);
        if (firstValue == null || secondValue == null) {
            return false;
        }
        return isNear(index, firstValue, secondValue);
    }

    /**
     * Whether the absolute difference between two measurements is at most
     * {@value #NEAR_RANGE_FACTOR} of the prior average range.
     *
     * <p>
     * The comparison {@code |first - second| <= factor * range} is evaluated as
     * {@code |first - second| * 10 <= range} on the raw-scale operands whenever
     * both are finite: the factor is one tenth, which is not exactly representable
     * in binary floating point, so scaling the measured difference keeps two
     * adjacent subnormal magnitudes strictly ordered instead of collapsing onto the
     * same half-scale value. Otherwise both the difference and the baseline are
     * computed at half scale, so a raw difference or mean that would overflow stays
     * decidable across {@link NumFactory}s, and a subnormal difference is not
     * erased by the halving.
     *
     * @param index  the candle index
     * @param first  the first measurement
     * @param second the second measurement
     * @return {@code true} when the two measurements are near each other,
     *         {@code false} below the warm-up boundary, when they diverge, or for a
     *         non-finite measurement
     * @since 0.24.2
     */
    boolean isNear(int index, Num first, Num second) {
        if (!isValid(index)) {
            return false;
        }
        if (Num.isFinite(first) && Num.isFinite(second)) {
            final Num rawDifference = first.minus(second).abs();
            final Num rawBaseline = rawPriorAverageRange.getValue(index);
            if (Num.isFinite(rawDifference) && Num.isFinite(rawBaseline)) {
                final Num scaledDifference = rawDifference.multipliedBy(rangeScale);
                if (Num.isFinite(scaledDifference)) {
                    return !scaledDifference.isGreaterThan(rawBaseline);
                }
            }
        }
        final Num halfDifference = halfDifference(first, second, series.numFactory());
        final Num baseline = halfPriorAverageRange.getValue(index).multipliedBy(nearRangeFactor);
        return Num.isFinite(halfDifference) && Num.isFinite(baseline) && !halfDifference.isGreaterThan(baseline);
    }

    /**
     * The absolute difference of two magnitudes, each divided by two before
     * differencing, so the result stays finite when the raw difference would
     * overflow. When both halves underflow to zero while the operands differ, the
     * raw difference is returned instead: it is subnormal, cannot overflow, and
     * would otherwise be erased by the halving. Non-finite operands propagate.
     *
     * @param first  the first operand
     * @param second the second operand
     * @return the half-scale difference, or the raw difference when halving
     *         underflows
     * @since 0.24.2
     */
    private static Num halfDifference(Num first, Num second, NumFactory numFactory) {
        final Num two = numFactory.numOf(2);
        final Num half = first.dividedBy(two).minus(second.dividedBy(two)).abs();
        if (!half.isZero() || first.equals(second)) {
            return half;
        }
        return first.minus(second).abs();
    }

    /**
     * Half of the candle body, i.e. {@code |open - close| / 2} computed by dividing
     * each operand before differencing, so the result stays finite whenever the raw
     * body magnitude would overflow the {@link Num} type; a subnormal raw body is
     * retained instead of being erased by the halving. A non-finite endpoint yields
     * {@link NaN#NaN}, mirroring {@link CandleBodyIndicator}.
     */
    private static final class HalfBodyIndicator extends CachedIndicator<Num> {
        private HalfBodyIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            final Bar bar = getBarSeries().getBar(index);
            final Num open = bar.getOpenPrice();
            final Num close = bar.getClosePrice();
            if (!Num.isFinite(open) || !Num.isFinite(close)) {
                return NaN.NaN;
            }
            return halfDifference(open, close, getBarSeries().numFactory());
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    /**
     * Half of the candle range, i.e. {@code (high - low) / 2} computed by dividing
     * each operand before differencing, so the result stays finite whenever the raw
     * range magnitude would overflow the {@link Num} type; a subnormal raw range is
     * retained instead of being erased by the halving. A non-finite endpoint yields
     * {@link NaN#NaN}, mirroring {@link CandleRangeIndicator}.
     */
    private static final class HalfRangeIndicator extends CachedIndicator<Num> {
        private HalfRangeIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            final Bar bar = getBarSeries().getBar(index);
            final Num high = bar.getHighPrice();
            final Num low = bar.getLowPrice();
            if (!Num.isFinite(high) || !Num.isFinite(low)) {
                return NaN.NaN;
            }
            return halfDifference(high, low, getBarSeries().numFactory());
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    /**
     * The signed difference of two magnitudes, each divided by two before
     * differencing, so the result stays finite when the raw difference would
     * overflow and a negative measurement (malformed bar) keeps its ordering. When
     * both halves underflow to zero while the operands differ, the raw signed
     * difference is returned instead: it is subnormal, cannot overflow, and would
     * otherwise be erased by the halving. Non-finite operands propagate.
     *
     * @param first  the first operand
     * @param second the second operand
     * @return the half-scale signed difference, or the raw difference when halving
     *         underflows
     * @since 0.24.2
     */
    private static Num halfSignedDifference(Num first, Num second, NumFactory numFactory) {
        final Num two = numFactory.numOf(2);
        final Num half = first.dividedBy(two).minus(second.dividedBy(two));
        if (!half.isZero() || first.equals(second)) {
            return half;
        }
        return first.minus(second);
    }

    /**
     * Half of the upper shadow, i.e. {@code (high - max(open, close)) / 2} computed
     * by dividing each operand before differencing, so the result stays finite
     * whenever the raw shadow would overflow the {@link Num} type and a negative
     * measurement (malformed bar) keeps its ordering; a subnormal raw shadow is
     * retained instead of being erased by the halving. A non-finite endpoint yields
     * {@link NaN#NaN}, mirroring {@link UpperShadowIndicator}.
     */
    private static final class HalfUpperShadowIndicator extends CachedIndicator<Num> {
        private HalfUpperShadowIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            final Bar bar = getBarSeries().getBar(index);
            final Num open = bar.getOpenPrice();
            final Num close = bar.getClosePrice();
            final Num high = bar.getHighPrice();
            if (!Num.isFinite(high) || !Num.isFinite(open) || !Num.isFinite(close)) {
                return NaN.NaN;
            }
            final Num peak = close.isGreaterThan(open) ? close : open;
            return halfSignedDifference(high, peak, getBarSeries().numFactory());
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    /**
     * Half of the lower shadow, i.e. {@code (min(open, close) - low) / 2} computed
     * by dividing each operand before differencing, so the result stays finite
     * whenever the raw shadow would overflow the {@link Num} type and a negative
     * measurement (malformed bar) keeps its ordering; a subnormal raw shadow is
     * retained instead of being erased by the halving. A non-finite endpoint yields
     * {@link NaN#NaN}, mirroring {@link LowerShadowIndicator}.
     */
    private static final class HalfLowerShadowIndicator extends CachedIndicator<Num> {
        private HalfLowerShadowIndicator(BarSeries series) {
            super(series);
        }

        @Override
        protected Num calculate(int index) {
            final Bar bar = getBarSeries().getBar(index);
            final Num open = bar.getOpenPrice();
            final Num close = bar.getClosePrice();
            final Num low = bar.getLowPrice();
            if (!Num.isFinite(low) || !Num.isFinite(open) || !Num.isFinite(close)) {
                return NaN.NaN;
            }
            final Num trough = close.isGreaterThan(open) ? open : close;
            return halfSignedDifference(trough, low, getBarSeries().numFactory());
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    /**
     * Full-scale view of a half-scale source: the source value doubled. Doubling
     * restores the magnitude that consumers comparing raw measurements (shadows,
     * the baseline accessors) expect, and preserves the overflow behavior those
     * comparisons documented before the half-scale migration: a doubled magnitude
     * that overflows stays non-finite, exactly as the raw mean did.
     *
     * @since 0.24.2
     */
    private static final class DoubledIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> source;
        private final Num two;

        private DoubledIndicator(Indicator<Num> source) {
            super(source);
            this.source = source;
            this.two = source.getBarSeries().numFactory().numOf(2);
        }

        @Override
        protected Num calculate(int index) {
            return source.getValue(index).multipliedBy(two);
        }

        @Override
        public int getCountOfUnstableBars() {
            return source.getCountOfUnstableBars();
        }
    }

    /**
     * Average of the preceding {@code barCount} half-scale source values, kept at
     * half scale, that never overflows an intermediate summation. The primary path
     * delegates to an {@link SMAIndicator}; when the accumulator overflowed into a
     * non-finite value (for example a {@code DoubleNum} summing several near-MAX
     * halves), the same window is re-averaged as an incremental mean, which never
     * accumulates a running sum beyond the window maximum. Because every term is at
     * most half the largest representable magnitude, the exact mean is at most the
     * largest representable magnitude, so the half-scale mean stays finite whenever
     * it is representable; a baseline that still overflows remains non-finite yet
     * decidable in every comparison below. Full-scale views are derived by the
     * owning support only where a consumer still expects raw magnitudes. Non-finite
     * source values propagate as non-finite results.
     */
    private static final class PriorAverageIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> source;
        private final int barCount;
        private final SMAIndicator primary;

        private PriorAverageIndicator(Indicator<Num> source, int barCount) {
            super(source);

            this.source = source;
            this.barCount = barCount;
            this.primary = new SMAIndicator(source, barCount);
        }

        @Override
        protected Num calculate(int index) {
            final Num result = primary.getValue(index);
            if (Num.isFinite(result)) {
                return result;
            }
            final int beginIndex = getBarSeries().getBeginIndex();
            final int start = Math.max(beginIndex, index - barCount + 1);
            Num mean = getBarSeries().numFactory().zero();
            for (int i = start; i <= index; i++) {
                final Num value = source.getValue(i);
                if (!Num.isFinite(value)) {
                    return value;
                }
                mean = mean.plus(value.minus(mean).dividedBy(getBarSeries().numFactory().numOf(i - start + 1)));
            }
            return mean;
        }

        @Override
        public int getCountOfUnstableBars() {
            return primary.getCountOfUnstableBars();
        }
    }
}
