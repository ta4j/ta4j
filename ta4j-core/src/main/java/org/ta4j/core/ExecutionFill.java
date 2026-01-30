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
package org.ta4j.core;

import com.google.gson.Gson;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import org.ta4j.core.num.Num;

/**
 * Execution fill for live trading record updates.
 *
 * @param time          execution timestamp (UTC)
 * @param price         execution price per asset
 * @param amount        execution amount
 * @param fee           execution fee (nullable, defaults to zero)
 * @param side          execution side (BUY/SELL)
 * @param orderId       optional order identifier
 * @param correlationId optional correlation identifier
 * @since 0.22.2
 */
public record ExecutionFill(Instant time, Num price, Num amount, Num fee, ExecutionSide side, String orderId,
        String correlationId) implements Serializable {

    @Serial
    private static final long serialVersionUID = 3196554864123769210L;

    private static final Gson GSON = new Gson();

    public ExecutionFill {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(side, "side");
        if (fee == null) {
            fee = price.getNumFactory().zero();
        }
    }

    /**
     * @return true when the fill has a non-zero fee
     * @since 0.22.2
     */
    public boolean hasFee() {
        return !fee.isZero();
    }

    @Override
    public String toString() {
        return GSON.toJson(this);
    }
}
