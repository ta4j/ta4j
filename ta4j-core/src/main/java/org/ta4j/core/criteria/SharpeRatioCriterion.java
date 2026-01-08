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
package org.ta4j.core.criteria;

import java.time.Duration;
import java.util.stream.IntStream;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SharpeRatioCriterion extends AbstractAnalysisCriterion {

    private static final double SECONDS_PER_YEAR = 365.2425d * 24 * 3600;
    private final Num riskFreeRate;

    public SharpeRatioCriterion(Num riskFreeRateValue) {
        this.riskFreeRate = riskFreeRateValue;
    }

    public SharpeRatioCriterion() {
        this.riskFreeRate = DecimalNumFactory.getInstance().zero();
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var numFactory = series.numFactory();
        if (position == null || !position.isClosed()) {
            return numFactory.zero();
        }

        var cashFlow = new CashFlow(series, position);
        var start = Math.max(position.getEntry().getIndex() + 1, series.getBeginIndex() + 1);
        var end = Math.min(position.getExit().getIndex(), series.getEndIndex());
        if (end - start + 1 < 2) {
            return numFactory.zero();
        }

        var stats = IntStream.rangeClosed(start, end)
                .mapToObj(i -> excessReturn(series, cashFlow, i))
                .reduce(Stats.empty(numFactory.zero()), (acc, x) -> acc.add(x, numFactory),
                        (a, b) -> a.merge(b, numFactory));

        if (stats.count() < 2) {
            return numFactory.zero();
        }
        var stdev = stats.sampleVariance(numFactory).sqrt();
        return stdev.isZero() ? numFactory.zero() : stats.mean().dividedBy(stdev);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();
        var closedPositions = tradingRecord.getPositions().stream().filter(Position::isClosed).toList();
        if (closedPositions.isEmpty()) {
            return zero;
        }

        var cashFlow = new CashFlow(series, tradingRecord);
        var start = series.getBeginIndex() + 1;
        var end = series.getEndIndex();
        if (end - start + 1 < 2) {
            return zero;
        }

        var stats = IntStream.rangeClosed(start, end)
                .mapToObj(i -> excessReturn(series, cashFlow, i))
                .reduce(Stats.empty(zero), (acc, x) -> acc.add(x, numFactory), (a, b) -> a.merge(b, numFactory));

        if (stats.count() < 2) {
            return zero;
        }
        var stdev = stats.sampleVariance(numFactory).sqrt();
        return stdev.isZero() ? zero : stats.mean().dividedBy(stdev);
    }

    private Num excessReturn(BarSeries series, CashFlow cashFlow, int i) {
        var numFactory = series.numFactory();
        var one = numFactory.one();
        var gross = cashFlow.getValue(i).dividedBy(cashFlow.getValue(i - 1)).minus(one);
        var rf = periodRiskFree(series, i);
        return gross.minus(rf);
    }

    private Num periodRiskFree(BarSeries series, int i) {
        var numFactory = series.numFactory();
        var endNow = series.getBar(i).getEndTime();
        var endPrev = series.getBar(i - 1).getEndTime();
        var seconds = Math.max(0, Duration.between(endPrev, endNow).getSeconds());
        if (seconds == 0) {
            return numFactory.zero();
        }
        var seconds = Duration.between(endPrev, endNow).toNanos() / 1_000_000_000.0;
        Num deltaYearsNum = numFactory.numOf(seconds).dividedBy(numFactory.numOf(SECONDS_PER_YEAR));
        Num per = riskFreeRate.plus(numFactory.one()).pow(deltaYearsNum).minus(numFactory.one());
        return per;
        var annual = riskFreeRate.doubleValue();
        var per = Math.pow(1.0 + annual, deltaYears) - 1.0;
        return numFactory.numOf(per);
    }

    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }

    record Stats(Num mean, Num m2, int count) {
        static Stats empty(Num zero) {
            return new Stats(zero, zero, 0);
        }

        Stats add(Num x, NumFactory f) {
            if (count == 0) {
                return new Stats(x, f.zero(), 1);
            }
            var n = count + 1;
            var nNum = f.numOf(n);
            var delta = x.minus(mean);
            var meanNext = mean.plus(delta.dividedBy(nNum));
            var delta2 = x.minus(meanNext);
            var m2Next = m2.plus(delta.multipliedBy(delta2));
            return new Stats(meanNext, m2Next, n);
        }

        Stats merge(Stats o, NumFactory f) {
            if (o.count == 0) {
                return this;
            }
            if (count == 0) {
                return o;
            }
            var n1 = count;
            var n2 = o.count;
            var n = n1 + n2;
            var n1Num = f.numOf(n1);
            var n2Num = f.numOf(n2);
            var nNum = f.numOf(n);
            var delta = o.mean.minus(mean);
            var meanNext = mean.plus(delta.multipliedBy(n2Num).dividedBy(nNum));
            var m2Next = m2.plus(o.m2)
                    .plus(delta.multipliedBy(delta).multipliedBy(n1Num).multipliedBy(n2Num).dividedBy(nNum));
            return new Stats(meanNext, m2Next, n);
        }

        Num sampleVariance(NumFactory f) {
            if (count < 2) {
                return f.zero();
            }
            return m2.dividedBy(f.numOf(count - 1));
        }
    }

}
