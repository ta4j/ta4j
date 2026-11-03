/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import java.util.concurrent.locks.ReentrantLock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

/**
 * Running Total aka Cumulative Sum indicator
 *
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Running_total">https://en.wikipedia.org/wiki/Running_total</a>
 */
public class RunningTotalIndicator extends CachedIndicator<Num> {
    private final Indicator<Num> indicator;
    private final int barCount;
    private transient volatile PartialSum previousPartialSum;

    private record PartialSum(int index, Num sum, BarSeries.BarSeriesChangeSnapshot snapshot) {
    }

    private final transient ReentrantLock rollingLock = new ReentrantLock();
    private transient volatile int rollingIndex = -1;
    private transient volatile int rollingStart = -1;
    private transient volatile long rollingRevision = -1;
    private transient Num rollingSum;
    private transient int rollingUpdates;

    public RunningTotalIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        Num zero = getBarSeries().numFactory().zero();
        if (barCount > 1 && (!(zero instanceof DecimalNum decimal) || decimal.getMathContext().getPrecision() >= 16)) {
            return calculateRolling(index);
        }
        final BarSeries series = getBarSeries();
        PartialSum partial = previousPartialSum;
        BarSeries.BarSeriesChangeSnapshot previous = partial == null ? series.getBarSeriesChangeSnapshot(-1L)
                : partial.snapshot();
        final BarSeries.BarSeriesChangeSnapshot snapshot = series.getBarSeriesChangeSnapshot(previous.revision());
        final boolean appendOnly = snapshot.endIndex() == previous.endIndex() + 1
                && snapshot.earliestChangedIndex() == snapshot.endIndex()
                && snapshot.removedThroughIndex() == previous.removedThroughIndex()
                && snapshot.maximumBarCount() == previous.maximumBarCount();
        // Revision equality alone does not capture window eviction: a constrained
        // series can advance removedThroughIndex/beginIndex without bumping the
        // history revision, which would keep a partial sum that still includes
        // the evicted bar. Treat the sum as unchanged only when the complete
        // snapshot state matches.
        final boolean unchanged = snapshot.revision() == previous.revision()
                && snapshot.endIndex() == previous.endIndex()
                && snapshot.earliestChangedIndex() == previous.earliestChangedIndex()
                && snapshot.removedThroughIndex() == previous.removedThroughIndex()
                && snapshot.maximumBarCount() == previous.maximumBarCount();
        // serial access can benefit from previous partial sums
        // which saves a lot of CPU work for very long barCounts.
        // A partial sum remains valid as long as the series is unchanged or has
        // only been appended to, because neither change alters previous values.
        final boolean partialSumUsable = partial != null && partial.index() == index - 1
                && partial.index() >= Math.max(0, series.getBeginIndex()) && (unchanged || appendOnly);
        if (partialSumUsable && Num.isFinite(partial.sum())) {
            return fastPath(index, partial.sum(), snapshot);
        }

