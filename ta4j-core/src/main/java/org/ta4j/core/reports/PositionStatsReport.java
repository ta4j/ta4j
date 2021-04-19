/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package org.ta4j.core.reports;

import org.ta4j.core.num.Num;

/**
 * This class represents report with statistics for positions.
 */
public class PositionStatsReport {

    private final Num profitCount;
    private final Num lossCount;
    private final Num breakEvenCount;

    /**
     * Constructor.
     *
     * @param profitCount    the number of positions making a profit
     * @param lossCount      the number of positions making a loss
     * @param breakEvenCount the number of positions with a break even
     */
    public PositionStatsReport(Num profitCount, Num lossCount, Num breakEvenCount) {
        this.profitCount = profitCount;
        this.lossCount = lossCount;
        this.breakEvenCount = breakEvenCount;
    }

    public Num getProfitCount() {
        return profitCount;
    }

    public Num getLossCount() {
        return lossCount;
    }

    public Num getBreakEvenCount() {
        return breakEvenCount;
    }
}
