/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
