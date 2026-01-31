/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.bars;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * A time bar is sampled after a fixed time period.
 *
 * <p>
 * When ingesting trades, missing intervals are omitted. Bars are created only
 * when a trade arrives within a time period. If you need continuity, reconcile
 * and backfill OHLCV data upstream (often by fetching a window with overlap and
 * upserting by bar end time).
 */
public class TimeBarBuilder implements BarBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(TimeBarBuilder.class);

    private final NumFactory numFactory;
    private final boolean realtimeBars;
    Duration timePeriod;
    Instant beginTime;
    Instant endTime;
    Num openPrice;
    Num highPrice;
    Num lowPrice;
    Num closePrice;
    Num volume;
    Num amount;
    long trades;
    private BarSeries baseBarSeries;
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

    /** A builder to build a new {@link BaseBar} with {@link DoubleNumFactory} */
    public TimeBarBuilder() {
        this(DoubleNumFactory.getInstance(), false);
    }

    /**
     * A builder to build a new {@link BaseBar}
     *
     * @param numFactory
     */
    public TimeBarBuilder(final NumFactory numFactory) {
        this(numFactory, false);
    }

    /**
     * A builder to build a new {@link BaseBar} or {@link BaseRealtimeBar}
     *
     * @param numFactory
     * @param realtimeBars {@code true} to build {@link BaseRealtimeBar} instances
     *
     * @since 0.22.2
     */
    public TimeBarBuilder(final NumFactory numFactory, final boolean realtimeBars) {
        this.numFactory = numFactory;
        this.realtimeBars = realtimeBars;
    }

    @Override
    public BarBuilder timePeriod(final Duration timePeriod) {
        this.timePeriod = timePeriod;
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
        this.openPrice = openPrice;
        return this;
    }

    @Override
    public BarBuilder openPrice(final Number openPrice) {
        openPrice(this.numFactory.numOf(openPrice));
        return this;
    }

    @Override
    public BarBuilder openPrice(final String openPrice) {
        openPrice(this.numFactory.numOf(openPrice));
        return this;
    }

    @Override
    public BarBuilder highPrice(final Number highPrice) {
        highPrice(this.numFactory.numOf(highPrice));
        return this;
    }

    @Override
    public BarBuilder highPrice(final String highPrice) {
        highPrice(this.numFactory.numOf(highPrice));
        return this;
    }

    @Override
    public BarBuilder highPrice(final Num highPrice) {
        this.highPrice = highPrice;
        return this;
    }

    @Override
    public BarBuilder lowPrice(final Num lowPrice) {
        this.lowPrice = lowPrice;
        return this;
    }

    @Override
    public BarBuilder lowPrice(final Number lowPrice) {
        lowPrice(this.numFactory.numOf(lowPrice));
        return this;
    }

    @Override
    public BarBuilder lowPrice(final String lowPrice) {
        lowPrice(this.numFactory.numOf(lowPrice));
        return this;
    }

    @Override
    public BarBuilder closePrice(final Num closePrice) {
        this.closePrice = closePrice;
        return this;
    }

    @Override
    public BarBuilder closePrice(final Number closePrice) {
        closePrice(this.numFactory.numOf(closePrice));
        return this;
    }

    @Override
    public BarBuilder closePrice(final String closePrice) {
        closePrice(this.numFactory.numOf(closePrice));
        return this;
    }

    @Override
    public BarBuilder volume(final Num volume) {
        this.volume = volume;
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
        this.amount = amount;
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
        this.trades = trades;
        return this;
    }

    @Override
    public BarBuilder trades(final String trades) {
        trades(Long.parseLong(trades));
        return this;
    }

    @Override
    public BarBuilder bindTo(final BarSeries barSeries) {
        this.baseBarSeries = Objects.requireNonNull(barSeries);
        return this;
    }

    /**
     * Ingests a trade into the current time bar and adds/replaces the bar in the
     * bound series. Bars are aligned to UTC epoch boundaries based on the current
     * {@link #timePeriod}. When the trade time skips one or more full periods,
     * those intervals are omitted; no bars are inserted for the gap.
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
     * Ingests a trade into the current time bar and adds/replaces the bar in the
     * bound series. Bars are aligned to UTC epoch boundaries based on the current
     * {@link #timePeriod}. When the trade time skips one or more full periods,
     * those intervals are omitted; no bars are inserted for the gap.
     *
     * @param time        the trade timestamp (UTC)
     * @param tradeVolume the traded volume
     * @param tradePrice  the traded price
     * @param side        aggressor side (optional)
     * @param liquidity   liquidity classification (optional)
     *
     * @since 0.22.2
     */
    @Override
    public void addTrade(final Instant time, final Num tradeVolume, final Num tradePrice, final RealtimeBar.Side side,
            final RealtimeBar.Liquidity liquidity) {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(tradeVolume, "tradeVolume");
        Objects.requireNonNull(tradePrice, "tradePrice");
        ensureRealtimeTracking(side, liquidity);
        if (timePeriod == null) {
            throw new IllegalStateException("Time period must be set before ingesting trades");
        }
        Objects.requireNonNull(baseBarSeries, "barSeries");
        ensureTimeRange(time);
        if (time.isBefore(beginTime)) {
            throw new IllegalArgumentException(
                    String.format("Trade time %s is before current bar begin time %s", time, beginTime));
        }
        Instant previousEndTime = endTime;
        long skippedPeriods = 0;
        while (!time.isBefore(endTime)) {
            persistCurrentBarIfPresent();
            resetTradeState();
            beginTime = endTime;
            endTime = beginTime.plus(timePeriod);
            skippedPeriods++;
        }
        if (skippedPeriods > 1) {
            long missingPeriods = skippedPeriods - 1;
            LOG.warn("Detected {} missing bar period(s) between {} and {} for series {}", missingPeriods,
                    previousEndTime, beginTime, baseBarSeries.getName());
        }
        recordTrade(tradeVolume, tradePrice, side, liquidity);
        baseBarSeries.addBar(build(), shouldReplaceCurrentBar());
    }

    @Override
    public Bar build() {
        if (realtimeBars) {
            return new BaseRealtimeBar(this.timePeriod, this.beginTime, this.endTime, this.openPrice, this.highPrice,
                    this.lowPrice, this.closePrice, this.volume, this.amount, this.trades, buyVolume, sellVolume,
                    buyAmount, sellAmount, buyTrades, sellTrades, makerVolume, takerVolume, makerAmount, takerAmount,
                    makerTrades, takerTrades, hasSideData, hasLiquidityData, numFactory);
        }
        return new BaseBar(this.timePeriod, this.beginTime, this.endTime, this.openPrice, this.highPrice, this.lowPrice,
                this.closePrice, this.volume, this.amount, this.trades);
    }

    @Override
    public void add() {
        if (amount == null && closePrice != null && volume != null) {
            amount = closePrice.multipliedBy(volume);
        }

        this.baseBarSeries.addBar(build());
    }

    private void ensureTimeRange(final Instant time) {
        if (beginTime == null && endTime == null) {
            beginTime = alignToTimePeriodStart(time);
            endTime = beginTime.plus(timePeriod);
            return;
        }
        if (beginTime == null) {
            beginTime = endTime.minus(timePeriod);
        } else if (endTime == null) {
            endTime = beginTime.plus(timePeriod);
        }
    }

    private Instant alignToTimePeriodStart(final Instant time) {
        try {
            final long periodNanos = timePeriod.toNanos();
            if (periodNanos <= 0) {
                throw new IllegalStateException("Time period must be positive");
            }
            final long timeNanos = Math.addExact(Math.multiplyExact(time.getEpochSecond(), 1_000_000_000L),
                    time.getNano());
            final long alignedNanos = timeNanos - Math.floorMod(timeNanos, periodNanos);
            final long alignedSeconds = Math.floorDiv(alignedNanos, 1_000_000_000L);
            final int alignedNanoPart = (int) Math.floorMod(alignedNanos, 1_000_000_000L);
            return Instant.ofEpochSecond(alignedSeconds, alignedNanoPart);
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("Time period too large to align trade time", ex);
        }
    }

    private void recordTrade(final Num tradeVolume, final Num tradePrice, final RealtimeBar.Side side,
            final RealtimeBar.Liquidity liquidity) {
        if (openPrice == null) {
            openPrice = tradePrice;
            highPrice = tradePrice;
            lowPrice = tradePrice;
        } else {
            highPrice = highPrice == null ? tradePrice : highPrice.max(tradePrice);
            lowPrice = lowPrice == null ? tradePrice : lowPrice.min(tradePrice);
        }
        closePrice = tradePrice;
        volume = volume == null ? tradeVolume : volume.plus(tradeVolume);
        Num tradeAmount = tradePrice.multipliedBy(tradeVolume);
        amount = amount == null ? tradeAmount : amount.plus(tradeAmount);
        trades++;

        if (side != null) {
            hasSideData = true;
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

    private void persistCurrentBarIfPresent() {
        if (!hasBarData()) {
            return;
        }
        if (amount == null && closePrice != null && volume != null) {
            amount = closePrice.multipliedBy(volume);
        }
        baseBarSeries.addBar(build(), shouldReplaceCurrentBar());
    }

    private boolean shouldReplaceCurrentBar() {
        if (baseBarSeries == null || baseBarSeries.isEmpty()) {
            return false;
        }
        return baseBarSeries.getLastBar().getEndTime().equals(endTime);
    }

    private boolean hasBarData() {
        return openPrice != null || highPrice != null || lowPrice != null || closePrice != null || volume != null
                || amount != null || trades > 0 || hasSideData || hasLiquidityData || buyVolume != null
                || sellVolume != null || buyAmount != null || sellAmount != null || buyTrades > 0 || sellTrades > 0
                || makerVolume != null || takerVolume != null || makerAmount != null || takerAmount != null
                || makerTrades > 0 || takerTrades > 0;
    }

    private void resetTradeState() {
        openPrice = null;
        highPrice = null;
        lowPrice = null;
        closePrice = null;
        volume = null;
        amount = null;
        trades = 0;
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

    private void ensureRealtimeTracking(final RealtimeBar.Side side, final RealtimeBar.Liquidity liquidity) {
        if (!realtimeBars && (side != null || liquidity != null)) {
            throw new IllegalStateException("Realtime trade data requires a realtime bar builder");
        }
    }

}
