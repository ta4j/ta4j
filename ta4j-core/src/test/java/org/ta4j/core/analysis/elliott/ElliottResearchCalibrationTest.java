/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.elliott.ElliottResearchCalibration.BinFit;
import org.ta4j.core.analysis.elliott.ElliottResearchCalibration.Calibrator;
import org.ta4j.core.analysis.elliott.ElliottResearchCalibration.Estimate;
import org.ta4j.core.analysis.elliott.ElliottResearchCalibration.Identity;
import org.ta4j.core.analysis.elliott.ElliottResearchCalibration.Outcome;
import org.ta4j.core.analysis.elliott.ElliottResearchCalibration.Provenance;
import org.ta4j.core.analysis.elliott.ElliottResearchCalibration.Row;
import org.ta4j.core.analysis.elliott.ElliottResearchCalibration.Settings;
import org.ta4j.core.analysis.elliott.ElliottResearchCalibration.Weighted;

/**
 * Behavioural tests for the outcome-calibration baseline.
 */
class ElliottResearchCalibrationTest {

    private static final Identity IDENTITY = new Identity("toy", "fractal-w3", "fractal(3)", "MOTIVE_5",
            "classical-all", "origin-pivot", List.of("wave2-origin"), "RSI(14)", ElliottResearchCalibration.TARGET,
            ElliottResearchCalibration.FEATURE, 20);
    private static final Provenance PROVENANCE = new Provenance("fp", "rev");
    private static final String MASK = "wave2-origin";

    private static Row row(final String partition, final int enrollIndex, final String key, final int pass,
            final int fail, final Outcome outcome, final int availableIndex, final int windowEnd) {
        return new Row("toy", partition, key, "1", "bullish", enrollIndex, Instant.ofEpochSecond(enrollIndex), pass,
                fail, 0, 0, 0, pass + fail > 0 ? MASK : "", outcome, availableIndex, windowEnd, -1, true);
    }

    /**
     * A fully observed row: label known and window ended 30 bars after enrollment.
     */
    private static Row labeled(final int enrollIndex, final String key, final int pass, final int fail,
            final Outcome outcome) {
        return row("calibration", enrollIndex, key, pass, fail, outcome, enrollIndex + 10, enrollIndex + 20);
    }

    @Test
    void siblingsOfOneDecisionShareUnitWeightAndUnlabeledRowsWeighNothing() {
        final List<Weighted> weighted = ElliottResearchCalibration
                .weigh(List.of(labeled(10, "a", 1, 0, Outcome.SUCCESS), labeled(10, "b", 0, 1, Outcome.FAILURE),
                        labeled(10, "c", 1, 0, Outcome.SUCCESS), labeled(11, "d", 1, 0, Outcome.FAILURE),
                        labeled(12, "e", 1, 0, Outcome.CENSORED)));
        assertEquals(5, weighted.size());
        assertEquals(1.0d / 3.0d, weighted.get(0).weight(), 1e-12);
        assertEquals(1.0d / 3.0d, weighted.get(2).weight(), 1e-12);
        assertEquals(1.0d, weighted.get(3).weight(), 1e-12);
        assertEquals(0.0d, weighted.get(4).weight(), 0.0d);
    }

