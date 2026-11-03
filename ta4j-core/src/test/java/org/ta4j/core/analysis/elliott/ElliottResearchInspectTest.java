/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.copyTree;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.launch;
import static org.ta4j.core.analysis.elliott.ElliottResearchFixtures.readJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.ta4j.core.analysis.elliott.ElliottResearchFixtures.Result;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavioural tests for the Elliott research {@code inspect} command: each
 * recorded ratio, label, lag and rule is reconstructed from the trace and the
 * metadata it is bound to.
 */
class ElliottResearchInspectTest {

    @TempDir
    static Path shared;

    @TempDir
    Path work;

    private static Path smokeRun;
    private static Path nullMemberRun;

    @BeforeAll
    static void runFixtures() throws IOException {
        smokeRun = shared.resolve("smoke");
        final Result result = launch("run", "smoke", "--out", smokeRun.toString());
        assertEquals(0, result.code(), result.err());

        nullMemberRun = shared.resolve("smoke-null-b20-m3");
        final Result member = launch("run", "smoke", "--trace", "selected-null-member", "--block", "20", "--member",
                "3", "--out", nullMemberRun.toString());
        assertEquals(0, member.code(), member.err());
    }

    @Test
    public void inspectBindsRealActiveRulesToEveryComparedMode() throws Exception {
        assertModeRuleBinding(smokeRun, "smoke-real.jsonl", false);
    }

    @Test
    public void inspectBindsSelectedNullActiveRulesToTheComparisonMode() throws Exception {
        assertModeRuleBinding(nullMemberRun, "smoke-null-b20-m3.jsonl", true);
    }

    @Test
    public void completeTraceBytesAreBoundToMetadataBeforeAnyScopeIsEvidence() throws Exception {
        final List<String> damages = List.of("selected-observation", "other-observation", "labels", "missing-digest");
        // Each damage corrupts one trace, alternating between the real and the
        // null-member trace.
        for (int index = 0; index < damages.size(); index++) {
            final boolean nullMember = index % 2 == 1;
            final String damage = damages.get(index);
            final Path copy = work.resolve("trace-digest-" + nullMember + "-" + damage);
            copyTree(nullMember ? nullMemberRun : smokeRun, copy);
            final String relative = nullMember ? "traces/smoke-null-b20-m3.jsonl" : "traces/smoke-real.jsonl";
            final Path trace = copy.resolve(relative);
            if ("missing-digest".equals(damage)) {
                final JsonObject run = readJson(copy.resolve("run.json"));
                run.getAsJsonArray("datasets").get(0).getAsJsonObject().remove("traceSha256");
                Files.writeString(copy.resolve("run.json"), run.toString());
            } else {
                final List<String> lines = new ArrayList<>(Files.readAllLines(trace, StandardCharsets.UTF_8));
                final String grammar = "other-observation".equals(damage) ? "MOTIVE_5" : "CYCLE_5_3";
                for (int i = 1; i < lines.size() - 1; i++) {
                    final JsonObject observation = JsonParser.parseString(lines.get(i)).getAsJsonObject();
                    if (!grammar.equals(observation.get("grammar").getAsString())
                            || !nullMember && !"topology-only".equals(observation.get("mode").getAsString())) {
                        continue;
                    }
                    if ("labels".equals(damage)) {
                        final JsonArray labels = observation.getAsJsonArray("labels");
                        if (labels.size() < 2) {
                            continue;
                        }
                        final JsonElement first = labels.get(0);
                        labels.set(0, labels.get(1));
                        labels.set(1, first);
                        lines.set(i, observation.toString());
                    } else {
                        lines.remove(i);
                        final JsonObject footer = JsonParser.parseString(lines.getLast()).getAsJsonObject();
                        footer.addProperty("records", footer.get("records").getAsLong() - 1);
                        lines.set(lines.size() - 1, footer.toString());
                    }
                    break;
                }
                Files.write(trace, lines, StandardCharsets.UTF_8);
            }
            final Result rejected = launch("inspect", copy.toString(),
                    "smoke|h2|topology-only|fractal-w5|calibration|completeOccupancyRate|b20");
            assertEquals(2, rejected.code(), rejected.out() + rejected.err());
            assertTrue(rejected.err().contains("SHA-256") && rejected.err().contains("Recapture"), rejected.err());
            final Result summary = launch("summarize", copy.toString());
            assertEquals(0, summary.code(), summary.err());
            assertFalse(Files.readString(copy.resolve("summary.md")).contains("trace captured"));
        }
    }

