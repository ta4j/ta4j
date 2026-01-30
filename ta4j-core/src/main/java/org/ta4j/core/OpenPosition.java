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
import java.util.List;
import org.ta4j.core.num.Num;

/**
 * Snapshot of an open position (net or per-lot).
 *
 * @param side              open side
 * @param amount            open amount
 * @param averageEntryPrice weighted average entry price
 * @param totalEntryCost    total entry cost (price * amount)
 * @param totalFees         total fees remaining
 * @param earliestEntryTime earliest entry time across lots
 * @param latestEntryTime   latest entry time across lots
 * @param lots              underlying lots
 * @since 0.22.2
 */
public record OpenPosition(ExecutionSide side, Num amount, Num averageEntryPrice, Num totalEntryCost, Num totalFees,
        Instant earliestEntryTime, Instant latestEntryTime, List<PositionLot> lots) implements Serializable {

    @Serial
    private static final long serialVersionUID = -6647261448915930675L;

    private static final Gson GSON = new Gson();

    public OpenPosition {
        lots = lots == null ? List.of() : List.copyOf(lots);
    }

    @Override
    public String toString() {
        return GSON.toJson(this);
    }
}
