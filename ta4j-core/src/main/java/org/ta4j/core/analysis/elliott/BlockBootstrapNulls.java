/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.function.Consumer;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

/**
 * Deterministic stationary-block-bootstrap null generator.
 *
 * <p>
 * The generator resamples contiguous blocks of observed log returns on a
 * circular return tape. Reconstructing prices from those returns preserves the
 * observed return scale and local dependence while retaining the original bar
 * timestamps and non-price fields. The source series is never mutated.
 * </p>
 */
final class BlockBootstrapNulls {

    private static final long SEED_MULTIPLIER = 1_000_003L;

    /**
     * Largest |log return| applied as a direct {@code previous * exp(return)} step;
     * exp(+/-700) stays a finite positive double with wide margin from both the
     * overflow (~709.78) and underflow-to-zero boundaries.
     */
    private static final double MAX_DIRECT_EXPONENT = 700d;

    /**
     * Direct multiplication keeps consecutive-close ratios exact, so it is the
     * preferred reconstruction whenever the factor itself is representable and, for
     * range-bounded Num domains such as {@link DoubleNum}, the accumulated product
     * also stays inside double range. Beyond those bounds only the decomposed
     * absolute-log path can still produce finite values.
     */
    /**
     * Mirrors the package-private {@code Num.isFinite} for double-backed nums: only
     * primitive-backed infinities can slip past the positivity check.
     */
    /**
     * A range-bounded double-backed domain holds the direct product only while it
     * stays finite and strictly positive; unbounded domains hold any result.
     */
    private static boolean representableInDomain(final NumFactory numFactory, final Num value) {
        if (!(numFactory instanceof DoubleNumFactory)) {
            return true;
        }
        return value.getDelegate() instanceof Double delegate && Double.isFinite(delegate) && delegate > 0d;
    }

    private static boolean nonFiniteDouble(final Num value) {
        return value.getDelegate() instanceof Double delegate && !Double.isFinite(delegate);
    }

    private BlockBootstrapNulls() {
    }

    /**
     * Generates a deterministic ensemble for one expected block length.
     *
     * @param source       source price series
     * @param blockLength  expected block length in bars
     * @param ensembleSize number of null series
     * @param seed         stable ensemble seed
     * @return immutable null series in ensemble order
     */
    static List<BarSeries> generate(final BarSeries source, final int blockLength, final int ensembleSize,
            final long seed) {
        final List<BarSeries> generated = new ArrayList<>(ensembleSize);
        forEachMember(source, blockLength, ensembleSize, seed, generated::add);
        return List.copyOf(generated);
    }

