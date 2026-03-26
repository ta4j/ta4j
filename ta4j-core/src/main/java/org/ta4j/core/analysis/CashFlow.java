/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Allows to follow the money cash flow involved by a list of positions over a
 * bar series, either marked to market or using realized values only.
 */
public class CashFlow implements PerformanceIndicator {

    /**
     * The bar series.
     */
    private final BarSeries barSeries;

    /**
     * The (accrued) cash flow sequence (without trading costs).
     */
    private final List<Num> values;

    /**
     * The first logical bar index materialized in {@link #values}.
     */
    private final int valueStartIndex;

    /**
     * The last logical bar index materialized in {@link #values}.
     */
    private final int valueEndIndex;

    /**
     * The equity curve calculation mode.
     */
    private final EquityCurveMode equityCurveMode;

    /**
     * Constructor.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param finalIndex           index up until cash flows of open positions are
     *                             considered
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, 0, barSeries.getEndIndex(), finalIndex, equityCurveMode, openPositionHandling);
    }

    /**
     * Constructor materializing only a bounded logical window on the original
     * series.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param startIndex           first logical bar index to materialize
     * @param finalIndex           last logical bar index to materialize and to
     *                             consider for open positions
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.22.5
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int startIndex, int finalIndex,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, startIndex, finalIndex, finalIndex, equityCurveMode, openPositionHandling);
    }

    /**
     * Constructor for cash flows of a closed position.
     *
     * @param barSeries       the bar series
     * @param position        a single position
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, Position position, EquityCurveMode equityCurveMode) {
        this(barSeries, new BaseTradingRecord(position), barSeries.getEndIndex(), equityCurveMode);
    }

    /**
     * Constructor.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param finalIndex      index up until cash flows of open positions are
     *                        considered
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex, EquityCurveMode equityCurveMode) {
        this(barSeries, tradingRecord, finalIndex, equityCurveMode, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor for cash flows of a closed position.
     *
     * @param barSeries the bar series
     * @param position  a single position
     */
    public CashFlow(BarSeries barSeries, Position position) {
        this(barSeries, position, EquityCurveMode.MARK_TO_MARKET);
    }

