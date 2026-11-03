/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.num.NaN.NaN;

import java.util.concurrent.locks.ReentrantLock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Indicator-Pearson-Correlation
 *
 * Sequential requests reuse rolling sums when both inputs share a series and
 * the state and numeric precision permit; other requests scan the window.
 *
 * @see <a href=
 *      "http://www.statisticshowto.com/probability-and-statistics/correlation-coefficient-formula/">
 *      http://www.statisticshowto.com/probability-and-statistics/correlation-coefficient-formula/</a>
 */
public class PearsonCorrelationIndicator extends RecursiveCachedIndicator<Num> {

    private static final long EXACT_INTEGER_BOUND = 1L << 49;
    private static final long EXACT_INTEGER_INPUT_BOUND = 1L << 24;

    private final Indicator<Num> indicator1;
    private final Indicator<Num> indicator2;
    private final int barCount;
    private final transient ReentrantLock rollingLock = new ReentrantLock();
    private transient volatile int rollingIndex = -1;
    private transient volatile int rollingStart = -1;
    private transient volatile long rollingRevision = -1;
    private transient int rollingUpdates;
    private transient Num rollingX;
    private transient Num rollingY;
    private transient Num rollingXX;
    private transient Num rollingYY;
    private transient Num rollingXY;
    private transient Num rollingExactIntegerMagnitude;

    /**
     * Constructor.
     *
     * @param indicator1 the first indicator
     * @param indicator2 the second indicator
     * @param barCount   the time frame
     */
    public PearsonCorrelationIndicator(Indicator<Num> indicator1, Indicator<Num> indicator2, int barCount) {
        super(indicator1.getBarSeries(), indicator1, indicator2);
        this.indicator1 = indicator1;
        this.indicator2 = indicator2;
        this.barCount = barCount;
    }

    /**
     * The correlation over a fixed window reads only retained bars, so every cached
     * value stays recomputable from the retained window after a head advance: the
     * unstable-range floor applies instead of keeping every cached value.
     */
    @Override
    protected boolean hasRecursiveDependencies() {
        return false;
    }

    @Override
    @SuppressFBWarnings(value = "AT_STALE_THREAD_WRITE_OF_PRIMITIVE", justification = "Rolling aggregate state is read and written only while holding rollingLock.")
    protected Num calculate(int index) {

        final var numFactory = getBarSeries().numFactory();
        final int beginIndex = Math.max(0, getBarSeries().getBeginIndex());
        final int start = Math.max(beginIndex, index - barCount + 1);
        // Once bar eviction has advanced the begin index, the retained window may be
        // smaller than the requested barCount; normalize by the number of values
        // actually iterated. The warm-up phase keeps the historical barCount
        // normalization for backward compatibility.
        final int normalizationCount = beginIndex > 0 ? index - start + 1 : barCount;
        boolean supported = barCount > 1
                && IndicatorUtils.isSameSeries(indicator2.getBarSeries(), indicator1.getBarSeries())
                && (!(numFactory.zero() instanceof DecimalNum decimal)
                        || decimal.getMathContext().getPrecision() >= 16);
        var snapshot = supported ? synchronizeCacheWithSeries(getBarSeries()) : null;
        boolean terminal = snapshot != null && index == snapshot.endIndex();
        if (snapshot != null) {
            if (terminal && index > start) {
                getValue(index - 1);
            }
            int checkpoint = rollingIndex;
            int previousStart = rollingStart;
            long revision = rollingRevision;
            if (checkpoint == index - 1 && previousStart >= beginIndex && start >= previousStart
                    && start <= previousStart + 1 && isCached(checkpoint)) {
                var changes = getBarSeries().getBarSeriesChangeSnapshot(revision);
                Num x = indicator1.getValue(index);
                Num y = indicator2.getValue(index);
                Num oldX = start == previousStart ? null : indicator1.getValue(previousStart);
                Num oldY = start == previousStart ? null : indicator2.getValue(previousStart);
                Num result = advance(checkpoint, previousStart, revision, changes.revision(),
                        changes.earliestChangedIndex(), changes.removedThroughIndex(), index, start, normalizationCount,
                        x, y, oldX, oldY, terminal);
                if (result != null) {
                    return result;
                }
            }
        }
        Num n = numFactory.numOf(normalizationCount);

        Num zero = numFactory.zero();
        Num Sx = zero;
        Num Sy = zero;
        Num Sxx = zero;
        Num Syy = zero;
        Num Sxy = zero;
        Num exactIntegerMagnitude = snapshot != null && !terminal && snapshot.revision() >= 0
                && (zero instanceof DoubleNum || zero instanceof DecimalNum) ? zero : null;

        for (int i = start; i <= index; i++) {

            Num x = indicator1.getValue(i);
            Num y = indicator2.getValue(i);

            Sx = Sx.plus(x);
            Sy = Sy.plus(y);
            Sxy = Sxy.plus(x.multipliedBy(y));
            Sxx = Sxx.plus(x.multipliedBy(x));
            Syy = Syy.plus(y.multipliedBy(y));
            if (exactIntegerMagnitude != null) {
                exactIntegerMagnitude = extendExactIntegerMagnitude(exactIntegerMagnitude, x, y);
            }
        }
        if (snapshot != null && !terminal && snapshot.revision() >= 0 && Num.isFinite(Sx) && Num.isFinite(Sy)
                && Num.isFinite(Sxx) && Num.isFinite(Syy) && Num.isFinite(Sxy) && rollingLock.tryLock()) {
            try {
                rollingX = Sx;
                rollingY = Sy;
                rollingXX = Sxx;
                rollingYY = Syy;
                rollingXY = Sxy;
                rollingExactIntegerMagnitude = exactIntegerMagnitude;
                rollingUpdates = 0;
                rollingStart = start;
                rollingIndex = index;
                rollingRevision = snapshot.revision();
            } finally {
                rollingLock.unlock();
            }
        }

        // (n * Sxx - Sx * Sx) * (n * Syy - Sy * Sy)
        Num toSqrt = (n.multipliedBy(Sxx).minus(Sx.multipliedBy(Sx)))
                .multipliedBy(n.multipliedBy(Syy).minus(Sy.multipliedBy(Sy)));

        if (toSqrt.isGreaterThan(numFactory.zero())) {
            // pearson = (n * Sxy - Sx * Sy) / sqrt((n * Sxx - Sx * Sx) * (n * Syy - Sy *
            // Sy))
            return (n.multipliedBy(Sxy).minus(Sx.multipliedBy(Sy))).dividedBy(toSqrt.sqrt());
        }

        return NaN;
    }

