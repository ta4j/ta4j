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
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TATestsUtils;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.Runner;
import eu.verdelhan.ta4j.analysis.criteria.AverageProfitCriterion;
import eu.verdelhan.ta4j.analysis.criteria.TotalProfitCriterion;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.slicers.RegularSlicer;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class DecisionTest {

    private TimeSeries series;

    private AnalysisCriterion criterion;

    @Before
    public void setUp() {
        criterion = new TotalProfitCriterion();
    }

    @Test
    public void evaluateCriterion() {

        series = new MockTimeSeries(3d, 5d, 7d, 9d);
        TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));

        Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null,
                new Operation(2, OperationType.BUY), null };

        Operation[] sell = new Operation[] { null, new Operation(1, OperationType.SELL), null,
                new Operation(3, OperationType.SELL) };

        Strategy fakeStrategy = new MockStrategy(buy, sell);
        
        Runner runner = new Runner(slicer,fakeStrategy);
 
        Decision decision = new Decision(fakeStrategy, slicer,0, criterion, runner.run(0), new Runner(slicer,fakeStrategy));
        assertThat(decision.evaluateCriterion()).isEqualTo(45d / 21, TATestsUtils.LONG_OFFSET);
    }

    @Test
    public void evaluateCriterionNotSelling() {
        series = new MockTimeSeries(3d, 1d, 7d, 9d);
        TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
        
        Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null,
                new Operation(2, OperationType.BUY), null };

        Operation[] sell = new Operation[] { null, null, null, null };

        Strategy fakeStrategy = new MockStrategy(buy, sell);
        Runner runner = new Runner(slicer,fakeStrategy);
        Decision decision = new Decision(fakeStrategy, slicer,0, criterion, runner.run(0), new Runner(slicer,fakeStrategy));
        assertThat(decision.evaluateCriterion()).isEqualTo(1d);
    }

    @Test
    public void evaluateCriterionWithAnotherCriteria() {
        series = new MockTimeSeries(3d, 1d, 7d, 9d);
        TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
        
        Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null, null, null };

        Operation[] sell = new Operation[] { null, null, null, new Operation(3, OperationType.SELL) };

        Strategy fakeStrategy = new MockStrategy(buy, sell);
        Runner runner = new Runner(slicer,fakeStrategy);
        Decision decision = new Decision(fakeStrategy, slicer,0, null, runner.run(0), new Runner(slicer,fakeStrategy));
        assertThat(decision.evaluateCriterion(new AverageProfitCriterion())).isEqualTo(Math.pow(3d, 1d / 4));
    }
    
    @Test
    public void averageProfitWithZeroNumberOfTicks() {
        series = new MockTimeSeries(3d, 1d, 7d, 9d);
        TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
        
        Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null,
                new Operation(2, OperationType.BUY), null };

        Operation[] sell = new Operation[] { null, null, null, null };

        Strategy fakeStrategy = new MockStrategy(buy, sell);
        Runner runner = new Runner(slicer,fakeStrategy);
        Decision decision = new Decision(fakeStrategy, slicer,0, null, runner.run(0), new Runner(slicer,fakeStrategy));
        assertThat(decision.evaluateCriterion(new AverageProfitCriterion())).isEqualTo(1d);
    }

    @Test
    public void applyFor() {
        DateTime date = new DateTime();
        series = new MockTimeSeries(new double[] { 1d, 2d, 3d, 4d, 5d,5d, 5d, 5d, 5d, 5d},new DateTime[]{date.withYear(2000),date.withYear(2000),date.withYear(2000),date.withYear(2000),date.withYear(2000),date.withYear(2001),date.withYear(2001),date.withYear(2001),date.withYear(2001),date.withYear(2001),});
        TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));

        Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null, null, null, null, null, null, null, null,null };
        Operation[] sell = new Operation[] { null, null, null, null, new Operation(4, OperationType.SELL), null, null, null, null,null };
        Strategy fakeStrategy = new MockStrategy(buy, sell);
        Runner runner = new Runner(slicer, fakeStrategy);
        List<Trade> trades = runner.run(0);
        Decision decision = new Decision(fakeStrategy, slicer,0, criterion, trades, runner);
        Decision nextDecision = new Decision(fakeStrategy, slicer,1, criterion, runner.run(1), runner);

        Decision appliedDecision = decision.applyFor(1);

        assertThat(appliedDecision).isEqualTo(nextDecision);
        assertThat(appliedDecision.evaluateCriterion()).isEqualTo(1d);
        assertThat(appliedDecision.getActualSlice().getBegin()).isEqualTo(slicer.getSlice(1).getBegin());
        assertThat(appliedDecision.getActualSlice().getEnd()).isEqualTo(slicer.getSlice(1).getEnd());
    }
}
