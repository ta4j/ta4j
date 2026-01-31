/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * {@link Bar} implementation that tracks realtime side and liquidity
 * breakdowns.
 *
 * @since 0.22.2
 */
public class BaseRealtimeBar extends BaseBar implements RealtimeBar {

    private static final long serialVersionUID = -5572746191534014724L;

    private final NumFactory numFactory;

    private Num buyVolume;
    private Num sellVolume;
    private Num buyAmount;
    private Num sellAmount;
    private long buyTrades;
    private long sellTrades;
    private boolean hasSideData;

    private Num makerVolume;
    private Num takerVolume;
    private Num makerAmount;
    private Num takerAmount;
    private long makerTrades;
    private long takerTrades;
    private boolean hasLiquidityData;

    /**
     * Constructor.
     *
     * @param timePeriod   the time period (optional if beginTime and endTime is
     *                     given)
     * @param beginTime    the begin time of the bar period (in UTC) (optional if
     *                     endTime is given)
     * @param endTime      the end time of the bar period (in UTC) (optional if
     *                     beginTime is given)
     * @param openPrice    the open price of the bar period
     * @param highPrice    the highest price of the bar period
     * @param lowPrice     the lowest price of the bar period
     * @param closePrice   the close price of the bar period
     * @param volume       the total traded volume of the bar period
     * @param amount       the total traded amount of the bar period
     * @param trades       the number of trades of the bar period
     * @param buyVolume    buy-side volume
     * @param sellVolume   sell-side volume
     * @param buyAmount    buy-side amount
     * @param sellAmount   sell-side amount
     * @param buyTrades    buy-side trades
     * @param sellTrades   sell-side trades
     * @param makerVolume  maker-side volume
     * @param takerVolume  taker-side volume
     * @param makerAmount  maker-side amount
     * @param takerAmount  taker-side amount
     * @param makerTrades  maker-side trades
     * @param takerTrades  taker-side trades
     * @param hasSideData  {@code true} if side data was provided
     * @param hasLiquidity {@code true} if liquidity data was provided
     * @param numFactory   the number factory backing this bar
     *
     * @since 0.22.2
     */
    public BaseRealtimeBar(final Duration timePeriod, final Instant beginTime, final Instant endTime,
            final Num openPrice, final Num highPrice, final Num lowPrice, final Num closePrice, final Num volume,
            final Num amount, final long trades, final Num buyVolume, final Num sellVolume, final Num buyAmount,
            final Num sellAmount, final long buyTrades, final long sellTrades, final Num makerVolume,
            final Num takerVolume, final Num makerAmount, final Num takerAmount, final long makerTrades,
            final long takerTrades, final boolean hasSideData, final boolean hasLiquidity,
            final NumFactory numFactory) {
        super(timePeriod, beginTime, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount, trades);
        this.numFactory = Objects.requireNonNull(numFactory, "numFactory cannot be null");
        this.buyVolume = buyVolume;
        this.sellVolume = sellVolume;
        this.buyAmount = buyAmount;
        this.sellAmount = sellAmount;
        this.buyTrades = buyTrades;
        this.sellTrades = sellTrades;
        this.hasSideData = hasSideData;
        this.makerVolume = makerVolume;
        this.takerVolume = takerVolume;
        this.makerAmount = makerAmount;
        this.takerAmount = takerAmount;
        this.makerTrades = makerTrades;
        this.takerTrades = takerTrades;
        this.hasLiquidityData = hasLiquidity;
    }

    @Override
    public boolean hasSideData() {
        return hasSideData;
    }

    @Override
    public boolean hasLiquidityData() {
        return hasLiquidityData;
    }

    @Override
    public Num getBuyVolume() {
        return buyVolume == null ? numFactory.zero() : buyVolume;
    }

    @Override
    public Num getSellVolume() {
        return sellVolume == null ? numFactory.zero() : sellVolume;
    }

    @Override
    public Num getBuyAmount() {
        return buyAmount == null ? numFactory.zero() : buyAmount;
    }

    @Override
    public Num getSellAmount() {
        return sellAmount == null ? numFactory.zero() : sellAmount;
    }

    @Override
    public long getBuyTrades() {
        return buyTrades;
    }

    @Override
    public long getSellTrades() {
        return sellTrades;
    }

    @Override
    public Num getMakerVolume() {
        return makerVolume == null ? numFactory.zero() : makerVolume;
    }

    @Override
    public Num getTakerVolume() {
        return takerVolume == null ? numFactory.zero() : takerVolume;
    }

    @Override
    public Num getMakerAmount() {
        return makerAmount == null ? numFactory.zero() : makerAmount;
    }

    @Override
    public Num getTakerAmount() {
        return takerAmount == null ? numFactory.zero() : takerAmount;
    }

    @Override
    public long getMakerTrades() {
        return makerTrades;
    }

    @Override
    public long getTakerTrades() {
        return takerTrades;
    }

    @Override
    public void addTrade(final Num tradeVolume, final Num tradePrice, final Side side, final Liquidity liquidity) {
        super.addTrade(tradeVolume, tradePrice);
        addSideData(tradeVolume, tradePrice, side);
        addLiquidityData(tradeVolume, tradePrice, liquidity);
    }

    private void addSideData(final Num tradeVolume, final Num tradePrice, final Side side) {
        if (side == null) {
            return;
        }
        hasSideData = true;
        final Num tradeAmount = tradePrice.multipliedBy(tradeVolume);
        if (side == Side.BUY) {
            buyVolume = buyVolume == null ? tradeVolume : buyVolume.plus(tradeVolume);
            buyAmount = buyAmount == null ? tradeAmount : buyAmount.plus(tradeAmount);
            buyTrades++;
        } else {
            sellVolume = sellVolume == null ? tradeVolume : sellVolume.plus(tradeVolume);
            sellAmount = sellAmount == null ? tradeAmount : sellAmount.plus(tradeAmount);
            sellTrades++;
        }
    }

    private void addLiquidityData(final Num tradeVolume, final Num tradePrice, final Liquidity liquidity) {
        if (liquidity == null) {
            return;
        }
        hasLiquidityData = true;
        final Num tradeAmount = tradePrice.multipliedBy(tradeVolume);
        if (liquidity == Liquidity.MAKER) {
            makerVolume = makerVolume == null ? tradeVolume : makerVolume.plus(tradeVolume);
            makerAmount = makerAmount == null ? tradeAmount : makerAmount.plus(tradeAmount);
            makerTrades++;
        } else {
            takerVolume = takerVolume == null ? tradeVolume : takerVolume.plus(tradeVolume);
            takerAmount = takerAmount == null ? tradeAmount : takerAmount.plus(tradeAmount);
            takerTrades++;
        }
    }
}
