/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.NumFactory;

/**
 * A dynamically aligned portfolio view over caller-owned bar series.
 *
 * <p>
 * Construction collects the sorted union of the currently retained end times.
 * {@link #advance()} appends newly observed end times greater than the current
 * timeline tail; it never inserts or removes a published portfolio index. A
 * newly added asset can resolve against existing timeline times, but its older
 * end times do not change that timeline. Removing an asset leaves all published
 * times intact. Stage source updates before advancing if all simultaneous asset
 * end times must appear in the timeline. The caller is responsible for
 * publishing only bars ready for strategy evaluation: a {@link BarSeries} does
 * not expose a general closed-bar flag.
 * </p>
 *
 * <p>
 * Sources are not copied. Exact-bar and as-of lookups read the current retained
 * bars, so retention, replacement, clearing, and changes to end times can alter
 * the answer even for an existing portfolio index. Reordered replacement bars
 * are supported; as-of ties select the greatest source index. Lookups scan the
 * retained source history rather than relying on end times remaining ordered
 * after a replacement. Retained bar values, including corrections to an
 * existing bar, are visible through the returned source series. Mutating a bar
 * directly has the concurrency limits documented by its owning series. For
 * concurrent updates use series with a coherent
 * {@link BarSeries#withReadLock(java.util.function.Supplier)} scope, such as
 * {@link org.ta4j.core.ConcurrentBarSeries}; ordinary single-threaded series
 * require external synchronization.
 * </p>
 *
 * <p>
 * Each view call observes one published membership/timeline state and reads at
 * most one source at a time under its read scope. No source lock is held while
 * evaluating an indicator or strategy, and multiple sources are not locked
 * together. Membership, source bounds and bar contents can change between
 * calls; callers needing a decision and its subsequent source access to be
 * atomic must coordinate those changes externally.
 * </p>
 *
 * @since 0.27.1
 */
public final class LivePortfolioSeries implements PortfolioSeriesView {

    private record State(Map<String, BarSeries> sources, List<String> assets, List<Instant> endTimes) {
    }

    private final NumFactory numFactory;
    private final AtomicReference<State> state;

    /**
     * Creates a live view using the map's encounter order. At least one asset is
     * required to establish the portfolio numeric factory, although individual
     * sources may initially be empty. Factories must match: decimal factories must
     * have equal math contexts, while other factories must be equal according to
     * {@link Object#equals}.
     *
     * @param seriesByAsset non-empty map of non-blank asset names to caller-owned
     *                      source series
     * @throws IllegalArgumentException if the map is empty, an asset name is blank,
     *                                  or the numeric factories do not match
     * @since 0.27.1
     */
    public LivePortfolioSeries(Map<String, BarSeries> seriesByAsset) {
        Objects.requireNonNull(seriesByAsset, "seriesByAsset");
        if (seriesByAsset.isEmpty()) {
            throw new IllegalArgumentException("portfolio series must contain at least one asset");
        }
        Map<String, BarSeries> sources = new LinkedHashMap<>();
        NumFactory firstFactory = null;
        for (Map.Entry<String, BarSeries> entry : seriesByAsset.entrySet()) {
            String asset = requireAssetName(entry.getKey());
            BarSeries source = Objects.requireNonNull(entry.getValue(), "series must not be null for asset " + asset);
            if (firstFactory == null) {
                firstFactory = Objects.requireNonNull(source.numFactory(), "source numFactory");
            } else {
                PortfolioSeries.requireMatchingFactory(firstFactory, source.numFactory(), asset);
            }
            sources.put(asset, source);
        }
        this.numFactory = firstFactory;
        this.state = new AtomicReference<>(
                new State(Collections.unmodifiableMap(sources), List.copyOf(sources.keySet()), List.of()));
        advance();
    }

    /**
     * Observes all current members and appends their retained end times that are
     * strictly later than the published tail, sorted and deduplicated. Times at or
     * before the tail are never inserted, including late-arriving bars or corrected
     * historical end times. Each source is captured independently; appends
     * occurring after its capture can be observed by a later advance.
     *
     * @return number of newly appended portfolio times
     * @since 0.27.1
     */
    public int advance() {
        while (true) {
            State current = state.get();
            List<Instant> updated = appendEndTimes(current.endTimes(), current.sources().values());
            if (updated == current.endTimes()) {
                if (state.get() == current) {
                    return 0;
                }
                continue;
            }
            State next = new State(current.sources(), current.assets(), updated);
            if (state.compareAndSet(current, next)) {
                return updated.size() - current.endTimes().size();
            }
        }
    }

