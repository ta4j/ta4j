/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Objects;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeries.BarSeriesChangeSnapshot;
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
 */
public abstract class CachedIndicator<T> extends AbstractIndicator<T> {

    /**
     * Maximum time (in milliseconds) to wait for a concurrent last-bar computation
     * to complete before computing independently. This prevents indefinite hangs if
     * the owning thread dies or encounters an unexpected issue.
     */
    private static final long LAST_BAR_WAIT_TIMEOUT_MS = 5000;

    /** The ring-buffer backed cache. */
    private final CachedBuffer<T> cache;
    private final long lastBarWaitTimeoutMs;
    private final AtomicReference<BarSeriesChangeSnapshot> observedSeriesSnapshot;
    /**
     * Shared empty source array for indicators constructed directly from a
     * {@link BarSeries}.
     */
    private static final Indicator<?>[] NO_SOURCES = new Indicator<?>[0];

    /**
     * The source indicators whose full-tail invalidations propagate to this
     * indicator on head advance (see
     * {@link #minimumCacheableIndexAfterHeadAdvance(int)}). Empty when constructed
     * directly from a {@link BarSeries}.
     */
    private final Indicator<?>[] sourceIndicators;

    private final IntFunction<T> calculator = this::calculate;
    private final IntConsumer computedIndexRecorder = this::updateHighestResultIndex;

    private static final AtomicIntegerFieldUpdater<CachedIndicator> HIGHEST_RESULT_INDEX_UPDATER = AtomicIntegerFieldUpdater
            .newUpdater(CachedIndicator.class, "highestResultIndex");

    /**
     * Should always be the index of the last (calculated) result in the cache.
     * Exposed for subclass access (e.g., RecursiveCachedIndicator).
     */
    protected volatile int highestResultIndex = -1;

    /** Lock protecting the last-bar cache check+compute sequence. */
    private final Object lastBarLock = new Object();

    // Last-bar caching state
    private boolean lastBarComputationInProgress;
    private int lastBarComputationIndex = -1;
    private long lastBarCacheInvalidationCount;
    private volatile Bar lastBarRef;
    private volatile long lastBarTradeCount;
    private volatile Num lastBarClosePrice;
    private volatile T lastBarCachedResult;
    private volatile int lastBarCachedIndex = -1;

    // First-available-bar caching state (for indices < removedBarsCount)
    private final Object firstBarLock = new Object();
    private volatile int firstBarCachedRemovedBarsCount = -1;
    private volatile boolean firstBarHasCachedResult;
    private volatile T firstBarCachedResult;

    private static boolean equalsNum(Num left, Num right) {
        return left == right || (left != null && left.equals(right));
    }

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    protected CachedIndicator(BarSeries series) {
        this(validatedConfig(series, LAST_BAR_WAIT_TIMEOUT_MS), NO_SOURCES);
    }

    CachedIndicator(BarSeries series, long lastBarWaitTimeoutMs) {
        this(validatedConfig(series, lastBarWaitTimeoutMs), NO_SOURCES);
    }

    private CachedIndicator(Config config, Indicator<?>[] sourceIndicators) {
        super(config.series());
        BarSeriesChangeSnapshot snapshot = config.snapshot();
        this.cache = CachedBuffer.of(snapshot.maximumBarCount());
        this.lastBarWaitTimeoutMs = config.lastBarWaitTimeoutMs();
        this.observedSeriesSnapshot = new AtomicReference<>(snapshot);
        this.sourceIndicators = sourceIndicators;
    }

    private static Config validatedConfig(BarSeries series, long lastBarWaitTimeoutMs) {
        if (lastBarWaitTimeoutMs <= 0) {
            throw new IllegalArgumentException("Last-bar wait timeout must be positive");
        }
        BarSeriesChangeSnapshot snapshot = series.getBarSeriesChangeSnapshot(-1L);
        if (snapshot.maximumBarCount() <= 0) {
            throw new IllegalArgumentException("Maximum bar count must be strictly positive");
        }
        return new Config(series, snapshot, lastBarWaitTimeoutMs);
    }

    /**
     * Constructor.
     *
     * @param indicator a related indicator (with a bar series); retained so
     *                  full-tail invalidations propagate to this indicator
     */
    protected CachedIndicator(Indicator<?> indicator) {
        this(validatedConfig(indicator, LAST_BAR_WAIT_TIMEOUT_MS), new Indicator<?>[] { indicator });
    }

