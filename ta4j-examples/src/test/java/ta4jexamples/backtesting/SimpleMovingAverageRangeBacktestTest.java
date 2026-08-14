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
import org.ta4j.core.research.ParameterResearch.TerminationReason;

public class SimpleMovingAverageRangeBacktestTest {

    @Test
    public void test() {
        SimpleMovingAverageRangeBacktest.main(null);
    }

    @Test
    public void smaResearchUsesMultiParameterCandidatesAndHoldoutValidation() {
        BarSeries series = buildSwingSeries(80);

        ParameterResearchReport report = SimpleMovingAverageRangeBacktest.runSmaResearch(series, 20);

        assertEquals(60, report.counts().proposed());
        assertEquals(18, report.counts().rejected());
        assertEquals(42, report.counts().attempted());
        assertEquals(42, report.counts().successful());
        assertEquals(TerminationReason.SEARCH_SPACE_EXHAUSTED, report.terminationReason());
        assertFalse(report.trainingLeaderboard().isEmpty());
        assertTrue(report.trainingLeaderboard().size() <= 3);
        assertFalse(report.holdoutLeaderboard().isEmpty());
        assertEquals(0, report.trainingWindow().startIndex());
        assertEquals(59, report.trainingWindow().endIndex());
        assertTrue(report.holdoutWindow().isPresent());
        assertEquals(60, report.holdoutWindow().orElseThrow().startIndex());
        assertEquals(79, report.holdoutWindow().orElseThrow().endIndex());
    }

    @Test
    public void smaResearchNarrativeExplainsCandidateSpaceHoldoutAndTakeaway() {
        BarSeries series = buildSwingSeries(80);
        ParameterResearchReport report = SimpleMovingAverageRangeBacktest.runSmaResearch(series, 20);

        String narrative = SimpleMovingAverageRangeBacktest.formatResearchNarrative(report, 2);

        assertTrue(narrative.contains("SMA trend parameter research"));
        assertTrue(narrative.contains("Candidate space:"));
        assertTrue(narrative.contains("Training top candidates:"));
        assertTrue(narrative.contains("Validation top candidates:"));
        assertTrue(narrative.contains("Takeaway:"));
        assertTrue(narrative.contains("Net Profit="));
        assertTrue(narrative.contains("Return Over Max Drawdown="));
    }

    private static BarSeries buildSwingSeries(int size) {
        double[] prices = new double[size];
        for (int i = 0; i < size; i++) {
            prices[i] = 100 + (i * 0.15) + (Math.sin(i / 4.0) * 6.0) + (Math.cos(i / 11.0) * 2.0);
        }
        return new MockBarSeriesBuilder().withData(prices).build();
    }
}
