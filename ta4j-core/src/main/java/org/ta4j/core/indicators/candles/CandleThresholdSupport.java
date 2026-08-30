/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
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
 * implemented as {@code new PreviousValueIndicator(new PriorAverageIndicator(m,
 * averagePeriod))}. {@link PriorAverageIndicator} delegates to an SMA and, when
 * the SMA accumulator overflows into a non-finite value, re-averages the same
 * window with every term normalized by the window's largest magnitude. The
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

    private final BarSeries series;
    private final int averagePeriod;
    private final Indicator<Num> body;
    private final Indicator<Num> range;
    private final Indicator<Num> upperShadow;
    private final Indicator<Num> lowerShadow;
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
        this.priorAverageBody = new PreviousValueIndicator(new PriorAverageIndicator(body, averagePeriod));
        this.priorAverageRange = new PreviousValueIndicator(new PriorAverageIndicator(range, averagePeriod));
        final NumFactory numFactory = this.series.numFactory();
        this.longBodyFactor = numFactory.numOf(LONG_BODY_FACTOR);
        this.shortBodyFactor = numFactory.numOf(SHORT_BODY_FACTOR);
        this.dojiRangeFactor = numFactory.numOf(DOJI_RANGE_FACTOR);
        this.longShadowFactor = numFactory.numOf(LONG_SHADOW_FACTOR);
        this.shortShadowRangeFactor = numFactory.numOf(SHORT_SHADOW_RANGE_FACTOR);
        this.nearRangeFactor = numFactory.numOf(NEAR_RANGE_FACTOR);
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
     * An unavailable (NaN) measurement is never classified. A magnitude that
     * overflows finite operands stays non-finite yet decidable, so it still
     * participates in the strict comparison instead of being rejected, mirroring
     * the operand-finiteness contract of {@link CandleBodyIndicator}.
     *
     * @param index the candle index
     * @return {@code true} for a long body, {@code false} below the warm-up
     *         boundary, for a short body, or for an unavailable measurement
     */
    boolean isLongBody(int index) {
        if (!isValid(index)) {
            return false;
        }
        final Num bodyValue = body.getValue(index);
        final Num baseline = priorAverageBody.getValue(index).multipliedBy(longBodyFactor);
        return !Num.isNaNOrNull(bodyValue) && !Num.isNaNOrNull(baseline) && bodyValue.isGreaterThan(baseline);
    }

    /**
     * Whether the candle body is less than {@value #SHORT_BODY_FACTOR} times the
     * prior average body.
     *
     * <p>
     * An unavailable (NaN) measurement is never classified; an overflowed magnitude
     * from finite operands stays decidable, so a finite body still qualifies as
     * short against an overflowed baseline.
     *
     * @param index the candle index
     * @return {@code true} for a short body, {@code false} below the warm-up
     *         boundary, for a long body, or for an unavailable measurement
     */
    boolean isShortBody(int index) {
        if (!isValid(index)) {
            return false;
        }
        final Num bodyValue = body.getValue(index);
        final Num baseline = priorAverageBody.getValue(index).multipliedBy(shortBodyFactor);
        return !Num.isNaNOrNull(bodyValue) && !Num.isNaNOrNull(baseline) && bodyValue.isLessThan(baseline);
    }

    /**
     * Whether the candle body is at most {@value #DOJI_RANGE_FACTOR} of the prior
     * average range, the classic doji neighborhood.
     *
     * <p>
     * An unavailable (NaN) measurement is never classified; an overflowed magnitude
     * from finite operands stays decidable, so it can never qualify as a doji-sized
     * body.
     *
     * @param index the candle index
     * @return {@code true} for a doji-like body, {@code false} below the warm-up
     *         boundary, for a substantial body, or for an unavailable measurement
     */
    boolean isDoji(int index) {
        if (!isValid(index)) {
            return false;
        }
        final Num bodyValue = body.getValue(index);
        final Num baseline = priorAverageRange.getValue(index).multipliedBy(dojiRangeFactor);
        return !Num.isNaNOrNull(bodyValue) && !Num.isNaNOrNull(baseline) && !bodyValue.isGreaterThan(baseline);
    }

    /**
     * Whether the given shadow measurement is greater than
     * {@value #LONG_SHADOW_FACTOR} times the prior average body.
     *
     * <p>
     * An unavailable (NaN) measurement is never classified; an overflowed magnitude
     * from finite operands stays decidable, so it still participates in the strict
     * comparison instead of being rejected.
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
        final Num baseline = priorAverageBody.getValue(index).multipliedBy(longShadowFactor);
        return !Num.isNaNOrNull(shadowValue) && !Num.isNaNOrNull(baseline) && shadowValue.isGreaterThan(baseline);
    }

    /**
     * Whether the given shadow measurement is at most
     * {@value #SHORT_SHADOW_RANGE_FACTOR} of the prior average range.
     *
     * <p>
     * An unavailable (NaN) measurement is never classified; an overflowed magnitude
     * from finite operands stays decidable, so it can never qualify as a short
     * shadow.
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
        final Num baseline = priorAverageRange.getValue(index).multipliedBy(shortShadowRangeFactor);
        return !Num.isNaNOrNull(shadowValue) && !Num.isNaNOrNull(baseline) && !shadowValue.isGreaterThan(baseline);
    }

    /**
     * Whether the absolute difference between the two measurements is at most
     * {@value #NEAR_RANGE_FACTOR} of the prior average range.
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
        final Num difference = firstValue.minus(secondValue).abs();
        final Num baseline = priorAverageRange.getValue(index).multipliedBy(nearRangeFactor);
        return Num.isFinite(firstValue) && Num.isFinite(secondValue) && Num.isFinite(difference)
                && Num.isFinite(baseline) && !difference.isGreaterThan(baseline);
    }

    /**
     * Average of the preceding {@code barCount} source values that never overflows
     * an intermediate summation. The primary path delegates to an
     * {@link SMAIndicator}; when its accumulator overflowed into a non-finite value
     * (for example a {@code DoubleNum} summing near-MAX candle ranges), the same
     * window is re-averaged with every term divided by the largest magnitude in the
     * window, and the final scale factor is clamped to at most 1 before scaling
     * back, so no intermediate sum or final product can overflow. Non-finite source
     * values propagate as non-finite results.
     */
    private static final class PriorAverageIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> source;
        private final int barCount;
        private final SMAIndicator primary;

        private PriorAverageIndicator(Indicator<Num> source, int barCount) {
            super(source.getBarSeries());
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
            final Num count = getBarSeries().numFactory().numOf(index - start + 1);
            Num max = getBarSeries().numFactory().zero();
            for (int i = start; i <= index; i++) {
                final Num value = source.getValue(i);
                if (!Num.isFinite(value)) {
                    return value;
                }
                if (value.abs().isGreaterThan(max)) {
                    max = value.abs();
                }
            }
            if (max.isZero()) {
                return getBarSeries().numFactory().zero();
            }
            Num scaledSum = getBarSeries().numFactory().zero();
            for (int i = start; i <= index; i++) {
                scaledSum = scaledSum.plus(source.getValue(i).dividedBy(max));
            }
            Num factor = scaledSum.dividedBy(count);
            if (factor.isGreaterThan(getBarSeries().numFactory().one())) {
                factor = getBarSeries().numFactory().one();
            }
            return max.multipliedBy(factor);
        }

        @Override
        public int getCountOfUnstableBars() {
            return primary.getCountOfUnstableBars();
        }
    }
}