    @Test
    public void inspectReconstructsLabelStabilityFromAdjacentRecordedLabelSets() {
        for (final String sectionMode : List.of("h1|topology-only", "h2|topology-only", "h2|all-rules")) {
            final Result result = launch("inspect", smokeRun.toString(),
                    "smoke|" + sectionMode + "|fractal-w5|calibration|labelStabilityJaccard|b20");
            assertEquals(0, result.code(), result.err());
            assertTrue(result.out().contains("adjacent label-set Jaccard mean="), result.out());
            assertTrue(result.out().contains("from 365 transitions") && result.out().contains("-> match"),
                    result.out());
            assertFalse(result.out().contains("not attributable"), result.out());
        }
    }

    @Test
    public void inspectReconstructsConfirmationLagFromRecordedCandidatePivots() throws Exception {
        for (final String sectionMode : List.of("h1|topology-only", "h2|topology-only", "h2|all-rules")) {
            final Result result = launch("inspect", smokeRun.toString(),
                    "smoke|" + sectionMode + "|fractal-w5|calibration|confirmationLagBars|b20");
            assertEquals(0, result.code(), result.err());
            assertTrue(result.out().contains("mean candidate-pivot confirmation lag=5 bars"), result.out());
            assertTrue(result.out().contains("complete candidate observations vs row 5 -> match"), result.out());
            assertTrue(result.out().contains("Supporting as-of indices ("), result.out());
            assertFalse(result.out().contains("not attributable"), result.out());
        }
        final Result member = launch("inspect", nullMemberRun.toString(),
                "smoke|h2|topology-only|fractal-w5|calibration|confirmationLagBars|b20");
        assertEquals(0, member.code(), member.err());
        assertTrue(
                member.out().contains("Null member confirmationLagBars: mean candidate-pivot confirmation lag=5 bars"),
                member.out());
        assertTrue(member.out().contains("Null member supporting as-of indices ("), member.out());

        final Path copy = work.resolve("confirmation-lag-changed-pivot");
        copyTree(smokeRun, copy);
        final String relative = "traces/smoke-real.jsonl";
        final Path trace = copy.resolve(relative);
        final List<String> lines = new ArrayList<>(Files.readAllLines(trace, StandardCharsets.UTF_8));
        boolean changed = false;
        for (int i = 1; i < lines.size() - 1; i++) {
            final JsonObject record = JsonParser.parseString(lines.get(i)).getAsJsonObject();
            if (!"h2".equals(record.get("section").getAsString())
                    || !"topology-only".equals(record.get("mode").getAsString())
                    || !"calibration".equals(record.get("partition").getAsString())
                    || !"COMPLETE".equals(record.get("status").getAsString())) {
                continue;
            }
            final int index = record.getAsJsonArray("candidates")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonArray("placement")
                    .get(0)
                    .getAsJsonObject()
                    .get("index")
                    .getAsInt();
            for (final JsonElement element : record.getAsJsonArray("pivots")) {
                final JsonObject pivot = element.getAsJsonObject();
                if (pivot.get("index").getAsInt() == index) {
                    pivot.addProperty("confirmationIndex", pivot.get("confirmationIndex").getAsInt() + 1);
                    changed = true;
                    break;
                }
            }
            lines.set(i, record.toString());
            break;
        }
        assertTrue(changed, "the fixture must change a completed candidate's confirmation lag");
        Files.write(trace, lines, StandardCharsets.UTF_8);
        final JsonObject run = readJson(copy.resolve("run.json"));
        run.getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("traceSha256")
                .addProperty(relative, HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(trace))));
        Files.writeString(copy.resolve("run.json"), run.toString());
        final Result mismatch = launch("inspect", copy.toString(),
                "smoke|h2|topology-only|fractal-w5|calibration|confirmationLagBars|b20");
        assertEquals(2, mismatch.code(), mismatch.out() + mismatch.err());
        assertTrue(mismatch.err().contains("candidate-pivot confirmation lags do not match")
                && mismatch.err().contains("Recapture with:"), mismatch.err());
    }

    @Test
    public void inspectChecksEveryRatioValueAndCountIncludingUnavailableRows() throws Exception {
        final List<ElliottResearchReport.Row> rows = ElliottResearchReport.readCsv(smokeRun.resolve("comparisons.csv"));
        final List<String> changes = List.of("observed-and-support", "proportional-support",
                "denominator-and-observed");
        final List<String> scopeMetrics = List.of("calibration|completeOccupancyRate", "calibration|ambiguousRate",
                "calibration|noMatchRate", "calibration|evidencePassRate", "calibration|jointPassRate",
                "validation|evidencePassRate", "validation|jointPassRate");
        for (int index = 0; index < scopeMetrics.size(); index++) {
            final String scopeMetric = scopeMetrics.get(index);
            final String key = "smoke|h2|all-rules|fractal-w5|" + scopeMetric + "|b20";
            final Result valid = launch("inspect", smokeRun.toString(), key);
            assertEquals(0, valid.code(), valid.err());
            assertTrue(valid.out().contains(scopeMetric.startsWith("validation|") ? "-> unavailable" : "-> match"),
                    valid.out());
            final Path copy = work.resolve("ratio-" + scopeMetric.replace('|', '-'));
            copyTree(smokeRun, copy);
            // Every metric is refused for one rotating change; the first unavailable
            // (zero-denominator) row branches differently and is refused for all three.
            final List<String> refused = "validation|evidencePassRate".equals(scopeMetric) ? changes
                    : List.of(changes.get(index % changes.size()));
            for (final String changed : refused) {
                final List<ElliottResearchReport.Row> mutated = new ArrayList<>(rows.size());
                for (final ElliottResearchReport.Row row : rows) {
                    if (!key.equals(row.key())) {
                        mutated.add(row);
                        continue;
                    }
                    final long numerator;
                    final long denominator;
                    if (row.denominator() == 0) {
                        numerator = "observed-and-support".equals(changed) ? 1 : 0;
                        denominator = "proportional-support".equals(changed) ? 2 : 1;
                    } else if ("observed-and-support".equals(changed)) {
                        numerator = row.numerator().equals(row.denominator()) ? row.numerator() - 1
                                : row.numerator() + 1;
                        denominator = row.denominator();
                    } else if ("proportional-support".equals(changed)) {
                        numerator = row.numerator() * 2;
                        denominator = row.denominator() * 2;
                    } else {
                        numerator = row.numerator();
                        denominator = row.denominator() + 1;
                    }
                    final double observed = numerator / (double) denominator;
                    final boolean replacedNullReference = row.validNullMembers() > 0;
                    mutated.add(new ElliottResearchReport.Row(row.dataset(), row.asset(), row.section(), row.mode(),
                            row.grammar(), row.activeRules(), row.detector(), row.policy(), row.partition(),
                            row.metric(), row.nullBlockLength(), observed, numerator, denominator,
                            row.validNullMembers(), row.requestedNullMembers(), row.unavailableNullMembers(),
                            replacedNullReference ? observed : row.nullMedian(),
                            replacedNullReference ? observed : row.nullLow(),
                            replacedNullReference ? observed : row.nullHigh(),
                            replacedNullReference ? 0.0d : row.observedMinusNullMedian(),
                            replacedNullReference ? 1.0d : row.empiricalReferenceRank(),
                            replacedNullReference ? ElliottResearchReport.AVAILABLE : row.availability()));
                }
                final Path csv = copy.resolve("comparisons.csv");
                ElliottResearchReport.writeCsv(csv, mutated);
                final JsonObject run = readJson(copy.resolve("run.json"));
                run.addProperty("comparisonsSha256",
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(csv))));
                Files.writeString(copy.resolve("run.json"), run.toString());
                final Result rejected = launch("inspect", copy.toString(), key);
                assertEquals(2, rejected.code(), changed + ": " + rejected.out() + rejected.err());
                assertTrue(rejected.err().contains("contributions do not match")
                        && rejected.err().contains("Recapture with:"), rejected.err());
            }
        }
    }

    @Test
    public void inspectRefusesEveryRatioWhenRecordedCompleteObservationIsMissing() throws Exception {
        final Path copy = work.resolve("ratios-missing-complete-observation");
        copyTree(smokeRun, copy);
        final String relative = "traces/smoke-real.jsonl";
        final Path trace = copy.resolve(relative);
        final List<String> lines = new ArrayList<>(Files.readAllLines(trace, StandardCharsets.UTF_8));
        boolean removed = false;
        for (int i = 1; i < lines.size() - 1; i++) {
            final JsonObject record = JsonParser.parseString(lines.get(i)).getAsJsonObject();
            if ("h2".equals(record.get("section").getAsString()) && "all-rules".equals(record.get("mode").getAsString())
                    && "calibration".equals(record.get("partition").getAsString())
                    && "COMPLETE".equals(record.get("status").getAsString())) {
                lines.remove(i);
                removed = true;
                break;
            }
        }
        assertTrue(removed, "the fixture must omit a completed observation");
        final JsonObject footer = JsonParser.parseString(lines.getLast()).getAsJsonObject();
        footer.addProperty("records", footer.get("records").getAsLong() - 1);
        lines.set(lines.size() - 1, footer.toString());
        Files.write(trace, lines, StandardCharsets.UTF_8);
        final JsonObject run = readJson(copy.resolve("run.json"));
        run.getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("traceSha256")
                .addProperty(relative, HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(trace))));
        Files.writeString(copy.resolve("run.json"), run.toString());
        for (final String metric : List.of("completeOccupancyRate", "ambiguousRate", "noMatchRate", "evidencePassRate",
                "jointPassRate")) {
            final Result rejected = launch("inspect", copy.toString(),
                    "smoke|h2|all-rules|fractal-w5|calibration|" + metric + "|b20");
            assertEquals(2, rejected.code(), rejected.out() + rejected.err());
            assertTrue(rejected.err().contains("trace " + metric + " contributions do not match")
                    && rejected.err().contains("Recapture with:"), rejected.err());
        }
    }

    @Test
    public void inspectIdentifiesUnavailableConfirmationLagWithoutClaimingANumericMatch() {
        for (final String key : List.of("smoke|competing|competing-3+3|fractal-w5|calibration|confirmationLagBars|b-",
                "smoke|h2|all-rules|fractal-w5|validation|confirmationLagBars|b20")) {
            final Result result = launch("inspect", smokeRun.toString(), key);
            assertEquals(0, result.code(), result.err());
            assertTrue(result.out().contains("unavailable (no complete topology candidates)"), result.out());
            assertFalse(result.out().contains("-> match"), result.out());
        }
    }

    private void assertModeRuleBinding(final Path source, final String traceName, final boolean nullMember)
            throws Exception {
        for (final boolean reorder : List.of(false, true)) {
            final Path copy = work.resolve("mode-bound-" + nullMember + "-" + reorder);
            copyTree(source, copy);
            final Path trace = copy.resolve("traces").resolve(traceName);
            final List<String> lines = new ArrayList<>(Files.readAllLines(trace, StandardCharsets.UTF_8));
            int recordLine = -1;
            JsonObject record = null;
            for (int i = 1; i < lines.size() - 1; i++) {
                final JsonObject observation = JsonParser.parseString(lines.get(i)).getAsJsonObject();
                if ((nullMember || "h2".equals(observation.get("section").getAsString()))
                        && observation.getAsJsonArray("activeRules").size() > 1
                        && !observation.getAsJsonArray("candidates").isEmpty()) {
                    recordLine = i;
                    record = observation;
                    break;
                }
            }
            assertTrue(recordLine > 0, "the captured trace must contain all-rules evidence");
            final JsonArray original = record.getAsJsonArray("activeRules");
            final JsonArray activeRules = new JsonArray();
            for (int i = 0; i < original.size(); i++) {
                activeRules.add(reorder ? original.get(original.size() - 1 - i).getAsString()
                        : "foreign-" + original.get(i).getAsString());
            }
            record.add("activeRules", activeRules);
            for (final JsonElement element : record.getAsJsonArray("candidates")) {
                final JsonObject candidate = element.getAsJsonObject();
                final JsonArray originalRules = candidate.getAsJsonArray("rules");
                final JsonArray rules = new JsonArray();
                for (int i = 0; i < originalRules.size(); i++) {
                    final JsonObject rule = originalRules.get(reorder ? originalRules.size() - 1 - i : i)
                            .getAsJsonObject();
                    rule.addProperty("id", activeRules.get(i).getAsString());
                    rules.add(rule);
                }
                candidate.add("rules", rules);
                final byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(rules.toString().getBytes(StandardCharsets.UTF_8));
                candidate.addProperty("version",
                        candidate.get("candidateKey").getAsString() + "@" + HexFormat.of().formatHex(digest, 0, 16));
            }
            lines.set(recordLine, record.toString());
            Files.write(trace, lines, StandardCharsets.UTF_8);
            final JsonObject observation = record;
            final List<ElliottResearchReport.Row> family = ElliottResearchReport
                    .readCsv(copy.resolve("comparisons.csv"))
                    .stream()
                    .filter(row -> "h2".equals(row.section())
                            && row.grammar().equals(observation.get("grammar").getAsString())
                            && row.partition().equals(observation.get("partition").getAsString()))
                    .toList();
            final ElliottResearchReport.Row allRules = family.stream()
                    .filter(row -> row.mode().equals(observation.get("mode").getAsString()))
                    .findFirst()
                    .orElseThrow();
            final List<String> keys = new ArrayList<>(List.of(allRules.key()));
            if (!nullMember) {
                keys.add(family.stream().filter(row -> row.activeRules().size() == 1).findFirst().orElseThrow().key());
            }
            for (final String key : keys) {
                final Result rejected = launch("inspect", copy.toString(), key);
                assertEquals(2, rejected.code(), rejected.out() + rejected.err());
                assertTrue(rejected.err().contains("activeRules"), rejected.err());
                assertTrue(rejected.err().contains("comparisons.csv mode"), rejected.err());
                assertTrue(rejected.err().contains("line " + (recordLine + 1)), rejected.err());
                assertTrue(rejected.err().contains("Recapture with:"), rejected.err());
            }
        }
    }
}
