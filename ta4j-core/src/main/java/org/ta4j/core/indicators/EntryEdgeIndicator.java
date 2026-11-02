/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Rolling realized edge score for entry signals.
 *
 * <p>
 * The indicator measures how much better a signal performs than an
 * unconditional baseline using only fully matured forward-return observations.
 * At index {@code i}, the score is computed from signal bars whose
 * {@code i + horizonBars} outcome is already known, which avoids look-ahead
 * leakage.
 * </p>
 *
 * <p>
 * Values are returned in basis points. Positive values indicate the signal's
 * recent realized outcomes beat the unconditional baseline; negative values
 * indicate underperformance.
 * </p>
 *
 * @since 0.22.7
 */
public class EntryEdgeIndicator extends CachedIndicator<Num> {

    private static final double BASIS_POINTS = 10_000.0d;
    private static final int NO_SIGNAL_INDEX = -1;

    private final Indicator<Boolean> signalIndicator;
    private final Indicator<Num> priceIndicator;
    private final TradeType tradeType;
    private final int horizonBars;
    private final int lookbackSignals;
    private final Indicator<Integer> latestMaturedSignalIndexIndicator;
    private final transient ReentrantLock rollingLock = new ReentrantLock();
    private final transient Deque<SignalReturn> rollingSignals = new ArrayDeque<>();
    private transient volatile int rollingIndex = -1;
    private transient volatile int rollingBegin = -1;
    private transient volatile int rollingBaselineStart = -1;
    private transient volatile long rollingRevision = -1;
    private transient int rollingUpdates;
    private transient int rollingBaselineCount;
    private transient double rollingBaselineTotal;
    private transient double rollingSignalTotal;

    private record SignalReturn(double value) {
    }

    /**
     * Creates a long-side edge indicator using close price as the return source.
     *
     * @param signalIndicator indicator returning {@code true} on signal bars
     * @param series          the bar series
     * @param horizonBars     the forward-return horizon
     * @param lookbackSignals the number of matured signal observations to include
     * @since 0.22.7
     */
    public EntryEdgeIndicator(Indicator<Boolean> signalIndicator, BarSeries series, int horizonBars,
            int lookbackSignals) {
        this(validatedConfig(signalIndicator, series, horizonBars, lookbackSignals));
    }

    /**
     * Creates a directional edge indicator using the supplied price source.
     *
     * @param signalIndicator indicator returning {@code true} on signal bars
     * @param priceIndicator  price indicator used to measure forward returns
     * @param tradeType       signal direction
     * @param horizonBars     the forward-return horizon
     * @param lookbackSignals the number of matured signal observations to include
     * @since 0.22.7
     */
    public EntryEdgeIndicator(Indicator<Boolean> signalIndicator, Indicator<Num> priceIndicator, TradeType tradeType,
            int horizonBars, int lookbackSignals) {
        this(validatedConfig(signalIndicator, priceIndicator, tradeType, horizonBars, lookbackSignals));
    }

    private EntryEdgeIndicator(Config config) {
        super(config.signalIndicator(), config.priceIndicator());
        this.signalIndicator = config.signalIndicator();
        this.priceIndicator = config.priceIndicator();
        this.tradeType = config.tradeType();
        this.horizonBars = config.horizonBars();
        this.lookbackSignals = config.lookbackSignals();
        this.latestMaturedSignalIndexIndicator = createLatestMaturedSignalIndexIndicator();
    }

    private static Config validatedConfig(Indicator<Boolean> signalIndicator, Indicator<Num> priceIndicator,
            TradeType tradeType, int horizonBars, int lookbackSignals) {
        BarSeries series = IndicatorUtils.requireSameSeries(signalIndicator, priceIndicator);
        if (horizonBars < 1) {
            throw new IllegalArgumentException("horizonBars must be greater than zero");
        }
        if (lookbackSignals < 1) {
            throw new IllegalArgumentException("lookbackSignals must be greater than zero");
        }
        Indicator<Boolean> validatedSignalIndicator = Objects.requireNonNull(signalIndicator, "signalIndicator");
        Indicator<Num> validatedPriceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator");
        TradeType validatedTradeType = Objects.requireNonNull(tradeType, "tradeType");
        return new Config(series, validatedSignalIndicator, validatedPriceIndicator, validatedTradeType, horizonBars,
                lookbackSignals);
    }

