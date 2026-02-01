/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import org.ta4j.core.Bar;
import org.ta4j.core.TradeView;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;

/**
 * A rule that waits for a number of {@link Bar bars} after a trade of a
 * specified type.
 *
 * <p>
 * Satisfied after a fixed number of bars have passed since the last trade.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 */
public class WaitForRule extends AbstractRule {

    /** The trade type to wait for. */
    private final TradeType tradeType;

    /** The number of bars to wait for. */
    private final int numberOfBars;

    /**
     * Constructor.
     *
     * @param tradeType    the trade type to wait for
     * @param numberOfBars the number of bars to wait for
     */
    public WaitForRule(TradeType tradeType, int numberOfBars) {
        this.tradeType = tradeType;
        this.numberOfBars = numberOfBars;
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        boolean satisfied = false;
        // No trading history, no need to wait
        if (tradingRecord != null) {
            TradeView lastTrade = tradingRecord.getLastTrade(tradeType);
            if (lastTrade != null) {
                int currentNumberOfBars = index - lastTrade.getIndex();
                satisfied = currentNumberOfBars >= numberOfBars;
            }
        }
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
