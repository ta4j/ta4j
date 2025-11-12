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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.ta4j.core.*;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;

public final class SortinoRatioByBarCriterion extends AbstractAnalysisCriterion {

    private final Num targetReturn;

    public SortinoRatioByBarCriterion() {
        this.targetReturn = DecimalNumFactory.getInstance().zero();
    }

    public SortinoRatioByBarCriterion(Num targetReturn) {
        this.targetReturn = targetReturn;
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();
        var positions = tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .sorted(Comparator.comparingInt(p -> p.getEntry().getIndex()))
                .toList();

        if (series.getBarCount() < 2)
            return zero;

        var returns = buildReturns(series, positions);
        if (returns.isEmpty())
            return zero;

        var mean = average(returns, series);
        var downside = downsideDeviation(returns, series);

        if (downside.isZero())
            return zero;

        return mean.minus(targetReturn).dividedBy(downside);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var entry = position.getEntry();
        if (Objects.isNull(entry))
            return series.numFactory().zero();
        var exit = position.getExit();
        var trades = exit == null ? new Trade[] { entry } : new Trade[] { entry, exit };
        return calculate(series, new BaseTradingRecord(trades));
    }

    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isGreaterThan(v2);
    }

    private List<Num> buildReturns(BarSeries series, List<Position> positions) {
        var one = series.numFactory().one();
        var first = positions.isEmpty() ? null : positions.getFirst();

        return IntStream.range(1, series.getBarCount())
                .boxed()
                .reduce(new Pair(new Accum(0, first), new ArrayList<>()), (pair, i) -> {
                    var idx = pair.accum().posIndex();
                    var current = pair.accum().currentPos();
                    while (current != null && i > current.getExit().getIndex()) {
                        idx += 1;
                        current = idx < positions.size() ? positions.get(idx) : null;
                    }
                    var prev = series.getBar(i - 1).getClosePrice();
                    var now = series.getBar(i).getClosePrice();
                    var ratio = (current != null && i > current.getEntry().getIndex())
                            ? current.getEntry().isBuy() ? now.dividedBy(prev) : prev.dividedBy(now)
                            : one;
                    pair.returns().add(ratio.minus(one));
                    return new Pair(new Accum(idx, current), pair.returns());
                }, (l, r) -> {
                    l.returns().addAll(r.returns());
                    return new Pair(r.accum(), l.returns());
                })
                .returns();
    }

    private Num average(List<Num> data, BarSeries series) {
        var sum = data.stream().reduce(series.numFactory().zero(), Num::plus);
        return sum.dividedBy(series.numFactory().numOf(data.size()));
    }

    private Num downsideDeviation(List<Num> data, BarSeries series) {
        var numFactory = series.numFactory();
        var negatives = data.stream()
                .filter(r -> r.isLessThanOrEqual(targetReturn)) // include zeros
                .toList();
        if (negatives.isEmpty())
            return numFactory.zero();
        var arr = negatives.stream().mapToDouble(n -> targetReturn.minus(n).doubleValue()).toArray();
        return numFactory.numOf(new StandardDeviation(true).evaluate(arr));
    }

    record Accum(int posIndex, Position currentPos) {
    }

    record Pair(Accum accum, List<Num> returns) {
    }
}