    private static Config validatedConfig(Indicator<Boolean> signalIndicator, BarSeries series, int horizonBars,
            int lookbackSignals) {
        return validatedConfig(signalIndicator, new ClosePriceIndicator(series), TradeType.BUY, horizonBars,
                lookbackSignals);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    @SuppressFBWarnings(value = { "AT_NONATOMIC_64BIT_PRIMITIVE",
            "AT_STALE_THREAD_WRITE_OF_PRIMITIVE" }, justification = "Rolling aggregate state is read and written only while holding rollingLock.")
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        int lastEligibleIndex = index - horizonBars;
        int beginIndex = firstEligibleSignalIndex();
        if (lastEligibleIndex < beginIndex) {
            return NaN.NaN;
        }
        var snapshot = synchronizeCacheWithSeries(getBarSeries());
        boolean terminal = index == snapshot.endIndex();
        if (terminal && index > getCountOfUnstableBars()) {
            getValue(index - 1);
        }
        int checkpoint = rollingIndex;
        int previousBegin = rollingBegin;
        int previousBaselineStart = rollingBaselineStart;
        long revision = rollingRevision;
        int baselineStart = Math.max(beginIndex, lastEligibleIndex - lookbackSignals + 1);
        if (checkpoint == index - 1 && previousBegin == beginIndex && baselineStart >= previousBaselineStart
                && baselineStart <= previousBaselineStart + 1 && isCached(checkpoint)) {
            var changes = getBarSeries().getBarSeriesChangeSnapshot(revision);
            double current = signedForwardReturn(lastEligibleIndex);
            double outgoing = baselineStart == previousBaselineStart ? Double.NaN
                    : signedForwardReturn(previousBaselineStart);
            boolean signal = Boolean.TRUE.equals(signalIndicator.getValue(lastEligibleIndex));
            Num result = advance(checkpoint, previousBegin, previousBaselineStart, revision, changes, index,
                    baselineStart, current, outgoing, signal, terminal);
            if (result != null) {
                return result;
            }
        }
        Deque<SignalReturn> signals = !terminal && snapshot.revision() >= 0 ? new ArrayDeque<>() : null;
        double signalTotal = 0.0d;
        int signalCount = 0;
        int signalIndex = latestMaturedSignalIndexIndicator.getValue(lastEligibleIndex);
        while (signalIndex >= beginIndex && signalCount < lookbackSignals) {
            double forwardReturn = signedForwardReturn(signalIndex);
            if (!Double.isFinite(forwardReturn)) {
                return NaN.NaN;
            }
            signalTotal += forwardReturn;
            if (signals != null) {
                signals.addFirst(new SignalReturn(forwardReturn));
            }
            signalCount++;
            int previousIndex = signalIndex - 1;
            signalIndex = previousIndex >= beginIndex ? latestMaturedSignalIndexIndicator.getValue(previousIndex)
                    : NO_SIGNAL_INDEX;
        }
        if (signalCount == 0) {
            return NaN.NaN;
        }

        double baselineTotal = 0.0d;
        int baselineCount = 0;
        baselineStart = Math.max(beginIndex, lastEligibleIndex - Math.max(signalCount, lookbackSignals) + 1);
        for (int cursor = baselineStart; cursor <= lastEligibleIndex; cursor++) {
            double forwardReturn = signedForwardReturn(cursor);
            if (!Double.isFinite(forwardReturn)) {
                continue;
            }
            baselineTotal += forwardReturn;
            baselineCount++;
        }
        if (baselineCount == 0) {
            return NaN.NaN;
        }
        if (signals != null && Double.isFinite(signalTotal) && Double.isFinite(baselineTotal)
                && rollingLock.tryLock()) {
            try {
                rollingSignals.clear();
                rollingSignals.addAll(signals);
                rollingSignalTotal = signalTotal;
                rollingBaselineTotal = baselineTotal;
                rollingBaselineCount = baselineCount;
                rollingBegin = beginIndex;
                rollingBaselineStart = baselineStart;
                rollingUpdates = 0;
                rollingIndex = index;
                rollingRevision = snapshot.revision();
            } finally {
                rollingLock.unlock();
            }
        }

        double edgeBps = (signalTotal / signalCount) - (baselineTotal / baselineCount);
        return getBarSeries().numFactory().numOf(edgeBps);
    }