    /**
     * Adds an asset without copying its source. Its end times later than the
     * current tail are appended immediately, while historical bars can resolve at
     * already published times. Adding an already present name is rejected; a
     * removed name can be added again.
     *
     * @param asset  non-blank asset name
     * @param source caller-owned series with a matching numeric factory
     * @throws IllegalArgumentException if the name is blank or already present, or
     *                                  the numeric factory does not match
     * @since 0.27.1
     */
    public void addAsset(String asset, BarSeries source) {
        requireAssetName(asset);
        Objects.requireNonNull(source, "source");
        PortfolioSeries.requireMatchingFactory(numFactory, source.numFactory(), asset);
        while (true) {
            State current = state.get();
            if (current.sources().containsKey(asset)) {
                throw new IllegalArgumentException("duplicate portfolio asset: " + asset);
            }
            Map<String, BarSeries> sources = new LinkedHashMap<>(current.sources());
            sources.put(asset, source);
            List<Instant> updated = appendEndTimes(current.endTimes(), List.of(source));
            State next = new State(Collections.unmodifiableMap(sources), List.copyOf(sources.keySet()), updated);
            if (state.compareAndSet(current, next)) {
                return;
            }
        }
    }

    /**
     * Removes an asset from the view without modifying its caller-owned series or
     * the portfolio timeline. Re-adding the name is permitted.
     *
     * @param asset asset name
     * @return {@code true} if the asset was present
     * @since 0.27.1
     */
    public boolean removeAsset(String asset) {
        Objects.requireNonNull(asset, "asset");
        while (true) {
            State current = state.get();
            if (!current.sources().containsKey(asset)) {
                return false;
            }
            Map<String, BarSeries> sources = new LinkedHashMap<>(current.sources());
            sources.remove(asset);
            State next = new State(Collections.unmodifiableMap(sources), List.copyOf(sources.keySet()),
                    current.endTimes());
            if (state.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public List<String> assets() {
        return state.get().assets();
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public int getBeginIndex() {
        return 0;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public int getEndIndex() {
        return state.get().endTimes().size() - 1;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public Instant getEndTime(int index) {
        return endTime(state.get(), index);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public boolean hasBar(String asset, int index) {
        State current = state.get();
        Instant time = endTime(current, index);
        BarSeries source = current.sources().get(Objects.requireNonNull(asset, "asset"));
        if (source == null) {
            return false;
        }
        return source.withReadLock(() -> {
            int endIndex = source.getEndIndex();
            for (long sourceIndex = source.getBeginIndex(); sourceIndex >= 0
                    && sourceIndex <= endIndex; sourceIndex++) {
                if (source.getBar((int) sourceIndex).getEndTime().equals(time)) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public int getSourceIndex(String asset, int index) {
        State current = state.get();
        Instant time = endTime(current, index);
        BarSeries source = current.sources().get(Objects.requireNonNull(asset, "asset"));
        if (source == null) {
            return -1;
        }
        return source.withReadLock(() -> {
            Instant latest = null;
            int latestIndex = -1;
            int endIndex = source.getEndIndex();
            for (long sourceIndex = source.getBeginIndex(); sourceIndex >= 0
                    && sourceIndex <= endIndex; sourceIndex++) {
                Instant candidate = source.getBar((int) sourceIndex).getEndTime();
                if (!candidate.isAfter(time) && (latest == null || !candidate.isBefore(latest))) {
                    latest = candidate;
                    latestIndex = (int) sourceIndex;
                }
            }
            return latestIndex;
        });
    }

    /**
     * Returns the caller-owned source directly. Mutations through the result are
     * visible in the view, subject to the source's own concurrency contract.
     *
     * @param asset asset name
     * @return caller-owned source series
     * @throws IllegalArgumentException if the asset is unknown or removed
     * @since 0.27.1
     */
    @Override
    public BarSeries getBarSeries(String asset) {
        BarSeries source = state.get().sources().get(Objects.requireNonNull(asset, "asset"));
        if (source == null) {
            throw new IllegalArgumentException("asset is not in this portfolio series: " + asset);
        }
        return source;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.27.1
     */
    @Override
    public NumFactory numFactory() {
        return numFactory;
    }

    private static String requireAssetName(String asset) {
        Objects.requireNonNull(asset, "asset");
        if (asset.isBlank()) {
            throw new IllegalArgumentException("asset must not be blank");
        }
        return asset;
    }

    private static Instant endTime(State state, int index) {
        if (index < 0 || index >= state.endTimes().size()) {
            throw new IndexOutOfBoundsException(
                    "index must be between 0 and " + (state.endTimes().size() - 1) + " but was " + index);
        }
        return state.endTimes().get(index);
    }

    private static List<Instant> appendEndTimes(List<Instant> current, Iterable<BarSeries> sources) {
        Instant last = current.isEmpty() ? null : current.getLast();
        TreeSet<Instant> additions = new TreeSet<>();
        for (BarSeries source : sources) {
            source.withReadLock(() -> {
                int endIndex = source.getEndIndex();
                for (long index = source.getBeginIndex(); index >= 0 && index <= endIndex; index++) {
                    Instant time = source.getBar((int) index).getEndTime();
                    if (last == null || time.isAfter(last)) {
                        additions.add(time);
                    }
                }
            });
        }
        if (additions.isEmpty()) {
            return current;
        }
        List<Instant> updated = new ArrayList<>(current.size() + additions.size());
        updated.addAll(current);
        updated.addAll(additions);
        return List.copyOf(updated);
    }
}