    /**
     * Constructor for indicators that read from several related indicators.
     *
     * @param firstSource       a related indicator (with a bar series); retained so
     *                          full-tail invalidations propagate to this indicator
     * @param additionalSources further related indicators retained for the same
     *                          propagation
     * @since 0.24.2
     */
    protected CachedIndicator(Indicator<?> firstSource, Indicator<?>... additionalSources) {
        this(validatedConfig(firstSource, LAST_BAR_WAIT_TIMEOUT_MS), flattened(firstSource, additionalSources));
    }

    private static Indicator<?>[] flattened(Indicator<?> firstSource, Indicator<?>[] additionalSources) {
        Indicator<?>[] sources = new Indicator<?>[additionalSources.length + 1];
        sources[0] = firstSource;
        System.arraycopy(additionalSources, 0, sources, 1, additionalSources.length);
        return sources;
    }

    /**
     * Returns the source indicators this indicator reads, in registration order.
     * Full-tail invalidations propagate through these sources on series head
     * advance, so reporting them keeps dependency traversal truthful.
     *
     * @return the registered source indicators; empty when constructed directly
     *         from a {@link BarSeries}
     * @since 0.24.2
     */
    @Override
    public List<Indicator<?>> getDependencies() {
        return List.<Indicator<?>>of(sourceIndicators);
    }

    private record Config(BarSeries series, BarSeriesChangeSnapshot snapshot, long lastBarWaitTimeoutMs) {
    }

    private static Config validatedConfig(Indicator<?> indicator, long lastBarWaitTimeoutMs) {
        return validatedConfig(Objects.requireNonNull(indicator, "indicator").getBarSeries(), lastBarWaitTimeoutMs);
    }

    /**
     * @param index the bar index
     * @return the value of the indicator
     */
    protected abstract T calculate(int index);

    @Override
    public T getValue(int index) {
        BarSeries series = getBarSeries();
        BarSeriesChangeSnapshot snapshot = synchronizeCacheWithSeries(series);
        final int removedBarsCount = snapshot.removedThroughIndex() + 1;
        final int endIndex = snapshot.endIndex();

        T result;
        if (index < removedBarsCount) {
            // Result already removed from cache
            if (log.isTraceEnabled()) {
                log.trace("{}: result from bar {} already removed from cache, use {}-th instead",
                        getClass().getSimpleName(), index, removedBarsCount);
            }
            // Map all pruned indices to zero to avoid recursive backtracking into
            // removed history. calculate(0) for recursive indicators is the base case
            // and does not chase further into negative/removed indexes.
            result = getFirstBarValue(series, removedBarsCount);
        } else if (index == endIndex) {
            // Last bar: use mutation-aware caching
            result = getLastBarValue(index, series);
        } else {
            // Normal case: use the cache
            result = getOrComputeAndCache(index);
        }

        if (log.isTraceEnabled()) {
            log.trace("{}({}): {}", this, index, result);
        }
        return result;
    }

