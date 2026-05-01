/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.lppl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
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

    private SpdrSectorLpplRotationDemo.InstrumentSnapshot instrument(String ticker, String sector, double score,
            LpplExhaustionSide side) {
        LpplFit fit = new LpplFit(0, LpplExhaustionStatus.NO_VALID_FIT, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, -1, 0);
        LpplExhaustion exhaustion = new LpplExhaustion(LpplExhaustionStatus.VALID, side, numFactory.numOf(score),
                numFactory.numOf(Math.abs(score)), fit, List.of(), 1, side == LpplExhaustionSide.NONE ? 0 : 1,
                side == LpplExhaustionSide.CRASH_EXHAUSTION ? 1 : 0,
                side == LpplExhaustionSide.BUBBLE_EXHAUSTION ? 1 : 0);
        return new SpdrSectorLpplRotationDemo.InstrumentSnapshot(ticker, sector, exhaustion);
    }
}
