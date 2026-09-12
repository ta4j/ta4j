/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.FuturesCashFlow;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Computes the realized account return of a native futures trading record.
 *
 * <p>
 * A financed native futures account is one investment, so its return is
 * {@code 1 + profit / initialCapital} instead of the product of the matched
 * position returns that spot aggregation uses. Partial closes therefore never
 * compound as separate investments.
 * </p>
 *
 * <p>
 * This helper mirrors the fee and funding aggregation of the core
 * {@code FuturesPositionAccounting} helper, which is not visible outside the
 * {@code org.ta4j.core} package: fees are the recorded allocated fill fees, and
 * funding is the credit-positive settlement amount of each funding event
 * accounted no later than the evaluated index. Variation margin is deliberately
 * absent here, because it is already inside the payoff of a closed position and
 * inside the realized profit of an open one.
 * </p>
 *
 * @since 0.25.1
 */
final class FuturesRecordReturnSupport {

    private FuturesRecordReturnSupport() {
    }

    /**
     * Returns whether {@code tradingRecord} records a native futures account.
     *
     * @param tradingRecord trading record, may be {@code null}
     * @return true when the record trades a native futures contract
     * @since 0.25.1
     */
    static boolean isFuturesRecord(TradingRecord tradingRecord) {
        return tradingRecord != null && tradingRecord.getFuturesContract() != null;
    }

    /**
     * Returns the realized total return of the futures account as of the record end
     * index.
     *
     * @param series        analysed series, supplies the evaluation index and
     *                      number factory
     * @param tradingRecord native futures trading record
     * @param gross         true for gross profit, i.e. realized net profit with the
     *                      executed fees and holding costs restored and signed
     *                      funding removed
     * @return total return including the base, i.e. {@code 1 + profit / capital}
     * @throws IllegalStateException when the record has no positive finite initial
     *                               capital
     * @since 0.25.1
     */
    static Num totalReturn(BarSeries series, TradingRecord tradingRecord, boolean gross) {
        NumFactory numFactory = series.numFactory();
        Num capital = requireCapital(numFactory, tradingRecord);
        int finalIndex = Math.max(tradingRecord.getEndIndex(series), lastExecutedIndex(tradingRecord));
        Num profit = realizedProfit(numFactory, tradingRecord, finalIndex);
        if (gross) {
            profit = profit.plus(executedFees(numFactory, tradingRecord, finalIndex))
                    .minus(funding(numFactory, tradingRecord, finalIndex))
                    .plus(holdingCosts(numFactory, tradingRecord, finalIndex));
        }
        return numFactory.one().plus(profit.dividedBy(capital));
    }

    private static int lastExecutedIndex(TradingRecord tradingRecord) {
        int lastIndex = -1;
        for (Trade trade : tradingRecord.getTrades()) {
            for (TradeFill fill : Trade.executionFillsOf(trade)) {
                if (fill.index() >= 0) {
                    lastIndex = Math.max(lastIndex, fill.index());
                }
            }
        }
        return lastIndex;
    }

    private static Num requireCapital(NumFactory numFactory, TradingRecord tradingRecord) {
        Num capital = tradingRecord.getInitialCapital();
        if (capital == null) {
            throw new IllegalStateException(
                    "native futures record return requires an explicit initial capital; configure the trading record initial capital");
        }
        Num converted = toNum(numFactory, capital);
        if (!converted.isPositive() || !Num.isFinite(converted)) {
            throw new IllegalStateException("native futures record return requires positive finite initial capital");
        }
        return converted;
    }

    private static Num realizedProfit(NumFactory numFactory, TradingRecord tradingRecord, int finalIndex) {
        Num total = numFactory.zero();
        for (Position position : positions(tradingRecord, finalIndex)) {
            total = total.plus(toNum(numFactory, position.getRealizedProfit(finalIndex)));
        }
        return total;
    }

    private static Num executedFees(NumFactory numFactory, TradingRecord tradingRecord, int finalIndex) {
        Num total = numFactory.zero();
        for (Position position : positions(tradingRecord, finalIndex)) {
            total = total.plus(executedFees(numFactory, position, finalIndex));
        }
        return total;
    }

    private static Num holdingCosts(NumFactory numFactory, TradingRecord tradingRecord, int finalIndex) {
        Num total = numFactory.zero();
        for (Position position : positions(tradingRecord, finalIndex)) {
            total = total.plus(toNum(numFactory, position.getHoldingCost(finalIndex)));
        }
        return total;
    }

    private static Num executedFees(NumFactory numFactory, Position position, int finalIndex) {
        Num total = numFactory.zero();
        Trade entry = position.getEntry();
        if (entry != null) {
            total = total.plus(fillFees(numFactory, entry, finalIndex));
        }
        Trade exit = position.getExit();
        if (exit != null) {
            total = total.plus(fillFees(numFactory, exit, finalIndex));
        }
        return total;
    }

    private static Num fillFees(NumFactory numFactory, Trade trade, int finalIndex) {
        Num total = numFactory.zero();
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= finalIndex) {
                total = total.plus(toNum(numFactory, fill.fee()));
            }
        }
        return total;
    }

    private static Num funding(NumFactory numFactory, TradingRecord tradingRecord, int finalIndex) {
        Num total = numFactory.zero();
        for (Position position : positions(tradingRecord, finalIndex)) {
            for (FuturesCashFlow cashFlow : position.getCashFlows()) {
                if (cashFlow.type() != FuturesCashFlow.Type.FUNDING || cashFlow.index() > finalIndex) {
                    continue;
                }
                Num amount = cashFlow.settlementAmount() == null ? cashFlow.amount() : cashFlow.settlementAmount();
                total = total.plus(toNum(numFactory, amount));
            }
        }
        return total;
    }

    private static List<Position> positions(TradingRecord tradingRecord, int finalIndex) {
        List<Position> positions = new ArrayList<>();
        for (Position position : tradingRecord.getPositions()) {
            addPosition(positions, position, finalIndex);
        }
        List<Position> openPositions = tradingRecord.getOpenPositions();
        if (openPositions.isEmpty()) {
            addPosition(positions, tradingRecord.getCurrentPosition(), finalIndex);
            return positions;
        }
        for (Position position : openPositions) {
            addPosition(positions, position, finalIndex);
        }
        return positions;
    }

    private static boolean hasExecutedEntryFill(Position position, int finalIndex) {
        for (TradeFill fill : Trade.executionFillsOf(position.getEntry())) {
            if (fill.index() >= 0 && fill.index() <= finalIndex) {
                return true;
            }
        }
        return false;
    }

    private static void addPosition(List<Position> positions, Position position, int finalIndex) {
        if (position == null || position.getEntry() == null || !hasExecutedEntryFill(position, finalIndex)) {
            return;
        }
        positions.add(position);
    }

    private static Num toNum(NumFactory numFactory, Num value) {
        if (value == null || value.isNaN()) {
            return NaN.NaN;
        }
        return numFactory.numOf(value.getDelegate());
    }
}
