/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import java.util.concurrent.locks.ReentrantLock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.BarSeries.BarSeriesChangeSnapshot;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Sequential weighted sums relative to a fixed anchor, periodically rebased.
 */
final class RollingWeighted {
    private static final long CANCELLATION_FACTOR = 100_000_000L;
    private static final long FLOAT_CANCELLATION_FACTOR = 10_000L;
    // Leave headroom for the centered sums and their intermediate updates.
    private static final long EXACT_INTEGER_BOUND = 1L << 49;

    private final ReentrantLock lock = new ReentrantLock();
    private volatile int index = -1;
    private volatile long revision = -1;
    private Num anchor;
    private Num offsets;
    private Num weightedOffsets;
    private Num denominator;
    private Num maxAbs;
    private Num cancellationFactor;
    private Num exactIntegerBound;
    private boolean exactIntegerInputs;
    private int updates;

    static boolean supports(Num zero) {
        if (zero instanceof DecimalNum decimal) {
            return decimal.getMathContext().getPrecision() >= 16;
        }
        return true;
    }

    int index() {
        return index;
    }

    long revision() {
        return revision;
    }

    Num advance(int expected, long expectedRevision, BarSeriesChangeSnapshot changes, boolean cached, int target,
            int barCount, Num current, Num outgoing, Num weight, boolean terminal) {
        if (!cached || expectedRevision < 0 || changes.revision() < 0 || !Num.isFinite(current)
                || !Num.isFinite(outgoing) || !lock.tryLock()) {
            return null;
        }
        try {
            if (index != expected || revision != expectedRevision || target != index + 1 || updates >= barCount
                    || (changes.earliestChangedIndex() >= 0 && changes.earliestChangedIndex() <= index)
                    || changes.removedThroughIndex() >= target - barCount) {
                return null;
            }
            Num currentAbs = current.abs();
            Num nextMaxAbs = maxAbs.max(currentAbs);
            Num bound = nextMaxAbs.multipliedBy(denominator);
            if (!Num.isFinite(bound)) {
                return null;
            }
            boolean nextExactIntegerInputs = exactIntegerInputs && bound.isLessThanOrEqual(exactIntegerBound)
                    && (current.isZero() || (currentAbs.isGreaterThanOrEqual(current.getNumFactory().one())
                            && current.isEqual(current.floor())));
            Num added = current.minus(anchor);
            Num removed = outgoing.minus(anchor);
            Num nextWeighted = weightedOffsets.minus(offsets).plus(added.multipliedBy(weight));
            Num nextOffsets = offsets.minus(removed).plus(added);
            Num result = anchor.plus(nextWeighted.dividedBy(denominator));
            Num numerator = anchor.multipliedBy(denominator).plus(nextWeighted);
            // Offset sums can retain rounding errors from a much larger, expired value.
            boolean exactZero = nextExactIntegerInputs && numerator.isZero() && result.isZero();
            if (!Num.isFinite(nextOffsets) || !Num.isFinite(nextWeighted) || !Num.isFinite(result)
                    || !Num.isFinite(numerator) || (!exactZero && !nextMaxAbs.isZero()
                            && result.abs().multipliedBy(cancellationFactor).isLessThan(nextMaxAbs))) {
                return null;
            }
            if (!terminal) {
                offsets = nextOffsets;
                weightedOffsets = nextWeighted;
                maxAbs = nextMaxAbs;
                exactIntegerInputs = nextExactIntegerInputs;
                updates++;
                index = target;
            }
            revision = changes.revision();
            return result;
        } catch (ArithmeticException e) {
            return null;
        } finally {
            lock.unlock();
        }
    }

    @SuppressFBWarnings(value = "AT_STALE_THREAD_WRITE_OF_PRIMITIVE", justification = "Rolling aggregate state is read and written only while holding lock.")
    Num rebuild(Indicator<Num> source, int start, int target, int barCount, boolean ascending, long observedRevision,
            boolean terminal) {
        NumFactory factory = source.getBarSeries().numFactory();
        Num first = source.getValue(start);
        Num sum = factory.zero();
        Num offsetSum = factory.zero();
        Num weighted = factory.zero();
        Num denominatorSum = factory.zero();
        Num maximumAbs = factory.zero();
        Num exactIntegerBound = factory.numOf(EXACT_INTEGER_BOUND);
        boolean integerInputs = first instanceof DoubleNum || first instanceof DecimalNum;
        boolean finite = Num.isFinite(first);
        for (int n = 0; n < barCount; n++) {
            int at = ascending ? start + n : target - n;
            int weight = ascending ? n + 1 : barCount - n;
            Num value = at == start ? first : source.getValue(at);
            Num factor = factory.numOf(weight);
            sum = sum.plus(ascending ? value.multipliedBy(factor) : factor.multipliedBy(value));
            if (ascending) {
                denominatorSum = denominatorSum.plus(factor);
            }
            if (finite && Num.isFinite(value)) {
                Num valueAbs = value.abs();
                maximumAbs = maximumAbs.max(valueAbs);
                if (integerInputs) {
                    integerInputs = maximumAbs.isLessThanOrEqual(exactIntegerBound) && (value.isZero()
                            || (valueAbs.isGreaterThanOrEqual(factory.one()) && value.isEqual(value.floor())));
                }
                try {
                    Num offset = value.minus(first);
                    offsetSum = offsetSum.plus(offset);
                    weighted = weighted.plus(offset.multipliedBy(factor));
                    finite = Num.isFinite(offsetSum) && Num.isFinite(weighted);
                } catch (ArithmeticException e) {
                    finite = false;
                }
            } else {
                finite = false;
            }
        }
        Num divisor = ascending ? denominatorSum : factory.numOf((barCount * (barCount + 1)) / 2);
        Num result = sum.dividedBy(divisor);
        if (finite && Num.isFinite(sum) && Num.isFinite(divisor) && !divisor.isZero() && safeBound(maximumAbs, divisor)
                && !terminal && observedRevision >= 0 && lock.tryLock()) {
            try {
                anchor = first;
                offsets = offsetSum;
                weightedOffsets = weighted;
                denominator = divisor;
                maxAbs = maximumAbs;
                cancellationFactor = factory
                        .numOf(first.getDelegate() instanceof Float ? FLOAT_CANCELLATION_FACTOR : CANCELLATION_FACTOR);
                this.exactIntegerBound = exactIntegerBound;
                exactIntegerInputs = integerInputs
                        && maximumAbs.multipliedBy(divisor).isLessThanOrEqual(exactIntegerBound);
                updates = 0;
                index = target;
                revision = observedRevision;
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    private static boolean safeBound(Num maximumAbs, Num divisor) {
        try {
            return Num.isFinite(maximumAbs.multipliedBy(divisor));
        } catch (ArithmeticException e) {
            return false;
        }
    }
}
