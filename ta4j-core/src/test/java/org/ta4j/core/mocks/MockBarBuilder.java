/**
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
package org.ta4j.core.mocks;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.BarSeries;
import org.ta4j.core.backtest.BacktestBar;
import org.ta4j.core.backtest.BacktestBarBuilder;

/**
 * @author Lukáš Kvídera
 */
public class MockBarBuilder extends BacktestBarBuilder {

    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(0), ZoneId.systemDefault());
    private boolean periodSet;
    private boolean endTimeSet;

    private static long countOfProducedBars;
    private Duration timePeriod;

    public MockBarBuilder(final BarSeries series) {
        super(series);
    }

    @Override
    public MockBarBuilder endTime(final ZonedDateTime endTime) {
      this.endTimeSet = true;
        super.endTime(endTime);
        return this;
    }

    @Override
    public MockBarBuilder timePeriod(final Duration timePeriod) {
      this.periodSet = true;
        this.timePeriod = timePeriod;
        super.timePeriod(this.timePeriod);
        return this;
    }

    @Override
    public BacktestBar build() {
        if (!this.periodSet) {
            timePeriod(Duration.ofDays(1));
        }

        if (!this.endTimeSet) {
            endTime(ZonedDateTime.now(Clock.offset(this.clock, this.timePeriod.multipliedBy(++countOfProducedBars))));
        }
        return super.build();
    }
}