    private Num advance(int expected, int previousStart, long expectedRevision, long revision, int earliestChange,
            int removedThrough, int target, int start, int count, Num x, Num y, Num oldX, Num oldY, boolean terminal) {
        if (expectedRevision < 0 || revision < 0 || !Num.isFinite(x) || !Num.isFinite(y)
                || (oldX != null && (!Num.isFinite(oldX) || !Num.isFinite(oldY))) || !rollingLock.tryLock()) {
            return null;
        }
        try {
            if (rollingIndex != expected || rollingStart != previousStart || rollingRevision != expectedRevision
                    || target != expected + 1 || rollingUpdates >= barCount
                    || (earliestChange >= 0 && earliestChange <= expected) || removedThrough >= previousStart) {
                return null;
            }
            Num sx = rollingX;
            Num sy = rollingY;
            Num sxx = rollingXX;
            Num syy = rollingYY;
            Num sxy = rollingXY;
            NumFactory factory = getBarSeries().numFactory();
            Num threshold = factory.numOf(1000000000);
            Num exactIntegerMagnitude = rollingExactIntegerMagnitude == null ? null
                    : extendExactIntegerMagnitude(rollingExactIntegerMagnitude, x, y);
            Num xx = x.multipliedBy(x);
            Num yy = y.multipliedBy(y);
            Num xy = x.multipliedBy(y);
            if (oldX != null) {
                Num oldXX = oldX.multipliedBy(oldX);
                Num oldYY = oldY.multipliedBy(oldY);
                Num oldXY = oldX.multipliedBy(oldY);
                Num remainingX = sx.minus(oldX);
                Num remainingY = sy.minus(oldY);
                Num remainingXX = sxx.minus(oldXX);
                Num remainingYY = syy.minus(oldYY);
                Num remainingXY = sxy.minus(oldXY);
                // Check before adding the new value: it can hide a lost residual.
                if (exactIntegerMagnitude == null && (losesSignificance(sx, oldX, remainingX, threshold, true)
                        || losesSignificance(sy, oldY, remainingY, threshold, true)
                        || losesSignificance(sxx, oldXX, remainingXX, threshold, true)
                        || losesSignificance(syy, oldYY, remainingYY, threshold, true)
                        || losesSignificance(sxy, oldXY, remainingXY, threshold, true))) {
                    return null;
                }
                sx = remainingX;
                sy = remainingY;
                sxx = remainingXX;
                syy = remainingYY;
                sxy = remainingXY;
            }
            Num nextX = sx.plus(x);
            Num nextY = sy.plus(y);
            Num nextXX = sxx.plus(xx);
            Num nextYY = syy.plus(yy);
            Num nextXY = sxy.plus(xy);
            if (exactIntegerMagnitude == null && (losesSignificance(sx, x, nextX, threshold, false)
                    || losesSignificance(sy, y, nextY, threshold, false)
                    || losesSignificance(sxx, xx, nextXX, threshold, false)
                    || losesSignificance(syy, yy, nextYY, threshold, false)
                    || losesSignificance(sxy, xy, nextXY, threshold, false))) {
                return null;
            }
            sx = nextX;
            sy = nextY;
            sxx = nextXX;
            syy = nextYY;
            sxy = nextXY;
            Num n = factory.numOf(count);
            Num xTerm = n.multipliedBy(sxx);
            Num yTerm = n.multipliedBy(syy);
            Num xyTerm = n.multipliedBy(sxy);
            Num xVariance = xTerm.minus(sx.multipliedBy(sx));
            Num yVariance = yTerm.minus(sy.multipliedBy(sy));
            Num covariance = xyTerm.minus(sx.multipliedBy(sy));
            if (!Num.isFinite(sx) || !Num.isFinite(sy) || !Num.isFinite(sxx) || !Num.isFinite(syy) || !Num.isFinite(sxy)
                    || !Num.isFinite(xVariance) || !Num.isFinite(yVariance) || !Num.isFinite(covariance)
                    || xVariance.abs().isLessThanOrEqual(xTerm.abs().dividedBy(threshold))
                    || yVariance.abs().isLessThanOrEqual(yTerm.abs().dividedBy(threshold))
                    || covariance.abs().isLessThanOrEqual(xyTerm.abs().dividedBy(threshold))) {
                return null;
            }
            Num toSqrt = xVariance.multipliedBy(yVariance);
            if (!Num.isFinite(toSqrt)) {
                return null;
            }
            Num result = toSqrt.isGreaterThan(getBarSeries().numFactory().zero()) ? covariance.dividedBy(toSqrt.sqrt())
                    : NaN;
            if (!terminal) {
                rollingX = sx;
                rollingY = sy;
                rollingXX = sxx;
                rollingYY = syy;
                rollingXY = sxy;
                rollingExactIntegerMagnitude = exactIntegerMagnitude;
                rollingUpdates++;
                rollingStart = start;
                rollingIndex = target;
            }
            rollingRevision = revision;
            return result;
        } catch (ArithmeticException e) {
            return null;
        } finally {
            rollingLock.unlock();
        }
    }

