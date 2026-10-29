/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.time.Instant;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.NumFactory;

/**
 * An end-time-aligned view of named bar series for portfolio strategies.
 *
 * <p>
 * {@link #hasBar(String, int)} tests for a bar ending exactly at the portfolio
 * time, whereas {@link #getSourceIndex(String, int)} is an as-of lookup and can
 * return an earlier bar across a gap. Neither operation forward-fills a bar. In
 * particular, an as-of index does not mean that an asset can be traded at the
 * aligned time.
 * </p>
 *
 * <p>
 * Implementations define whether their sources are detached snapshots or
 * caller-owned live series. A live view can change between individual calls;
 * callers needing a coherent sequence of decisions must coordinate source and
 * membership changes outside those calls. No view operation evaluates an
 * indicator or strategy while a source read lock is held.
 * </p>
 *
 * @since 0.27.1
 */
public interface PortfolioSeriesView {

    /**
     * Returns an immutable snapshot of the currently available asset names, in
     * portfolio order.
     *
     * @return asset names
     * @since 0.27.1
     */
    List<String> assets();

    /**
     * Returns the first portfolio index. An empty timeline has begin index zero and
     * end index minus one.
     *
     * @return first portfolio index
     * @since 0.27.1
     */
    int getBeginIndex();

    /**
     * @return last portfolio index, or {@code -1} for an empty timeline
     * @since 0.27.1
     */
    int getEndIndex();

    /**
     * Returns the end time at an aligned portfolio index.
     *
     * @param index portfolio index
     * @return the aligned end time
     * @throws IndexOutOfBoundsException if the index is outside the timeline
     * @since 0.27.1
     */
    Instant getEndTime(int index);

    /**
     * Tests whether a currently available asset has a retained bar ending exactly
     * at the aligned time. Unknown and removed assets are absent.
     *
     * @param asset asset name
     * @param index portfolio index
     * @return whether an exact bar is available
     * @throws IndexOutOfBoundsException if the index is outside the timeline
     * @since 0.27.1
     */
    boolean hasBar(String asset, int index);

    /**
     * Returns the source index of the retained bar with the greatest end time at or
     * before the aligned time. Ties are resolved by the greatest source index.
     * Returns {@code -1} before the first retained bar and for a removed or unknown
     * asset, except that legacy intersection snapshots reject unknown assets with
     * {@link IllegalArgumentException}. A returned index can become invalid after
     * this method returns when a live source removes or replaces bars; callers must
     * coordinate subsequent source access when necessary.
     *
     * @param asset asset name
     * @param index portfolio index
     * @return as-of source index, or {@code -1} when no retained bar is available
     * @throws IndexOutOfBoundsException if the index is outside the timeline
     * @since 0.27.1
     */
    int getSourceIndex(String asset, int index);

    /**
     * Returns an asset's source series. Snapshot views may return a detached copy;
     * live views may return the caller-owned series. Unknown and removed assets are
     * rejected.
     *
     * @param asset asset name
     * @return the asset's source series
     * @throws IllegalArgumentException if the asset is not in the view
     * @since 0.27.1
     */
    BarSeries getBarSeries(String asset);

    /**
     * @return the numeric factory for portfolio-level values
     * @since 0.27.1
     */
    NumFactory numFactory();
}
