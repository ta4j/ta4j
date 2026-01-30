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
package org.ta4j.core.criteria.drawdown;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.Trade;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.criteria.Statistics;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class MonteCarloMaximumDrawdownCriterionTest extends AbstractCriterionTest {

    public MonteCarloMaximumDrawdownCriterionTest(NumFactory numFactory) {
        super(params -> new MonteCarloMaximumDrawdownCriterion(), numFactory);
    }

    @Test
    public void calculateWithOnlyGains() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));
        var criterion = new MonteCarloMaximumDrawdownCriterion(200, null, 123L, Statistics.P95);
        assertNumEquals(0d, criterion.calculate(series, record));
    }

    @Test
    public void reproducibility() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 2, 4, 5, 6, 5)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(3, series),
                Trade.sellAt(4, series), Trade.buyAt(5, series), Trade.sellAt(7, series), Trade.buyAt(8, series),
                Trade.sellAt(9, series));
        var criterion1 = new MonteCarloMaximumDrawdownCriterion(100, null, 42L, Statistics.P95);
        var value1 = criterion1.calculate(series, record);
        var criterion2 = new MonteCarloMaximumDrawdownCriterion(100, null, 42L, Statistics.P95);
        var value2 = criterion2.calculate(series, record);
        assertNumEquals(value1, value2);
    }

    @Test
    public void differentMetrics() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 2, 4, 5, 6, 5)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series), Trade.buyAt(3, series),
                Trade.sellAt(4, series), Trade.buyAt(5, series), Trade.sellAt(7, series), Trade.buyAt(8, series),
                Trade.sellAt(9, series));
        var medianCriterion = new MonteCarloMaximumDrawdownCriterion(100, null, 42L, Statistics.MEDIAN);
        var maxCriterion = new MonteCarloMaximumDrawdownCriterion(100, null, 42L, Statistics.MAX);
        var median = medianCriterion.calculate(series, record);
        var max = maxCriterion.calculate(series, record);
        // max drawdown should be at least as large as median drawdown
        Assert.assertTrue(max.isGreaterThanOrEqual(median));
    }

    @Test
    public void fallbackToHistoricalMaximumDrawdown() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 2, 1).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series));
        var monteCarlo = new MonteCarloMaximumDrawdownCriterion(100, null, 7L, Statistics.P95);
        var result = monteCarlo.calculate(series, record);
        var expected = new MaximumDrawdownCriterion().calculate(series, record);
        assertNumEquals(expected, result);
    }

    @Test
    public void usesInjectedRandomGenerator() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));
        var counter = new AtomicInteger();
        class CountingRandom implements RandomGenerator {
            @Override
            public int nextInt() {
                counter.incrementAndGet();
                return 0;
            }

            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public boolean nextBoolean() {
                return false;
            }

            @Override
            public float nextFloat() {
                return 0f;
            }

            @Override
            public double nextDouble() {
                return 0d;
            }
        }
        var criterion = new MonteCarloMaximumDrawdownCriterion(1, 2, CountingRandom::new, Statistics.P95);
        criterion.calculate(series, record);
        assertEquals(2, counter.get());
    }

}
