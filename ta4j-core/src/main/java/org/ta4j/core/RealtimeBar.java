/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import org.ta4j.core.num.Num;

/**
 * Extension of {@link Bar} for realtime analytics that track trade side and
 * liquidity breakdowns.
 *
 * <p>
 * Side- and liquidity-aware values are optional because exchanges may not
 * provide them for every trade. When {@link #hasSideData()} or
 * {@link #hasLiquidityData()} is {@code false}, the corresponding getters
 * return zero values.
 *
 * @since 0.22.2
 */
public interface RealtimeBar extends Bar {

    /**
     * The aggressor side of a trade.
     *
     * @since 0.22.2
     */
    enum Side {
        BUY, SELL
    }

    /**
     * The liquidity classification of a trade.
     *
     * @since 0.22.2
     */
    enum Liquidity {
        MAKER, TAKER
    }

    /**
     * @return {@code true} if at least one trade with side information was
     *         aggregated into this bar
     *
     * @since 0.22.2
     */
    boolean hasSideData();

    /**
     * @return {@code true} if at least one trade with liquidity information was
     *         aggregated into this bar
     *
     * @since 0.22.2
     */
    boolean hasLiquidityData();

    /**
     * @return buy-side traded volume
     *
     * @since 0.22.2
     */
    Num getBuyVolume();

    /**
     * @return sell-side traded volume
     *
     * @since 0.22.2
     */
    Num getSellVolume();

    /**
     * @return buy-side traded amount
     *
     * @since 0.22.2
     */
    Num getBuyAmount();

    /**
     * @return sell-side traded amount
     *
     * @since 0.22.2
     */
    Num getSellAmount();

    /**
     * @return number of buy-side trades
     *
     * @since 0.22.2
     */
    long getBuyTrades();

    /**
     * @return number of sell-side trades
     *
     * @since 0.22.2
     */
    long getSellTrades();

    /**
     * @return maker-side traded volume
     *
     * @since 0.22.2
     */
    Num getMakerVolume();

    /**
     * @return taker-side traded volume
     *
     * @since 0.22.2
     */
    Num getTakerVolume();

    /**
     * @return maker-side traded amount
     *
     * @since 0.22.2
     */
    Num getMakerAmount();

    /**
     * @return taker-side traded amount
     *
     * @since 0.22.2
     */
    Num getTakerAmount();

    /**
     * @return number of maker-side trades
     *
     * @since 0.22.2
     */
    long getMakerTrades();

    /**
     * @return number of taker-side trades
     *
     * @since 0.22.2
     */
    long getTakerTrades();

    /**
     * Adds a trade with optional side and liquidity classification.
     *
     * @param tradeVolume traded volume
     * @param tradePrice  traded price
     * @param side        aggressor side (optional)
     * @param liquidity   liquidity classification (optional)
     *
     * @since 0.22.2
     */
    void addTrade(Num tradeVolume, Num tradePrice, Side side, Liquidity liquidity);
}
