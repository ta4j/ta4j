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
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

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
        this.barSeries = Objects.requireNonNull(barSeries);
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        var seriesEnd = barSeries.getEndIndex();
        var size = Math.max(seriesEnd + 1, 0);
        this.values = new ArrayList<>(Collections.nCopies(size, barSeries.numFactory().one()));
        calculate(Objects.requireNonNull(tradingRecord), finalIndex, Objects.requireNonNull(openPositionHandling));
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
        var entry = position.getEntry();
        if (entry == null) {
            return;
        }
        var seriesEnd = barSeries.getEndIndex();
        var entryIndex = entry.getIndex();
        if (entryIndex > finalIndex || entryIndex > seriesEnd) {
            return;
        }
        var endIndex = determineEndIndex(position, finalIndex, seriesEnd);
        var seriesBegin = barSeries.getBeginIndex();
        if (endIndex < seriesBegin) {
            return;
        }

        var numFactory = barSeries.numFactory();
        var isLongTrade = entry.isBuy();
        var start = Math.max(entryIndex + 1, seriesBegin + 1);
        var netEntryPrice = entry.getNetPrice();
        var entryEquity = values.get(entryIndex);
        if (!entryEquity.isGreaterThan(numFactory.zero())) {
            return;
        }
        var ratioIndex = endIndex;
        if (ratioIndex == entryIndex && entryIndex < seriesEnd) {
            ratioIndex = entryIndex + 1;
        }

        if (equityCurveMode == EquityCurveMode.MARK_TO_MARKET) {
            var averageHoldingCostPerPeriod = averageHoldingCostPerPeriod(position, endIndex, numFactory);
            for (var barIndex = start; barIndex < endIndex; barIndex++) {
                var closePrice = barSeries.getBar(barIndex).getClosePrice();
                var intermediateNetPrice = addCost(closePrice, averageHoldingCostPerPeriod, isLongTrade);
                var ratio = getIntermediateRatio(isLongTrade, netEntryPrice, intermediateNetPrice);
                multiplyValue(barIndex, ratio);
            }
            var exitPrice = resolveExitPrice(position, endIndex, barSeries);
            var netExitPrice = addCost(exitPrice, averageHoldingCostPerPeriod, isLongTrade);
            var ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
            multiplyValue(ratioIndex, ratio);
            multiplyRange(ratioIndex + 1, seriesEnd, ratio);
            return;
        }

        var exit = position.getExit();
        if (exit != null && endIndex >= exit.getIndex()) {
            var holdingCost = position.getHoldingCost(endIndex);
            var netExitPrice = addCost(exit.getNetPrice(), holdingCost, isLongTrade);
            var ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
            multiplyRange(ratioIndex, seriesEnd, ratio);
        }
    }

    /**
     * @param index the bar index
     * @return the cash flow value at the index-th position
     */
    @Override
    public Num getValue(int index) {
        return values.get(index);
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
        if (index < 0 || index >= values.size()) {
            return;
        }
        values.set(index, values.get(index).multipliedBy(ratio));
    }

    private void multiplyRange(int startIndex, int endIndex, Num ratio) {
        if (values.isEmpty()) {
            return;
        }
        var start = Math.max(0, startIndex);
        var end = Math.min(endIndex, values.size() - 1);
        if (start > end) {
            return;
        }
        for (var i = start; i <= end; i++) {
            values.set(i, values.get(i).multipliedBy(ratio));
        }
    }

    private static Num getIntermediateRatio(boolean isLongTrade, Num entryPrice, Num exitPrice) {
        if (isLongTrade) {
            return exitPrice.dividedBy(entryPrice);
        }
        return entryPrice.getNumFactory().numOf(2).minus(exitPrice.dividedBy(entryPrice));
    }
}