    /**
     * Generates each deterministic ensemble member and releases it after the
     * consumer returns.
     *
     * @param source       source price series
     * @param blockLength  expected block length in bars
     * @param ensembleSize number of null series
     * @param seed         stable ensemble seed
     * @param consumer     member consumer
     */
    static void forEachMember(final BarSeries source, final int blockLength, final int ensembleSize, final long seed,
            final Consumer<BarSeries> consumer) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(consumer, "consumer");
        if (blockLength <= 0 || ensembleSize <= 0) {
            throw new IllegalArgumentException("blockLength and ensembleSize must be positive");
        }
        final int count = source.getBarCount();
        if (count < 2) {
            throw new IllegalArgumentException("stationary bootstrap requires at least two bars");
        }
        final double[] logReturns = logReturns(source);
        for (int ensembleIndex = 0; ensembleIndex < ensembleSize; ensembleIndex++) {
            final long memberSeed = seed * SEED_MULTIPLIER + ensembleIndex;
            consumer.accept(generateMember(source, logReturns, blockLength, memberSeed, ensembleIndex));
        }
    }

    /**
     * Close-to-close log returns of the source, one entry per bar transition.
     *
     * <p>
     * Representation floor: returns are stored as primitive doubles per the frozen
     * null protocol, so a relative move whose full-precision Num delta underflows
     * the smallest positive double (below roughly 4.9e-324) records zero. Such
     * moves are orders of magnitude below any recorded market tick quantization;
     * preserving them would require abandoning the frozen double return
     * representation.
     */
    static double[] logReturns(final BarSeries source) {
        final int count = source.getBarCount();
        final double[] returns = new double[count - 1];
        final NumFactory numFactory = source.numFactory();
        for (int offset = 1; offset < count; offset++) {
            // The relative delta stays in the active Num domain so DecimalNum
            // closes beyond double range keep a finite ratio AND tiny moves
            // such as 1e30 -> 1e30+1 survive the narrowing: log(1 + delta)
            // with delta computed at full precision keeps variation that a
            // narrowed ratio would round to zero.
            final Num previous = source.getBar(source.getBeginIndex() + offset - 1).getClosePrice();
            final Num current = source.getBar(source.getBeginIndex() + offset).getClosePrice();
            if (!previous.isPositive() || !current.isPositive()) {
                throw new IllegalArgumentException("bootstrap source close prices must be positive");
            }
            // DoubleNum accepts infinite delegates whose isPositive() is true;
            // feeding one into the decomposed logarithm would scale it by 1e300
            // forever. Reject non-finite range-bounded closes up front.
            if (nonFiniteDouble(previous) || nonFiniteDouble(current)) {
                throw new IllegalArgumentException(
                        "bootstrap source close prices must be finite; bar " + offset + " holds " + current);
            }
            final Num ratio = current.dividedBy(previous);
            final double narrowedRatio = ratio.doubleValue();
            if (Double.isFinite(narrowedRatio) && narrowedRatio != 0.0d) {
                final double d = ratio.minus(numFactory.one()).doubleValue();
                if (d != 0.0d && Math.abs(d) <= 0.5d) {
                    // Near unity the ratio's own rounding would swallow tiny
                    // moves; the full-precision Num delta narrows without
                    // collapsing down to the smallest positive double.
                    returns[offset - 1] = Math.log1p(d);
                } else {
                    returns[offset - 1] = Math.log(narrowedRatio);
                }
            } else {
                // The ratio overflowed or underflowed double; scaling a
                // non-finite Num would loop forever (an infinite DoubleNum
                // divided by the scale stays infinite), so take the difference
                // of individually decomposed close logarithms instead.
                returns[offset - 1] = logNum(numFactory, current) - logNum(numFactory, previous);
            }
        }
        return returns;
    }

    /**
     * Natural logarithm of a positive Num whose magnitude may exceed double range:
     * decompose the value into a representable significand plus powers of 1e300
     * before narrowing, so Infinity and zero never reach Math.log. Callers must
     * pass values that are finite and positive inside their own Num domain
     * (guaranteed by the positivity check above); each decomposition step then
     * strictly shrinks or grows the magnitude, so the loops terminate.
     */
    private static double logNum(final NumFactory numFactory, final Num value) {
        final Num scale = numFactory.numOf("1e300");
        final double logScale = Math.log(1e300);
        Num scaled = value;
        double narrowed = scaled.doubleValue();
        int applications = 0;
        while (Double.isInfinite(narrowed)) {
            scaled = scaled.dividedBy(scale);
            narrowed = scaled.doubleValue();
            applications++;
        }
        while (narrowed == 0.0d) {
            scaled = scaled.multipliedBy(scale);
            narrowed = scaled.doubleValue();
            applications--;
        }
        return Math.log(narrowed) + applications * logScale;
    }

    /**
     * e^y as a Num: the fractional part stays within double range and the integer
     * part becomes fast-exponentiation squaring inside the active Num domain, so
     * reconstruction survives returns beyond double range such as a single-bar jump
     * from 1 to 1e400.
     */
    private static Num expNum(final NumFactory numFactory, final double y) {
        if (y < 0.0d) {
            if (numFactory instanceof DoubleNumFactory) {
                return numFactory.numOf(Math.exp(y));
            }
            return numFactory.one().dividedBy(expNum(numFactory, -y));
        }
        final int whole = (int) Math.floor(y);
        Num result = numFactory.numOf(Math.exp(y - whole));
        Num base = numFactory.numOf(Math.E);
        int n = whole;
        while (n > 0) {
            if ((n & 1) == 1) {
                result = result.multipliedBy(base);
            }
            n >>= 1;
            if (n > 0) {
                base = base.multipliedBy(base);
            }
        }
        return result;
    }

    static BarSeries generateMember(final BarSeries source, final double[] logReturns, final int blockLength,
            final long seed, final int ensembleIndex) {
        final int count = source.getBarCount();
        final SplittableRandom random = new SplittableRandom(seed);
        // Member prices are reconstructed entirely in the active Num domain so
        // DecimalNum sources beyond double range neither overflow nor lose
        // high-precision return variation; only the bounded values passed to
        // Math.exp/Math.log narrow to double. Ordinary transitions multiply the
        // previous close directly; transitions too steep for any representable
        // multiplicative factor (both endpoints finite, yet exp(return) out of
        // double range, like MIN_VALUE -> MAX_VALUE) fall back to the
        // accumulated running log-close so no infinite close can materialize.
        final Num[] closes = new Num[count];
        closes[0] = source.getBar(source.getBeginIndex()).getClosePrice();

        final NumFactory numFactory = source.numFactory();
        final BarSeries result = new BaseBarSeriesBuilder().withName(source.getName() + "-null-" + ensembleIndex)
                .withNumFactory(numFactory)
                .build();
        // Intrabar shape travels WITH its resampled return: bar k of a member
        // carries the OHLC ratios of the source bar whose close-to-close return
        // was drawn for k, never the ratios at the same chronological position.
        // Otherwise every member would inherit the real series' wick sequence.
        final int[] shapePositions = new int[count];
        double runningLogClose = logNum(numFactory, closes[0]);
        int tapePosition = random.nextInt(logReturns.length);
        for (int offset = 1; offset < count; offset++) {
            if (offset > 1 && random.nextInt(blockLength) == 0) {
                tapePosition = random.nextInt(logReturns.length);
            }
            final double drawnReturn = logReturns[tapePosition];
            runningLogClose += drawnReturn;
            final Num previousClose = closes[offset - 1];
            Num direct = null;
            if (Math.abs(drawnReturn) <= MAX_DIRECT_EXPONENT) {
                // Exact relative step; ordinary-market members keep their tight
                // consecutive-close ratios. The product itself decides: a flat
                // MIN_VALUE source has an accumulated log-close near -744 yet a
                // perfectly representable direct close, so the accumulated-log
                // magnitude alone must never reject it.
                final Num candidate = previousClose.multipliedBy(expNum(numFactory, drawnReturn));
                if (representableInDomain(numFactory, candidate)) {
                    direct = candidate;
                }
            }
            if (direct != null) {
                closes[offset] = direct;
            } else {
                // A steeper transition (for example MIN_VALUE -> MAX_VALUE), or
                // an accumulated path outside double range, has no representable
                // multiplicative reconstruction; materialize from the running
                // log-close and reject what a range-bounded Num domain cannot
                // hold instead of silently writing zero or infinite closes.
                final Num reconstructed = expNum(numFactory, runningLogClose);
                if (numFactory instanceof DoubleNumFactory) {
                    final double narrowed = reconstructed.doubleValue();
                    if (!Double.isFinite(narrowed) || narrowed <= 0d) {
                        throw new IllegalStateException("resampled null path leaves double range at member bar "
                                + offset + " (accumulated log-close " + runningLogClose
                                + "); not representable in the active Num domain");
                    }
                }
                closes[offset] = reconstructed;
            }
            shapePositions[offset] = tapePosition + 1;
            tapePosition = (tapePosition + 1) % logReturns.length;
        }
        for (int offset = 0; offset < count; offset++) {
            // Timeline stays chronological; only the intrabar shape follows the
            // sampled return's source bar.
            final Bar timelineBar = source.getBar(source.getBeginIndex() + offset);
            final Bar shapeBar = offset == 0 ? timelineBar
                    : source.getBar(source.getBeginIndex() + shapePositions[offset]);
            final Num close = closes[offset];
            final Num sourceClose = shapeBar.getClosePrice();
            final Num open = scaled(shapeBar.getOpenPrice(), sourceClose, close);
            Num high = scaled(shapeBar.getHighPrice(), sourceClose, close);
            Num low = scaled(shapeBar.getLowPrice(), sourceClose, close);
            if (high == null || high.isLessThan(close)) {
                high = close;
            }
            if (low == null || low.isGreaterThan(close)) {
                low = close;
            }
            final Num volume = timelineBar.getVolume() == null ? numFactory.zero() : timelineBar.getVolume();
            final Num amount = timelineBar.getAmount() == null ? numFactory.zero() : timelineBar.getAmount();
            final BaseBar nullBar = new BaseBar(timelineBar.getTimePeriod(), timelineBar.getBeginTime(),
                    timelineBar.getEndTime(), open, high, low, close, volume, amount, timelineBar.getTrades());
            result.addBar(nullBar);
        }
        return result;
    }

    static Num scaled(final Num value, final Num sourceClose, final Num close) {
        if (value == null || !sourceClose.isPositive()) {
            return close;
        }
        // A wick equal to its source close scales by exactly one; short-circuit
        // so a range-bounded domain never overflows while computing ratio one
        // (for example a flat Double.MAX_VALUE source).
        if (value.equals(sourceClose)) {
            return close;
        }
        // Divide before multiplying: wick * memberClose can overflow even when
        // the mathematically exact scaled value is representable (for example
        // wick 1e308 against source close Double.MAX_VALUE), while the ratio
        // wick / sourceClose stays inside double range. The reordered product
        // preserves such wicks instead of clamping them to the close.
        Num scaled = close.multipliedBy(value.dividedBy(sourceClose));
        if (scaled.isZero() && !value.isZero() && !close.isZero()) {
            // If the ratio underflows before multiplication, reverse the
            // operations so a representable subnormal wick is not lost.
            scaled = value.multipliedBy(close.dividedBy(sourceClose));
        }
        // A range-bounded domain that cannot hold the scaled wick clamps to the
        // member close instead of letting infinity or NaN reach BaseBar.
        if (scaled.getDelegate() instanceof Double delegate && !Double.isFinite(delegate)) {
            return close;
        }
        return scaled;
    }
}