    private Num extendExactIntegerMagnitude(Num maximum, Num x, Num y) {
        if (!Num.isFinite(x) || !Num.isFinite(y)) {
            return null;
        }
        NumFactory factory = getBarSeries().numFactory();
        Num xMagnitude = x.abs();
        Num yMagnitude = y.abs();
        Num nextMaximum = maximum.max(xMagnitude).max(yMagnitude);
        if (nextMaximum.isGreaterThan(factory.numOf(EXACT_INTEGER_INPUT_BOUND))
                || !isExactInteger(x, xMagnitude, factory.one()) || !isExactInteger(y, yMagnitude, factory.one())) {
            return null;
        }
        // Bound every integer product and its accumulated sum, including an update.
        Num bound = nextMaximum.multipliedBy(nextMaximum).multipliedBy(factory.numOf((long) barCount + 1));
        return bound.isLessThanOrEqual(factory.numOf(EXACT_INTEGER_BOUND)) ? nextMaximum : null;
    }

    private static boolean isExactInteger(Num value, Num magnitude, Num one) {
        return (value instanceof DoubleNum
                || (value instanceof DecimalNum decimal && decimal.getMathContext().getPrecision() >= 16))
                && (magnitude.isZero() || (magnitude.isGreaterThanOrEqual(one) && value.isEqual(value.floor())));
    }

    private static boolean losesSignificance(Num before, Num operand, Num result, Num threshold, boolean subtract) {
        return !before.isZero() && !operand.isZero() && (before.isPositive() == operand.isPositive()) == subtract
                && result.abs().isLessThanOrEqual(before.abs().max(operand.abs()).dividedBy(threshold));
    }

    @Override
    public int getCountOfUnstableBars() {
        int baseUnstableBars = Math.max(indicator1.getCountOfUnstableBars(), indicator2.getCountOfUnstableBars());
        return baseUnstableBars + barCount - 1;
    }
}
