/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.IndicatorFamilyResult;
import org.ta4j.core.num.Num;

public class IndicatorFamilyAnalysisDemoTest {

    @Test
    public void analyzesOssifiedSp500IndicatorsAcrossThresholds() {
        BarSeries series = IndicatorFamilyAnalysisDemo.loadSeries();
        Map<String, Indicator<Num>> indicators = IndicatorFamilyAnalysisDemo.buildIndicators(series);

        List<IndicatorFamilyAnalysisDemo.AnalysisPass> passes = IndicatorFamilyAnalysisDemo.analyze(series, indicators);

        assertTrue(series.getBarCount() >= 700, "S&P500 weekly fixture should cover a long history");
        assertTrue(indicators.size() >= 20 && indicators.size() <= 30, "demo should use 20-30 indicators");
        assertEquals(3, passes.size(), "demo should run three threshold passes");
        assertEquals(0.80, passes.get(0).threshold());
        assertEquals(0.90, passes.get(1).threshold());
        assertEquals(0.97, passes.get(2).threshold());
        for (IndicatorFamilyAnalysisDemo.AnalysisPass pass : passes) {
            IndicatorFamilyResult result = pass.result();
            assertFalse(result.families().isEmpty(), "families should not be empty");
            assertEquals(indicators.size(), result.familyByIndicator().size(), "all indicators should be mapped");
            assertEquals((indicators.size() * (indicators.size() - 1)) / 2, result.pairSimilarities().size(),
                    "all indicator pairs should be scored");
            assertTrue(result.stableIndex() >= series.getBeginIndex() && result.stableIndex() <= series.getEndIndex(),
                    "stable index should fall inside the long fixture");
            assertTrue(IndicatorFamilyAnalysisDemo.describe(pass).contains("Interpretation:"),
                    "description should explain how to read the result");
        }
        int looseFamilyCount = passes.get(0).result().families().size();
        int strictFamilyCount = passes.get(2).result().families().size();
        long distinctFamilyCounts = passes.stream().map(pass -> pass.result().families().size()).distinct().count();
        assertTrue(looseFamilyCount < strictFamilyCount, "strict threshold should split more families");
        assertTrue(distinctFamilyCounts > 1, "threshold passes should produce different family counts");
    }
}
