/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;

/**
 * Locks down anchor-free macro-cycle detection against the committed BTC truth
 * set.
 *
 * <p>
 * The detector is allowed to infer its own anchor ids and provenance, but the
 * recovered macro turns must stay aligned with the committed BTC anchor
 * windows. The runtime historical report itself is now series-native and no
 * longer uses the detector as a front-end.
 *
 * @since 0.22.4
 */
@Tag("elliott-macro-cycle-replay")
class ElliottWaveMacroCycleDetectorTest {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveMacroCycleDetectorTest.class);

    @TempDir
    Path chartDirectory;

    @Test
    void inferredBitcoinAnchorsRecoverCommittedMacroTurns() {
        final BarSeries series = loadBitcoinSeries();
        final ElliottWaveAnchorCalibrationHarness.AnchorRegistry expected = ElliottWaveAnchorCalibrationHarness
                .defaultBitcoinAnchors(series);
        final ElliottWaveAnchorCalibrationHarness.AnchorRegistry inferred = ElliottWaveMacroCycleDetector
                .inferAnchorRegistry(series);

        assertEquals(expected.anchors().size(), inferred.anchors().size());

        for (int index = 0; index < expected.anchors().size(); index++) {
            final ElliottWaveAnchorCalibrationHarness.Anchor expectedAnchor = expected.anchors().get(index);
            final ElliottWaveAnchorCalibrationHarness.Anchor inferredAnchor = inferred.anchors().get(index);
            assertEquals(expectedAnchor.type(), inferredAnchor.type());
            assertEquals(expectedAnchor.partition(), inferredAnchor.partition());
            assertEquals(expectedAnchor.expectedPhases(), inferredAnchor.expectedPhases());
            assertWithinDays(expectedAnchor.at(), inferredAnchor.at(), 21);
        }
    }

    @Test
    void seriesNativeHistoricalMacroDemoProducesCanonicalChronologicalCycles() throws Exception {
        final BarSeries series = loadBitcoinSeries();
        final ElliottWaveMacroCycleDemo.DemoReport seriesNative = ElliottWaveMacroCycleDemo
                .generateHistoricalReport(series, chartDirectory.resolve("series-native"));

        assertEquals("canonical-structure", seriesNative.structureSource());
        assertChronologicalCycles(seriesNative.cycles(), "historical demo");
        assertTrue(Files.exists(Path.of(seriesNative.chartPath())));
        assertTrue(Files.exists(Path.of(seriesNative.summaryPath())));
    }

    @Test
    void seriesNativeHistoricalAndLiveReportsShareCanonicalProfileSelection() throws Exception {
        final BarSeries series = loadBitcoinSeries();

        final ElliottWaveMacroCycleDemo.DemoReport historical = ElliottWaveMacroCycleDemo
                .generateHistoricalReport(series, chartDirectory.resolve("historical"));
        final ElliottWaveMacroCycleDemo.LivePresetReport live = ElliottWaveMacroCycleDemo
                .generateLivePresetReport(series, chartDirectory.resolve("live"));

        assertEquals("canonical-structure", historical.structureSource());
        assertEquals("canonical-structure", live.structureSource());
        assertEquals(historical.selectedProfileId(), live.selectedProfileId());
        assertEquals(historical.selectedHypothesisId(), live.selectedHypothesisId());
        assertTrue(Files.exists(Path.of(live.chartPath())));
        assertTrue(Files.exists(Path.of(live.summaryPath())));
    }

    @Test
    void canonicalReplayAtMajorMacroTurnsPreservesHistoricalCurrentProfileCoherence() {
        final BarSeries fullSeries = loadBitcoinSeries();

        assertReplay(fullSeries, "2013-11-30T00:00:00Z");
        assertReplay(fullSeries, "2015-08-19T00:00:00Z");
        assertReplay(fullSeries, "2017-12-18T00:00:00Z");
        assertReplay(fullSeries, "2018-12-16T00:00:00Z");
        assertReplay(fullSeries, "2021-11-11T00:00:00Z");
        assertReplay(fullSeries, "2022-11-22T00:00:00Z");
    }

    @Test
    void canonicalReplayAt2018BottomRecoversTwoCompletedCycles() {
        final BarSeries fullSeries = loadBitcoinSeries();
        final BarSeries slicedSeries = sliceThrough(fullSeries, Instant.parse("2018-12-16T00:00:00Z"));
        final ElliottWaveMacroCycleDemo.CanonicalStructure structure = ElliottWaveMacroCycleDemo
                .analyzeCanonicalStructure(slicedSeries);
        final ElliottWaveMacroCycleDemo.MacroStudy study = structure.historicalStudy().orElseThrow();

        assertEquals(2, study.cycles().size(), cycleDateSignatures(study.cycles()).toString());
        assertWithinDays(Instant.parse("2011-11-18T00:00:00Z"), Instant.parse(study.cycles().get(0).startTimeUtc()),
                21);
        assertWithinDays(Instant.parse("2013-11-30T00:00:00Z"), Instant.parse(study.cycles().get(0).peakTimeUtc()), 21);
        assertWithinDays(Instant.parse("2015-08-19T00:00:00Z"), Instant.parse(study.cycles().get(0).lowTimeUtc()), 21);
        assertWithinDays(Instant.parse("2015-08-19T00:00:00Z"), Instant.parse(study.cycles().get(1).startTimeUtc()),
                21);
        assertWithinDays(Instant.parse("2017-12-18T00:00:00Z"), Instant.parse(study.cycles().get(1).peakTimeUtc()), 21);
        assertWithinDays(Instant.parse("2018-12-16T00:00:00Z"), Instant.parse(study.cycles().get(1).lowTimeUtc()), 21);
    }

    @Test
    void canonicalReplayAt2021TopUsesContinuationAdjustedSecondCycleLow() {
        final BarSeries fullSeries = loadBitcoinSeries();
        final BarSeries slicedSeries = sliceThrough(fullSeries, Instant.parse("2021-11-11T00:00:00Z"));
        final ElliottWaveMacroCycleDemo.CanonicalStructure structure = ElliottWaveMacroCycleDemo
                .analyzeCanonicalStructure(slicedSeries);
        final ElliottWaveMacroCycleDemo.MacroStudy study = structure.historicalStudy().orElseThrow();

        assertEquals(2, study.cycles().size(), cycleDateSignatures(study.cycles()).toString());
        assertCycleWithinDays(study, 0, "2011-11-18T00:00:00Z", "2013-11-30T00:00:00Z", "2015-08-19T00:00:00Z");
        assertCycleWithinDays(study, 1, "2015-08-19T00:00:00Z", "2017-12-18T00:00:00Z", "2020-05-26T00:00:00Z");
    }

    @Test
    void canonicalReplayAt2022BottomRecoversChronologicalContinuationCandidates() {
        final BarSeries fullSeries = loadBitcoinSeries();
        final BarSeries slicedSeries = sliceThrough(fullSeries, Instant.parse("2022-11-22T00:00:00Z"));
        final ElliottWaveMacroCycleDemo.CanonicalStructure structure = ElliottWaveMacroCycleDemo
                .analyzeCanonicalStructure(slicedSeries);
        final ElliottWaveMacroCycleDemo.MacroStudy study = structure.historicalStudy().orElseThrow();

        assertEquals(4, study.cycles().size(), cycleDateSignatures(study.cycles()).toString());
        assertCycleWithinDays(study, 0, "2011-11-18T00:00:00Z", "2013-11-30T00:00:00Z", "2015-08-19T00:00:00Z");
        assertCycleWithinDays(study, 1, "2015-08-19T00:00:00Z", "2016-06-19T00:00:00Z", "2016-08-03T00:00:00Z");
        assertCycleWithinDays(study, 2, "2016-08-03T00:00:00Z", "2017-12-18T00:00:00Z", "2020-03-14T00:00:00Z");
        assertCycleWithinDays(study, 3, "2020-03-14T00:00:00Z", "2021-11-11T00:00:00Z", "2022-11-22T00:00:00Z");
    }

    @Test
    void sliceThroughPreservesSourceNumFactory() {
        final BarSeries fullSeries = loadBitcoinSeries();

        final BarSeries slicedSeries = sliceThrough(fullSeries, Instant.parse("2022-11-22T00:00:00Z"));

        assertEquals(fullSeries.numFactory(), slicedSeries.numFactory());
    }

    private static BarSeries loadBitcoinSeries() {
        final BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottWaveMacroCycleDetectorTest.class,
                ElliottWaveAnchorCalibrationHarness.BTC_RESOURCE, ElliottWaveAnchorCalibrationHarness.BTC_SERIES_NAME,
                LOG);
        assertNotNull(series);
        return series;
    }

    private static void assertWithinDays(final Instant expected, final Instant actual, final long maxDays) {
        final Duration delta = Duration.between(expected, actual).abs();
        assertTrue(delta.compareTo(Duration.ofDays(maxDays)) <= 0,
                () -> "expected " + actual + " to stay within " + maxDays + " days of " + expected);
    }

    private static void assertCycleWithinDays(final ElliottWaveMacroCycleDemo.MacroStudy study, final int cycleIndex,
            final String expectedStartIso, final String expectedPeakIso, final String expectedLowIso) {
        final ElliottWaveMacroCycleDemo.DirectionalCycleSummary cycle = study.cycles().get(cycleIndex);
        assertWithinDays(Instant.parse(expectedStartIso), Instant.parse(cycle.startTimeUtc()), 21);
        assertWithinDays(Instant.parse(expectedPeakIso), Instant.parse(cycle.peakTimeUtc()), 21);
        assertWithinDays(Instant.parse(expectedLowIso), Instant.parse(cycle.lowTimeUtc()), 21);
    }

    private static void assertReplay(final BarSeries fullSeries, final String cutoffIso) {
        final BarSeries slicedSeries = sliceThrough(fullSeries, Instant.parse(cutoffIso));
        final ElliottWaveMacroCycleDemo.CanonicalStructure structure = ElliottWaveMacroCycleDemo
                .analyzeCanonicalStructure(slicedSeries);
        final ElliottWaveMacroCycleDemo.MacroStudy study = structure.historicalStudy().orElseThrow();

        assertChronologicalCycles(study.cycles(), cutoffIso);
        for (ElliottWaveMacroCycleDemo.DirectionalCycleSummary cycle : study.cycles()) {
            assertTrue(!Instant.parse(cycle.lowTimeUtc()).isAfter(Instant.parse(cutoffIso)));
        }
        assertEquals(study.selectedProfile().profile().id(), structure.currentCycle().summary().winningProfileId());
    }

    private static BarSeries sliceThrough(final BarSeries fullSeries, final Instant cutoff) {
        final BarSeries slicedSeries = new BaseBarSeriesBuilder().withName(fullSeries.getName() + "@" + cutoff)
                .withNumFactory(fullSeries.numFactory())
                .build();
        for (int index = fullSeries.getBeginIndex(); index <= fullSeries.getEndIndex(); index++) {
            if (fullSeries.getBar(index).getEndTime().isAfter(cutoff)) {
                break;
            }
            slicedSeries.addBar(fullSeries.getBar(index));
        }
        assertTrue(slicedSeries.getBarCount() > 0, () -> "No bars found through cutoff " + cutoff);
        return slicedSeries;
    }

    private static void assertChronologicalCycles(final List<ElliottWaveMacroCycleDemo.DirectionalCycleSummary> cycles,
            final String contextLabel) {
        assertTrue(!cycles.isEmpty(), () -> "Expected at least one completed cycle for " + contextLabel);
        for (ElliottWaveMacroCycleDemo.DirectionalCycleSummary cycle : cycles) {
            final Instant cycleStart = Instant.parse(cycle.startTimeUtc());
            final Instant cyclePeak = Instant.parse(cycle.peakTimeUtc());
            final Instant cycleLow = Instant.parse(cycle.lowTimeUtc());
            assertTrue(cycleStart.compareTo(cyclePeak) < 0, () -> "Non-chronological peak in " + cycleLabel(cycle));
            assertTrue(cyclePeak.compareTo(cycleLow) < 0, () -> "Non-chronological low in " + cycleLabel(cycle));
        }
    }

    private static List<String> cycleDateSignatures(
            final List<ElliottWaveMacroCycleDemo.DirectionalCycleSummary> cycles) {
        return cycles.stream().map(ElliottWaveMacroCycleDetectorTest::cycleLabel).toList();
    }

    private static String cycleLabel(final ElliottWaveMacroCycleDemo.DirectionalCycleSummary cycle) {
        return String.join("|", cycle.startTimeUtc(), cycle.peakTimeUtc(), cycle.lowTimeUtc());
    }
}
