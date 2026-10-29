/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.Objects;

import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

/**
 * A decision about an asset at an index on a portfolio timeline. The record
 * supplies both the aligned view and the portfolio's trading state.
 *
 * @since 0.27.1
 */
@FunctionalInterface
public interface PortfolioRule {

    /**
     * Evaluates this rule.
     *
     * @param asset  asset name
     * @param index  portfolio timeline index
     * @param record portfolio trading state and series view
     * @return whether the rule is satisfied
     * @since 0.27.1
     */
    boolean isSatisfied(String asset, int index, PortfolioTradingRecord record);

    /**
     * Returns a short-circuiting conjunction.
     *
     * @param other right-hand rule
     * @return conjunction of this rule and {@code other}
     * @since 0.27.1
     */
    default PortfolioRule and(PortfolioRule other) {
        return new CompositePortfolioRule("and", this, Objects.requireNonNull(other, "other"));
    }

    /**
     * Returns a short-circuiting disjunction.
     *
     * @param other right-hand rule
     * @return disjunction of this rule and {@code other}
     * @since 0.27.1
     */
    default PortfolioRule or(PortfolioRule other) {
        return new CompositePortfolioRule("or", this, Objects.requireNonNull(other, "other"));
    }

    /**
     * Returns the logical inverse of this rule.
     *
     * @return negated rule
     * @since 0.27.1
     */
    default PortfolioRule negation() {
        return new CompositePortfolioRule("not", this, null);
    }

    /**
     * Describes a reconstructible rule. Opaque custom rules and factories cannot be
     * represented automatically.
     *
     * @return rule descriptor
     * @throws UnsupportedOperationException if the rule has no descriptor
     * @since 0.27.1
     */
    default ComponentDescriptor toDescriptor() {
        throw new UnsupportedOperationException("Portfolio rule is not descriptor-backed: " + getClass().getName());
    }

    /**
     * Serializes a reconstructible rule.
     *
     * @return component JSON
     * @throws UnsupportedOperationException if any component is opaque
     * @since 0.27.1
     */
    default String toJson() {
        return ComponentSerialization.toJson(toDescriptor());
    }

    /**
     * Reconstructs a portfolio rule against a view.
     *
     * @param view series view
     * @param json component JSON
     * @return reconstructed rule
     * @since 0.27.1
     */
    static PortfolioRule fromJson(PortfolioSeriesView view, String json) {
        Objects.requireNonNull(view, "view");
        return PortfolioSerialization.rule(view, ComponentSerialization.parse(Objects.requireNonNull(json, "json")));
    }
}

final class CompositePortfolioRule implements PortfolioRule {
    private final String operator;
    private final PortfolioRule left;
    private final PortfolioRule right;

    CompositePortfolioRule(String operator, PortfolioRule left, PortfolioRule right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    /** @since 0.27.1 */
    @Override
    public boolean isSatisfied(String asset, int index, PortfolioTradingRecord record) {
        return switch (operator) {
        case "and" -> left.isSatisfied(asset, index, record) && right.isSatisfied(asset, index, record);
        case "or" -> left.isSatisfied(asset, index, record) || right.isSatisfied(asset, index, record);
        case "not" -> !left.isSatisfied(asset, index, record);
        default -> throw new IllegalStateException("Unknown portfolio rule operator: " + operator);
        };
    }

    /** @since 0.27.1 */
    @Override
    public ComponentDescriptor toDescriptor() {
        ComponentDescriptor.Builder builder = ComponentDescriptor.builder()
                .withType(CompositePortfolioRule.class.getName())
                .withLabel("CompositePortfolioRule")
                .withParameters(java.util.Map.of("operator", operator))
                .addComponent(left.toDescriptor());
        if (right != null) {
            builder.addComponent(right.toDescriptor());
        }
        return builder.build();
    }
}
