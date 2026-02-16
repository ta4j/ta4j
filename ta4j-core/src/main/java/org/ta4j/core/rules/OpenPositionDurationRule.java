/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;

/**
 * A rule that checks whether the current open position has been held for at
 * least a minimum number of bars.
 *
 * <p>
 * This rule uses the {@code tradingRecord}.
 *
 * @since 0.22.2
 */
public class OpenPositionDurationRule extends AbstractRule {

    private final Indicator<Num> minBarsIndicator;

    /**
     * Constructor.
     *
     * @param minBarsIndicator indicator providing the minimum number of bars
     */
    public OpenPositionDurationRule(final Indicator<Num> minBarsIndicator) {
        this.minBarsIndicator = Objects.requireNonNull(minBarsIndicator, "minBarsIndicator");
    }

    /**
     * Constructor.
     *
     * @param indicator indicator providing the bar series context
     * @param minBars   minimum number of bars to hold the position
     */
    public OpenPositionDurationRule(final Indicator<?> indicator, final Number minBars) {
        this(new ConstantIndicator<>(indicator.getBarSeries(), indicator.getBarSeries().numFactory().numOf(minBars)));
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        if (tradingRecord == null || tradingRecord.isClosed()) {
            traceIsSatisfied(index, false);
            return false;
        }
        Position position = tradingRecord.getCurrentPosition();
        Trade entry = position == null ? null : position.getEntry();
        if (entry == null) {
            traceIsSatisfied(index, false);
            return false;
        }
        int barsOpen = index - entry.getIndex();
        if (barsOpen <= 0) {
            traceIsSatisfied(index, false);
            return false;
        }
        Num minBars = minBarsIndicator.getValue(index);
        if (Num.isNaNOrNull(minBars)) {
            traceIsSatisfied(index, false);
            return false;
        }
        Num barsOpenNum = minBars.getNumFactory().numOf(barsOpen);
        boolean satisfied = barsOpenNum.isGreaterThanOrEqual(minBars);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
