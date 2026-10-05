/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.ta4j.core.BarSeries;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/**
 * Research-run artifact for optional corrective-family experiments.
 *
 * <p>
 * A run whose recipe declares {@code families} (which requires a
 * {@code hierarchy}) writes one sidecar file per dataset,
 * {@code families/<dataset>.jsonl}: a header that declares every profile with
 * its identity and bands, one {@code frame} line per observation that changed a
 * verdict or an extraction's bookkeeping, and a footer. Each verdict is the
 * {@link CorrectiveFamily} judgement of one parent candidate: envelope
 * predicates with actual values, per-leg subdivision evidence that points at
 * the causal child relation edges, and a bounded list of compositions. Frames
 * are immutable lifecycle events; the active verdict set at any as-of index is
 * recovered by replaying frames up to that index.
 *
 * <p>
 * The file is a research artifact, not a released API. It never feeds the base
 * grammar, the observation trace, the comparison rows or the reports, and a
 * {@code verified} verdict is structural evidence under one declared profile,
 * not a forecast or a measure of predictive efficacy.
 */
final class ElliottResearchFamilies {

    static final String SCHEMA = "elliott-research-families/1";
    static final String FAMILIES_DIR = "families";

    /**
     * Comparison status written to every file: verdicts are observed on the real
     * series only, so no matched real/null comparison rows exist.
     */
    static final String COMPARISON_STATUS = "descriptive";
    private static final String COMPARISON_REASON = "verdicts are observed on the real series under the relation"
            + " admission and lifecycle policy of the hierarchy; no matched null comparison is computed, so counts are"
            + " descriptive and not comparable across profiles";

    private ElliottResearchFamilies() {
    }

    static String fileName(final String datasetId) {
        return FAMILIES_DIR + "/" + datasetId + ".jsonl";
    }

    /**
     * The experimental profiles a recipe selected.
     *
     * @param maxCompositions most compositions one verdict lists
     * @param specs           selected profiles, each at most once
     */
    record Families(int maxCompositions, List<CorrectiveFamily.Spec> specs) {
        Families {
            specs = List.copyOf(specs);
            if (maxCompositions < 1) {
                throw new IllegalArgumentException(
                        "recipe.families.maxCompositions must be positive, was " + maxCompositions);
            }
            CorrectiveFamilyStudy.groupSpecs(specs);
        }

        /** The declared experiment as it appears under {@code recipe.families}. */
        JsonObject toJson() {
            final JsonObject json = new JsonObject();
            json.add("profiles", profilesJson());
            json.addProperty("maxCompositions", maxCompositions);
            return json;
        }

        JsonArray profilesJson() {
            final JsonArray array = new JsonArray();
            for (final CorrectiveFamily.Spec spec : specs) {
                final CorrectiveFamily.Profile profile = spec.profile();
                final JsonObject entry = new JsonObject();
                entry.addProperty("id", profile.id());
                entry.addProperty("version", spec.version());
                entry.addProperty("scenarioType", profile.scenarioType().name());
                entry.addProperty("subdivision", profile.subdivision());
                entry.addProperty("parentGrammar", profile.parentGrammar().name());
                final JsonArray children = new JsonArray();
                profile.childGrammars().forEach(grammar -> children.add(grammar.name()));
                entry.add("childGrammars", children);
                entry.addProperty("minRetracement",
                        spec.minRetracement() == null ? null : spec.minRetracement().toPlainString());
                entry.addProperty("maxOvershoot",
                        spec.maxOvershoot() == null ? null : spec.maxOvershoot().toPlainString());
                array.add(entry);
            }
            return array;
        }
    }

    /** Exact totals of one written family file. */
    record Totals(long frames, long events, long truncatedFrames, long incompleteFrames, int peakRetainedEdges,
            long truncatedCompositions) {
    }

    // ---------------------------------------------------------------- write