    /**
     * Reconciles cached state with the latest series revision, retained window, and
     * capacity before a read or recursive prefill.
     *
     * @param series indicator bar series
     * @return the synchronized series snapshot
     * @since 0.24.1
     */
    protected final BarSeriesChangeSnapshot synchronizeCacheWithSeries(BarSeries series) {
        boolean reconciliationRequired = false;
        while (true) {
            BarSeriesChangeSnapshot sinceSnapshot = observedSeriesSnapshot.get();
            BarSeriesChangeSnapshot snapshot = series.getBarSeriesChangeSnapshot(sinceSnapshot.revision());
            if (!reconciliationRequired && sameSeriesState(snapshot, sinceSnapshot)) {
                return snapshot;
            }

            if (cache.isWriteLockedByCurrentThread()) {
                // Called recursively from calculate()/prefill on this thread while the
                // ring's write lock is held. Applying a destructive range trim here would
                // punch a hole in the cache that an in-flight iterative prefill cannot
                // refill (the prefill depth guard skips nested prefills), forcing the
                // recursive fallback to walk the gap index by index until the stack
                // overflows. Defer the trim and the observation advance to the next
                // top-level read, which reconciles against the full change journal.
                return snapshot;
            }

            int invalidateFrom = snapshot.earliestChangedIndex();
            int firstRetainedIndex = snapshot.removedThroughIndex() + 1;
            // When the series head advanced, entries cached at indexes whose
            // preceding window is no longer fully available were computed from
            // bars that have since been removed. Drop them so reads after the
            // advance recompute against the retained window; the eviction floor
            // is chosen by minimumCacheableIndexAfterHeadAdvance(int).
            boolean headAdvanced = snapshot.removedThroughIndex() != sinceSnapshot.removedThroughIndex();
            int minimumCacheableIndex = !headAdvanced ? firstRetainedIndex
                    : minimumCacheableIndexAfterHeadAdvance(firstRetainedIndex);
            int lastBarIndex = synchronizeLastBarCache(snapshot, invalidateFrom, minimumCacheableIndex);

            // The first-bar cache holds the indicator value for the first available
            // bar, i.e. series index firstRetainedIndex. Any published change at or
            // below that index alters the data the cached value was computed from, so
            // the cache must be cleared whenever such a change is observed - not only
            // when index 0 changes (a replaced first available bar at index
            // removedBarsCount > 0 would otherwise keep serving the stale value).
            if (snapshot.removedThroughIndex() != sinceSnapshot.removedThroughIndex()
                    || (invalidateFrom >= 0 && invalidateFrom <= firstRetainedIndex)) {
                clearFirstBarCache();
            }

            int cacheHighest = cache.synchronize(minimumCacheableIndex, snapshot.maximumBarCount(), invalidateFrom);
            highestResultIndex = Math.max(cacheHighest, lastBarIndex);

            if (observedSeriesSnapshot.compareAndSet(sinceSnapshot, snapshot)) {
                return snapshot;
            }
            reconciliationRequired = true;
        }
    }

