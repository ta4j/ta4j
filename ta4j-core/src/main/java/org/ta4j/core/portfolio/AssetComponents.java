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

    synchronized void bind(PortfolioSeriesView newView) {
        Objects.requireNonNull(newView, "view");
        if (view != newView) {
            entries.clear();
            view = newView;
        } else if (!(newView instanceof PortfolioSeries)) {
            entries.keySet().retainAll(newView.assets());
        }
    }

    synchronized T get(PortfolioSeriesView newView, String asset) {
        bind(newView);
        Entry<T> entry = entries.get(asset);
        // Snapshot views return detached copies on each call; other views may
        // replace a source (e.g. a live asset removed and re-added).
        if (entry != null && newView instanceof PortfolioSeries) {
            return entry.component();
        }
        BarSeries series = newView.getBarSeries(asset);
        Object identity = newView instanceof LivePortfolioSeries live ? live.assetIdentity(asset)
                : newView instanceof PortfolioSeriesManager.ChronologicalView chronological
                        ? chronological.assetIdentity(asset)
                        : series;
        if (entry == null || entry.series() != series || entry.identity() != identity) {
            entry = new Entry<>(series, identity, Objects.requireNonNull(factory.apply(series), "factory result"));
            entries.put(asset, entry);
        }
        return entry.component();
    }

    synchronized int size() {
        return entries.size();
    }

    private record Entry<T>(BarSeries series, Object identity, T component) {
    }
}
