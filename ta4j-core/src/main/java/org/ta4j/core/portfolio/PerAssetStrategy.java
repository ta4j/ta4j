/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.serialization.ComponentDescriptor;

/**
 * Runs one ordinary strategy per asset, translating portfolio indices to source
 * indices and supplying that asset's trading record. Bind explicitly before
 * requesting metadata, or let a decision bind lazily from its record. All
 * assets must have the same entry type and unstable-bar count. Instances cache
 * per-asset strategies and are not thread-safe.
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
    private ComponentDescriptor template;
    private PortfolioSeriesView view;
    private TradeType startingType;
    private Integer unstableBars;

    private PerAssetStrategy(Function<BarSeries, Strategy> factory, boolean descriptorBacked,
            ComponentDescriptor template, TradeType expectedType, Integer expectedUnstableBars, String strategyJson) {
        this.strategies = new AssetComponents<>(factory);
        this.descriptorBacked = descriptorBacked;
        this.template = template;
        this.expectedType = expectedType;
        this.expectedUnstableBars = expectedUnstableBars;
        this.strategyJson = strategyJson;
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
        return new PerAssetStrategy(factory, false, null, null, null, null);
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
        return ofJson(Objects.requireNonNull(strategy, "strategy").toJson());
    }

    /**
     * Uses {@link Strategy#fromJson(BarSeries, String)} on each asset's series.
     * Both canonical and version-2 strategy JSON are accepted. The original JSON is
     * retained in the portfolio descriptor so that each asset is restored from the
     * same input even when numeric factories differ. The canonical descriptor of
     * the first bound strategy is also exposed as a child.
     *
     * @param strategyJson single-series strategy JSON
     * @return descriptor-backed portfolio strategy
     * @since 0.27.1
     */
    public static PerAssetStrategy ofJson(String strategyJson) {
        Objects.requireNonNull(strategyJson, "strategyJson");
        return new PerAssetStrategy(series -> Strategy.fromJson(series, strategyJson), true, null, null, null,
                strategyJson);
    }

    static PerAssetStrategy fromDescriptor(ComponentDescriptor descriptor, TradeType expectedType,
            Integer expectedUnstableBars, String strategyJson) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(strategyJson, "strategyJson");
        return new PerAssetStrategy(series -> Strategy.fromJson(series, strategyJson), true, descriptor, expectedType,
                expectedUnstableBars, strategyJson);
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
            strategies.bind(newView);
            startingType = null;
            unstableBars = null;
        }
        try {
            if (newView.assets().isEmpty()) {
                throw new IllegalStateException("Cannot determine per-asset strategy metadata from an empty view");
            }
            boolean first = true;
            for (String asset : newView.assets()) {
                Strategy strategy = strategies.get(newView, asset);
                validateMetadata(strategy, asset);
                if (first && descriptorBacked) {
                    template = strategy.toDescriptor();
                }
                first = false;
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

    private void invalidateBinding() {
        view = null;
        startingType = null;
        unstableBars = null;
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public String getName() {
        return "PerAssetStrategy";
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
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public TradeType getStartingType() {
        requireBound();
        return startingType;
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public int getUnstableBars() {
        requireBound();
        return unstableBars;
    }

    private void requireBound() {
        if (view == null || startingType == null || unstableBars == null) {
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
        requireBound();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("unstableBars", unstableBars);
        parameters.put("startingType", startingType.name());
        parameters.put("strategyJson", strategyJson);
        return ComponentDescriptor.builder()
                .withType(PerAssetStrategy.class.getName())
                .withLabel("PerAssetStrategy")
                .withParameters(parameters)
                .addComponent(template)
                .build();
    }
}
