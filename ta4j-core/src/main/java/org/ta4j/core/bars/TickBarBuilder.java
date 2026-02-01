/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseRealtimeBar;
import org.ta4j.core.RealtimeBar;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * A tick bar is sampled after a fixed number of ticks.
 */
public class TickBarBuilder implements BarBuilder {

    private final NumFactory numFactory;
    private final boolean realtimeBars;
    private final int tickCount;
    private int passedTicksCount;
    private BarSeries barSeries;
    private Duration timePeriod;
    private Instant beginTime;
    private Instant endTime;
    private Num volume;
    private Num openPrice;
    private Num highPrice;
    private Num closePrice;
    private Num lowPrice;
    private Num amount;
    private long trades;
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
     * A builder to build a new {@link BaseBar} with {@link DoubleNumFactory}
     *
     * @param tickCount the number of ticks at which a new bar should be created
     */
    public TickBarBuilder(final int tickCount) {
        this(DoubleNumFactory.getInstance(), tickCount, false);
    }

    /**
     * A builder to build a new {@link BaseBar}
     *
     * @param numFactory
     * @param tickCount  the number of ticks at which a new bar should be created
     */
    public TickBarBuilder(final NumFactory numFactory, final int tickCount) {
        this(numFactory, tickCount, false);
    }

    /**
     * A builder to build a new {@link BaseBar} or {@link BaseRealtimeBar}
     *
     * @param numFactory
     * @param tickCount    the number of ticks at which a new bar should be created
     * @param realtimeBars {@code true} to build {@link BaseRealtimeBar} instances
     *
     * @since 0.22.0
     */
    public TickBarBuilder(final NumFactory numFactory, final int tickCount, final boolean realtimeBars) {
        this.numFactory = numFactory;
        this.realtimeBars = realtimeBars;
        this.tickCount = tickCount;
        reset();
    }

    @Override
    public BarBuilder timePeriod(final Duration timePeriod) {
        this.timePeriod = this.timePeriod == null ? timePeriod : this.timePeriod.plus(timePeriod);
        return this;
    }

    @Override
    public BarBuilder beginTime(final Instant beginTime) {
        this.beginTime = beginTime;
        return this;
    }

    @Override
    public BarBuilder endTime(final Instant endTime) {
        this.endTime = endTime;
        return this;
    }

