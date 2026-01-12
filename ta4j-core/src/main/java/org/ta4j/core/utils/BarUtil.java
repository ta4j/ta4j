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
package org.ta4j.core.utils;

import org.ta4j.core.Bar;
import org.ta4j.core.num.NumFactory;

/**
 * Common utilities and helper methods for {@link Bar} instances.
 *
 * @since 0.22.1
 */
public final class BarUtil {

    private BarUtil() {
    }

    /**
     * Selects a {@link NumFactory} by choosing the first available price on the
     * bar. Preference order: open, close, high, low.
     *
     * @param bar the bar to inspect for available prices
     * @return the selected {@link NumFactory}
     * @throws IllegalArgumentException if no price fields are available
     */
    public static NumFactory selectNumFactoryForBar(Bar bar) {
        var open = bar.getOpenPrice();
        if (open != null) {
            return open.getNumFactory();
        }
        var close = bar.getClosePrice();
        if (close != null) {
            return close.getNumFactory();
        }
        var high = bar.getHighPrice();
        if (high != null) {
            return high.getNumFactory();
        }
        var low = bar.getLowPrice();
        if (low != null) {
            return low.getNumFactory();
        }
        throw new IllegalArgumentException("Cannot select a NumFactory: no price fields are available on the bar.");
    }
}