    private Num advance(int expected, int begin, int oldStart, long expectedRevision,
            BarSeries.BarSeriesChangeSnapshot changes, int target, int start, double current, double outgoing,
            boolean signal, boolean terminal) {
        if (expectedRevision < 0 || changes.revision() < 0 || !rollingLock.tryLock()) {
            return null;
        }
        try {
            if (rollingIndex != expected || rollingBegin != begin || rollingBaselineStart != oldStart
                    || rollingRevision != expectedRevision || rollingUpdates >= lookbackSignals
                    || (changes.earliestChangedIndex() >= 0 && changes.earliestChangedIndex() <= expected)
                    || changes.removedThroughIndex() >= begin) {
                return null;
            }
            double baseline = rollingBaselineTotal;
            int baselineCount = rollingBaselineCount;
            if (start != oldStart && Double.isFinite(outgoing)) {
                baseline -= outgoing;
                baselineCount--;
            }
            if (Double.isFinite(current)) {
                baseline += current;
                baselineCount++;
            }
            boolean addSignal = signal && Double.isFinite(current);
            SignalReturn removed = addSignal && rollingSignals.size() == lookbackSignals ? rollingSignals.peekFirst()
                    : null;
            double signalTotal = rollingSignalTotal - (removed == null ? 0 : removed.value())
                    + (addSignal ? current : 0);
            int signalCount = rollingSignals.size() - (removed == null ? 0 : 1) + (addSignal ? 1 : 0);
            if (baselineCount == 0 || signalCount == 0 || !Double.isFinite(baseline) || !Double.isFinite(signalTotal)
                    || Math.abs(baseline) < Math.abs(rollingBaselineTotal) * 1e-8
                    || Math.abs(signalTotal) < Math.abs(rollingSignalTotal) * 1e-8) {
                return null;
            }
            double edge = signalTotal / signalCount - baseline / baselineCount;
            if (!Double.isFinite(edge)) {
                return null;
            }
            if (!terminal) {
                if (removed != null) {
                    rollingSignals.removeFirst();
                }
                if (addSignal) {
                    rollingSignals.addLast(new SignalReturn(current));
                }
                rollingSignalTotal = signalTotal;
                rollingBaselineTotal = baseline;
                rollingBaselineCount = baselineCount;
                rollingBaselineStart = start;
                rollingUpdates++;
                rollingIndex = target;
            }
            rollingRevision = changes.revision();
            return getBarSeries().numFactory().numOf(edge);
        } finally {
            rollingLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(signalIndicator.getCountOfUnstableBars(), priceIndicator.getCountOfUnstableBars())
                + horizonBars;
    }

    /**
     * @return the signal indicator used to pick entry bars
     * @since 0.22.7
     */
    public Indicator<Boolean> getSignalIndicator() {
        return signalIndicator;
    }

    /**
     * @return the price indicator used to measure forward returns
     * @since 0.22.7
     */
    public Indicator<Num> getPriceIndicator() {
        return priceIndicator;
    }

    /**
     * @return the trade direction used to sign forward returns
     * @since 0.22.7
     */
    public TradeType getTradeType() {
        return tradeType;
    }

    /**
     * @return the forward-return horizon
     * @since 0.22.7
     */
    public int getHorizonBars() {
        return horizonBars;
    }

    /**
     * @return the number of matured signal observations included in the score
     * @since 0.22.7
     */
    public int getLookbackSignals() {
        return lookbackSignals;
    }

    private double signedForwardReturn(int entryIndex) {
        int exitIndex = entryIndex + horizonBars;
        Num entryPrice = priceIndicator.getValue(entryIndex);
        Num exitPrice = priceIndicator.getValue(exitIndex);
        if (Num.isNaNOrNull(entryPrice) || Num.isNaNOrNull(exitPrice) || entryPrice.isZero()) {
            return Double.NaN;
        }
        Num basisPoints = entryPrice.getNumFactory().numOf(BASIS_POINTS);
        double rawReturn = exitPrice.minus(entryPrice).dividedBy(entryPrice).multipliedBy(basisPoints).doubleValue();
        return tradeType == TradeType.SELL ? -rawReturn : rawReturn;
    }

    private Indicator<Integer> createLatestMaturedSignalIndexIndicator() {
        return new RecursiveCachedIndicator<>(signalIndicator, priceIndicator) {
            @Override
            protected Integer calculate(int index) {
                int beginIndex = firstEligibleSignalIndex();
                if (index < beginIndex) {
                    return NO_SIGNAL_INDEX;
                }
                if (Boolean.TRUE.equals(signalIndicator.getValue(index))
                        && Double.isFinite(signedForwardReturn(index))) {
                    return index;
                }
                int previousIndex = index - 1;
                return previousIndex >= beginIndex ? getValue(previousIndex) : NO_SIGNAL_INDEX;
            }

            @Override
            public int getCountOfUnstableBars() {
                return EntryEdgeIndicator.this.getCountOfUnstableBars();
            }
        };
    }

    private int firstEligibleSignalIndex() {
        return Math.max(getBarSeries().getBeginIndex(),
                getBarSeries().getBeginIndex() + signalIndicator.getCountOfUnstableBars());
    }

    private record Config(BarSeries series, Indicator<Boolean> signalIndicator, Indicator<Num> priceIndicator,
            TradeType tradeType, int horizonBars, int lookbackSignals) {
    }
}
