/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.Objects;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

/**
 * An asset-scoped strategy evaluated on a shared portfolio timeline and trading
 * record. Indices supplied to decision methods are portfolio indices, not
 * source-series indices.
 *
 * @since 0.27.1
 */
public interface PortfolioStrategy {

    /**
     * @return the strategy name
     * @since 0.27.1
     */
    String getName();

    /**
     * @return the asset-scoped entry rule
     * @since 0.27.1
     */
    PortfolioRule getEntryRule();

    /**
     * @return the asset-scoped exit rule
     * @since 0.27.1
     */
    PortfolioRule getExitRule();

    /**
     * Binds the view before metadata is requested. Stateless strategies may use
     * this default no-op. Strategies may also bind lazily from a record when
     * evaluated directly.
     *
     * @param view portfolio series view
     * @since 0.27.1
     */
    default void bind(PortfolioSeriesView view) {
        Objects.requireNonNull(view, "view");
    }

    /**
     * Returns the common entry direction for this portfolio strategy.
     *
     * @return starting trade type
     * @since 0.27.1
     */
    default TradeType getStartingType() {
        return TradeType.BUY;
    }

    /**
     * Returns the number of initial source-series bars ignored by the strategy.
     * This metadata is not a portfolio-timeline offset.
     *
     * @return number of unstable bars
     * @since 0.27.1
     */
    default int getUnstableBars() {
        return 0;
    }

    /**
     * Evaluates an entry decision.
     *
     * @param asset  asset name
     * @param index  portfolio timeline index
     * @param record portfolio trading state and series view
     * @return true when entry is recommended
     * @since 0.27.1
     */
    default boolean shouldEnter(String asset, int index, PortfolioTradingRecord record) {
        PortfolioSeriesView view = Objects.requireNonNull(record, "record").series();
        return view.hasBar(asset, index) && view.getSourceIndex(asset, index) >= getUnstableBars()
                && getEntryRule().isSatisfied(asset, index, record);
    }

    /**
     * Evaluates an exit decision.
     *
     * @param asset  asset name
     * @param index  portfolio timeline index
     * @param record portfolio trading state and series view
     * @return true when exit is recommended
     * @since 0.27.1
     */
    default boolean shouldExit(String asset, int index, PortfolioTradingRecord record) {
        PortfolioSeriesView view = Objects.requireNonNull(record, "record").series();
        return view.hasBar(asset, index) && view.getSourceIndex(asset, index) >= getUnstableBars()
                && getExitRule().isSatisfied(asset, index, record);
    }

    /**
     * Returns the structured description of a reconstructible strategy.
     *
     * @return strategy descriptor
     * @throws UnsupportedOperationException if the strategy contains opaque
     *                                       components
     * @since 0.27.1
     */
    default ComponentDescriptor toDescriptor() {
        throw new UnsupportedOperationException("Portfolio strategy is not descriptor-backed: " + getClass().getName());
    }

    /**
     * Serializes this strategy using component descriptors.
     *
     * @return strategy JSON
     * @throws UnsupportedOperationException if the strategy contains opaque
     *                                       components
     * @since 0.27.1
     */
    default String toJson() {
        return ComponentSerialization.toJson(toDescriptor());
    }

    /**
     * Reconstructs and binds a portfolio strategy.
     *
     * @param view portfolio series view
     * @param json strategy component JSON
     * @return reconstructed, bound strategy
     * @since 0.27.1
     */
    static PortfolioStrategy fromJson(PortfolioSeriesView view, String json) {
        Objects.requireNonNull(view, "view");
        ComponentDescriptor descriptor = ComponentSerialization.parse(Objects.requireNonNull(json, "json"));
        PortfolioStrategy strategy = PortfolioSerialization.strategy(view, descriptor);
        strategy.bind(view);
        return strategy;
    }
}
