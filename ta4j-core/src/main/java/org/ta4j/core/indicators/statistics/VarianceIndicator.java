/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Variance indicator.
 *
 * <p>
 * The default constructor computes <b>sample variance</b> (divisor
 * {@code n - 1}) for rolling windows. Use {@link #ofPopulation(Indicator, int)}
 * (or the {@link SampleType} constructor) when population variance is required.
 * </p>
 *
 * <p>
 * Sequential requests reuse centered rolling moments when the series state and
 * numeric precision permit; other requests use the window calculation.
 * </p>
 */
public class VarianceIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final SampleType sampleType;
    private final transient ReentrantLock rollingLock = new ReentrantLock();
    private transient volatile int rollingIndex = -1;
    private transient volatile int rollingStart = -1;
    private transient int rollingCount;
    private transient volatile long rollingRevision = -1;
    private transient Num rollingAnchor;
    private transient Num rollingMean;
    private transient Num rollingSquaredDeviation;
    private transient int rollingUpdates;

    /**
     * Constructor using {@link SampleType#SAMPLE}.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     * @since 0.22.4
     */
    public VarianceIndicator(Indicator<Num> indicator, int barCount) {
        this(indicator, barCount, SampleType.SAMPLE);
    }

    /**
     * Constructor.
     *
     * @param indicator  the indicator
     * @param barCount   the time frame
     * @param sampleType sample/population variance selection
     * @since 0.22.4
     */
    public VarianceIndicator(Indicator<Num> indicator, int barCount, SampleType sampleType) {
        super(indicator);
        this.indicator = Objects.requireNonNull(indicator, "indicator must not be null");
        this.barCount = Math.max(barCount, 1);
        this.sampleType = Objects.requireNonNull(sampleType, "sampleType must not be null");
    }

    /**
     * Creates an indicator using sample variance ({@code n - 1} divisor).
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     * @return a sample-variance indicator
     * @since 0.22.4
     */
    public static VarianceIndicator ofSample(Indicator<Num> indicator, int barCount) {
        return new VarianceIndicator(indicator, barCount, SampleType.SAMPLE);
    }

    /**
     * Creates an indicator using population variance ({@code n} divisor).
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     * @return a population-variance indicator
     * @since 0.22.4
     */
    public static VarianceIndicator ofPopulation(Indicator<Num> indicator, int barCount) {
        return new VarianceIndicator(indicator, barCount, SampleType.POPULATION);
    }

    @Override
    @SuppressFBWarnings(value = "AT_STALE_THREAD_WRITE_OF_PRIMITIVE", justification = "Rolling aggregate state is read and written only while holding rollingLock.")
    protected Num calculate(int index) {
        final int startIndex = Math.max(Math.max(0, getBarSeries().getBeginIndex()), index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        NumFactory numFactory = getBarSeries().numFactory();
        var series = getBarSeries();
        boolean supported = !(numFactory.zero() instanceof DecimalNum decimal)
                || decimal.getMathContext().getPrecision() >= 16;
        var snapshot = barCount > 1 && supported ? synchronizeCacheWithSeries(series) : null;
        boolean terminal = snapshot != null && index == snapshot.endIndex();
        if (snapshot != null) {
            if (terminal && index > startIndex) {
                getValue(index - 1);
            }
            int checkpoint = rollingIndex;
            long revision = rollingRevision;
            int previousStart = rollingStart;
            if (checkpoint == index - 1 && startIndex >= previousStart && startIndex <= previousStart + 1
                    && previousStart >= series.getBeginIndex() && isCached(checkpoint)) {
                var changes = series.getBarSeriesChangeSnapshot(revision);
                Num current = indicator.getValue(index);
                Num outgoing = startIndex == previousStart ? null : indicator.getValue(previousStart);
                Num result = advance(checkpoint, previousStart, revision, changes.revision(),
                        changes.earliestChangedIndex(), changes.removedThroughIndex(), index, startIndex,
                        numberOfObservations, current, outgoing, terminal, numFactory);
                if (result != null) {
                    return result;
                }
            }
        }
        Num anchor = indicator.getValue(startIndex);
        Num averageOffset = numFactory.zero();
        Num squaredDeviationTotal = numFactory.zero();
        int observationCount = 1;
        for (int i = startIndex + 1; i <= index; i++) {
            Num offset = indicator.getValue(i).minus(anchor);
            observationCount++;
            Num difference = offset.minus(averageOffset);
            averageOffset = averageOffset.plus(difference.dividedBy(numFactory.numOf(observationCount)));
            squaredDeviationTotal = squaredDeviationTotal.plus(difference.multipliedBy(offset.minus(averageOffset)));
        }
        if (snapshot != null && !terminal && snapshot.revision() >= 0 && Num.isFinite(anchor)
                && Num.isFinite(averageOffset) && Num.isFinite(squaredDeviationTotal) && rollingLock.tryLock()) {
            try {
                rollingAnchor = anchor;
                rollingMean = averageOffset;
                rollingStart = startIndex;
                rollingCount = numberOfObservations;
                rollingSquaredDeviation = squaredDeviationTotal;
                rollingUpdates = 0;
                rollingIndex = index;
                rollingRevision = snapshot.revision();
            } finally {
                rollingLock.unlock();
            }
        }
        final int divisor = sampleType.isSample() ? numberOfObservations - 1 : numberOfObservations;
        if (divisor <= 0) {
            return numFactory.zero();
        }
        return squaredDeviationTotal.dividedBy(numFactory.numOf(divisor));
    }

    private Num advance(int expected, int previousStart, long expectedRevision, long revision, int earliestChange,
            int removedThrough, int target, int start, int count, Num current, Num outgoing, boolean terminal,
            NumFactory factory) {
        if (expectedRevision < 0 || revision < 0 || !Num.isFinite(current)
                || (outgoing != null && !Num.isFinite(outgoing)) || !rollingLock.tryLock()) {
            return null;
        }
        try {
            if (rollingIndex != expected || rollingRevision != expectedRevision || target != expected + 1
                    || rollingStart != previousStart || rollingUpdates >= barCount
                    || (earliestChange >= 0 && earliestChange <= expected) || removedThrough >= previousStart) {
                return null;
            }
            Num added = current.minus(rollingAnchor);
            Num mean;
            Num squared;
            if (start == previousStart && count == rollingCount + 1) {
                Num difference = added.minus(rollingMean);
                mean = rollingMean.plus(difference.dividedBy(factory.numOf(count)));
                squared = rollingSquaredDeviation.plus(difference.multipliedBy(added.minus(mean)));
            } else if (start == previousStart + 1 && count == rollingCount && outgoing != null) {
                Num removed = outgoing.minus(rollingAnchor);
                Num delta = added.minus(removed);
                mean = rollingMean.plus(delta.dividedBy(factory.numOf(count)));
                squared = rollingSquaredDeviation
                        .plus(delta.multipliedBy(added.minus(mean).plus(removed.minus(rollingMean))));
            } else {
                return null;
            }
            if (!Num.isFinite(mean) || !Num.isFinite(squared) || squared.isLessThan(factory.zero())
                    || (!rollingSquaredDeviation.isZero() && squared.abs()
                            .isLessThan(rollingSquaredDeviation.abs().dividedBy(factory.numOf(100000000))))) {
                return null;
            }
            int divisor = sampleType.isSample() ? count - 1 : count;
            Num result = divisor <= 0 ? factory.zero() : squared.dividedBy(factory.numOf(divisor));
            if (!terminal) {
                rollingMean = mean;
                rollingSquaredDeviation = squared;
                rollingUpdates++;
                rollingStart = start;
                rollingCount = count;
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

    /**
     * @return the number of bars in the rolling variance window
     * @since 0.28.1
     */
    public int getBarCount() {
        return barCount;
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount + " sampleType: " + sampleType;
    }
}
