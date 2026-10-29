/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.IndicatorSerialization;
import org.ta4j.core.serialization.RuleSerialization;

/** Reconstructs the explicitly supported portfolio descriptor vocabulary. */
final class PortfolioSerialization {
    private PortfolioSerialization() {
    }

    static PortfolioStrategy strategy(PortfolioSeriesView view, ComponentDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        String type = descriptor.getType();
        if (BasePortfolioStrategy.class.getName().equals(type)) {
            requireShape(descriptor, 2, Set.of("unstableBars", "startingType"));
            return new BasePortfolioStrategy(descriptor.getLabel(), rule(view, descriptor.getComponents().get(0)),
                    rule(view, descriptor.getComponents().get(1)), integer(descriptor, "unstableBars"),
                    tradeType(descriptor));
        }
        if (PerAssetStrategy.class.getName().equals(type)) {
            requireShape(descriptor, 1, Set.of("strategyJson"));
            return PerAssetStrategy.fromDescriptor(descriptor.getComponents().get(0), text(descriptor, "strategyJson"));
        }
        throw new IllegalArgumentException("Unknown portfolio strategy type: " + type);
    }

    static PortfolioRule rule(PortfolioSeriesView view, ComponentDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        String type = descriptor.getType();
        if (CompositePortfolioRule.class.getName().equals(type)) {
            String operator = text(descriptor, "operator");
            int count = "not".equals(operator) ? 1 : 2;
            requireShape(descriptor, count, Set.of("operator"));
            PortfolioRule left = rule(view, descriptor.getComponents().get(0));
            return switch (operator) {
            case "and" -> left.and(rule(view, descriptor.getComponents().get(1)));
            case "or" -> left.or(rule(view, descriptor.getComponents().get(1)));
            case "not" -> left.negation();
            default -> throw new IllegalArgumentException("Unknown portfolio rule operator: " + operator);
            };
        }
        if (MaxOpenPositionsRule.class.getName().equals(type)) {
            requireShape(descriptor, 0, Set.of("maximum"));
            return new MaxOpenPositionsRule(integer(descriptor, "maximum"));
        }
        if (EndTimeRule.class.getName().equals(type)) {
            requireShape(descriptor, 0, Set.of("endTime"));
            return new EndTimeRule(Instant.parse(text(descriptor, "endTime")));
        }
        if (AssetRule.class.getName().equals(type)) {
            requireShape(descriptor, 1, Set.of());
            ComponentDescriptor child = descriptor.getComponents().get(0);
            if (!view.assets().isEmpty()) {
                BarSeries series = view.getBarSeries(view.assets().get(0));
                // Validate against a series without replacing the precision-bearing template.
                RuleSerialization.fromDescriptor(series, child);
            }
            return AssetRule.fromDescriptor(child);
        }
        if (AssetRankRule.class.getName().equals(type)) {
            requireShape(descriptor, 1, Set.of("topCount"));
            return new AssetRankRule(ranker(view, descriptor.getComponents().get(0)), integer(descriptor, "topCount"));
        }
        throw new IllegalArgumentException("Unknown portfolio rule type: " + type);
    }

    private static AssetRanker ranker(PortfolioSeriesView view, ComponentDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (IndicatorAssetRanker.class.getName().equals(descriptor.getType())) {
            requireShape(descriptor, 1, Set.of("descending"));
            Object direction = descriptor.getParameters().get("descending");
            if (!(direction instanceof Boolean)) {
                throw new IllegalArgumentException("Expected boolean parameter: descending");
            }
            ComponentDescriptor child = descriptor.getComponents().get(0);
            if (!view.assets().isEmpty()) {
                BarSeries series = view.getBarSeries(view.assets().get(0));
                // Each asset must reconstruct the original template with its own factory.
                IndicatorSerialization.fromDescriptor(series, child);
            }
            return IndicatorAssetRanker.fromDescriptor(child, (Boolean) direction);
        }
        throw new IllegalArgumentException("Unknown asset ranker type: " + descriptor.getType());
    }

    private static void requireShape(ComponentDescriptor descriptor, int componentCount, Set<String> parameters) {
        List<ComponentDescriptor> components = descriptor.getComponents();
        if (components.size() != componentCount || components.contains(null)
                || !descriptor.getParameters().keySet().equals(parameters)) {
            throw new IllegalArgumentException("Invalid descriptor for " + descriptor.getType());
        }
    }

    private static String text(ComponentDescriptor descriptor, String key) {
        Object value = descriptor.getParameters().get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException("Expected string parameter: " + key);
        }
        return string;
    }

    private static int integer(ComponentDescriptor descriptor, String key) {
        Object value = descriptor.getParameters().get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue()) || number.doubleValue() < 0
                || number.doubleValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Expected non-negative integer parameter: " + key);
        }
        return number.intValue();
    }

    private static TradeType tradeType(ComponentDescriptor descriptor) {
        return TradeType.valueOf(text(descriptor, "startingType"));
    }
}
