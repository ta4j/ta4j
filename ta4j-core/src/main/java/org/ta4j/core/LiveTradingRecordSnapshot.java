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
import java.util.List;

/**
 * Immutable snapshot of a live trading record.
 *
 * @param positions       closed positions
 * @param openPositions   open position lots
 * @param netOpenPosition aggregated open position
 * @param trades          recorded trades
 * @since 0.22.2
 */
public record LiveTradingRecordSnapshot(List<Position> positions, List<OpenPosition> openPositions,
        OpenPosition netOpenPosition, List<Trade> trades) implements Serializable {

    @Serial
    private static final long serialVersionUID = -7838090610321477417L;

    private static final Gson GSON = new Gson();

    public LiveTradingRecordSnapshot {
        positions = positions == null ? List.of() : List.copyOf(positions);
        openPositions = openPositions == null ? List.of() : List.copyOf(openPositions);
        trades = trades == null ? List.of() : List.copyOf(trades);
    }

    @Override
    public String toString() {
        return GSON.toJson(this);
    }
}
