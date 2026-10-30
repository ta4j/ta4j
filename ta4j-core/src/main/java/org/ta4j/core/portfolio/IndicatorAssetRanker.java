/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;
import org.ta4j.core.serialization.IndicatorSerialization;

/**
 * Ranks assets by an indicator bound to each source series. Missing bars,
 * unstable values, and null or NaN values are excluded. Equal scores are
 * ordered by ascending asset name, independently of view iteration order.
 * Instances cache per-asset indicators and are not thread-safe.
 *
 * @since 0.27.1
 */
public final class IndicatorAssetRanker implements AssetRanker {
    private final AssetComponents<Indicator<Num>> indicators;
    private final ComponentDescriptor template;
    private final boolean descending;

    /**
     * Creates a ranker from an arbitrary per-series indicator factory. Such a
     * factory is not serializable; use {@link #of(Indicator)} for a
     * descriptor-backed ranker.
     *
     * @param factory creates an indicator for each asset series
     * @since 0.27.1
     */
    public IndicatorAssetRanker(Function<BarSeries, Indicator<Num>> factory) {
        this(factory, true);
    }

    /**
     * Creates a ranker with an explicit score direction.
     *
     * @param factory    creates an indicator for each asset series
     * @param descending true to rank higher values first, false for lower values
     * @since 0.27.1
     */
    public IndicatorAssetRanker(Function<BarSeries, Indicator<Num>> factory, boolean descending) {
        this(factory, null, descending);
    }

    private IndicatorAssetRanker(Function<BarSeries, Indicator<Num>> factory, ComponentDescriptor template,
            boolean descending) {
        this.indicators = new AssetComponents<>(factory);
        this.template = template;
        this.descending = descending;
    }

    /**
     * Creates a ranker that reconstructs the indicator descriptor for every asset,
     * independently of the sample indicator's original series.
     *
     * @param indicator descriptor-supported indicator template
     * @return descriptor-backed ranker
     * @since 0.27.1
     */
    public static IndicatorAssetRanker of(Indicator<Num> indicator) {
        return of(indicator, true);
    }

    /**
     * Creates a descriptor-backed ranker with an explicit score direction.
     *
     * @param indicator  descriptor-supported indicator template
     * @param descending true to rank higher values first, false for lower values
     * @return descriptor-backed ranker
     * @since 0.27.1
     */
    public static IndicatorAssetRanker of(Indicator<Num> indicator, boolean descending) {
        return fromDescriptor(Objects.requireNonNull(indicator, "indicator").toDescriptor(), descending);
    }

    static IndicatorAssetRanker fromDescriptor(ComponentDescriptor descriptor, boolean descending) {
        Objects.requireNonNull(descriptor, "descriptor");
        ComponentDescriptor canonical = ComponentSerialization.parse(ComponentSerialization.toJson(descriptor));
        return new IndicatorAssetRanker(series -> numericIndicator(series, descriptor), canonical, descending);
    }

    @SuppressWarnings("unchecked")
    private static Indicator<Num> numericIndicator(BarSeries series, ComponentDescriptor descriptor) {
        return (Indicator<Num>) IndicatorSerialization.fromDescriptor(series, descriptor);
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public List<String> rank(PortfolioSeriesView view, int index) {
        Objects.requireNonNull(view, "view");
        NumFactory factory = view.numFactory();
        List<ScoredAsset> scores = new ArrayList<>();
        for (String asset : view.assets()) {
            if (!view.hasBar(asset, index)) {
                continue;
            }
            int sourceIndex = view.getSourceIndex(asset, index);
            Indicator<Num> indicator = indicators.get(view, asset);
            if (sourceIndex < indicator.getCountOfUnstableBars()) {
                continue;
            }
            Num score = indicator.getValue(sourceIndex);
            if (Num.isNaNOrNull(score)) {
                continue;
            }
            if (!factory.produces(score)) {
                score = factory.numOf(score.getDelegate());
            }
            scores.add(new ScoredAsset(asset, score));
        }
        scores.sort((left, right) -> {
            int comparison = descending ? right.score().compareTo(left.score()) : left.score().compareTo(right.score());
            return comparison != 0 ? comparison : left.asset().compareTo(right.asset());
        });
        List<String> ranked = new ArrayList<>(scores.size());
        for (ScoredAsset score : scores) {
            ranked.add(score.asset());
        }
        return ranked;
    }

    private record ScoredAsset(String asset, Num score) {
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        if (template == null) {
            throw new UnsupportedOperationException("Cannot serialize an opaque indicator factory");
        }
        return ComponentDescriptor.builder()
                .withType(IndicatorAssetRanker.class.getName())
                .withLabel("IndicatorAssetRanker")
                .withParameters(Map.of("descending", descending))
                .addComponent(template)
                .build();
    }
}
