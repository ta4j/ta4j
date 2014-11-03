/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j.analysis;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TATestsUtils;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.analysis.criteria.AverageProfitCriterion;
import eu.verdelhan.ta4j.analysis.criteria.TotalProfitCriterion;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import static org.junit.Assert.*;
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

        Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null,
                new Operation(2, OperationType.BUY), null };

        Operation[] sell = new Operation[] { null, new Operation(1, OperationType.SELL), null,
                new Operation(3, OperationType.SELL) };

        Strategy fakeStrategy = new MockStrategy(buy, sell);
 
        Decision decision = new Decision(fakeStrategy, series, criterion);
        assertEquals(45d / 21, TATestsUtils.TA_OFFSET, decision.evaluateCriterion());
    }

    @Test
    public void evaluateCriterionNotSelling() {
        series = new MockTimeSeries(3d, 1d, 7d, 9d);
        
        Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null,
                new Operation(2, OperationType.BUY), null };

        Operation[] sell = new Operation[] { null, null, null, null };

        Strategy fakeStrategy = new MockStrategy(buy, sell);
        Decision decision = new Decision(fakeStrategy, series, criterion);
        assertEquals(1d, decision.evaluateCriterion(), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void evaluateCriterionWithAnotherCriteria() {
        series = new MockTimeSeries(3d, 1d, 7d, 9d);
        
        Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null, null, null };

        Operation[] sell = new Operation[] { null, null, null, new Operation(3, OperationType.SELL) };

        Strategy fakeStrategy = new MockStrategy(buy, sell);
        Decision decision = new Decision(fakeStrategy, series, null);
        assertEquals(Math.pow(3d, 1d / 4), decision.evaluateCriterion(new AverageProfitCriterion()), TATestsUtils.TA_OFFSET);
    }
    
    @Test
    public void averageProfitWithZeroNumberOfTicks() {
        series = new MockTimeSeries(3d, 1d, 7d, 9d);
        
        Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null,
                new Operation(2, OperationType.BUY), null };

        Operation[] sell = new Operation[] { null, null, null, null };

        Strategy fakeStrategy = new MockStrategy(buy, sell);
        Decision decision = new Decision(fakeStrategy, series, null);
        assertEquals(1d, decision.evaluateCriterion(new AverageProfitCriterion()), TATestsUtils.TA_OFFSET);
    }

    @Test
    public void applyFor() {
        final DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd");
        series = new MockTimeSeries(
                new double[] { 1d, 2d, 3d, 4d, 5d, 5d, 5d, 5d, 5d, 5d },
                new DateTime[] {
                    dtf.parseDateTime("2013-12-27"),
                    dtf.parseDateTime("2013-12-28"),
                    dtf.parseDateTime("2013-12-29"),
                    dtf.parseDateTime("2013-12-30"),
                    dtf.parseDateTime("2013-12-31"),
                    dtf.parseDateTime("2014-01-04"),
                    dtf.parseDateTime("2014-01-05"),
                    dtf.parseDateTime("2014-01-06"),
                    dtf.parseDateTime("2014-01-07"),
                    dtf.parseDateTime("2014-01-08")
                });
        List<TimeSeries> subseries = series.split(Period.weeks(1));

        Operation[] buy = new Operation[] { new Operation(0, OperationType.BUY), null, null, null, null, null, null, null, null,null };
        Operation[] sell = new Operation[] { null, null, null, null, new Operation(4, OperationType.SELL), null, null, null, null,null };
        Strategy fakeStrategy = new MockStrategy(buy, sell);
        Decision decision = new Decision(fakeStrategy, subseries.get(0), criterion);
        Decision nextDecision = new Decision(fakeStrategy, subseries.get(1), criterion);

        Decision appliedDecision = decision.applyFor(subseries.get(1));

        assertEquals(nextDecision, appliedDecision);
        assertEquals(1d, appliedDecision.evaluateCriterion(), TATestsUtils.TA_OFFSET);
    }
}