    /**
     * Constructor for cash flows of closed positions of a trading record.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor.
     *
     * @param barSeries       the bar series
     * @param tradingRecord   the trading record
     * @param equityCurveMode the calculation mode
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), equityCurveMode,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param equityCurveMode      the calculation mode
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, EquityCurveMode equityCurveMode,
            OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), equityCurveMode, openPositionHandling);
    }

    /**
     * Constructor.
     *
     * @param barSeries     the bar series
     * @param tradingRecord the trading record
     * @param finalIndex    index up until cash flows of open positions are
     *                      considered
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex) {
        this(barSeries, tradingRecord, finalIndex, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Constructor.
     *
     * @param barSeries            the bar series
     * @param tradingRecord        the trading record
     * @param openPositionHandling how to handle open positions
     * @since 0.22.2
     */
    public CashFlow(BarSeries barSeries, TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries), EquityCurveMode.MARK_TO_MARKET,
                openPositionHandling);
    }

    private CashFlow(BarSeries barSeries, TradingRecord tradingRecord, int startIndex, int endIndex, int finalIndex,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this.barSeries = Objects.requireNonNull(barSeries);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        int seriesEnd = barSeries.getEndIndex();
        this.valueStartIndex = Math.max(0, startIndex);
        this.valueEndIndex = seriesEnd < 0 ? -1 : Math.min(Math.max(endIndex, this.valueStartIndex), seriesEnd);
        int size = this.valueEndIndex < this.valueStartIndex ? 0 : this.valueEndIndex - this.valueStartIndex + 1;
        this.values = new ArrayList<>(Collections.nCopies(size, barSeries.numFactory().one()));
        calculate(Objects.requireNonNull(tradingRecord), finalIndex, Objects.requireNonNull(openPositionHandling));
    }

    /**
     * Calculates the cash flow for a single position (including accrued cashflow
     * for open positions).
     *
     * @param position   a single position
     * @param finalIndex index up until cash flow of open positions is considered
     * @since 0.22.2
     */
    @Override
    public void calculatePosition(Position position, int finalIndex) {
        Trade entry = position.getEntry();
        if (entry == null) {
            return;
        }
        int seriesEnd = barSeries.getEndIndex();
        int entryIndex = entry.getIndex();
        if (entryIndex > finalIndex || entryIndex > seriesEnd) {
            return;
        }
        int endIndex = determineEndIndex(position, finalIndex, seriesEnd);
        int seriesBegin = barSeries.getBeginIndex();
        if (endIndex < seriesBegin) {
            return;
        }
        int windowStartIndex = Math.max(valueStartIndex, seriesBegin);
        int windowEndIndex = Math.min(valueEndIndex, seriesEnd);
        if (windowStartIndex > windowEndIndex || endIndex < windowStartIndex) {
            return;
        }

        NumFactory numFactory = barSeries.numFactory();
        boolean isLongTrade = entry.isBuy();
        Num netEntryPrice = entry.getNetPrice();
        Num entryEquity = getStoredValue(Math.max(entryIndex, windowStartIndex));
        if (!entryEquity.isGreaterThan(numFactory.zero())) {
            return;
        }
        int ratioIndex = endIndex;
        if (ratioIndex == entryIndex && entryIndex < seriesEnd) {
            ratioIndex = entryIndex + 1;
        }

        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            Num averageHoldingCostPerPeriod = averageHoldingCostPerPeriod(position, endIndex, numFactory);
            boolean windowStartSeeded = false;
            if (entryIndex < windowStartIndex) {
                Num windowStartPrice = windowStartIndex == endIndex ? resolveExitPrice(position, endIndex, barSeries)
                        : barSeries.getBar(windowStartIndex).getClosePrice();
                Num windowStartNetPrice = addCost(windowStartPrice, averageHoldingCostPerPeriod, isLongTrade);
                Num windowStartRatio = getIntermediateRatio(isLongTrade, netEntryPrice, windowStartNetPrice);
                multiplyValue(windowStartIndex, windowStartRatio);
                windowStartSeeded = true;
            }
            int start = Math.max(Math.max(entryIndex + 1, seriesBegin + 1), windowStartIndex + 1);
            for (int barIndex = start; barIndex < endIndex && barIndex <= windowEndIndex; barIndex++) {
                Num closePrice = barSeries.getBar(barIndex).getClosePrice();
                Num intermediateNetPrice = addCost(closePrice, averageHoldingCostPerPeriod, isLongTrade);
                Num ratio = getIntermediateRatio(isLongTrade, netEntryPrice, intermediateNetPrice);
                multiplyValue(barIndex, ratio);
            }
            Num exitPrice = resolveExitPrice(position, endIndex, barSeries);
            Num netExitPrice = addCost(exitPrice, averageHoldingCostPerPeriod, isLongTrade);
            Num ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
            if (ratioIndex <= windowEndIndex && !(windowStartSeeded && ratioIndex == windowStartIndex)) {
                multiplyValue(ratioIndex, ratio);
            }
            multiplyRange(ratioIndex + 1, windowEndIndex, ratio);
            return;
        }

        Trade exit = position.getExit();
        if (exit != null && endIndex >= exit.getIndex()) {
            Num holdingCost = position.getHoldingCost(endIndex);
            Num netExitPrice = addCost(exit.getNetPrice(), holdingCost, isLongTrade);
            Num ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
            multiplyRange(Math.max(ratioIndex, windowStartIndex), windowEndIndex, ratio);
        }
    }

    /**
     * @param index the bar index
     * @return the cash flow value at the index-th position
     */
    @Override
    public Num getValue(int index) {
        return getStoredValue(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    @Override
    public BarSeries getBarSeries() {
        return barSeries;
    }

    /**
     * @return the size of the bar series
     */
    public int getSize() {
        return barSeries.getBarCount();
    }

    /**
     * @return the equity curve mode used for this cash flow
     * @since 0.22.2
     */
    @Override
    public EquityCurveMode getEquityCurveMode() {
        return equityCurveMode;
    }

    private void multiplyValue(int index, Num ratio) {
        if (!containsIndex(index)) {
            return;
        }
        int valueIndex = toValueIndex(index);
        values.set(valueIndex, values.get(valueIndex).multipliedBy(ratio));
    }

    private void multiplyRange(int startIndex, int endIndex, Num ratio) {
        if (values.isEmpty()) {
            return;
        }
        int start = Math.max(valueStartIndex, startIndex);
        int end = Math.min(endIndex, valueEndIndex);
        if (start > end) {
            return;
        }
        for (int i = start; i <= end; i++) {
            int valueIndex = toValueIndex(i);
            values.set(valueIndex, values.get(valueIndex).multipliedBy(ratio));
        }
    }

    private boolean containsIndex(int index) {
        return index >= valueStartIndex && index <= valueEndIndex;
    }

    private Num getStoredValue(int index) {
        return values.get(toValueIndex(index));
    }

    private int toValueIndex(int index) {
        return index - valueStartIndex;
    }

    private static Num getIntermediateRatio(boolean isLongTrade, Num entryPrice, Num exitPrice) {
        if (isLongTrade) {
            return exitPrice.dividedBy(entryPrice);
        }
        return entryPrice.getNumFactory().numOf(2).minus(exitPrice.dividedBy(entryPrice));
    }
}
