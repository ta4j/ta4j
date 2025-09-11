/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.analysis;

import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

/**
 * Utility functions to support performance and risk analysis in ta4j.
 * <p>
 * These methods are used internally by indicators and criteria such as
 * cumulative PnL or drawdown calculations, ensuring consistent handling of
 * position boundaries and transaction costs.
 * </p>
 *
 * @since 0.19
 */
public class AnalysisUtils {

    /**
     * Determines the valid final index to be considered.
     *
     * @param position   the position
     * @param finalIndex index up until cash flows of open positions are considered
     * @param maxIndex   maximal valid index
     */
    static int determineEndIndex(Position position, int finalIndex, int maxIndex) {
        var idx = finalIndex;
        // After closing of position, no further accrual necessary
        if (position.getExit() != null) {
            idx = Math.min(position.getExit().getIndex(), finalIndex);
        }
        // Accrual at most until maximal index of asset data
        if (idx > maxIndex) {
            idx = maxIndex;
        }
        return idx;
    }

    /**
     * Adjusts (intermediate) price to incorporate trading costs.
     *
     * @param rawPrice    the gross asset price
     * @param holdingCost share of the holding cost per period
     * @param isLongTrade true, if the entry trade type is BUY
     */
    static Num addCost(Num rawPrice, Num holdingCost, boolean isLongTrade) {
        Num netPrice;
        if (isLongTrade) {
            netPrice = rawPrice.minus(holdingCost);
        } else {
            netPrice = rawPrice.plus(holdingCost);
        }
        return netPrice;
    }

}
