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
 * A volume bar is sampled after a fixed number of contracts (volume) have been
 * traded.
 */
public class VolumeBarBuilder implements BarBuilder {

    private final NumFactory numFactory;
    private final RemainderCarryOverPolicy carryOverPolicy;
    private final boolean realtimeBars;
    private final Num volumeThreshold;
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
    private Num lastTradeVolume;
    private Num lastTradePrice;
    private RealtimeBar.Side lastTradeSide;
    private RealtimeBar.Liquidity lastTradeLiquidity;

    /**
     * A builder to build a new {@link BaseBar} with {@link DoubleNumFactory}
     *
     * @param volumeThreshold the threshold at which a new bar should be created
     */
    public VolumeBarBuilder(final int volumeThreshold) {
        this(DoubleNumFactory.getInstance(), volumeThreshold, false, RemainderCarryOverPolicy.NONE);
    }

    /**
     * A builder to build a new {@link BaseBar}
     *
     * @param numFactory
     * @param volumeThreshold the threshold at which a new bar should be created
     */
    public VolumeBarBuilder(final NumFactory numFactory, final int volumeThreshold) {
        this(numFactory, volumeThreshold, false, RemainderCarryOverPolicy.NONE);
    }

    /**
     * A builder to build a new {@link BaseBar} or {@link BaseRealtimeBar}
     *
     * @param numFactory
     * @param volumeThreshold the threshold at which a new bar should be created
     * @param realtimeBars    {@code true} to build {@link BaseRealtimeBar}
     *                        instances
     *
     * @since 0.22.0
     */
    public VolumeBarBuilder(final NumFactory numFactory, final int volumeThreshold, final boolean realtimeBars) {
        this(numFactory, volumeThreshold, realtimeBars, RemainderCarryOverPolicy.NONE);
    }

    /**
     * A builder to build a new {@link BaseBar} or {@link BaseRealtimeBar}
     *
     * @param numFactory      the backing number factory
     * @param volumeThreshold the threshold at which a new bar should be created
     * @param realtimeBars    {@code true} to build {@link BaseRealtimeBar}
     *                        instances
     * @param carryOverPolicy policy for handling side/liquidity remainder splits
     *
     * @since 0.22.0
     */
    public VolumeBarBuilder(final NumFactory numFactory, final int volumeThreshold, final boolean realtimeBars,
            final RemainderCarryOverPolicy carryOverPolicy) {
        this.numFactory = numFactory;
        this.carryOverPolicy = carryOverPolicy == null ? RemainderCarryOverPolicy.NONE : carryOverPolicy;
        this.realtimeBars = realtimeBars;
        this.volumeThreshold = numFactory.numOf(volumeThreshold);
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
        throw new IllegalArgumentException("VolumeBar can only be built from closePrice");
    }

    @Override
    public BarBuilder openPrice(final Number openPrice) {
        throw new IllegalArgumentException("VolumeBar can only be built from closePrice");
    }

    @Override
    public BarBuilder openPrice(final String openPrice) {
        throw new IllegalArgumentException("VolumeBar can only be built from closePrice");
    }

    @Override
    public BarBuilder highPrice(final Number highPrice) {
        throw new IllegalArgumentException("VolumeBar can only be built from closePrice");
    }

    @Override
    public BarBuilder highPrice(final String highPrice) {
        throw new IllegalArgumentException("VolumeBar can only be built from closePrice");
    }

    @Override
    public BarBuilder highPrice(final Num highPrice) {
        throw new IllegalArgumentException("VolumeBar can only be built from closePrice");
    }

    @Override
    public BarBuilder lowPrice(final Num lowPrice) {
        throw new IllegalArgumentException("VolumeBar can only be built from closePrice");
    }

    @Override
    public BarBuilder lowPrice(final Number lowPrice) {
        throw new IllegalArgumentException("VolumeBar can only be built from closePrice");
    }

    @Override
    public BarBuilder lowPrice(final String lowPrice) {
        throw new IllegalArgumentException("VolumeBar can only be built from closePrice");
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
        this.volume = this.volume == null ? volume : this.volume.plus(volume);
        return this;
    }

    @Override
    public BarBuilder volume(final Number volume) {
        volume(numFactory.numOf(volume));
        return this;
    }

    @Override
    public BarBuilder volume(final String volume) {
        volume(numFactory.numOf(volume));
        return this;
    }

    @Override
    public BarBuilder amount(final Num amount) {
        this.amount = this.amount == null ? amount : this.amount.plus(amount);
        return this;
    }

    @Override
    public BarBuilder amount(final Number amount) {
        amount(numFactory.numOf(amount));
        return this;
    }

