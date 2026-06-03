/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.research.ParameterResearch.ParameterResearchReport;
import org.ta4j.core.research.ParameterResearch.PruningPolicy;

public class SimpleMovingAverageRangeBacktestTest {

    @Test
    public void test() {
        SimpleMovingAverageRangeBacktest.main(null);
    }

    @Test
    public void smaResearchUsesMultiParameterCandidatesAndHoldoutValidation() {
        BarSeries series = buildSwingSeries(80);

        ParameterResearchReport report = SimpleMovingAverageRangeBacktest.runSmaResearch(series,
                PruningPolicy.EXACT_TRADING_RECORD, 20);

        assertEquals(60, report.generatedCandidateCount());
        assertEquals(42, report.validCandidateCount());
        assertEquals(18, report.invalidCandidateCount());
        assertFalse(report.trainingScores().isEmpty());
        assertFalse(report.validationScores().isEmpty());
        assertEquals(0, report.window().trainingStartIndex());
        assertEquals(59, report.window().trainingEndIndex());
        assertEquals(60, report.window().validationStartIndex());
        assertEquals(79, report.window().validationEndIndex());
    }

    @Test
    public void smaResearchNarrativeExplainsCandidateSpaceHoldoutAndTakeaway() {
        BarSeries series = buildSwingSeries(80);
        ParameterResearchReport report = SimpleMovingAverageRangeBacktest.runSmaResearch(series, PruningPolicy.NONE,
                20);

        String narrative = SimpleMovingAverageRangeBacktest.formatResearchNarrative(report, 2);

        assertTrue(narrative.contains("SMA trend parameter research"));
        assertTrue(narrative.contains("Candidate space:"));
        assertTrue(narrative.contains("Training top candidates:"));
        assertTrue(narrative.contains("Validation top candidates:"));
        assertTrue(narrative.contains("Takeaway:"));
        assertTrue(narrative.contains("NetProfitCriterion="));
        assertTrue(narrative.contains("ReturnOverMaxDrawdownCriterion="));
    }

    private static BarSeries buildSwingSeries(int size) {
        double[] prices = new double[size];
        for (int i = 0; i < size; i++) {
            prices[i] = 100 + (i * 0.15) + (Math.sin(i / 4.0) * 6.0) + (Math.cos(i / 11.0) * 2.0);
        }
        return new MockBarSeriesBuilder().withData(prices).build();
    }
}