    /**
     * Returns the lowest index whose cached value remains trustworthy after the
     * series head advanced to {@code firstRetainedIndex}: the unstable range
     * declared by {@link #getCountOfUnstableBars()} was computed from bars that no
     * longer exist and must be recomputed against the retained window.
     *
     * @param firstRetainedIndex the first series index that remains available
     * @return the cache floor for the retained range
     */
    private int unstableRangeFloor(int firstRetainedIndex) {
        final long unstableBars = getCountOfUnstableBars();
        if (unstableBars <= 0L) {
            return firstRetainedIndex;
        }
        final long floor = (long) firstRetainedIndex + unstableBars;
        return floor >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) floor;
    }

    /**
     * Reports whether values of this indicator depend, directly or through
     * recursion, on history that precedes any finite declared unstable range.
     * Recursive indicators (for example {@link RecursiveCachedIndicator}
     * subclasses) return {@code true}; their cached values cannot be recomputed
     * against the retained window of a bounded series, so head-advance
     * reconciliation keeps them instead of applying the unstable-range floor.
     *
     * @return {@code true} when values depend on earlier values beyond the declared
     *         unstable range
     */
    protected boolean hasRecursiveDependencies() {
        return false;
    }

    /**
     * Selects the cache floor applied after the series head advanced, i.e. the
     * lowest cached index that stays valid once {@code removedThroughIndex} grew.
     * Entries below the floor are evicted and recomputed against the retained
     * window on the next read; entries at or above it are kept.
     *
     * <p>
     * The default resolves one of two policies, after propagating source
     * invalidations: when the source graph - traversed through
     * {@link Indicator#getDependencies()} so non-cached wrappers are not a barrier
     * - contains a {@link CachedIndicator} that applies a floor above its own
     * default unstable-range floor (for example a rebaselining source such as
     * {@link StochasticIndicator} discarding its whole cache), this indicator
     * discards its whole cache too, because its cached values were derived from
     * source values that no longer exist. A source whose floor merely covers its
     * own declared unstable band does not trigger propagation, because that floor
     * is the band's default eviction and dependents already tolerate it. Indicators
     * with unbounded historical dependencies ({@link #hasRecursiveDependencies()}
     * returns {@code true}) and no such source keep every cached value, because
     * their results cannot be recomputed from the retained window alone; all other
     * indicators evict the declared unstable range ({@code firstRetainedIndex} plus
     * {@link #getCountOfUnstableBars()}) so that re-seeded values match a fresh
     * calculation against the retained window. A subclass whose values are always
     * recomputable from the retained window, including any portion that only
     * conditionally recurses (for example {@link StochasticIndicator}), overrides
     * this method and returns {@link Integer#MAX_VALUE} to discard the whole cache:
     * keeping only the recursively derived band would preserve stale results
     * computed from evicted bars.
     * </p>
     *
     * @param firstRetainedIndex the first series index that remains available
     * @return the lowest cached index that stays valid after the head advance;
     *         {@link Integer#MAX_VALUE} discards every cached entry
     */
    protected int minimumCacheableIndexAfterHeadAdvance(int firstRetainedIndex) {
        // One identity-visited set across every source: subgraphs shared by
        // several sources are inspected once instead of once per source.
        Set<Indicator<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Indicator<?> sourceIndicator : sourceIndicators) {
            if (sourceGraphRebaselinesBeyondDefaultBand(sourceIndicator, firstRetainedIndex, visited)) {
                return Integer.MAX_VALUE;
            }
        }
        return hasRecursiveDependencies() ? firstRetainedIndex : unstableRangeFloor(firstRetainedIndex);
    }

    /**
     * Whether any indicator in the source graph rooted at {@code source} applies a
     * cache floor above its own default unstable-range band after the series head
     * advanced to {@code firstRetainedIndex}. A direct {@link CachedIndicator}
     * source is checked like before; any other source is traversed through
     * {@link Indicator#getDependencies()}, so a rebaselining source hidden behind
     * non-cached wrappers (for example a stochastic inside a
     * {@code BinaryOperationIndicator}) still invalidates the whole cache.
     * <p>
     * The traversal tracks visited nodes by identity, so subgraphs shared by
     * several dependency paths or sources are inspected once instead of once per
     * incoming path, and dependency cycles terminate.
     *
     * @param source             the source indicator to inspect
     * @param firstRetainedIndex the first series index that remains available
     * @return {@code true} when a rebaselining source lies in the graph
     */

    private static boolean sourceGraphRebaselinesBeyondDefaultBand(Indicator<?> source, int firstRetainedIndex,
            Set<Indicator<?>> visited) {
        if (!visited.add(source)) {
            return false;
        }
        if (source instanceof CachedIndicator<?> cachedSource) {
            return rebaselinesBeyondDefaultBand(cachedSource, firstRetainedIndex);
        }
        for (Indicator<?> dependency : source.getDependencies()) {
            if (sourceGraphRebaselinesBeyondDefaultBand(dependency, firstRetainedIndex, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean rebaselinesBeyondDefaultBand(CachedIndicator<?> source, int firstRetainedIndex) {
        long defaultBandFloor = (long) firstRetainedIndex + source.getCountOfUnstableBars();
        return source.minimumCacheableIndexAfterHeadAdvance(firstRetainedIndex) > defaultBandFloor;
    }

    private static boolean sameSeriesState(BarSeriesChangeSnapshot left, BarSeriesChangeSnapshot right) {
        return left.revision() == right.revision() && left.removedThroughIndex() == right.removedThroughIndex()
                && left.maximumBarCount() == right.maximumBarCount() && left.endIndex() == right.endIndex();
    }

    private int synchronizeLastBarCache(BarSeriesChangeSnapshot snapshot, int invalidateFrom, int firstRetainedIndex) {
        synchronized (lastBarLock) {
            boolean cachedIndexChangedRole = lastBarCachedIndex >= 0 && lastBarCachedIndex != snapshot.endIndex();
            boolean cachedIndexInvalid = lastBarCachedIndex >= 0 && (lastBarCachedIndex < firstRetainedIndex
                    || invalidateFrom >= 0 && lastBarCachedIndex >= invalidateFrom);
            boolean computationInvalid = lastBarComputationInProgress
                    && (lastBarComputationIndex != snapshot.endIndex() || lastBarComputationIndex < firstRetainedIndex
                            || invalidateFrom >= 0 && lastBarComputationIndex >= invalidateFrom);
            if (cachedIndexChangedRole || cachedIndexInvalid || computationInvalid) {
                clearLastBarCacheLocked();
            }
            return lastBarCachedIndex;
        }
    }

    /**
     * Gets the cached value or computes and caches it.
     *
     * @param index the series index
     * @return the indicator value
     */
    private T getOrComputeAndCache(int index) {
        return cache.getOrCompute(index, calculator, computedIndexRecorder);
    }

    /**
     * Updates {@link #highestResultIndex} to at least {@code index} without
     * regressing under contention.
     */
    protected final void updateHighestResultIndex(int index) {
        int current;
        do {
            current = highestResultIndex;
            if (index <= current) {
                return;
            }
        } while (!HIGHEST_RESULT_INDEX_UPDATER.compareAndSet(this, current, index));
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
        if (firstBarHasCachedResult && firstBarCachedRemovedBarsCount == removedBarsCount) {
            return firstBarCachedResult;
        }

        // Compute outside the lock to avoid lock-order deadlocks with the cache lock.
        T computed = calculate(0);

        // If the series window advanced during computation, don't cache this value.
        if (series.getRemovedBarsCount() != removedBarsCount) {
            return computed;
        }

        synchronized (firstBarLock) {
            if (firstBarHasCachedResult && firstBarCachedRemovedBarsCount == removedBarsCount) {
                return firstBarCachedResult;
            }
            firstBarCachedRemovedBarsCount = removedBarsCount;
            firstBarCachedResult = computed;
            firstBarHasCachedResult = true;
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
            synchronized (lastBarLock) {
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

                if (stableRead && index == lastBarCachedIndex && currentBar == lastBarRef
                        && currentTradeCount == lastBarTradeCount && equalsNum(currentClosePrice, lastBarClosePrice)) {
                    return lastBarCachedResult;
                }

                // Check write lock BEFORE lastBarComputationInProgress to handle recursive
                // calls from calculate() while holding the cache write lock. In this case,
                // we must bypass caching to avoid advancing highestResultIndex while the
                // main cache doesn't have the value stored.
                if (cache.isWriteLockedByCurrentThread()) {
                    snapshotBar = currentBar;
                    snapshotTradeCount = currentTradeCount;
                    snapshotClosePrice = currentClosePrice;
                    snapshotInvalidationCount = -1;
                    break;
                }

                if (!lastBarComputationInProgress) {
                    lastBarComputationInProgress = true;
                    lastBarComputationIndex = index;
                    ownsComputation = true;
                    snapshotBar = currentBar;
                    snapshotTradeCount = currentTradeCount;
                    snapshotClosePrice = currentClosePrice;
                    snapshotInvalidationCount = lastBarCacheInvalidationCount;
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
                    lastBarLock.wait(lastBarWaitTimeoutMs);
                    // Only mark as timed out if the computation is still in progress.
                    // If notifyAll() woke us because computation finished, we should
                    // loop back and re-check for a cache hit (or become the new owner).
                    if (lastBarComputationInProgress) {
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
                synchronized (lastBarLock) {
                    lastBarComputationInProgress = false;
                    lastBarComputationIndex = -1;
                    lastBarLock.notifyAll();
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

        synchronized (lastBarLock) {
            try {
                if (snapshotInvalidationCount == lastBarCacheInvalidationCount) {
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
                        lastBarRef = snapshotBar;
                        lastBarTradeCount = snapshotTradeCount;
                        lastBarClosePrice = snapshotClosePrice;
                        lastBarCachedResult = computed;
                        lastBarCachedIndex = index;
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
                lastBarComputationInProgress = false;
                lastBarComputationIndex = -1;
                lastBarLock.notifyAll();
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
        cache.clear();
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
        synchronized (lastBarLock) {
            lastBarIndex = lastBarCachedIndex;
            if (lastBarIndex >= index || (lastBarComputationInProgress && lastBarComputationIndex >= index)) {
                clearLastBarCacheLocked();
                lastBarIndex = -1;
            }
        }

        if (index <= 0) {
            clearFirstBarCache();
        }

        cache.invalidateFrom(index);
        int cacheHighest = cache.getHighestResultIndex();

        // Preserve last-bar cache knowledge when it is still valid. This avoids
        // decreasing highestResultIndex when the primary cache does not contain the
        // last-bar result.
        highestResultIndex = Math.max(cacheHighest, lastBarIndex);
    }

    /**
     * Clears the last-bar cache state.
     */
    private void clearLastBarCache() {
        synchronized (lastBarLock) {
            clearLastBarCacheLocked();
        }
    }

    private void clearLastBarCacheLocked() {
        lastBarCacheInvalidationCount++;
        lastBarRef = null;
        lastBarTradeCount = 0;
        lastBarClosePrice = null;
        lastBarCachedResult = null;
        lastBarCachedIndex = -1;
    }

    private void clearFirstBarCache() {
        synchronized (firstBarLock) {
            firstBarCachedRemovedBarsCount = -1;
            firstBarHasCachedResult = false;
            firstBarCachedResult = null;
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
        return cache;
    }
}
