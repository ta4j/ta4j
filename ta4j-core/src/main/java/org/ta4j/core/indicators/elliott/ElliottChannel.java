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
package org.ta4j.core.indicators.elliott;

import org.ta4j.core.indicators.PriceChannel;
import org.ta4j.core.num.Num;

/**
 * Encapsulates a projected Elliott price channel for the current bar.
 *
 * <p>
 * Elliott channels are parallel trend lines that help validate wave structure
 * by projecting expected support and resistance levels. The channel is
 * calculated from the most recent two swing highs and lows, with the median
 * representing the arithmetic midline between the boundaries.
 *
 * <p>
 * This record implements {@link org.ta4j.core.indicators.PriceChannel} and
 * provides all standard channel operations including validity checks, width
 * calculation, and price containment tests.
 *
 * @param upper  expected resistance boundary
 * @param lower  expected support boundary
 * @param median arithmetic midline between upper and lower bounds
 * @see org.ta4j.core.indicators.elliott.ElliottChannelIndicator
 * @see org.ta4j.core.indicators.PriceChannel
 * @since 0.22.0
 */
public record ElliottChannel(Num upper, Num lower, Num median) implements PriceChannel {
}
