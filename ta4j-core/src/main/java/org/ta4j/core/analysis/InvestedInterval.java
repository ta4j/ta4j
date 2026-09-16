/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.List;
import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Indicates whether each bar interval is part of an invested position.
 *
 * <p>
 * The indicator marks index {@code i} as invested when the interval between
 * {@code i - 1} and {@code i} belongs to a position in the provided trading
 * record.
 *
 * @since 0.22.2
 */
public class InvestedInterval extends CachedIndicator<Boolean> {

    private final boolean[] investedIntervals;

    /**
     * Creates an indicator that reports invested intervals for the trading record.
     *
     * @param series        the bar series backing the indicator
     * @param tradingRecord the trading record used to detect invested intervals
     * @since 0.22.2
     */
    public InvestedInterval(BarSeries series, TradingRecord tradingRecord) {
        this(series, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Creates an indicator that reports invested intervals for the trading record.
     *
     * @param series               the bar series backing the indicator
     * @param tradingRecord        the trading record used to detect invested
     *                             intervals
     * @param openPositionHandling how open positions should be handled
     * @since 0.22.2
     */
    public InvestedInterval(BarSeries series, TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        super(series);
        Objects.requireNonNull(series, "series cannot be null");
        Objects.requireNonNull(tradingRecord, "tradingRecord cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");
        investedIntervals = buildInvestedIntervals(tradingRecord, openPositionHandling);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < 0 || index >= investedIntervals.length) {
            return Boolean.FALSE;
        }
        return investedIntervals[index];
    }

    private boolean[] buildInvestedIntervals(TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        BarSeries series = getBarSeries();
        int size = Math.max(series.getEndIndex() + 1, 0);
        boolean[] invested = new boolean[size];
        tradingRecord.getPositions()
                .forEach(position -> markInvestedIntervals(position, invested, openPositionHandling));
        if (openPositionHandling == OpenPositionHandling.MARK_TO_MARKET) {
            List<Position> openPositions = AnalysisPositionSupport.openPositions(tradingRecord, series.getEndIndex());
            openPositions.forEach(position -> markInvestedIntervals(position, invested, openPositionHandling));
        }
        return invested;
    }

    private void markInvestedIntervals(Position position, boolean[] invested,
            OpenPositionHandling openPositionHandling) {
        BarSeries series = getBarSeries();
        if (position == null || position.getEntry() == null) {
            return;
        }
        int finalIndex = series.getEndIndex();
        int entryIndex = firstExecutedFillIndex(position.getEntry(), finalIndex);
        if (entryIndex < 0) {
            return;
        }
        int exitIndex;
        if (position.isClosed()) {
            exitIndex = lastExecutedFillIndex(position.getExit(), finalIndex);
            if (exitIndex < 0 && openPositionHandling != OpenPositionHandling.MARK_TO_MARKET) {
                return;
            }
        } else {
            exitIndex = finalIndex;
        }
        if (openPositionHandling == OpenPositionHandling.MARK_TO_MARKET && hasResidualExposure(position, finalIndex)) {
            exitIndex = finalIndex;
        }
        int start = Math.max(entryIndex + 1, series.getBeginIndex() + 1);
        int end = Math.min(exitIndex, invested.length - 1);
        for (int i = start; i <= end; i++) {
            invested[i] = true;
        }
    }

    private static boolean hasResidualExposure(Position position, int finalIndex) {
        if (position.getEntry() == null || position.getEntry().getIndex() > finalIndex) {
            return false;
        }
        Trade entry = position.getEntry();
        Num entryAmount = executedAmount(entry, finalIndex);
        if (!entryAmount.isPositive()) {
            return false;
        }
        Trade exit = position.getExit();
        return exit == null || entryAmount.isGreaterThan(executedAmount(exit, finalIndex));
    }

    private static Num executedAmount(Trade trade, int finalIndex) {
        NumFactory numFactory = trade.getAmount().getNumFactory();
        Num amount = numFactory.zero();
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= finalIndex) {
                amount = amount.plus(numFactory.numOf(fill.amount().getDelegate()));
            }
        }
        return amount;
    }

    private static int firstExecutedFillIndex(Trade trade, int finalIndex) {
        int firstIndex = Integer.MAX_VALUE;
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= finalIndex) {
                firstIndex = Math.min(firstIndex, fill.index());
            }
        }
        return firstIndex == Integer.MAX_VALUE ? -1 : firstIndex;
    }

    private static int lastExecutedFillIndex(Trade trade, int finalIndex) {
        int lastIndex = -1;
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= finalIndex) {
                lastIndex = Math.max(lastIndex, fill.index());
            }
        }
        return lastIndex;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
