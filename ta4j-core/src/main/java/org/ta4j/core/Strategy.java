/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.StrategySerialization;

/**
 * A {@code Strategy} (also called "trading strategy") is a pair of
 * complementary (entry and exit) {@link Rule rules}. It may recommend to enter
 * or to exit. Recommendations are based respectively on the entry rule or on
 * the exit rule.
 */
public interface Strategy {

    /**
     * @return the name of the strategy
     */
    String getName();

    /**
     * @return the entry rule
     */
    Rule getEntryRule();

    /**
     * Returns the starting trade type for this strategy.
     *
     * <p>
     * Defaults to {@link TradeType#BUY} (long-only). Override when the strategy is
     * meant to run in short-only mode.
     * </p>
     *
     * @return starting trade type
     * @since 0.22.2
     */
    default TradeType getStartingType() {
        return TradeType.BUY;
    }

    /**
     * @return the exit rule
     */
    Rule getExitRule();

    /**
     * @param strategy the other strategy
     * @return the AND combination of two {@link Strategy strategies}
     */
    Strategy and(Strategy strategy);

    /**
     * @param strategy the other strategy
     * @return the OR combination of two {@link Strategy strategies}
     */
    Strategy or(Strategy strategy);

    /**
     * @param name         the name of the strategy
     * @param strategy     the other strategy
     * @param unstableBars the number of first bars in a bar series that this
     *                     strategy ignores
     * @return the AND combination of two {@link Strategy strategies}
     */
    Strategy and(String name, Strategy strategy, int unstableBars);

    /**
     * @param name         the name of the strategy
     * @param strategy     the other strategy
     * @param unstableBars the number of first bars in a bar series that this
     *                     strategy ignores
     * @return the OR combination of two {@link Strategy strategies}
     */
    Strategy or(String name, Strategy strategy, int unstableBars);

    /**
     * @return the opposite of the {@link Strategy strategy}
     */
    Strategy opposite();

    /**
     * @param unstableBars the number of first bars in a bar series that this
     *                     strategy ignores
     */
    void setUnstableBars(int unstableBars);

    /**
     * @return unstableBars the number of first bars in a bar series that this
     *         strategy ignores
     */
    int getUnstableBars();

    /**
     * @param index a bar index
     * @return true if this strategy is unstable at the provided index, false
     *         otherwise (stable)
     */
    boolean isUnstableAt(int index);

    /**
     * @param index         the bar index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend a trade, false otherwise (no recommendation)
     */
    default boolean shouldOperate(int index, TradingRecord tradingRecord) {
        Position position = tradingRecord.getCurrentPosition();
        if (position.isNew()) {
            return shouldEnter(index, tradingRecord);
        } else if (position.isOpened()) {
            return shouldExit(index, tradingRecord);
        }
        return false;
    }

    /**
     * @param index the bar index
     * @return true to recommend to enter, false otherwise
     */
    default boolean shouldEnter(int index) {
        return shouldEnter(index, null);
    }

    /**
     * @param index         the bar index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend to enter, false otherwise
     */
    default boolean shouldEnter(int index, TradingRecord tradingRecord) {
        return !isUnstableAt(index) && getEntryRule().isSatisfied(index, tradingRecord);
    }

    /**
     * @param index the bar index
     * @return true to recommend to exit, false otherwise
     */
    default boolean shouldExit(int index) {
        return shouldExit(index, null);
    }

    /**
     * @param index         the bar index
     * @param tradingRecord the potentially needed trading history
     * @return true to recommend to exit, false otherwise
     */
    default boolean shouldExit(int index, TradingRecord tradingRecord) {
        return !isUnstableAt(index) && getExitRule().isSatisfied(index, tradingRecord);
    }

    /**
     * Serializes {@code this} strategy into a JSON payload that captures its
     * metadata and rule descriptors.
     *
     * @return JSON description of the strategy
     * @throws NullPointerException  if the strategy or any of its components are
     *                               {@code null}
     * @throws IllegalStateException if serialization fails due to an internal error
     *                               during JSON generation
     * @throws RuntimeException      if serialization fails due to an I/O error or
     *                               other runtime exception during JSON processing
     * @since 0.19
     */
    default String toJson() {
        return StrategySerialization.toJson(this);
    }

    /**
     * Converts {@code this} strategy into a structured descriptor that can be
     * embedded inside other component metadata.
     *
     * @return component descriptor for the strategy
     * @throws NullPointerException     if the strategy or any of its rules are
     *                                  {@code null}
     * @throws IllegalArgumentException if rule serialization fails due to
     *                                  unsupported constructor signatures or
     *                                  invalid rule configurations
     * @since 0.19
     */
    default ComponentDescriptor toDescriptor() {
        return StrategySerialization.describe(this);
    }

    /**
     * Reconstructs a strategy instance from its serialized representation.
     *
     * @param series backing series to attach to the reconstructed strategy; must
     *               not be {@code null}
     * @param json   serialized strategy payload generated by {@link #toJson()};
     *               must not be {@code null} and must be a valid JSON
     *               representation of a strategy descriptor
     * @return reconstructed strategy instance with entry and exit rules restored
     *         from the descriptor
     * @throws NullPointerException     if {@code series} or {@code json} is
     *                                  {@code null}
     * @throws IllegalArgumentException if the JSON payload is malformed, missing
     *                                  required component descriptors (entry/exit
     *                                  rules), contains incompatible component
     *                                  types, or fails validation during
     *                                  deserialization
     * @throws IllegalStateException    if strategy construction fails due to
     *                                  missing constructors, instantiation errors,
     *                                  or unresolved component dependencies
     * @since 0.19
     */
    static Strategy fromJson(BarSeries series, String json) {
        return StrategySerialization.fromJson(series, json);
    }
}
