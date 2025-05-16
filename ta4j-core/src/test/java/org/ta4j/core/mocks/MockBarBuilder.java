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
package org.ta4j.core.mocks;

import java.time.Duration;
import java.time.Instant;

import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.num.NumFactory;

public class MockBarBuilder extends TimeBarBuilder {

    private final Instant beginTime = Instant.EPOCH;
    private boolean periodSet;
    private boolean endTimeSet;

    private static long countOfProducedBars;
    private Duration timePeriod;

    public MockBarBuilder(NumFactory numFactory) {
        super(numFactory);
    }

    @Override
    public BarBuilder endTime(final Instant endTime) {
        endTimeSet = true;
        return super.endTime(endTime);
    }

    @Override
    public BarBuilder timePeriod(final Duration timePeriod) {
        periodSet = true;
        this.timePeriod = timePeriod;
        return super.timePeriod(this.timePeriod);
    }

    @Override
    public Bar build() {
        if (!periodSet) {
            timePeriod(Duration.ofDays(1));
        }

        if (!endTimeSet) {
            endTime(beginTime.plus(timePeriod.multipliedBy(++countOfProducedBars)));
        }
        return super.build();
    }

}
