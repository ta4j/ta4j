/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.BarSeries;

/** Caches series-bound components, invalidating replaced live asset series. */
final class AssetComponents<T> {
    private final Function<BarSeries, T> factory;
    private final Map<String, Entry<T>> entries = new HashMap<>();
    private PortfolioSeriesView view;

    AssetComponents(Function<BarSeries, T> factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    void bind(PortfolioSeriesView newView) {
        Objects.requireNonNull(newView, "view");
        if (view != newView) {
            entries.clear();
            view = newView;
        }
    }

    T get(PortfolioSeriesView newView, String asset) {
        bind(newView);
        Entry<T> entry = entries.get(asset);
        // Snapshot views return detached copies on each call; other views may
        // replace a source (e.g. a live asset removed and re-added).
        if (entry != null && newView instanceof PortfolioSeries) {
            return entry.component();
        }
        BarSeries series = newView.getBarSeries(asset);
        if (entry == null || entry.series() != series) {
            entry = new Entry<>(series, Objects.requireNonNull(factory.apply(series), "factory result"));
            entries.put(asset, entry);
        }
        return entry.component();
    }

    private record Entry<T>(BarSeries series, T component) {
    }
}
