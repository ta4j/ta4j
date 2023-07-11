/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.analysis.cost;

import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

/**
 * With the {@code CostModel}, we can include trading costs that may be incurred
 * when opening or closing a position.
 */
public interface CostModel {

    /**
     * @param position   the position
     * @param finalIndex the index up to which open positions are considered
     * @return the trading cost of the single {@code position}
     */
    Num calculate(Position position, int finalIndex);

    /**
     * @param position the position
     * @return the trading cost of the single {@code position}
     */
    Num calculate(Position position);

    /**
     * @param price  the trade price per asset
     * @param amount the trade amount (i.e. the number of traded assets)
     * @return the trading cost for the traded {@code amount}
     */
    Num calculate(Num price, Num amount);

    /**
     * Evaluates if two models are equal.
     *
     * @param otherModel
     * @return true if {@code this} and {@code otherModel} are equal
     */
    boolean equals(CostModel otherModel);
}