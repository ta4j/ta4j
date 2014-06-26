/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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
package eu.verdelhan.ta4j.analysis.evaluators;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import java.util.List;

/**
 * A decision.
 * <p>
 * A decision is the result of an evaluation of several {@link Strategy strategies}.
 * It contains a reference to the best strategies over a {@link TimeSeries series}, according to an {@link AnalysisCriterion analysis criterion}.
 */
public class Decision {

    private AnalysisCriterion criterion;

    private Strategy strategy;

    private List<Trade> trades;

    private TimeSeries series;

    public Decision(Strategy bestStrategy, TimeSeries series, AnalysisCriterion criterion) {
        this(bestStrategy, series, criterion, series.run(bestStrategy));
    }

    public Decision(Strategy bestStrategy, TimeSeries series, AnalysisCriterion criterion, List<Trade> trades) {
        this.strategy = bestStrategy;
        this.series = series;
        this.criterion = criterion;
        this.trades = trades;
    }

    public double evaluateCriterion() {
        return criterion.calculate(series, trades);
    }

    public double evaluateCriterion(AnalysisCriterion otherCriterion) {
        return otherCriterion.calculate(series, trades);
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public Decision applyFor(TimeSeries otherSeries) {
        return new Decision(strategy, otherSeries, criterion);
    }

    public List<Trade> getTrades() {
        return trades;
    }

    @Override
    public String toString() {
        return String.format("[strategy %s, criterion %s, value %.3f]", strategy, criterion.getClass().getSimpleName(), evaluateCriterion());
    }
    
    public String getName() {
        return series + ": " + series.getPeriodName();
    }
    
    public String getFileName() {
        return this.getClass().getSimpleName() + series.getTick(series.getBegin()).getEndTime().toString("hhmmddMMyyyy");
    }

    @Override
    public int hashCode() {
        final int prime = 47;
        int result = 1;
        result = prime * result + ((criterion == null) ? 0 : criterion.hashCode());
        result = prime * result + ((series == null) ? 0 : series.hashCode());
        result = prime * result + ((strategy == null) ? 0 : strategy.hashCode());
        result = prime * result + ((trades == null) ? 0 : trades.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Decision other = (Decision) obj;
        if (criterion == null) {
            if (other.criterion != null) {
                return false;
            }
        } else if (!criterion.equals(other.criterion)) {
            return false;
        }
        if (series == null) {
            if (other.series != null) {
                return false;
            }
        } else if (!series.equals(other.series)) {
            return false;
        }
        if (strategy == null) {
            if (other.strategy != null) {
                return false;
            }
        } else if (!strategy.equals(other.strategy)) {
            return false;
        }
        if (trades == null) {
            if (other.trades != null) {
                return false;
            }
        } else if (!trades.equals(other.trades)) {
            return false;
        }
        return true;
    }
}