    @Override
    public BarBuilder openPrice(final Num openPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder openPrice(final Number openPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder openPrice(final String openPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder highPrice(final Number highPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder highPrice(final String highPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder highPrice(final Num highPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder lowPrice(final Num lowPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder lowPrice(final Number lowPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder lowPrice(final String lowPrice) {
        throw new IllegalArgumentException("TickBar can only be built from closePrice");
    }

    @Override
    public BarBuilder closePrice(final Num tickPrice) {
        closePrice = tickPrice;
        if (openPrice == null) {
            openPrice = tickPrice;
        }

        highPrice = highPrice.max(tickPrice);
        lowPrice = lowPrice.min(tickPrice);

        return this;
    }

    @Override
    public BarBuilder closePrice(final Number closePrice) {
        return closePrice(numFactory.numOf(closePrice));
    }

    @Override
    public BarBuilder closePrice(final String closePrice) {
        return closePrice(numFactory.numOf(closePrice));
    }

    @Override
    public BarBuilder volume(final Num volume) {
        this.volume = this.volume.plus(volume);
        return this;
    }

    @Override
    public BarBuilder volume(final Number volume) {
        volume(this.numFactory.numOf(volume));
        return this;
    }

    @Override
    public BarBuilder volume(final String volume) {
        volume(this.numFactory.numOf(volume));
        return this;
    }

    @Override
    public BarBuilder amount(final Num amount) {
        this.amount = this.amount == null ? amount : this.amount.plus(amount);
        return this;
    }

    @Override
    public BarBuilder amount(final Number amount) {
        amount(this.numFactory.numOf(amount));
        return this;
    }

    @Override
    public BarBuilder amount(final String amount) {
        amount(this.numFactory.numOf(amount));
        return this;
    }

    @Override
    public BarBuilder trades(final long trades) {
        this.trades += trades;
        return this;
    }

    @Override
    public BarBuilder trades(final String trades) {
        trades(Long.parseLong(trades));
        return this;
    }

    @Override
    public TickBarBuilder bindTo(final BarSeries barSeries) {
        this.barSeries = Objects.requireNonNull(barSeries);
        return this;
    }

    /**
     * Ingests a trade into the current tick bar and appends the bar once the tick
     * threshold is met.
     *
     * @param time        the trade timestamp (UTC)
     * @param tradeVolume the traded volume
     * @param tradePrice  the traded price
     *
     * @since 0.22.0
     */
    @Override
    public void addTrade(final Instant time, final Num tradeVolume, final Num tradePrice) {
        addTrade(time, tradeVolume, tradePrice, null, null);
    }

    /**
     * Ingests a trade into the current tick bar and appends the bar once the tick
     * threshold is met.
     *
     * @param time        the trade timestamp (UTC)
     * @param tradeVolume the traded volume
     * @param tradePrice  the traded price
     * @param side        aggressor side (optional)
     * @param liquidity   liquidity classification (optional)
     *
     * @since 0.22.0
     */
    @Override
    public void addTrade(final Instant time, final Num tradeVolume, final Num tradePrice, final RealtimeBar.Side side,
            final RealtimeBar.Liquidity liquidity) {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(tradeVolume, "tradeVolume");
        Objects.requireNonNull(tradePrice, "tradePrice");
        ensureRealtimeTracking(side, liquidity);
        if (endTime != null && time.isBefore(endTime)) {
            throw new IllegalArgumentException(
                    String.format("Trade time %s is before current bar end time %s", time, endTime));
        }
        if (beginTime == null) {
            beginTime = time;
        }
        endTime = time;
        closePrice(tradePrice);
        volume(tradeVolume);
        trades(1);
        recordRealtimeTrade(tradeVolume, tradePrice, side, liquidity);
        add();
    }

    /**
     * Builds bar from current state that is modified for each tick.
     *
     * @return snapshot of current state
     */
    @Override
    public Bar build() {
        if (realtimeBars) {
            return new BaseRealtimeBar(timePeriod, beginTime, endTime, openPrice, highPrice, lowPrice, closePrice,
                    volume, amount, trades, buyVolume, sellVolume, buyAmount, sellAmount, buyTrades, sellTrades,
                    makerVolume, takerVolume, makerAmount, takerAmount, makerTrades, takerTrades, hasSideData,
                    hasLiquidityData, numFactory);
        }
        return new BaseBar(timePeriod, beginTime, endTime, openPrice, highPrice, lowPrice, closePrice, volume, amount,
                trades);
    }

    @Override
    public void add() {
        if (++passedTicksCount % tickCount == 0) {
            if (amount == null && volume != null) {
                amount = closePrice.multipliedBy(volume);
            }

            barSeries.addBar(build());
            reset();
        }
    }

    private void reset() {
        final var zero = numFactory.zero();
        timePeriod = null;
        beginTime = null;
        endTime = null;
        openPrice = null;
        highPrice = zero;
        lowPrice = numFactory.numOf(Integer.MAX_VALUE);
        closePrice = null;
        amount = null;
        trades = 0;
        volume = zero;
        buyVolume = null;
        sellVolume = null;
        buyAmount = null;
        sellAmount = null;
        buyTrades = 0;
        sellTrades = 0;
        hasSideData = false;
        makerVolume = null;
        takerVolume = null;
        makerAmount = null;
        takerAmount = null;
        makerTrades = 0;
        takerTrades = 0;
        hasLiquidityData = false;
    }

    private void recordRealtimeTrade(final Num tradeVolume, final Num tradePrice, final RealtimeBar.Side side,
            final RealtimeBar.Liquidity liquidity) {
        if (side != null) {
            hasSideData = true;
            final Num tradeAmount = tradePrice.multipliedBy(tradeVolume);
            if (side == RealtimeBar.Side.BUY) {
                buyVolume = buyVolume == null ? tradeVolume : buyVolume.plus(tradeVolume);
                buyAmount = buyAmount == null ? tradeAmount : buyAmount.plus(tradeAmount);
                buyTrades++;
            } else {
                sellVolume = sellVolume == null ? tradeVolume : sellVolume.plus(tradeVolume);
                sellAmount = sellAmount == null ? tradeAmount : sellAmount.plus(tradeAmount);
                sellTrades++;
            }
        }

        if (liquidity != null) {
            hasLiquidityData = true;
            final Num tradeAmount = tradePrice.multipliedBy(tradeVolume);
            if (liquidity == RealtimeBar.Liquidity.MAKER) {
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

    private void ensureRealtimeTracking(final RealtimeBar.Side side, final RealtimeBar.Liquidity liquidity) {
        if (!realtimeBars && (side != null || liquidity != null)) {
            throw new IllegalStateException("Realtime trade data requires a realtime bar builder");
        }
    }
}
