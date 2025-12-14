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
/**
 * SuperTrend indicator and its components.
 *
 * <p>
 * SuperTrend is a popular trend-following indicator developed by Olivier Seban.
 * It uses ATR (Average True Range) to create dynamic support and resistance
 * bands that adapt to market volatility.
 *
 * <p>
 * The package contains:
 * <ul>
 * <li>{@link org.ta4j.core.indicators.supertrend.SuperTrendIndicator} - The
 * main indicator that provides the current SuperTrend value and trend direction
 * methods</li>
 * <li>{@link org.ta4j.core.indicators.supertrend.SuperTrendUpperBandIndicator}
 * - The dynamic resistance band (used in downtrends)</li>
 * <li>{@link org.ta4j.core.indicators.supertrend.SuperTrendLowerBandIndicator}
 * - The dynamic support band (used in uptrends)</li>
 * </ul>
 *
 * @see <a href="https://www.investopedia.com/supertrend-indicator-7976167">
 *      Investopedia: SuperTrend Indicator</a>
 */
package org.ta4j.core.indicators.supertrend;
