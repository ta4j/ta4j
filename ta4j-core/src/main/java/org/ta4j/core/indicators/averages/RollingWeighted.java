/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import java.util.concurrent.locks.ReentrantLock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.BarSeries.BarSeriesChangeSnapshot;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Sequential weighted sums relative to a fixed anchor, periodically rebased.
 */
final class RollingWeighted {
    private final ReentrantLock lock = new ReentrantLock();
    private volatile int index = -1;
    private volatile long revision = -1;
    private Num anchor;
    private Num offsets;
    private Num weightedOffsets;
    private Num denominator;
    private Num maxAbs;
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
            Num nextMaxAbs = maxAbs.max(current.abs());
            if (!Num.isFinite(nextMaxAbs.multipliedBy(denominator))) {
                return null;
            }
            Num added = current.minus(anchor);
            Num removed = outgoing.minus(anchor);
            Num nextWeighted = weightedOffsets.minus(offsets).plus(added.multipliedBy(weight));
            Num nextOffsets = offsets.minus(removed).plus(added);
            Num result = anchor.plus(nextWeighted.dividedBy(denominator));
            Num numerator = anchor.multipliedBy(denominator).plus(nextWeighted);
            if (!Num.isFinite(nextOffsets) || !Num.isFinite(nextWeighted) || !Num.isFinite(result)
                    || !Num.isFinite(numerator) || (!anchor.isZero() && result.abs()
                            .isLessThan(anchor.abs().dividedBy(current.getNumFactory().numOf(100000000))))) {
                return null;
            }
            if (!terminal) {
                offsets = nextOffsets;
                weightedOffsets = nextWeighted;
                maxAbs = nextMaxAbs;
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
                maximumAbs = maximumAbs.max(value.abs());
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
