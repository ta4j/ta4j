/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
    private final OffsetNumBuffer values;

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
        this(barSeries, tradingRecord, 0, Math.max(barSeries.getEndIndex(), tradingRecord.getEndIndex(barSeries)),
                finalIndex, equityCurveMode, openPositionHandling);
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
        this.barSeries = Objects.requireNonNull(barSeries, "barSeries");
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode);
        this.valueStartIndex = Math.max(0, startIndex);
        this.valueEndIndex = Math.min(Math.max(endIndex, this.valueStartIndex),
                OffsetNumBuffer.addressableEndIndex(this.barSeries));
        Num one = this.barSeries.numFactory().one();
        this.values = new OffsetNumBuffer(valueStartIndex, valueEndIndex, one, one);
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
        int analysisEndIndex = OffsetNumBuffer.addressableEndIndex(barSeries);
        int entryIndex = entry.getIndex();
        if (entryIndex > finalIndex || entryIndex > seriesEnd) {
            return;
        }
        int endIndex = determineEndIndex(position, finalIndex, analysisEndIndex);
        int seriesBegin = barSeries.getBeginIndex();
        if (endIndex < seriesBegin) {
            return;
        }
        int windowStartIndex = Math.max(valueStartIndex, seriesBegin);
        int windowEndIndex = Math.min(valueEndIndex, analysisEndIndex);
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
            long loopStart = Math.max(Math.max((long) entryIndex + 1, (long) seriesBegin + 1),
                    (long) windowStartIndex + 1);
            for (long barIndex = loopStart; barIndex < endIndex && barIndex <= windowEndIndex; barIndex++) {
                int currentIndex = (int) barIndex;
                Num closePrice = barSeries.getBar(currentIndex).getClosePrice();
                Num intermediateNetPrice = addCost(closePrice, averageHoldingCostPerPeriod, isLongTrade);
                Num ratio = getIntermediateRatio(isLongTrade, netEntryPrice, intermediateNetPrice);
                multiplyValue(currentIndex, ratio);
            }
            Num exitPrice = resolveExitPrice(position, endIndex, barSeries);
            Num netExitPrice = addCost(exitPrice, averageHoldingCostPerPeriod, isLongTrade);
            Num ratio = getIntermediateRatio(isLongTrade, netEntryPrice, netExitPrice);
            if (ratioIndex <= windowEndIndex && !(windowStartSeeded && ratioIndex == windowStartIndex)) {
                multiplyValue(ratioIndex, ratio);
            }
            if (ratioIndex < windowEndIndex) {
                // ratioIndex + 1 must stay representable: skip the empty
                // successor range instead of letting the increment overflow.
                multiplyRange(ratioIndex + 1, windowEndIndex, ratio);
            }
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
     * @return the cash flow value at the index-th position, or the neutral value
     *         one for indices outside the materialized window
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
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Returns the borrowed caller series by contract.")
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
        values.multiply(index, ratio);
    }

    private void multiplyRange(int startIndex, int endIndex, Num ratio) {
        values.multiplyRange(startIndex, endIndex, ratio);
    }

    private Num getStoredValue(int index) {
        return values.get(index);
    }

    private static Num getIntermediateRatio(boolean isLongTrade, Num entryPrice, Num exitPrice) {
        if (isLongTrade) {
            return exitPrice.dividedBy(entryPrice);
        }
        return entryPrice.getNumFactory().numOf(2).minus(exitPrice.dividedBy(entryPrice));
    }
}