    /**
     * Replays the declared scales over {@code [start, end]} and writes the family
     * file.
     *
     * @return exact totals, also recorded in the footer
     * @throws IOException on filesystem failure
     */
    static Totals write(final Path file, final String datasetId, final String revision, final String fingerprint,
            final String sourceSha256, final ElliottResearchRelations.Hierarchy hierarchy, final Families families,
            final List<ScaleRelationStudy.ScaleInput> inputs, final List<RelationshipRule> childRules,
            final StudyRunner.Partitions partitions, final BarSeries series, final int start, final int end,
            final DetectorReplays replays) throws IOException {
        final ScaleRelation.Policy policy = ScaleRelation.Policy.defaults()
                .withInterior(hierarchy.interior())
                .withEdgeCap(hierarchy.edgeCap());
        final CorrectiveFamilyStudy study = new CorrectiveFamilyStudy(inputs, policy, childRules,
                ElliottResearchRelations.identity(), partitions, families.specs(), families.maxCompositions());
        final Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        final long[] counts = new long[5];
        final int[] peak = new int[1];
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            final JsonObject header = new JsonObject();
            header.addProperty("schema", SCHEMA);
            header.addProperty("dataset", datasetId);
            header.addProperty("revision", revision);
            header.addProperty("fingerprint", fingerprint);
            header.addProperty("sourceSha256", sourceSha256);
            hierarchy.toJson().entrySet().forEach(entry -> header.add(entry.getKey(), entry.getValue()));
            header.addProperty("profileRevision", CorrectiveFamily.PROFILE_REVISION);
            header.addProperty("maxCompositions", families.maxCompositions());
            header.add("profiles", families.profilesJson());
            header.addProperty("comparisonStatus", COMPARISON_STATUS);
            header.addProperty("comparisonReason", COMPARISON_REASON);
            final JsonArray groups = new JsonArray();
            for (final CorrectiveFamilyStudy.Group group : study.groups()) {
                final JsonObject entry = new JsonObject();
                entry.addProperty("id", group.id());
                final JsonArray profiles = new JsonArray();
                group.specs().forEach(spec -> profiles.add(spec.profile().id()));
                entry.add("profiles", profiles);
                groups.add(entry);
            }
            header.add("groups", groups);
            final JsonArray rules = new JsonArray();
            childRules.forEach(rule -> rules.add(rule.id()));
            header.add("childRules", rules);
            ElliottResearchRelations.writeLine(writer, header);
            try {
                study.run(series, start, end, replays, frame -> {
                    try {
                        ElliottResearchRelations.writeLine(writer, frameJson(datasetId, frame));
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    counts[0]++;
                    counts[1] += frame.events().size();
                    boolean truncated = false;
                    boolean incomplete = false;
                    for (final CorrectiveFamilyStudy.GroupCoverage group : frame.coverage()) {
                        truncated |= group.coverage().truncated();
                        incomplete |= group.coverage().incomplete();
                        peak[0] = Math.max(peak[0], group.coverage().edgesRetained());
                    }
                    counts[2] += truncated ? 1 : 0;
                    counts[3] += incomplete ? 1 : 0;
                    for (final CorrectiveFamilyStudy.Event event : frame.events()) {
                        if (event.lifecycle() == ScaleRelation.Lifecycle.ACTIVE
                                && event.verdict().compositionsTruncated()) {
                            counts[4]++;
                        }
                    }
                });
            } catch (final UncheckedIOException e) {
                throw e.getCause();
            }
            final JsonObject footer = new JsonObject();
            footer.addProperty("complete", true);
            footer.addProperty("frames", counts[0]);
            footer.addProperty("events", counts[1]);
            footer.addProperty("truncatedFrames", counts[2]);
            footer.addProperty("incompleteFrames", counts[3]);
            footer.addProperty("peakRetainedEdges", peak[0]);
            footer.addProperty("truncatedCompositions", counts[4]);
            ElliottResearchRelations.writeLine(writer, footer);
        }
        return new Totals(counts[0], counts[1], counts[2], counts[3], peak[0], counts[4]);
    }

