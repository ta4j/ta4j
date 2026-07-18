/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ScenarioType;

class ElliottWaveAnalysisReportOwnershipTest {

    @Test
    void reportAndScenarioListsAreImmutableSnapshots() {
        ElliottWaveAnalysisReport.SwingData swing = new ElliottWaveAnalysisReport.SwingData(0, 1, 100d, 110d, true);
        List<ElliottWaveAnalysisReport.SwingData> baseSwings = new ArrayList<>(List.of(swing));
        List<ElliottWaveAnalysisReport.SwingData> alternativeSwings = new ArrayList<>(List.of(swing));
        ElliottWaveAnalysisReport.BaseCaseScenario baseCase = new ElliottWaveAnalysisReport.BaseCaseScenario(
                ElliottPhase.WAVE3, ScenarioType.IMPULSE, 80d, 0.6d, 0.55d, "HIGH", 75d, 70d, 65d, 60d, 55d,
                "fibonacci", "time", "BULLISH", 95d, 130d, baseSwings);
        ElliottWaveAnalysisReport.AlternativeScenario alternative = new ElliottWaveAnalysisReport.AlternativeScenario(
                ElliottPhase.CORRECTIVE_C, ScenarioType.CORRECTIVE_ZIGZAG, 40d, 0.4d, 0.45d, alternativeSwings);
        List<ElliottWaveAnalysisReport.AlternativeScenario> alternatives = new ArrayList<>(List.of(alternative));
        List<String> chartImages = new ArrayList<>(List.of("image"));

        ElliottWaveAnalysisReport report = new ElliottWaveAnalysisReport(ElliottDegree.MINOR, 1, null, null, null, null,
                null, null, baseCase, alternatives, "base-image", chartImages);
        baseSwings.clear();
        alternativeSwings.clear();
        alternatives.clear();
        chartImages.clear();

        assertEquals(1, report.baseCase().swings().size());
        assertEquals(1, alternative.swings().size());
        assertEquals(1, report.alternatives().size());
        assertEquals(1, report.alternativeChartImages().size());
        assertThrows(UnsupportedOperationException.class, () -> report.baseCase().swings().clear());
        assertThrows(UnsupportedOperationException.class, () -> alternative.swings().clear());
        assertThrows(UnsupportedOperationException.class, () -> report.alternatives().clear());
        assertThrows(UnsupportedOperationException.class, () -> report.alternativeChartImages().clear());
    }
}
