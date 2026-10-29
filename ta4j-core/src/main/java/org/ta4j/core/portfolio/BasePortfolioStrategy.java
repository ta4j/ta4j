/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.serialization.ComponentDescriptor;

/**
 * Portfolio strategy defined by entry and exit rules. Unstable bars are checked
 * against each asset's source index, not the aligned timeline index.
 *
 * @since 0.27.1
 */
public final class BasePortfolioStrategy implements PortfolioStrategy {
    private final String name;
    private final PortfolioRule entryRule;
    private final PortfolioRule exitRule;
    private final int unstableBars;
    private final TradeType startingType;

    /**
     * Creates a long strategy with no unstable bars.
     *
     * @param entryRule entry condition
     * @param exitRule  exit condition
     * @since 0.27.1
     */
    public BasePortfolioStrategy(PortfolioRule entryRule, PortfolioRule exitRule) {
        this("BasePortfolioStrategy", entryRule, exitRule, 0, TradeType.BUY);
    }

    /**
     * Creates a long strategy with the specified unstable-bar count.
     *
     * @param entryRule    entry condition
     * @param exitRule     exit condition
     * @param unstableBars initial source bars to ignore
     * @since 0.27.1
     */
    public BasePortfolioStrategy(PortfolioRule entryRule, PortfolioRule exitRule, int unstableBars) {
        this("BasePortfolioStrategy", entryRule, exitRule, unstableBars, TradeType.BUY);
    }

    /**
     * Creates a strategy with explicit unstable-bar count and entry direction.
     *
     * @param entryRule    entry condition
     * @param exitRule     exit condition
     * @param unstableBars initial source bars to ignore
     * @param startingType entry trade type
     * @since 0.27.1
     */
    public BasePortfolioStrategy(PortfolioRule entryRule, PortfolioRule exitRule, int unstableBars,
            TradeType startingType) {
        this("BasePortfolioStrategy", entryRule, exitRule, unstableBars, startingType);
    }

    /**
     * Creates a named long strategy with no unstable bars.
     *
     * @param name      strategy name
     * @param entryRule entry condition
     * @param exitRule  exit condition
     * @since 0.27.1
     */
    public BasePortfolioStrategy(String name, PortfolioRule entryRule, PortfolioRule exitRule) {
        this(name, entryRule, exitRule, 0, TradeType.BUY);
    }

    /**
     * Creates a named strategy.
     *
     * @param name         strategy name
     * @param entryRule    entry condition
     * @param exitRule     exit condition
     * @param unstableBars initial source bars to ignore
     * @param startingType entry trade type
     * @throws IllegalArgumentException if unstable bars are negative or name is
     *                                  blank
     * @since 0.27.1
     */
    public BasePortfolioStrategy(String name, PortfolioRule entryRule, PortfolioRule exitRule, int unstableBars,
            TradeType startingType) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (unstableBars < 0) {
            throw new IllegalArgumentException("unstableBars must not be negative");
        }
        this.entryRule = Objects.requireNonNull(entryRule, "entryRule");
        this.exitRule = Objects.requireNonNull(exitRule, "exitRule");
        this.unstableBars = unstableBars;
        this.startingType = Objects.requireNonNull(startingType, "startingType");
    }

    /**
     * @return the name of this strategy
     * @since 0.27.1
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * @return the entry condition
     * @since 0.27.1
     */
    @Override
    public PortfolioRule getEntryRule() {
        return entryRule;
    }

    /**
     * @return the exit condition
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
        return startingType;
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public int getUnstableBars() {
        return unstableBars;
    }

    /**
     * {@inheritDoc}
     * 
     * @since 0.27.1
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("unstableBars", unstableBars);
        parameters.put("startingType", startingType.name());
        return ComponentDescriptor.builder()
                .withType(BasePortfolioStrategy.class.getName())
                .withLabel(name)
                .withParameters(parameters)
                .addComponent(entryRule.toDescriptor())
                .addComponent(exitRule.toDescriptor())
                .build();
    }
}