    static JsonObject frameJson(final String datasetId, final CorrectiveFamilyStudy.Frame frame) {
        final JsonObject json = new JsonObject();
        json.addProperty("kind", "frame");
        json.addProperty("dataset", datasetId);
        json.addProperty("partition", frame.partition());
        json.addProperty("asOfIndex", frame.asOfIndex());
        json.addProperty("asOfTime", frame.asOfTime().toString());
        final JsonArray coverage = new JsonArray();
        for (final CorrectiveFamilyStudy.GroupCoverage group : frame.coverage()) {
            final JsonObject entry = ElliottResearchRelations.coverageJson(group.coverage());
            entry.addProperty("group", group.group());
            coverage.add(entry);
        }
        json.add("coverage", coverage);
        final JsonArray events = new JsonArray();
        for (final CorrectiveFamilyStudy.Event event : frame.events()) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("lifecycle", label(event.lifecycle().name()));
            entry.addProperty("reason", label(event.reason().name()));
            entry.add("verdict", verdictJson(event.verdict()));
            events.add(entry);
        }
        json.add("events", events);
        return json;
    }

    static JsonObject verdictJson(final CorrectiveFamily.Verdict verdict) {
        final CorrectiveFamily.Profile profile = verdict.spec().profile();
        final JsonObject json = new JsonObject();
        json.addProperty("key", verdict.key());
        json.addProperty("version", verdict.version());
        json.addProperty("profile", profile.id());
        json.addProperty("profileVersion", verdict.spec().version());
        json.addProperty("scenarioType", profile.scenarioType().name());
        json.addProperty("subdivision", profile.subdivision());
        json.addProperty("parentScale", verdict.parentScale());
        json.addProperty("parentCandidateKey", verdict.parentCandidateKey());
        json.addProperty("parentVersion", verdict.parentVersion());
        json.addProperty("parentDirection", verdict.parentDirection().name());
        final JsonArray pivots = new JsonArray();
        verdict.parentPivots().forEach(pivot -> pivots.add(ElliottResearchRelations.pivotJson(pivot)));
        json.add("parentPivots", pivots);
        json.addProperty("status", verdict.status().label());
        json.addProperty("reason", verdict.reason());
        json.add("envelope", ElliottResearchRelations.predicatesJson(verdict.envelope()));
        final JsonArray legs = new JsonArray();
        for (final CorrectiveFamily.Leg leg : verdict.legs()) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("leg", leg.leg());
            entry.addProperty("expected", leg.expected().name());
            entry.addProperty("state", leg.state().label());
            final JsonArray edges = new JsonArray();
            for (final CorrectiveFamily.ChildEdge edge : leg.edges()) {
                final JsonObject child = new JsonObject();
                child.addProperty("key", edge.key());
                child.addProperty("version", edge.version());
                child.addProperty("state", edge.state().label());
                child.addProperty("childCandidateKey", edge.childCandidateKey());
                child.addProperty("childPivotCount", edge.childPivotCount());
                child.addProperty("availableAt", edge.availableAt());
                child.add("predicates", ElliottResearchRelations.predicatesJson(edge.predicates()));
                edges.add(child);
            }
            entry.add("edges", edges);
            legs.add(entry);
        }
        json.add("legs", legs);
        final JsonArray compositions = new JsonArray();
        for (final CorrectiveFamily.Composition composition : verdict.compositions()) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("key", composition.key());
            final JsonArray keys = new JsonArray();
            composition.edgeKeys().forEach(keys::add);
            entry.add("edgeKeys", keys);
            compositions.add(entry);
        }
        json.add("compositions", compositions);
        json.addProperty("compositionCount", verdict.compositionCount());
        json.addProperty("compositionsTruncated", verdict.compositionsTruncated());
        json.addProperty("evidenceComplete", verdict.evidenceComplete());
        json.addProperty("availableAt", verdict.availableAt());
        return json;
    }

    private static String label(final String name) {
        return name.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    // ----------------------------------------------------------------- read

    /**
     * Streams a family file, validating every line and handing each frame to
     * {@code sink}; memory is bounded by what the sink retains.
     *
     * @throws IllegalArgumentException naming file and line when the schema is
     *                                  unsupported or a line is corrupt
     */
    static ElliottResearchRelations.Meta read(final Path path, final Consumer<JsonObject> sink) throws IOException {
        return ElliottResearchRelations.read(path, SCHEMA, ElliottResearchFamilies::requireFrame, sink);
    }

    private static void requireFrame(final Path path, final long line, final JsonObject frame) {
        for (final String field : List.of("asOfIndex", "partition", "coverage", "events")) {
            if (!frame.has(field) || frame.get(field).isJsonNull()) {
                throw ElliottResearchRelations.corrupt(path, line, "frame lacks " + field);
            }
        }
        try {
            frame.get("asOfIndex").getAsInt();
            for (final JsonElement group : frame.getAsJsonArray("coverage")) {
                group.getAsJsonObject().get("group").getAsString();
                group.getAsJsonObject().get("truncated").getAsBoolean();
                group.getAsJsonObject().get("incomplete").getAsBoolean();
            }
            for (final JsonElement event : frame.getAsJsonArray("events")) {
                final JsonObject object = event.getAsJsonObject();
                final JsonObject verdict = object.getAsJsonObject("verdict");
                verdict.get("key").getAsString();
                verdict.get("profile").getAsString();
                verdict.get("status").getAsString();
                object.get("lifecycle").getAsString();
                object.get("reason").getAsString();
            }
        } catch (final RuntimeException e) {
            throw ElliottResearchRelations.corrupt(path, line, "malformed frame: " + e.getMessage());
        }
    }

    // --------------------------------------------------------------- replay

    /**
     * Active verdicts at one as-of index and the evidence behind them.
     *
     * @param everVerified verdicts per profile that were {@code verified} at some
     *                     frame up to the cursor
     * @param latestStatus latest status per profile of every verdict observed up to
     *                     the cursor, keyed {@code profile/status}
     */
    record Replay(Integer cursor, String cursorTime, JsonArray coverage, Map<String, JsonObject> active,
            Map<String, Integer> everVerified, Map<String, Integer> latestStatus, Map<String, Integer> endedByReason,
            List<JsonObject> history, long truncatedFrames, long incompleteFrames) {
    }

    /**
     * Replays frames up to and including {@code asOf}.
     *
     * @param asOf       last as-of index to include, or {@code null} for the whole
     *                   file
     * @param verdictKey verdict whose events are collected into
     *                   {@link Replay#history()}, or {@code null}
     */
    static Replay replay(final Path path, final Integer asOf, final String verdictKey) throws IOException {
        final Map<String, JsonObject> active = new TreeMap<>();
        final Map<String, JsonObject> latest = new TreeMap<>();
        final Set<String> verified = new HashSet<>();
        final Map<String, Integer> everVerified = new TreeMap<>();
        final Map<String, Integer> ended = new TreeMap<>();
        final List<JsonObject> history = new ArrayList<>();
        final Object[] cursor = new Object[3];
        final long[] flags = new long[2];
        read(path, frame -> {
            final int index = frame.get("asOfIndex").getAsInt();
            if (asOf != null && index > asOf) {
                return;
            }
            cursor[0] = index;
            cursor[1] = ElliottResearchRelations.text(frame, "asOfTime");
            final JsonArray coverage = frame.getAsJsonArray("coverage");
            cursor[2] = coverage;
            boolean truncated = false;
            boolean incomplete = false;
            for (final JsonElement group : coverage) {
                truncated |= group.getAsJsonObject().get("truncated").getAsBoolean();
                incomplete |= group.getAsJsonObject().get("incomplete").getAsBoolean();
            }
            flags[0] += truncated ? 1 : 0;
            flags[1] += incomplete ? 1 : 0;
            for (final JsonElement element : frame.getAsJsonArray("events")) {
                final JsonObject event = element.getAsJsonObject();
                final JsonObject verdict = event.getAsJsonObject("verdict");
                final String key = verdict.get("key").getAsString();
                if ("ended".equals(event.get("lifecycle").getAsString())) {
                    active.remove(key);
                    ended.merge(event.get("reason").getAsString(), 1, Integer::sum);
                } else {
                    active.put(key, verdict);
                    latest.put(key, verdict);
                    if ("verified".equals(verdict.get("status").getAsString()) && verified.add(key)) {
                        everVerified.merge(verdict.get("profile").getAsString(), 1, Integer::sum);
                    }
                }
                if (key.equals(verdictKey)) {
                    final JsonObject entry = event.deepCopy();
                    entry.addProperty("asOfIndex", index);
                    history.add(entry);
                }
            }
        });
        final Map<String, Integer> latestStatus = new TreeMap<>();
        latest.values()
                .forEach(verdict -> latestStatus.merge(
                        verdict.get("profile").getAsString() + "/" + verdict.get("status").getAsString(), 1,
                        Integer::sum));
        return new Replay((Integer) cursor[0], (String) cursor[1], (JsonArray) cursor[2], active, everVerified,
                latestStatus, ended, history, flags[0], flags[1]);
    }

    // --------------------------------------------------------------- render

    /**
     * Prints the verdict set at an as-of index.
     *
     * @param out        destination
     * @param path       family file
     * @param asOf       as-of index, or {@code null} for the last recorded frame
     * @param limit      most verdicts printed
     * @param verdictKey verdict whose full event history is also printed, or
     *                   {@code null}
     * @return {@code false} when the file is incomplete
     */
    static boolean print(final PrintStream out, final Path path, final Integer asOf, final int limit,
            final String verdictKey) throws IOException {
        final ElliottResearchRelations.Meta meta = read(path, frame -> {
        });
        final Replay replay = replay(path, asOf, verdictKey);
        final JsonObject header = meta.header();
        out.println("Families: " + path);
        out.println("Dataset: " + ElliottResearchRelations.text(header, "dataset") + "  profiles: "
                + profileNames(header) + "  scales: " + scaleNames(header) + "  edge cap: "
                + header.get("edgeCap").getAsInt() + "  max compositions: " + header.get("maxCompositions").getAsInt());
        out.println("Note: a verdict is structural evidence under one declared experimental profile; it is not a"
                + " forecast and does not rescue or replace a base grammar count.");
        out.println("Comparison: " + ElliottResearchRelations.text(header, "comparisonStatus") + " - "
                + ElliottResearchRelations.text(header, "comparisonReason") + ".");
        if (!meta.complete()) {
            out.println("Note: the file has no footer; the run was interrupted and the verdicts are incomplete.");
        }
        if (replay.cursor() == null) {
            out.println("No family frame at or before index " + asOf + "; no verdict was observable yet.");
            return meta.complete();
        }
        out.println("As of index " + replay.cursor() + " (" + replay.cursorTime() + "): " + replay.active().size()
                + " active verdict(s)");
        for (final JsonElement element : replay.coverage()) {
            final JsonObject coverage = element.getAsJsonObject();
            out.println("Coverage " + coverage.get("group").getAsString() + ": parentCandidates="
                    + coverage.get("parentCandidates").getAsInt() + " legsChecked="
                    + coverage.get("legsChecked").getAsInt() + " generated=" + coverage.get("edgesGenerated").getAsInt()
                    + " retained=" + coverage.get("edgesRetained").getAsInt() + " omitted="
                    + coverage.get("edgesOmitted").getAsInt() + " cap=" + coverage.get("edgeCap").getAsInt()
                    + " decompositionLegsTruncated=" + coverage.get("decompositionLegsTruncated").getAsInt());
            if (coverage.get("incomplete").getAsBoolean()) {
                out.println("Note: a bound was hit in group " + coverage.get("group").getAsString()
                        + " at this observation; absent evidence is not proof of absent structure.");
            }
        }
        final Map<String, Integer> byProfileStatus = new TreeMap<>();
        replay.active()
                .values()
                .forEach(verdict -> byProfileStatus.merge(
                        verdict.get("profile").getAsString() + "/" + verdict.get("status").getAsString(), 1,
                        Integer::sum));
        out.println("Active by profile/status: " + (byProfileStatus.isEmpty() ? "(none)" : byProfileStatus));
        final List<JsonObject> verdicts = replay.active()
                .values()
                .stream()
                .sorted(Comparator.comparing((JsonObject verdict) -> verdict.get("profile").getAsString())
                        .thenComparing(verdict -> verdict.get("parentScale").getAsString())
                        .thenComparing(verdict -> verdict.getAsJsonArray("parentPivots")
                                .get(0)
                                .getAsJsonObject()
                                .get("index")
                                .getAsInt())
                        .thenComparing(verdict -> verdict.get("key").getAsString()))
                .toList();
        int printed = 0;
        for (final JsonObject verdict : verdicts) {
            if (printed++ >= limit) {
                out.println("... " + (verdicts.size() - limit) + " more (raise --limit)");
                break;
            }
            out.println(describe(verdict));
            evidence(verdict, "      ").forEach(out::println);
        }
        if (verdictKey != null) {
            out.println("History of verdict " + verdictKey + ":");
            if (replay.history().isEmpty()) {
                out.println("  (no event for this verdict up to the cursor)");
            }
            for (final JsonObject event : replay.history()) {
                final JsonObject verdict = event.getAsJsonObject("verdict");
                out.println("  @" + event.get("asOfIndex").getAsInt() + " " + event.get("lifecycle").getAsString()
                        + " " + event.get("reason").getAsString() + " status=" + verdict.get("status").getAsString()
                        + " (" + verdict.get("reason").getAsString() + ") version="
                        + verdict.get("version").getAsString() + " availableAt="
                        + verdict.get("availableAt").getAsInt());
                evidence(verdict, "      ").forEach(out::println);
            }
        }
        return meta.complete();
    }

    private static String describe(final JsonObject verdict) {
        final JsonArray pivots = verdict.getAsJsonArray("parentPivots");
        final StringBuilder line = new StringBuilder("  ").append(verdict.get("key").getAsString())
                .append(' ')
                .append(verdict.get("profile").getAsString())
                .append(' ')
                .append(verdict.get("status").getAsString())
                .append(" (")
                .append(verdict.get("reason").getAsString())
                .append(") parent ")
                .append(verdict.get("parentScale").getAsString())
                .append(" [")
                .append(pivots.get(0).getAsJsonObject().get("index").getAsInt())
                .append("..")
                .append(pivots.get(pivots.size() - 1).getAsJsonObject().get("index").getAsInt())
                .append("] ")
                .append(verdict.get("parentDirection").getAsString())
                .append(" subdivision ")
                .append(verdict.get("subdivision").getAsString())
                .append(" legs ");
        final List<String> legs = new ArrayList<>();
        for (final JsonElement element : verdict.getAsJsonArray("legs")) {
            legs.add(element.getAsJsonObject().get("state").getAsString());
        }
        line.append(String.join("/", legs));
        line.append(" compositions=").append(verdict.get("compositionCount").getAsLong());
        if (verdict.get("compositionsTruncated").getAsBoolean()) {
            line.append(" (list truncated at ").append(verdict.getAsJsonArray("compositions").size()).append(')');
        }
        line.append(" availableAt=").append(verdict.get("availableAt").getAsInt());
        return line.toString();
    }

    /**
     * Evidence lines of one stored verdict version: the declared profile, every
     * envelope predicate, then each parent leg with the child relations behind it.
     */
    private static List<String> evidence(final JsonObject verdict, final String indent) {
        final List<String> lines = new ArrayList<>();
        lines.add(indent + "profile " + verdict.get("profileVersion").getAsString() + " scenario "
                + verdict.get("scenarioType").getAsString() + " evidenceComplete="
                + verdict.get("evidenceComplete").getAsBoolean());
        final List<String> pivots = new ArrayList<>();
        for (final JsonElement element : verdict.getAsJsonArray("parentPivots")) {
            final JsonObject pivot = element.getAsJsonObject();
            pivots.add(pivot.get("index").getAsInt() + " " + pivot.get("type").getAsString() + " "
                    + pivot.get("price").getAsString() + " (confirmed @" + pivot.get("confirmationIndex").getAsInt()
                    + ")");
        }
        lines.add(indent + "parent pivots: " + String.join(" -> ", pivots));
        for (final JsonElement element : verdict.getAsJsonArray("envelope")) {
            final JsonObject predicate = element.getAsJsonObject();
            lines.add(indent + "envelope " + predicate.get("id").getAsString() + " "
                    + predicate.get("state").getAsString() + ": " + predicate.get("detail").getAsString());
        }
        for (final JsonElement element : verdict.getAsJsonArray("legs")) {
            final JsonObject leg = element.getAsJsonObject();
            lines.add(indent + "leg " + (leg.get("leg").getAsInt() + 1) + " expects " + leg.get("expected").getAsString()
                    + ": " + leg.get("state").getAsString());
            for (final JsonElement edgeElement : leg.getAsJsonArray("edges")) {
                final JsonObject edge = edgeElement.getAsJsonObject();
                lines.add(indent + "    relation " + edge.get("key").getAsString() + " "
                        + edge.get("state").getAsString() + " pivots=" + edge.get("childPivotCount").getAsInt()
                        + " availableAt=" + edge.get("availableAt").getAsInt());
                for (final JsonElement predicateElement : edge.getAsJsonArray("predicates")) {
                    final JsonObject predicate = predicateElement.getAsJsonObject();
                    final String state = predicate.get("state").getAsString();
                    if (!"PASS".equals(state)) {
                        lines.add(indent + "        " + predicate.get("id").getAsString() + " " + state + ": "
                                + predicate.get("detail").getAsString());
                    }
                }
            }
        }
        for (final JsonElement element : verdict.getAsJsonArray("compositions")) {
            final JsonObject composition = element.getAsJsonObject();
            final List<String> keys = new ArrayList<>();
            composition.getAsJsonArray("edgeKeys").forEach(key -> keys.add(key.getAsString()));
            lines.add(indent + "composition " + composition.get("key").getAsString() + ": "
                    + String.join(" + ", keys));
        }
        if (verdict.get("compositionsTruncated").getAsBoolean()) {
            lines.add(indent + "note: " + verdict.get("compositionCount").getAsLong() + " compositions exist but only "
                    + verdict.getAsJsonArray("compositions").size() + " are listed; the list is truncated");
        }
        return lines;
    }

    private static String profileNames(final JsonObject header) {
        final List<String> names = new ArrayList<>();
        for (final JsonElement element : header.getAsJsonArray("profiles")) {
            names.add(element.getAsJsonObject().get("version").getAsString());
        }
        return String.join(", ", names);
    }

    private static String scaleNames(final JsonObject header) {
        final List<String> names = new ArrayList<>();
        for (final JsonElement element : header.getAsJsonArray("scales")) {
            names.add(element.getAsJsonObject().get("name").getAsString());
        }
        return String.join(" > ", names);
    }

    /**
     * Markdown lines summarising one dataset's family file for {@code summary.md}.
     * The file is cross-checked against the run before any claim is made.
     *
     * @param expectedHeader header fields the file must carry to belong to the run
     */
    static List<String> summaryLines(final Path path, final String datasetId, final JsonObject expectedHeader) {
        final List<String> lines = new ArrayList<>();
        try {
            final ElliottResearchRelations.Meta meta = read(path, frame -> {
            });
            for (final Map.Entry<String, JsonElement> field : expectedHeader.entrySet()) {
                final JsonElement actual = meta.header().get(field.getKey());
                if (!field.getValue().equals(actual == null ? JsonNull.INSTANCE : actual)) {
                    lines.add("- `" + datasetId + "`: family file does not belong to this run (header "
                            + field.getKey() + " differs); rerun the recipe");
                    return lines;
                }
            }
            if (!meta.complete()) {
                lines.add("- `" + datasetId + "`: family file is incomplete (no footer); rerun the recipe");
                return lines;
            }
            final Replay replay = replay(path, null, null);
            final JsonObject footer = meta.footer();
            lines.add("- `" + datasetId + "`: " + profileNames(meta.header()) + ", " + meta.frames() + " frame(s), "
                    + footer.get("events").getAsLong() + " event(s); latest status of every observed verdict "
                    + (replay.latestStatus().isEmpty() ? "(none)" : replay.latestStatus())
                    + "; ever verified " + (replay.everVerified().isEmpty() ? "(none)" : replay.everVerified())
                    + "; ended by reason " + (replay.endedByReason().isEmpty() ? "(none)" : replay.endedByReason())
                    + "; frames with omitted edges " + footer.get("truncatedFrames").getAsLong()
                    + ", with an unexamined bound " + footer.get("incompleteFrames").getAsLong()
                    + "; verdicts with truncated compositions " + footer.get("truncatedCompositions").getAsLong()
                    + "; comparison " + ElliottResearchRelations.text(meta.header(), "comparisonStatus")
                    + " (no matched null rows)");
        } catch (final IOException | IllegalArgumentException unreadable) {
            lines.add("- `" + datasetId + "`: family file unreadable (" + unreadable.getMessage()
                    + "); rerun the recipe");
        }
        return lines;
    }
}
