/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.RuleSerialization;

/**
 * Adapts a single-series rule to each asset. It evaluates the rule at that
 * asset's source index and supplies its own trading record. Missing bars never
 * satisfy the rule. Instances cache per-asset rules and are not thread-safe.
 *
 * @since 0.27.1
 */
public final class AssetRule implements PortfolioRule {
    private final AssetComponents<Rule> rules;
    private final ComponentDescriptor template;

    /**
     * Creates a rule using an arbitrary per-series factory. Opaque factories are
     * evaluable but cannot be serialized; use {@link #of(Rule)} to construct a
     * descriptor-backed rule.
     *
     * @param factory creates a rule for each source series
     * @since 0.27.1
     */
    public AssetRule(Function<BarSeries, Rule> factory) {
        this(factory, null);
    }

    private AssetRule(Function<BarSeries, Rule> factory, ComponentDescriptor template) {
        this.rules = new AssetComponents<>(factory);
        this.template = template;
    }

    /**
     * Constructs a rule from a serializable single-series rule template. The
     * descriptor is reconstructed independently for every asset.
     *
     * @param rule single-series rule template
     * @return descriptor-backed asset rule
     * @since 0.27.1
     */
    public static AssetRule of(Rule rule) {
        return fromDescriptor(Objects.requireNonNull(rule, "rule").toDescriptor());
    }

    static AssetRule fromDescriptor(ComponentDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return new AssetRule(series -> RuleSerialization.fromDescriptor(series, descriptor), descriptor);
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public boolean isSatisfied(String asset, int index, PortfolioTradingRecord record) {
        PortfolioSeriesView view = Objects.requireNonNull(record, "record").series();
        if (!view.hasBar(asset, index)) {
            return false;
        }
        return rules.get(view, asset).isSatisfied(view.getSourceIndex(asset, index), record.record(asset));
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        if (template == null) {
            throw new UnsupportedOperationException("Cannot serialize an opaque asset rule factory");
        }
        return ComponentDescriptor.builder()
                .withType(AssetRule.class.getName())
                .withLabel("AssetRule")
                .addComponent(template)
                .build();
    }
}