    @Override
    public BarBuilder amount(final String amount) {
        amount(numFactory.numOf(amount));
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
    public VolumeBarBuilder bindTo(final BarSeries barSeries) {
        this.barSeries = Objects.requireNonNull(barSeries);
        return this;
    }

    /**
     * Ingests a trade into the current volume bar and appends the bar once the
     * volume threshold is met.
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
     * Ingests a trade into the current volume bar and appends the bar once the
     * volume threshold is met.
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
        lastTradeVolume = tradeVolume;
        lastTradePrice = tradePrice;
        lastTradeSide = side;
        lastTradeLiquidity = liquidity;
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
        if (volume.isGreaterThanOrEqual(volumeThreshold)) {
            // move volume remainder to next bar
            var volumeRemainder = numFactory.zero();
            CarryOverSnapshot carryOverSnapshot = null;
            if (volume.isGreaterThan(volumeThreshold)) {
                volumeRemainder = volume.minus(volumeThreshold);
                // cap currently built bar, volume is then restored to volumeRemainder
                volume = volumeThreshold;
                if (carryOverPolicy == RemainderCarryOverPolicy.PROPORTIONAL
                        || carryOverPolicy == RemainderCarryOverPolicy.PROPORTIONAL_WITH_TRADE_COUNT) {
                    carryOverSnapshot = applyProportionalCarryOver(volumeRemainder);
                }
            }

            if (amount == null) {
                amount = closePrice.multipliedBy(volume);
            }

            barSeries.addBar(build());
            volume = volumeRemainder;

            reset();
            if (carryOverSnapshot != null) {
                carryOverSnapshot.applyTo(this);
            }
        }
    }

    private void reset() {
        timePeriod = null;
        beginTime = null;
        endTime = null;
        openPrice = null;
        highPrice = numFactory.zero();
        lowPrice = numFactory.numOf(Integer.MAX_VALUE);
        amount = null;
        trades = 0;
        closePrice = null;
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
        lastTradeVolume = null;
        lastTradePrice = null;
        lastTradeSide = null;
        lastTradeLiquidity = null;
    }

    private CarryOverSnapshot applyProportionalCarryOver(final Num volumeRemainder) {
        if (volumeRemainder == null || volumeRemainder.isZero() || lastTradeVolume == null || lastTradePrice == null) {
            return null;
        }
        final CarryOverSnapshot snapshot = new CarryOverSnapshot();
        final Num remainderAmount = lastTradePrice.multipliedBy(volumeRemainder);
        final boolean carryTradeCount = shouldCarryTradeCount(volumeRemainder);
        if (lastTradeSide != null) {
            if (lastTradeSide == RealtimeBar.Side.BUY) {
                buyVolume = subtractOrNull(buyVolume, volumeRemainder);
                buyAmount = subtractOrNull(buyAmount, remainderAmount);
                snapshot.buyVolume = volumeRemainder;
                snapshot.buyAmount = remainderAmount;
                if (carryTradeCount) {
                    buyTrades = Math.max(0, buyTrades - 1);
                    snapshot.buyTrades = 1;
                }
            } else {
                sellVolume = subtractOrNull(sellVolume, volumeRemainder);
                sellAmount = subtractOrNull(sellAmount, remainderAmount);
                snapshot.sellVolume = volumeRemainder;
                snapshot.sellAmount = remainderAmount;
                if (carryTradeCount) {
                    sellTrades = Math.max(0, sellTrades - 1);
                    snapshot.sellTrades = 1;
                }
            }
        }
        if (lastTradeLiquidity != null) {
            if (lastTradeLiquidity == RealtimeBar.Liquidity.MAKER) {
                makerVolume = subtractOrNull(makerVolume, volumeRemainder);
                makerAmount = subtractOrNull(makerAmount, remainderAmount);
                snapshot.makerVolume = volumeRemainder;
                snapshot.makerAmount = remainderAmount;
                if (carryTradeCount) {
                    makerTrades = Math.max(0, makerTrades - 1);
                    snapshot.makerTrades = 1;
                }
            } else {
                takerVolume = subtractOrNull(takerVolume, volumeRemainder);
                takerAmount = subtractOrNull(takerAmount, remainderAmount);
                snapshot.takerVolume = volumeRemainder;
                snapshot.takerAmount = remainderAmount;
                if (carryTradeCount) {
                    takerTrades = Math.max(0, takerTrades - 1);
                    snapshot.takerTrades = 1;
                }
            }
        }
        if (carryTradeCount) {
            trades = Math.max(0, trades - 1);
            snapshot.trades = 1;
        }
        snapshot.hasSideData = snapshot.buyVolume != null || snapshot.sellVolume != null;
        snapshot.hasLiquidityData = snapshot.makerVolume != null || snapshot.takerVolume != null;
        return snapshot;
    }

    private boolean shouldCarryTradeCount(final Num volumeRemainder) {
        if (carryOverPolicy != RemainderCarryOverPolicy.PROPORTIONAL_WITH_TRADE_COUNT) {
            return false;
        }
        if (lastTradeVolume == null || lastTradeVolume.isZero()) {
            return false;
        }
        return volumeRemainder.multipliedBy(numFactory.numOf(2)).isGreaterThanOrEqual(lastTradeVolume);
    }

    private Num subtractOrNull(final Num current, final Num remainder) {
        if (current == null) {
            return null;
        }
        final Num updated = current.minus(remainder);
        return updated.isZero() ? null : updated;
    }

    private static final class CarryOverSnapshot {
        private Num buyVolume;
        private Num sellVolume;
        private Num buyAmount;
        private Num sellAmount;
        private Num makerVolume;
        private Num takerVolume;
        private Num makerAmount;
        private Num takerAmount;
        private long trades;
        private long buyTrades;
        private long sellTrades;
        private long makerTrades;
        private long takerTrades;
        private boolean hasSideData;
        private boolean hasLiquidityData;

        private void applyTo(final VolumeBarBuilder builder) {
            builder.buyVolume = buyVolume;
            builder.sellVolume = sellVolume;
            builder.buyAmount = buyAmount;
            builder.sellAmount = sellAmount;
            builder.makerVolume = makerVolume;
            builder.takerVolume = takerVolume;
            builder.makerAmount = makerAmount;
            builder.takerAmount = takerAmount;
            builder.trades = trades;
            builder.buyTrades = buyTrades;
            builder.sellTrades = sellTrades;
            builder.makerTrades = makerTrades;
            builder.takerTrades = takerTrades;
            builder.hasSideData = hasSideData;
            builder.hasLiquidityData = hasLiquidityData;
        }
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