    @Test
    void duplicateRowsCollapseAndConflictingDuplicatesAreRejected() {
        final Row first = labeled(10, "a", 1, 0, Outcome.SUCCESS);
        assertEquals(1, ElliottResearchCalibration.weigh(List.of(first, first)).size());
        final Row conflicting = labeled(10, "a", 1, 0, Outcome.FAILURE);
        final IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ElliottResearchCalibration.weigh(List.of(first, conflicting)));
        assertTrue(failure.getMessage().contains("conflicting rows"), failure.getMessage());
    }

    @Test
    void fitReadsOnlyLabelsWhoseWindowEndedByTheCutoff() {
        final List<Row> rows = new ArrayList<>();
        rows.add(labeled(10, "in", 1, 0, Outcome.SUCCESS));
        // window ends at 130 > cutoff 100: a later outcome must not shape the table
        rows.add(row("calibration", 90, "late-window", 1, 0, Outcome.FAILURE, 95, 130));
        // label known only after the cutoff
        rows.add(row("calibration", 20, "late-label", 1, 0, Outcome.FAILURE, 120, 40));
        rows.add(row("validation", 30, "other-partition", 1, 0, Outcome.FAILURE, 40, 50));
        rows.add(labeled(40, "censored", 1, 0, Outcome.CENSORED));
        final Calibrator table = Calibrator.fit("validation", IDENTITY, PROVENANCE, 1, rows, List.of("calibration"),
                100);
        assertEquals(1, table.base().groups());
        assertEquals(1, table.base().successes());
        assertEquals(0, table.base().failures());
        assertEquals(10, table.firstIndex());
        assertEquals(100, table.cutoffIndex());
    }

    @Test
    void estimateRefusesAnotherIdentityAndDecisionsThatPrecedeTheTable() {
        final Calibrator table = Calibrator.fit("validation", IDENTITY, PROVENANCE, 1,
                List.of(labeled(10, "a", 1, 0, Outcome.SUCCESS)), List.of("calibration"), 100);
        final Row later = row("validation", 150, "x", 1, 0, Outcome.SUCCESS, 160, 170);
        final Identity other = new Identity("toy", "fractal-w5", "fractal(5)", "MOTIVE_5", "classical-all",
                "origin-pivot", List.of("wave2-origin"), "RSI(14)", ElliottResearchCalibration.TARGET,
                ElliottResearchCalibration.FEATURE, 20);
        final IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
                () -> table.estimate(other, later));
        assertTrue(mismatch.getMessage().contains("detector (fractal-w3 vs fractal-w5)"), mismatch.getMessage());
        for (final int index : new int[] { 10, 100 }) {
            final Row early = row("validation", index, "x", 1, 0, Outcome.SUCCESS, 160, 170);
            final IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> table.estimate(IDENTITY, early));
            assertTrue(failure.getMessage().contains("not after the fit cutoff 100"), failure.getMessage());
        }
    }

    @Test
    void estimateRefusesTheSameDetectorNameFittedAtAnotherScaleOrMomentumLookback() {
        final Calibrator table = Calibrator.fit("validation", IDENTITY, PROVENANCE, 1,
                List.of(labeled(10, "a", 1, 0, Outcome.SUCCESS)), List.of("calibration"), 100);
        final Row later = row("validation", 150, "x", 1, 0, Outcome.SUCCESS, 160, 170);
        final Identity otherScale = new Identity("toy", "fractal-w3", "fractal(5)", "MOTIVE_5", "classical-all",
                "origin-pivot", List.of("wave2-origin"), "RSI(14)", ElliottResearchCalibration.TARGET,
                ElliottResearchCalibration.FEATURE, 20);
        final IllegalArgumentException scale = assertThrows(IllegalArgumentException.class,
                () -> table.estimate(otherScale, later));
        assertTrue(scale.getMessage().contains("detectorConfig (fractal(3) vs fractal(5))"), scale.getMessage());
        final Identity otherMomentum = new Identity("toy", "fractal-w3", "fractal(3)", "MOTIVE_5", "classical-all",
                "origin-pivot", List.of("wave2-origin"), "RSI(21)", ElliottResearchCalibration.TARGET,
                ElliottResearchCalibration.FEATURE, 20);
        final IllegalArgumentException momentum = assertThrows(IllegalArgumentException.class,
                () -> table.estimate(otherMomentum, later));
        assertTrue(momentum.getMessage().contains("momentum (RSI(14) vs RSI(21))"), momentum.getMessage());
        assertEquals(List.of(), IDENTITY.differences(IDENTITY));
    }

    @Test
    void scopeMatchesOnlyThePrimaryDetectorsRealMotiveStream() {
        assertEquals(null, IDENTITY.scopeMismatch("h1", "MOTIVE_5", "fractal-w3"));
        assertTrue(
                IDENTITY.scopeMismatch("robustness", "MOTIVE_5", "fractal-w5").contains("section (h1 vs robustness)"));
        assertTrue(IDENTITY.scopeMismatch("robustness", "MOTIVE_5", "fractal-w5")
                .contains("detector (fractal-w3 vs fractal-w5)"));
        assertTrue(IDENTITY.scopeMismatch("competing", "CYCLE_5_3", "fractal-w3")
                .contains("grammar (MOTIVE_5 vs CYCLE_5_3)"));
        assertTrue(IDENTITY.scopeMismatch("h2", "MOTIVE_5", "fractal-w3").contains("section (h1 vs h2)"));
    }

    @Test
    void lineagePresentInTheFitPartitionsIsPurgedFromTheScoredPartition() {
        final List<Row> rows = List.of(labeled(10, "spans", 1, 0, Outcome.SUCCESS),
                labeled(11, "fit-only", 1, 0, Outcome.FAILURE),
                row("validation", 150, "spans", 1, 0, Outcome.FAILURE, 160, 170),
                row("validation", 151, "fresh", 1, 0, Outcome.SUCCESS, 161, 171),
                row("evaluation", 250, "spans", 1, 0, Outcome.SUCCESS, 260, 270),
                row("evaluation", 251, "late", 1, 0, Outcome.SUCCESS, 261, 271));
        final ElliottResearchCalibration.LineageSplit validation = ElliottResearchCalibration.splitLineages(rows,
                List.of("calibration"), "validation");
        assertEquals(List.of("fresh"), validation.heldOut().stream().map(Row::candidateKey).toList());
        assertEquals(List.of("spans"), validation.purged().stream().map(Row::candidateKey).toList());
        final ElliottResearchCalibration.LineageSplit evaluation = ElliottResearchCalibration.splitLineages(rows,
                List.of("calibration", "validation"), "evaluation");
        assertEquals(List.of("late"), evaluation.heldOut().stream().map(Row::candidateKey).toList());
        assertEquals(List.of("spans"), evaluation.purged().stream().map(Row::candidateKey).toList());
    }

    @Test
    void supportedBinGivesSmoothedProbabilityAndWeakBinsAbstainWithReasons() {
        final List<Row> rows = new ArrayList<>();
        // bin 4 (pass only): 2 successes, 2 failures in 4 distinct groups -> (2 + 1) /
        // (4 + 2)
        rows.add(labeled(1, "a", 1, 0, Outcome.SUCCESS));
        rows.add(labeled(2, "b", 1, 0, Outcome.SUCCESS));
        rows.add(labeled(3, "c", 1, 0, Outcome.FAILURE));
        rows.add(labeled(4, "d", 1, 0, Outcome.FAILURE));
        // bin 0 (fail only): failures only
        rows.add(labeled(5, "e", 0, 1, Outcome.FAILURE));
        rows.add(labeled(6, "f", 0, 1, Outcome.FAILURE));
        // bin 2 (1 pass of 2): successes only in enough groups
        rows.add(labeled(7, "g", 1, 1, Outcome.SUCCESS));
        rows.add(labeled(8, "h", 1, 1, Outcome.SUCCESS));
        // bin 3 (3 of 4): a single group
        rows.add(labeled(9, "i", 3, 1, Outcome.SUCCESS));
        final Calibrator table = Calibrator.fit("validation", IDENTITY, PROVENANCE, 2, rows, List.of("calibration"),
                100);
        assertEquals("fitted", table.estimatorStatus());
        assertEquals(9.0d, table.base().weight(), 1e-12);

        final Estimate supported = table.estimate(IDENTITY, row("validation", 200, "x", 1, 0, Outcome.SUCCESS, 1, 2));
        assertTrue(supported.predicted());
        assertEquals(3.0d / 6.0d, supported.probability(), 1e-12);
        assertEquals(4, supported.bin());
        assertEquals(4, supported.support().groups());
        assertEquals(table.base().probability(), supported.baseProbability(), 1e-12);

        assertAbstains(table, 0, 1, "no observed success");
        assertAbstains(table, 1, 1, "no observed failure");
        assertAbstains(table, 3, 1, "insufficient decision groups: 1 < 2");

        final Estimate noFeature = table.estimate(IDENTITY, row("validation", 200, "x", 0, 0, Outcome.SUCCESS, 1, 2));
        assertEquals(ElliottResearchCalibration.STATUS_NO_FEATURE, noFeature.status());
        assertTrue(Double.isNaN(noFeature.probability()));
        final Row unseenMask = new Row("toy", "validation", "x", "1", "bullish", 200, Instant.EPOCH, 1, 0, 0, 0, 0,
                "wave4-nonoverlap", Outcome.SUCCESS, 1, 2, -1, true);
        final Estimate unseen = table.estimate(IDENTITY, unseenMask);
        assertEquals(ElliottResearchCalibration.STATUS_UNSUPPORTED, unseen.status());
        assertEquals(0, unseen.support().groups());
        assertTrue(unseen.reason().contains("insufficient decision groups: 0 < 2"), unseen.reason());
    }

    private static void assertAbstains(final Calibrator table, final int pass, final int fail, final String reason) {
        final Row row = row("validation", 200, "x", pass, fail, Outcome.SUCCESS, 1, 2);
        final Estimate estimate = table.estimate(IDENTITY, row);
        assertFalse(estimate.predicted(), estimate.toString());
        assertEquals(ElliottResearchCalibration.STATUS_UNSUPPORTED, estimate.status());
        assertTrue(Double.isNaN(estimate.probability()));
        assertEquals(reason, estimate.reason());
    }

    @Test
    void emptyFitIsUnavailableRatherThanFabricated() {
        final Calibrator table = Calibrator.fit("validation", IDENTITY, PROVENANCE, 1, List.of(),
                List.of("calibration"), 100);
        assertEquals("unavailable", table.estimatorStatus());
        assertTrue(Double.isNaN(table.baseProbability()));
        assertEquals(-1, table.firstIndex());
        final Estimate estimate = table.estimate(IDENTITY, row("validation", 200, "x", 1, 0, Outcome.SUCCESS, 1, 2));
        assertEquals(ElliottResearchCalibration.STATUS_UNSUPPORTED, estimate.status());
    }

    @Test
    void forecastBinsIncludeTheirLowerEdgeAndTheLastBinIncludesOne() {
        assertEquals(0, ElliottResearchCalibration.forecastBin(0.0d));
        assertEquals(0, ElliottResearchCalibration.forecastBin(0.199d));
        assertEquals(1, ElliottResearchCalibration.forecastBin(0.2d));
        assertEquals(3, ElliottResearchCalibration.forecastBin(0.8d - 1e-9));
        assertEquals(4, ElliottResearchCalibration.forecastBin(0.8d));
        assertEquals(4, ElliottResearchCalibration.forecastBin(1.0d));
    }

    @Test
    void tableRoundTripsThroughJsonAndKeepsItsEstimates() {
        final List<Row> rows = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            rows.add(labeled(index, "k" + index, 2, 1, index % 2 == 0 ? Outcome.SUCCESS : Outcome.FAILURE));
        }
        final Calibrator table = Calibrator.fit("evaluation", IDENTITY, PROVENANCE, 2, rows, List.of("calibration"),
                100);
        final JsonObject json = table.toJson();
        assertEquals(ElliottResearchCalibration.SCHEMA, json.get("schema").getAsString());
        final Calibrator reloaded = Calibrator.fromJson(JsonParser.parseString(json.toString()).getAsJsonObject());
        assertEquals(table, reloaded);
        final Row query = row("holdout", 300, "q", 2, 1, Outcome.SUCCESS, 310, 320);
        assertEquals(table.estimate(IDENTITY, query), reloaded.estimate(IDENTITY, query));
        json.addProperty("schema", "other/1");
        assertThrows(IllegalArgumentException.class, () -> Calibrator.fromJson(json));
    }

    @Test
    void settingsParseRequiresAHorizonOfTheRecipeAndChronologicalPartitions() {
        final ElliottResearchOutcomes.Settings outcomes = ElliottResearchOutcomes.Settings.defaults();
        final List<StudyRunner.Partition> partitions = List.of(
                new StudyRunner.Partition("calibration", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 30)),
                new StudyRunner.Partition("validation", LocalDate.of(2020, 7, 1), LocalDate.of(2020, 9, 30)),
                new StudyRunner.Partition("holdout", LocalDate.of(2020, 10, 1), LocalDate.of(2020, 12, 31)));
        final Settings parsed = Settings.parse(JsonParser.parseString("{\"horizon\":20}").getAsJsonObject(), outcomes,
                partitions);
        assertEquals(new Settings(20, "calibration", "validation", "holdout", 30), parsed);
        assertEquals(parsed.toJson().get("target").getAsString(), ElliottResearchCalibration.TARGET);

        assertParseFails("{}", outcomes, partitions, "calibration.horizon is required");
        assertParseFails("{\"horizon\":7}", outcomes, partitions, "must be one of outcomes.horizons [5, 20, 60]");
        assertParseFails("{\"horizon\":20,\"folds\":3}", outcomes, partitions, "unknown field 'folds'");
        assertParseFails("{\"horizon\":20.5}", outcomes, partitions, "calibration.horizon must be an integer");
        assertParseFails("{\"horizon\":20,\"fit\":\"nope\"}", outcomes, partitions, "unknown partition 'nope'");
        assertParseFails("{\"horizon\":20,\"minGroups\":0}", outcomes, partitions, "minGroups must be positive");
        assertParseFails("{\"horizon\":20,\"fit\":\"holdout\",\"evaluation\":\"calibration\"}", outcomes, partitions,
                "chronological and disjoint");
        assertParseFails("{\"horizon\":20,\"validation\":\"calibration\"}", outcomes, partitions,
                "three distinct partitions");
    }

    private static void assertParseFails(final String json, final ElliottResearchOutcomes.Settings outcomes,
            final List<StudyRunner.Partition> partitions, final String message) {
        final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> Settings.parse(JsonParser.parseString(json).getAsJsonObject(), outcomes, partitions), json);
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
    }

    @Test
    void binFitSmoothsWithoutClippingAndNamesWeakSupport() {
        final BinFit fit = new BinFit(4.0d, 1.0d, 4, 1, 3);
        assertEquals(2.0d / 6.0d, fit.probability(), 1e-12);
        assertEquals(null, fit.reason(4));
        assertEquals("insufficient decision groups: 4 < 5", fit.reason(5));
    }
}
