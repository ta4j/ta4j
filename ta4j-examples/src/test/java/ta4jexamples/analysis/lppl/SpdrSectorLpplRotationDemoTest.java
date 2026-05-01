/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.lppl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.lppl.LpplCalibrationProfile;
import org.ta4j.core.indicators.lppl.LpplExhaustion;
import org.ta4j.core.indicators.lppl.LpplExhaustionSide;
import org.ta4j.core.indicators.lppl.LpplExhaustionStatus;
import org.ta4j.core.indicators.lppl.LpplFit;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

class SpdrSectorLpplRotationDemoTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();

    @Test
    void closedUniverseContainsAllElevenSpdrSectorEtfs() {
        String[] tickers = SpdrSectorLpplRotationDemo.closedUniverse()
                .stream()
                .map(SpdrSectorLpplRotationDemo.SectorDefinition::ticker)
                .toArray(String[]::new);
        assertArrayEquals(new String[] { "XLI", "XLV", "XLF", "XLRE", "XLE", "XLU", "XLK", "XLB", "XLP", "XLY", "XLC" },
                tickers);
    }

    @Test
    void aggregateComputesRelativeRotationAgainstClosedUniverseAverage() {
        List<SpdrSectorLpplRotationDemo.InstrumentSnapshot> instruments = List.of(
                instrument("XLI", "Industrials", 0.6, LpplExhaustionSide.CRASH_EXHAUSTION),
                instrument("XLV", "Health Care", -0.2, LpplExhaustionSide.BUBBLE_EXHAUSTION),
                instrument("XLF", "Financials", 0.0, LpplExhaustionSide.NONE));

        List<SpdrSectorLpplRotationDemo.SectorSnapshot> snapshots = SpdrSectorLpplRotationDemo.aggregate(instruments);

        assertEquals(3, snapshots.size());
        assertEquals("Industrials", snapshots.get(0).sector());
        assertEquals(1.0, snapshots.get(0).netExhaustionScore());
        assertEquals(0.466666, snapshots.get(0).relativeRotationScore(), 0.0001);
        assertEquals("Health Care", snapshots.get(2).sector());
        assertEquals(-1.0, snapshots.get(2).netExhaustionScore());
    }

    @Test
    void aggregateIgnoresInvalidSidesWhenCountingCrashAndBubbleFits() {
        List<SpdrSectorLpplRotationDemo.InstrumentSnapshot> instruments = List.of(instrument("XLI", "Industrials", 0.4,
                LpplExhaustionSide.CRASH_EXHAUSTION, LpplExhaustionStatus.NO_VALID_FIT));

        List<SpdrSectorLpplRotationDemo.SectorSnapshot> snapshots = SpdrSectorLpplRotationDemo.aggregate(instruments);

        assertEquals(1, snapshots.size());
        assertEquals(0, snapshots.get(0).crashCount());
        assertEquals(0, snapshots.get(0).bubbleCount());
        assertEquals(0.0, snapshots.get(0).netExhaustionScore());
        assertEquals(0.0, snapshots.get(0).lpplScore());
    }

    @Test
    void renderReportProducesDeterministicCsvTable() {
        List<SpdrSectorLpplRotationDemo.SectorSnapshot> snapshots = List
                .of(new SpdrSectorLpplRotationDemo.SectorSnapshot("Industrials", "XLI", 1, 1, 0, 1.0, 0.6, 0.4));

        String report = SpdrSectorLpplRotationDemo.renderReport(snapshots);

        assertEquals(
                "date,sector,ticker,total,crash_count,bubble_count,net_exhaustion_score,standalone_lppl_score,relative_rotation_score\n"
                        + "2026-04-29,Industrials,XLI,1,1,0,1.0000,0.6000,0.4000\n",
                report);
    }

    @Test
    void analyzeLoadsAllOfflineSpdrResources() {
        LpplCalibrationProfile fastProfile = new LpplCalibrationProfile(new int[] { 200 }, 0.1, 0.9, 2, 6.0, 13.0, 2,
                10, 30, 10, 10, 30, 25, 0.5);

        List<SpdrSectorLpplRotationDemo.SectorSnapshot> snapshots = SpdrSectorLpplRotationDemo.analyze(fastProfile);

        assertEquals(11, snapshots.size());
        assertTrue(snapshots.stream().allMatch(snapshot -> Double.isFinite(snapshot.lpplScore())));
        assertTrue(snapshots.stream().allMatch(snapshot -> Double.isFinite(snapshot.relativeRotationScore())));
    }

    private SpdrSectorLpplRotationDemo.InstrumentSnapshot instrument(String ticker, String sector, double score,
            LpplExhaustionSide side) {
        return instrument(ticker, sector, score, side, LpplExhaustionStatus.VALID);
    }

    private SpdrSectorLpplRotationDemo.InstrumentSnapshot instrument(String ticker, String sector, double score,
            LpplExhaustionSide side, LpplExhaustionStatus status) {
        LpplFit fit = new LpplFit(0, LpplExhaustionStatus.NO_VALID_FIT, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, -1, 0);
        LpplExhaustion exhaustion = new LpplExhaustion(status, side, numFactory.numOf(score),
                numFactory.numOf(Math.abs(score)), fit, List.of(), 1, side == LpplExhaustionSide.NONE ? 0 : 1,
                side == LpplExhaustionSide.CRASH_EXHAUSTION ? 1 : 0,
                side == LpplExhaustionSide.BUBBLE_EXHAUSTION ? 1 : 0);
        return new SpdrSectorLpplRotationDemo.InstrumentSnapshot(ticker, sector, exhaustion);
    }
}
