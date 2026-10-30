/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Runs one ordinary strategy per asset, translating portfolio indices to source
 * indices and supplying that asset's trading record. Descriptor-backed
 * strategies can be serialized before binding a view. Metadata from a sample
 * strategy is available immediately; a JSON or arbitrary factory must be bound
 * before requesting its starting type or unstable bars. All assets must have
 * the same entry type and unstable-bar count. Instances cache per-asset
 * strategies and are not thread-safe. On subsequent cache access or binding,
 * components for assets no longer in the view are discarded. An asset re-added
 * after eviction receives a newly constructed strategy, even if it uses the
 * same series; re-addition before eviction retains it only if the series is
 * unchanged.
 *
 * <p>
 * Arbitrary factories can depend on the asset series in ways a descriptor
 * cannot capture. Use {@link #ofJson(String)} or {@link #of(Strategy)} for a
 * reconstructible, descriptor-defined factory; {@link #of(Function)} does not
 * pretend to serialize arbitrary code.
 * </p>
 *
 * @since 0.27.1
 */
public final class PerAssetStrategy implements PortfolioStrategy {
    private final AssetComponents<Strategy> strategies;
    private final PortfolioRule entryRule = (asset, index, record) -> ruleSatisfied(asset, index, record, true);
    private final PortfolioRule exitRule = (asset, index, record) -> ruleSatisfied(asset, index, record, false);
    private final boolean descriptorBacked;
    private final TradeType expectedType;
    private final String strategyJson;
    private final Integer expectedUnstableBars;
    private final ComponentDescriptor template;
    private final String name;
    private PortfolioSeriesView view;
    private TradeType startingType;
    private Integer unstableBars;

    private PerAssetStrategy(Function<BarSeries, Strategy> factory, ComponentDescriptor template,
            TradeType expectedType, Integer expectedUnstableBars, String strategyJson, String name) {
        this.strategies = new AssetComponents<>(factory);
        this.descriptorBacked = strategyJson != null;
        this.template = template;
        this.expectedType = expectedType;
        this.expectedUnstableBars = expectedUnstableBars;
        this.strategyJson = strategyJson;
        this.name = name;
        this.startingType = expectedType;
        this.unstableBars = expectedUnstableBars;
    }

    /**
     * Adapts an arbitrary strategy factory. Its behavior is not inferable from
     * samples and attempts to serialize it fail explicitly.
     *
     * @param factory strategy factory invoked with each asset's source series
     * @return portfolio strategy
     * @since 0.27.1
     */
    public static PerAssetStrategy of(Function<BarSeries, Strategy> factory) {
        return new PerAssetStrategy(factory, null, null, null, null, "PerAssetStrategy");
    }

    /**
     * Uses a strategy's descriptor as the template for independently reconstructing
     * each asset's strategy. This intentionally uses descriptor semantics, rather
     * than retaining the sample strategy or its series.
     *
     * @param strategy serializable single-series strategy template
     * @return descriptor-backed portfolio strategy
     * @since 0.27.1
     */
    public static PerAssetStrategy of(Strategy strategy) {
        Strategy sample = Objects.requireNonNull(strategy, "strategy");
        String json = sample.toJson();
        return new PerAssetStrategy(series -> Strategy.fromJson(series, json), describeJson(json),
                sample.getStartingType(), sample.getUnstableBars(), json, sample.getName());
    }

    /**
     * Uses {@link Strategy#fromJson(BarSeries, String)} on each asset's series.
     * Both canonical and version-2 strategy JSON are accepted. The original JSON is
     * retained in the portfolio descriptor so that each asset is restored from the
     * same input even when numeric factories differ. The descriptor is available
     * before binding; starting type and unstable-bar metadata require binding.
     *
     * @param strategyJson single-series strategy JSON
     * @return descriptor-backed portfolio strategy
     * @since 0.27.1
     */
    public static PerAssetStrategy ofJson(String strategyJson) {
        Objects.requireNonNull(strategyJson, "strategyJson");
        ComponentDescriptor template = describeJson(strategyJson);
        return new PerAssetStrategy(series -> Strategy.fromJson(series, strategyJson), template, null, null,
                strategyJson, template.getLabel());
    }

    static PerAssetStrategy fromDescriptor(ComponentDescriptor descriptor, String strategyJson) {
        Objects.requireNonNull(descriptor, "descriptor");
        PerAssetStrategy strategy = ofJson(strategyJson);
        if (!strategy.template.equals(descriptor)) {
            throw new IllegalArgumentException("Per-asset strategy descriptor does not match its JSON");
        }
        return strategy;
    }

    private static ComponentDescriptor describeJson(String strategyJson) {
        if (strategyJson.contains("\"version\"")) {
            JsonElement json = JsonParser.parseString(strategyJson);
            if (json.isJsonObject()) {
                JsonObject object = json.getAsJsonObject();
                if (object.has("version") && (object.has("strategy") || object.has("name") || object.has("entryRule")
                        || object.has("exitRule"))) {
                    JsonElement name = object.get("name");
                    String label = name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()
                            ? name.getAsString()
                            : null;
                    return ComponentDescriptor.builder()
                            .withType("StrategyJson")
                            .withLabel(label)
                            .withParameters(Map.of("json", strategyJson))
                            .build();
                }
            }
        }
        ComponentDescriptor descriptor = ComponentSerialization.parse(strategyJson);
        if (descriptor == null) {
            throw new IllegalArgumentException("Missing strategy JSON descriptor");
        }
        return descriptor;
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public void bind(PortfolioSeriesView newView) {
        Objects.requireNonNull(newView, "view");
        if (view != newView) {
            view = newView;
            startingType = expectedType;
            unstableBars = expectedUnstableBars;
        }
        strategies.bind(newView);
        try {
            if (newView.assets().isEmpty()) {
                throw new IllegalStateException("Cannot determine per-asset strategy metadata from an empty view");
            }
            for (String asset : newView.assets()) {
                Strategy strategy = strategies.get(newView, asset);
                validateMetadata(strategy, asset);
            }
        } catch (RuntimeException ex) {
            invalidateBinding();
            throw ex;
        }
    }

    private void validateMetadata(Strategy strategy, String asset) {
        TradeType type = Objects.requireNonNull(strategy.getStartingType(), "strategy startingType");
        int unstable = strategy.getUnstableBars();
        if (unstable < 0) {
            invalidateBinding();
            throw new IllegalArgumentException("Negative unstable bars for asset " + asset);
        }
        if (startingType == null) {
            startingType = type;
            unstableBars = unstable;
        } else if (startingType != type || unstableBars != unstable) {
            invalidateBinding();
            throw new IllegalArgumentException("Inconsistent per-asset strategy metadata for asset " + asset);
        }
        if (expectedType != null && (expectedType != type || expectedUnstableBars.intValue() != unstable)) {
            invalidateBinding();
            throw new IllegalArgumentException("Serialized per-asset strategy metadata does not match asset " + asset);
        }
    }

    int cachedStrategyCount() {
        return strategies.size();
    }

    private void invalidateBinding() {
        view = null;
        startingType = expectedType;
        unstableBars = expectedUnstableBars;
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public PortfolioRule getEntryRule() {
        return entryRule;
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public PortfolioRule getExitRule() {
        return exitRule;
    }

    /**
     * Returns the sample strategy's starting type, or the common type discovered
     * when a JSON or arbitrary factory is bound.
     *
     * @return starting trade type
     * @throws IllegalStateException if factory metadata is requested before binding
     * @since 0.27.1
     */
    @Override
    public TradeType getStartingType() {
        requireMetadata();
        return startingType;
    }

    /**
     * Returns the sample strategy's unstable-bar count, or the common count
     * discovered when a JSON or arbitrary factory is bound.
     *
     * @return number of unstable bars
     * @throws IllegalStateException if factory metadata is requested before binding
     * @since 0.27.1
     */
    @Override
    public int getUnstableBars() {
        requireMetadata();
        return unstableBars;
    }

    private void requireMetadata() {
        if (startingType == null || unstableBars == null) {
            throw new IllegalStateException("Bind the portfolio view before requesting strategy metadata");
        }
    }

    private Strategy strategy(String asset, PortfolioTradingRecord record) {
        PortfolioSeriesView recordView = Objects.requireNonNull(record, "record").series();
        if (view != recordView) {
            bind(recordView);
        }
        Strategy strategy = strategies.get(recordView, asset);
        validateMetadata(strategy, asset);
        return strategy;
    }

    private boolean ruleSatisfied(String asset, int index, PortfolioTradingRecord record, boolean entry) {
        PortfolioSeriesView recordView = Objects.requireNonNull(record, "record").series();
        if (!recordView.hasBar(asset, index)) {
            return false;
        }
        Strategy inner = strategy(asset, record);
        int sourceIndex = recordView.getSourceIndex(asset, index);
        return (entry ? inner.getEntryRule() : inner.getExitRule()).isSatisfied(sourceIndex, record.record(asset));
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public boolean shouldEnter(String asset, int index, PortfolioTradingRecord record) {
        PortfolioSeriesView recordView = Objects.requireNonNull(record, "record").series();
        if (view != recordView) {
            bind(recordView);
        }
        return recordView.hasBar(asset, index)
                && strategy(asset, record).shouldEnter(recordView.getSourceIndex(asset, index), record.record(asset));
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public boolean shouldExit(String asset, int index, PortfolioTradingRecord record) {
        PortfolioSeriesView recordView = Objects.requireNonNull(record, "record").series();
        if (view != recordView) {
            bind(recordView);
        }
        return recordView.hasBar(asset, index)
                && strategy(asset, record).shouldExit(recordView.getSourceIndex(asset, index), record.record(asset));
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        if (!descriptorBacked) {
            throw new UnsupportedOperationException("Cannot serialize an opaque per-asset strategy factory");
        }
        return ComponentDescriptor.builder()
                .withType(PerAssetStrategy.class.getName())
                .withLabel(template.getLabel())
                .withParameters(Map.of("strategyJson", strategyJson))
                .addComponent(template)
                .build();
    }
}