        return slowPath(index, snapshot);
    }

    @SuppressFBWarnings(value = "AT_STALE_THREAD_WRITE_OF_PRIMITIVE", justification = "Rolling aggregate state is read and written only while holding rollingLock.")
    private Num calculateRolling(int index) {
        var series = getBarSeries();
        int begin = Math.max(0, series.getBeginIndex());
        int start = index < begin ? Math.max(0, index - barCount + 1) : Math.max(begin, index - barCount + 1);
        var snapshot = synchronizeCacheWithSeries(series);
        boolean terminal = index == snapshot.endIndex();
        if (terminal && index > start) {
            getValue(index - 1);
        }
        int checkpoint = rollingIndex;
        int previousStart = rollingStart;
        long revision = rollingRevision;
        if (checkpoint == index - 1 && previousStart >= begin && start >= previousStart && start <= previousStart + 1
                && isCached(checkpoint)) {
            var changes = series.getBarSeriesChangeSnapshot(revision);
            Num current = indicator.getValue(index);
            Num outgoing = start == previousStart ? null : indicator.getValue(previousStart);
            Num result = advance(checkpoint, previousStart, revision, changes, index, start, current, outgoing,
                    terminal);
            if (result != null) {
                return result;
            }
        }
        Num sum = series.numFactory().zero();
        for (int i = start; i <= index; i++) {
            sum = sum.plus(indicator.getValue(i));
        }
        if (!terminal && snapshot.revision() >= 0 && Num.isFinite(sum) && rollingLock.tryLock()) {
            try {
                rollingSum = sum;
                rollingStart = start;
                rollingUpdates = 0;
                rollingIndex = index;
                rollingRevision = snapshot.revision();
            } finally {
                rollingLock.unlock();
            }
        }
        return sum;
    }

    private Num advance(int expected, int previousStart, long expectedRevision,
            BarSeries.BarSeriesChangeSnapshot changes, int target, int start, Num current, Num outgoing,
            boolean terminal) {
        if (expectedRevision < 0 || changes.revision() < 0 || !Num.isFinite(current)
                || (outgoing != null && !Num.isFinite(outgoing)) || !rollingLock.tryLock()) {
            return null;
        }
        try {
            if (rollingIndex != expected || rollingStart != previousStart || rollingRevision != expectedRevision
                    || target != expected + 1 || rollingUpdates >= barCount
                    || (changes.earliestChangedIndex() >= 0 && changes.earliestChangedIndex() <= expected)
                    || changes.removedThroughIndex() >= previousStart) {
                return null;
            }
            Num retained = outgoing == null ? rollingSum : rollingSum.minus(outgoing);
            Num sum = retained.plus(current);
            if (!Num.isFinite(sum)) {
                return null;
            }
            if (outgoing != null && !rollingSum.isZero()) {
                Num threshold = rollingSum.abs().dividedBy(getBarSeries().numFactory().numOf(100000000));
                if (retained.abs().isLessThan(threshold) || sum.abs().isLessThan(threshold)) {
                    return null;
                }
            }
            if (!terminal) {
                rollingSum = sum;
                rollingStart = start;
                rollingUpdates++;
                rollingIndex = target;
            }
            rollingRevision = changes.revision();
            return sum;
        } catch (ArithmeticException e) {
            return null;
        } finally {
            rollingLock.unlock();
        }
    }

    private Num fastPath(final int index, final Num previousSum, final BarSeries.BarSeriesChangeSnapshot snapshot) {
        Num newSum = partialSum(index, previousSum);
        updatePartialSum(index, newSum, snapshot);
        return newSum;
    }

    private Num slowPath(final int index, final BarSeries.BarSeriesChangeSnapshot snapshot) {
        final BarSeries series = getBarSeries();
        Num sum = series.numFactory().zero();
        final int beginIndex = series.getBeginIndex();
        // Below beginIndex the series clamps reads to the first remaining bar,
        // so the legacy 0-anchored window is preserved (existing contract).
        // At/above beginIndex the window is anchored at beginIndex so that
        // getValue(beginIndex) equals the front bar's value, not barCount times it.
        final int firstInWindow = index < beginIndex ? Math.max(0, index - barCount + 1)
                : Math.max(Math.max(0, beginIndex), index - barCount + 1);
        for (int i = firstInWindow; i <= index; i++) {
            sum = sum.plus(indicator.getValue(i));
        }

        updatePartialSum(index, sum, snapshot);
        return sum;
    }

    private void updatePartialSum(final int index, final Num sum, final BarSeries.BarSeriesChangeSnapshot snapshot) {
        previousPartialSum = new PartialSum(index, sum, snapshot);
    }

    private Num partialSum(int index, Num previousSum) {
        Num sum = previousSum.plus(indicator.getValue(index));

        final int firstInWindow = index - barCount + 1;
        if (firstInWindow > Math.max(0, getBarSeries().getBeginIndex())) {
            return sum.minus(indicator.getValue(index - barCount));
        }

        return sum;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }
}
