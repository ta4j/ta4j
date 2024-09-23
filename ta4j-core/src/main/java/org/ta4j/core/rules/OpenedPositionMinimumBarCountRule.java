/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.rules;

import org.ta4j.core.TradingRecord;

/**
 * A rule for setting the minimum number of bars up to which an open position
 * should not be closed.
 *
 * <p>
 * Using this rule only makes sense for exit rules. For entry rules,
 * {@link OpenedPositionMinimumBarCountRule#isSatisfied(int, TradingRecord)}
 * always returns {@code false}.
 */
public class OpenedPositionMinimumBarCountRule extends AbstractRule {

    /**
     * The minimum number of bars up to which an open position should not be closed.
     */
    private final int barCount;

    /**
     * Constructor.
     *
     * @param barCount the {@link #barCount}
     */
    public OpenedPositionMinimumBarCountRule(int barCount) {
        if (barCount < 1) {
            throw new IllegalArgumentException("Bar count must be positive");
        }
        this.barCount = barCount;
    }

    /**
     * @param index         the bar index
     * @param tradingRecord the required trading history
     * @return true if opened trade has reached {@link #barCount}, otherwise false
     */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (tradingRecord.getCurrentPosition().isOpened()) {
            final int entryIndex = tradingRecord.getLastEntry().getIndex();
            final int currentBarCount = index - entryIndex;
            return currentBarCount >= barCount;
        }
        return false;
    }

    /** @return the {@link #barCount} */
    public int getBarCount() {
        return barCount;
    }
}
