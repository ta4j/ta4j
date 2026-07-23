/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Cached {@link Indicator indicator}.
 *
 * <p>
 * Caches the calculated results of the indicator to avoid calculating the same
 * index of the indicator twice. The caching drastically speeds up access to
 * indicator values. Caching is especially recommended when indicators calculate
 * their values based on the values of other indicators. Such nested indicators
 * can call {@link #getValue(int)} multiple times without the need to
 * {@link #calculate(int)} again.
 *
 * <p>
 * This implementation uses a ring buffer for O(1) eviction when
 * {@code maximumBarCount} is set, and read-optimized locking for better
 * concurrency on cache hits.
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. Concurrent reads of cached values are optimized
 * via a lock-free fast path with seqlock-style validation. Cache misses and
 * mutations acquire appropriate locks. The implementation is reentrant,
 * allowing recursive indicators to call {@link #getValue(int)} from within
 * {@link #calculate(int)} without deadlocking.
 *
 * <p>
 * <strong>Note:</strong> Unlike previous versions, this class no longer uses
 * {@code synchronized} methods for external locking purposes. Code that relied
 * on synchronizing on indicator instances for atomicity guarantees must be
 * updated to use explicit external synchronization.
 *
 * @param <T> cached result type
 */
public abstract class CachedIndicator<T> extends AbstractIndicator<T> {

    /**
     * Maximum time (in milliseconds) to wait for a concurrent last-bar computation
     * to complete before computing independently. This prevents indefinite hangs if
     * the owning thread dies or encounters an unexpected issue.
     */
    private static final long LAST_BAR_WAIT_TIMEOUT_MS = 5000;

    /** Shared runtime state for structurally equivalent indicator instances. */
    private final SharedState<T> state;
    private final IntFunction<T> calculator = this::calculate;
    private final IntConsumer computedIndexRecorder = this::updateHighestResultIndex;

    /**
     * Should always be the index of the last (calculated) result in the cache.
     * Exposed for subclass access (e.g., RecursiveCachedIndicator).
     */
    protected volatile int highestResultIndex = -1;

    private static boolean equalsNum(Num left, Num right) {
        return left == right || (left != null && left.equals(right));
    }

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    protected CachedIndicator(BarSeries series) {
        this(validatedConfig(series, LAST_BAR_WAIT_TIMEOUT_MS, null));
    }

    /**
     * Creates a deterministic indicator whose cache state is shared with equivalent
     * instances on the same standard series.
     *
     * @param series   the bar series
     * @param identity the complete immutable constructor inputs
     * @since 0.23.1
     */
    protected CachedIndicator(BarSeries series, IndicatorIdentity identity) {
        this(validatedConfig(series, LAST_BAR_WAIT_TIMEOUT_MS, identity));
    }

    CachedIndicator(BarSeries series, long lastBarWaitTimeoutMs) {
        this(validatedConfig(series, lastBarWaitTimeoutMs, null));
    }

    private CachedIndicator(Config config) {
        super(config.series(), config.identity());
        BarSeries series = underlyingBarSeries();
        IndicatorIdentity identity = indicatorIdentity();
        long historyEpoch = series.getBarHistoryEpoch();
        this.state = identity == null || historyEpoch < 0
                ? new SharedState<>(identity, config.cacheLimit(), config.lastBarWaitTimeoutMs(), historyEpoch)
                : series.indicators().sharedState(identity, config.cacheLimit(), config.lastBarWaitTimeoutMs());
        this.highestResultIndex = state.highestResultIndex;
    }

    private static Config validatedConfig(BarSeries series, long lastBarWaitTimeoutMs, IndicatorIdentity identity) {
        if (lastBarWaitTimeoutMs <= 0) {
            throw new IllegalArgumentException("Last-bar wait timeout must be positive");
        }
        int limit = series.getMaximumBarCount();
        return new Config(series, limit, lastBarWaitTimeoutMs, identity);
    }

    /**
     * Constructor.
     *
     * @param indicator a related indicator (with a bar series)
     */
    protected CachedIndicator(Indicator<?> indicator) {
        this(validatedConfig(indicator, LAST_BAR_WAIT_TIMEOUT_MS, null));
    }

    /**
     * Creates a deterministic indicator whose cache state is shared with equivalent
     * instances on the source indicator's series.
     *
     * @param indicator a related indicator
     * @param identity  the complete immutable constructor inputs
     * @since 0.23.1
     */
    protected CachedIndicator(Indicator<?> indicator, IndicatorIdentity identity) {
        this(validatedConfig(indicator, LAST_BAR_WAIT_TIMEOUT_MS, identity));
    }

    private record Config(BarSeries series, int cacheLimit, long lastBarWaitTimeoutMs, IndicatorIdentity identity) {
    }

    private static Config validatedConfig(Indicator<?> indicator, long lastBarWaitTimeoutMs,
            IndicatorIdentity identity) {
        return validatedConfig(Objects.requireNonNull(indicator, "indicator").getBarSeries(), lastBarWaitTimeoutMs,
                identity);
    }

    /**
     * @param index the bar index
     * @return the value of the indicator
     */
    protected abstract T calculate(int index);

    @Override
    public T getValue(int index) {
        BarSeries series = underlyingBarSeries();
        while (true) {
            long historyEpoch = series.getBarHistoryEpoch();
            state.ensureEpoch(historyEpoch);
            highestResultIndex = state.highestResultIndex;
            final int removedBarsCount = series.getRemovedBarsCount();
            final int endIndex = series.getEndIndex();

            try {
                T result;
                if (index < removedBarsCount) {
                    if (log.isTraceEnabled()) {
                        log.trace("{}: result from bar {} already removed from cache, use {}-th instead",
                                getClass().getSimpleName(), index, removedBarsCount);
                    }
                    result = getFirstBarValue(series, removedBarsCount);
                    requireUnchangedEpoch(series, historyEpoch);
                } else if (index == endIndex) {
                    result = getLastBarValue(index, series);
                    requireUnchangedEpoch(series, historyEpoch);
                } else {
                    boolean promotedLastBar = promotePreviousLastBar(index, series);
                    result = promotedLastBar ? state.cache.get(index)
                            : getOrComputeAndCache(index, series, historyEpoch);
                    if (promotedLastBar) {
                        requireUnchangedEpoch(series, historyEpoch);
                    }
                }

                if (log.isTraceEnabled()) {
                    log.trace("{}({}): {}", this, index, result);
                }
                return result;
            } catch (HistoryEpochChangedException ignored) {
                // A concurrent historical mutation won the race. The next iteration
                // invalidates lazily and recomputes against the new epoch.
            }
        }
    }

    /**
     * Gets the cached value or computes and caches it.
     *
     * @param index the series index
     * @return the indicator value
     */
    private T getOrComputeAndCache(int index, BarSeries series, long historyEpoch) {
        return state.cache.getOrCompute(index, calculator, computedIndexRecorder, series, historyEpoch);
    }

    private static void requireUnchangedEpoch(BarSeries series, long historyEpoch) {
        if (historyEpoch >= 0 && series.getBarHistoryEpoch() != historyEpoch) {
            throw HistoryEpochChangedException.INSTANCE;
        }
    }

    private boolean promotePreviousLastBar(int index, BarSeries series) {
        if (state.lastBarCachedIndex != index) {
            return false;
        }
        T cachedResult;
        Bar cachedBar;
        long cachedTradeCount;
        Num cachedClosePrice;
        synchronized (state.lastBarLock) {
            if (state.lastBarCachedIndex != index || state.lastBarRef == null) {
                return false;
            }
            Bar currentBar = series.getBar(index);
            if (currentBar != state.lastBarRef || currentBar.getTrades() != state.lastBarTradeCount
                    || !equalsNum(currentBar.getClosePrice(), state.lastBarClosePrice)) {
                return false;
            }
            cachedResult = state.lastBarCachedResult;
            cachedBar = currentBar;
            cachedTradeCount = currentBar.getTrades();
            cachedClosePrice = currentBar.getClosePrice();
        }

        state.cache.put(index, cachedResult);
        Bar currentBar = series.getBar(index);
        if (currentBar != cachedBar || currentBar.getTrades() != cachedTradeCount
                || !equalsNum(currentBar.getClosePrice(), cachedClosePrice)) {
            state.cache.invalidateFrom(index);
            return false;
        }
        updateHighestResultIndex(index);
        return true;
    }

    /**
     * Updates {@link #highestResultIndex} to at least {@code index} without
     * regressing under contention.
     *
     * @param index computed result index
     */
    protected final void updateHighestResultIndex(int index) {
        int current;
        do {
            current = state.highestResultIndex;
            if (index <= current) {
                highestResultIndex = current;
                return;
            }
        } while (!SharedState.HIGHEST_RESULT_INDEX_UPDATER.compareAndSet(state, current, index));
        highestResultIndex = index;
    }

    /**
     * Gets the value for indices before the removed bars count.
     *
     * <p>
     * Bars with indices &lt; {@code removedBarsCount} are no longer available in
     * the series. The series maps such accesses to the first remaining bar. Caching
     * this value must be aware of {@code removedBarsCount} changes; otherwise a
     * cached value for index 0 may become stale when the series window advances.
     *
     * <h3>Concurrency Note (TOCTOU)</h3>
     * <p>
     * This method computes the value outside the lock to avoid deadlocks with the
     * main cache lock. If the series window advances during computation (i.e.,
     * {@code removedBarsCount} changes), the computed value is returned to the
     * caller but <em>not</em> cached. This means the caller may receive a value
     * computed against bar data that is no longer the "first available" bar.
     *
     * <p>
     * In practice, this is acceptable because:
     * <ul>
     * <li>The returned value is still valid for the bar that existed at the start
     * of the request.</li>
     * <li>Subsequent calls will compute against the new first bar.</li>
     * <li>Caching stale data would be worse than returning a slightly outdated but
     * correct value.</li>
     * </ul>
     *
     * @param series           the bar series
     * @param removedBarsCount the removed bars count at the time of the request
     * @return the indicator value for the first available bar
     */
    private T getFirstBarValue(BarSeries series, int removedBarsCount) {
        if (state.firstBarHasCachedResult && state.firstBarCachedRemovedBarsCount == removedBarsCount) {
            return state.firstBarCachedResult;
        }

        // Compute outside the lock to avoid lock-order deadlocks with the cache lock.
        T computed = calculate(0);

        // If the series window advanced during computation, don't cache this value.
        if (series.getRemovedBarsCount() != removedBarsCount) {
            return computed;
        }

        synchronized (state.firstBarLock) {
            if (state.firstBarHasCachedResult && state.firstBarCachedRemovedBarsCount == removedBarsCount) {
                return state.firstBarCachedResult;
            }
            state.firstBarCachedRemovedBarsCount = removedBarsCount;
            state.firstBarCachedResult = computed;
            state.firstBarHasCachedResult = true;
            return computed;
        }
    }

    /**
     * Gets the value for the last bar with mutation-aware caching.
     *
     * <p>
     * The last bar (endIndex) is special because it may be mutated (e.g., via
     * {@link Bar#addTrade(Num, Num)} or {@link Bar#addPrice(Num)}), or replaced via
     * {@link BarSeries#addBar(Bar, boolean)} with {@code replace=true}. This method
     * caches the result but invalidates it if the bar has been modified since the
     * last computation (tracked via trades count and close price). The computation
     * is performed outside the lock to avoid lock-order deadlocks with the main
     * cache.
     *
     * @param index  the series index (should be endIndex)
     * @param series the bar series
     * @return the indicator value
     */
    private T getLastBarValue(int index, BarSeries series) {
        Bar snapshotBar;
        long snapshotTradeCount;
        Num snapshotClosePrice;
        long snapshotInvalidationCount;

        boolean ownsComputation = false;
        boolean timedOut = false;
        while (true) {
            synchronized (state.lastBarLock) {
                Bar bar1 = series.getLastBar();
                long tradeCount1 = bar1.getTrades();
                Num closePrice1 = bar1.getClosePrice();

                Bar bar2 = series.getLastBar();
                long tradeCount2 = bar2.getTrades();
                Num closePrice2 = bar2.getClosePrice();

                boolean stableRead = bar1 == bar2 && tradeCount1 == tradeCount2 && equalsNum(closePrice1, closePrice2);
                Bar currentBar = stableRead ? bar1 : bar2;
                long currentTradeCount = stableRead ? tradeCount1 : tradeCount2;
                Num currentClosePrice = stableRead ? closePrice1 : closePrice2;

                if (stableRead && index == state.lastBarCachedIndex && currentBar == state.lastBarRef
                        && currentTradeCount == state.lastBarTradeCount
                        && equalsNum(currentClosePrice, state.lastBarClosePrice)) {
                    return state.lastBarCachedResult;
                }

                // Check write lock BEFORE lastBarComputationInProgress to handle recursive
                // calls from calculate() while holding the cache write lock. In this case,
                // we must bypass caching to avoid advancing highestResultIndex while the
                // main cache doesn't have the value stored.
                if (state.cache.isWriteLockedByCurrentThread()) {
                    snapshotBar = currentBar;
                    snapshotTradeCount = currentTradeCount;
                    snapshotClosePrice = currentClosePrice;
                    snapshotInvalidationCount = -1;
                    break;
                }

                if (!state.lastBarComputationInProgress) {
                    state.lastBarComputationInProgress = true;
                    state.lastBarComputationIndex = index;
                    ownsComputation = true;
                    snapshotBar = currentBar;
                    snapshotTradeCount = currentTradeCount;
                    snapshotClosePrice = currentClosePrice;
                    snapshotInvalidationCount = state.lastBarCacheInvalidationCount;
                    break;
                }

                // If we already timed out waiting for another computation, compute
                // independently to prevent indefinite blocking
                if (timedOut) {
                    snapshotBar = currentBar;
                    snapshotTradeCount = currentTradeCount;
                    snapshotClosePrice = currentClosePrice;
                    snapshotInvalidationCount = -1;
                    break;
                }

                try {
                    // Wait with timeout to prevent indefinite hangs if the owning thread
                    // dies or encounters an unexpected issue. After timeout, we compute
                    // independently rather than blocking forever.
                    state.lastBarLock.wait(state.lastBarWaitTimeoutMs);
                    // Only mark as timed out if the computation is still in progress.
                    // If notifyAll() woke us because computation finished, we should
                    // loop back and re-check for a cache hit (or become the new owner).
                    if (state.lastBarComputationInProgress) {
                        timedOut = true;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    snapshotBar = currentBar;
                    snapshotTradeCount = currentTradeCount;
                    snapshotClosePrice = currentClosePrice;
                    snapshotInvalidationCount = -1;
                    break;
                }
            }
        }

        final T computed;
        try {
            computed = calculate(index);
        } catch (RuntimeException | Error error) {
            if (ownsComputation) {
                synchronized (state.lastBarLock) {
                    state.lastBarComputationInProgress = false;
                    state.lastBarComputationIndex = -1;
                    state.lastBarLock.notifyAll();
                }
            }
            throw error;
        }

        if (!ownsComputation) {
            // snapshotInvalidationCount == -1 signals that caching should be skipped
            // (e.g., recursive call while holding cache write lock, or thread
            // interrupted). In these cases, do not update highestResultIndex to avoid
            // creating a stale state where the index tracker is ahead of actual cached
            // values.
            if (snapshotInvalidationCount != -1) {
                updateHighestResultIndex(index);
            }
            return computed;
        }

        synchronized (state.lastBarLock) {
            try {
                if (snapshotInvalidationCount == state.lastBarCacheInvalidationCount) {
                    Bar bar1 = series.getLastBar();
                    long tradeCount1 = bar1.getTrades();
                    Num closePrice1 = bar1.getClosePrice();

                    Bar bar2 = series.getLastBar();
                    long tradeCount2 = bar2.getTrades();
                    Num closePrice2 = bar2.getClosePrice();

                    boolean stableRead = bar1 == bar2 && tradeCount1 == tradeCount2
                            && equalsNum(closePrice1, closePrice2);
                    Bar currentBar = stableRead ? bar1 : bar2;
                    long currentTradeCount = stableRead ? tradeCount1 : tradeCount2;
                    Num currentClosePrice = stableRead ? closePrice1 : closePrice2;

                    if (stableRead && currentBar == snapshotBar && currentTradeCount == snapshotTradeCount
                            && equalsNum(currentClosePrice, snapshotClosePrice)) {
                        state.lastBarRef = snapshotBar;
                        state.lastBarTradeCount = snapshotTradeCount;
                        state.lastBarClosePrice = snapshotClosePrice;
                        state.lastBarCachedResult = computed;
                        state.lastBarCachedIndex = index;
                        updateHighestResultIndex(index);
                    }
                } else {
                    // Cache was invalidated while this computation was in flight.
                    // Return the computed value to the caller, but do not update any
                    // cache state (including highestResultIndex) to avoid resurrecting
                    // invalidated state.
                }
                return computed;
            } finally {
                state.lastBarComputationInProgress = false;
                state.lastBarComputationIndex = -1;
                state.lastBarLock.notifyAll();
            }
        }
    }

    /**
     * Clears all cached values for this indicator.
     * <p>
     * Intended for indicators whose outputs can change retroactively (e.g., rolling
     * window recomputations). Regular indicators should not need to call this, as
     * cached values are assumed stable.
     */
    protected void invalidateCache() {
        clearLastBarCache();
        clearFirstBarCache();
        state.cache.clear();
        state.highestResultIndex = -1;
        highestResultIndex = -1;
    }

    /**
     * Clears cached values from the specified index (inclusive) to the end of the
     * cache. Values before the index remain cached.
     *
     * <p>
     * If an affected last-bar computation is in progress, its result will not be
     * cached.
     *
     * @param index the first index to invalidate; if negative, the entire cache is
     *              cleared
     */
    protected void invalidateFrom(int index) {
        int lastBarIndex;
        synchronized (state.lastBarLock) {
            lastBarIndex = state.lastBarCachedIndex;
            if (lastBarIndex >= index
                    || (state.lastBarComputationInProgress && state.lastBarComputationIndex >= index)) {
                clearLastBarCacheLocked();
                lastBarIndex = -1;
            }
        }

        if (index <= 0) {
            clearFirstBarCache();
        }

        state.cache.invalidateFrom(index);
        int cacheHighest = state.cache.getHighestResultIndex();

        // Preserve last-bar cache knowledge when it is still valid. This avoids
        // decreasing highestResultIndex when the primary cache does not contain the
        // last-bar result.
        state.highestResultIndex = Math.max(cacheHighest, lastBarIndex);
        highestResultIndex = state.highestResultIndex;
    }

    /**
     * Clears the last-bar cache state.
     */
    private void clearLastBarCache() {
        synchronized (state.lastBarLock) {
            clearLastBarCacheLocked();
        }
    }

    private void clearLastBarCacheLocked() {
        state.lastBarCacheInvalidationCount++;
        state.lastBarRef = null;
        state.lastBarTradeCount = 0;
        state.lastBarClosePrice = null;
        state.lastBarCachedResult = null;
        state.lastBarCachedIndex = -1;
    }

    private void clearFirstBarCache() {
        synchronized (state.firstBarLock) {
            state.firstBarCachedRemovedBarsCount = -1;
            state.firstBarHasCachedResult = false;
            state.firstBarCachedResult = null;
        }
    }

    /**
     * Returns the underlying cache buffer.
     * <p>
     * For internal use by subclasses (e.g., RecursiveCachedIndicator).
     *
     * @return the cache buffer
     */
    CachedBuffer<T> getCache() {
        return state.cache;
    }

    final int sharedHighestResultIndex() {
        return state.highestResultIndex;
    }

    final Object sharedStateIdentity() {
        return state;
    }

    static final class HistoryEpochChangedException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        static final HistoryEpochChangedException INSTANCE = new HistoryEpochChangedException();

        private HistoryEpochChangedException() {
            super(null, null, false, false);
        }
    }

    /** Runtime state shared only by structurally equivalent indicators. */
    static final class SharedState<T> {

        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<SharedState> HIGHEST_RESULT_INDEX_UPDATER = AtomicIntegerFieldUpdater
                .newUpdater(SharedState.class, "highestResultIndex");

        // Keeps the WeakHashMap key alive for exactly as long as any indicator uses
        // this state. The context itself retains the state only through a weak value.
        final IndicatorIdentity identity;
        private final CachedBuffer<T> cache;
        private final long lastBarWaitTimeoutMs;
        private final Object epochLock = new Object();
        private final Object lastBarLock = new Object();
        private final Object firstBarLock = new Object();

        private volatile long historyEpoch;
        private volatile int highestResultIndex = -1;

        private boolean lastBarComputationInProgress;
        private int lastBarComputationIndex = -1;
        private long lastBarCacheInvalidationCount;
        private volatile Bar lastBarRef;
        private volatile long lastBarTradeCount;
        private volatile Num lastBarClosePrice;
        private volatile T lastBarCachedResult;
        private volatile int lastBarCachedIndex = -1;

        private volatile int firstBarCachedRemovedBarsCount = -1;
        private volatile boolean firstBarHasCachedResult;
        private volatile T firstBarCachedResult;

        SharedState(IndicatorIdentity identity, int cacheLimit, long lastBarWaitTimeoutMs, long historyEpoch) {
            this.identity = identity;
            this.cache = new CachedBuffer<>(cacheLimit);
            this.lastBarWaitTimeoutMs = lastBarWaitTimeoutMs;
            this.historyEpoch = historyEpoch;
        }

        private void ensureEpoch(long currentEpoch) {
            if (currentEpoch < 0 || historyEpoch == currentEpoch) {
                return;
            }
            synchronized (epochLock) {
                if (historyEpoch == currentEpoch) {
                    return;
                }
                cache.clear();
                synchronized (lastBarLock) {
                    lastBarCacheInvalidationCount++;
                    lastBarRef = null;
                    lastBarTradeCount = 0;
                    lastBarClosePrice = null;
                    lastBarCachedResult = null;
                    lastBarCachedIndex = -1;
                    lastBarLock.notifyAll();
                }
                synchronized (firstBarLock) {
                    firstBarCachedRemovedBarsCount = -1;
                    firstBarHasCachedResult = false;
                    firstBarCachedResult = null;
                }
                highestResultIndex = -1;
                historyEpoch = currentEpoch;
            }
        }
    }
}
