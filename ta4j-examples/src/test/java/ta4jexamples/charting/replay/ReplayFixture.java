/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;

/**
 * Writes a small but complete {@code elliott-research-run/1} bundle in the
 * persisted format of the research recorder, so the replay adapter is tested
 * against the file contract and not against test classes of another module.
 *
 * <p>
 * The bundle holds {@value #BARS} daily bars, the complete configured row
 * inventory and its six recorded streams: motive topology, four cycle ablations
 * and the competing recognizer. All candidate pivots are strictly ordered,
 * alternating and confirmed before first appearance. Bullish cycle candidate A
 * exists from bar 23 and has revised rule evidence at bar 33; bearish candidate
 * B exists on bars 33 to 40. Their keys and versions follow the research writer
 * protocol.
 */
final class ReplayFixture {

    static final int BARS = 60;
    static final int FIRST_AS_OF = 10;
    static final String DATASET = "d1";
    static final String FINGERPRINT = "fixture-fingerprint";
    static final String RULES_KEY = "d1|h2|all-rules|fractal-w5|calibration|ambiguousRate|b20";
    static final String TOPOLOGY_KEY = "d1|h1|topology-only|fractal-w5|calibration|ambiguousRate|b20";
    static final String COMPETING_KEY = "d1|competing|competing-3+3|fractal-w5|calibration|ambiguousRate|b-";
    static final Instant START = Instant.parse("2020-01-01T00:00:00Z");

    private static final int[][] PIVOTS = { { 0, 0, 0 }, { 1, 1, 1 }, { 2, 0, 2 }, { 5, 1, 8 }, { 12, 0, 15 },
            { 20, 1, 23 }, { 30, 0, 33 }, { 40, 1, 43 } };
    private static final int[][] CYCLE_PIVOTS = { { 0, 0, 0 }, { 1, 1, 1 }, { 2, 0, 2 }, { 3, 1, 3 }, { 4, 0, 4 },
            { 5, 1, 8 }, { 6, 0, 9 }, { 12, 1, 15 }, { 20, 0, 23 }, { 30, 1, 33 }, { 40, 0, 43 } };
    static final String CANDIDATE_A = candidate(0, false, true).get("candidateKey").getAsString();
    static final String CANDIDATE_B = candidate(1, false, true).get("candidateKey").getAsString();
    static final String A_VERSION_1 = candidate(0, false, true).get("version").getAsString();
    static final String A_VERSION_2 = candidate(0, true, true).get("version").getAsString();
    static final String B_VERSION_1 = candidate(1, false, true).get("version").getAsString();
    /** Price offset of recorded null-member bars versus the real bars. */
    static final int NULL_BAR_OFFSET = 1000;
    private static final String HEADER = "key,dataset,asset,section,mode,grammar,activeRules,detector,policy,partition,metric,nullBlockLength,observed,numerator,denominator,validNullMembers,requestedNullMembers,unavailableNullMembers,nullMedian,nullLow,nullHigh,observedMinusNullMedian,empiricalReferenceRank,availability";

    private ReplayFixture() {
    }

    /** Fixture knobs; defaults produce a valid bundle. */
    static final class Options {
        /** First bar index whose prices are replaced by different ones, or -1. */
        int mutateBarsFrom = -1;
        /** Emits a pivot that is only confirmed after the record's as-of bar. */
        boolean leakFuturePivot;
        /** Omits the completion footer of the trace. */
        boolean truncateTrace;
        /** Omits the priceBars entry from run.json. */
        boolean omitPriceBars;
        /** Skips writing the bars sidecar file. */
        boolean omitBarsFile;
        /** Writes the trace without a trace entry in the dataset. */
        boolean omitTrace;
        /** Source digest recorded in the run's dataset, or null for none. */
        String datasetSource;
        /** Source digest recorded in the trace headers, or null for none. */
        String traceSource;
        /** Also writes a selected-null-member trace with recorded member bars. */
        boolean nullTrace;
        /** Omits the bar record from the null trace. */
        boolean nullTraceWithoutBars;
        /** Block length written in the null trace header and records. */
        int nullBlockLength = 20;
        String runSchema = "elliott-research-run/1";
        String traceSchema = "elliott-research-trace/1";
        String status = "complete";
    }

    static Options options() {
        return new Options();
    }

    static Path write(final Path directory) {
        return write(directory, options());
    }

    static Path write(final Path directory, final UnaryOperator<Options> customize) {
        return write(directory, customize.apply(options()));
    }

    static Path write(final Path directory, final Options options) {
        try {
            Files.createDirectories(directory.resolve("bars"));
            Files.createDirectories(directory.resolve("traces"));
            final StringBuilder bars = new StringBuilder("index,beginTime,endTime,open,high,low,close,volume\n");
            for (int i = 0; i < BARS; i++) {
                final int shift = options.mutateBarsFrom >= 0 && i >= options.mutateBarsFrom ? 500 : 0;
                bars.append(i)
                        .append(',')
                        .append(START.plus(i, ChronoUnit.DAYS))
                        .append(',')
                        .append(START.plus(i + 1L, ChronoUnit.DAYS))
                        .append(',')
                        .append(close(i) - 1 + shift)
                        .append(',')
                        .append(high(i) + shift)
                        .append(',')
                        .append(low(i) + shift)
                        .append(',')
                        .append(close(i) + shift)
                        .append(",1\n");
            }
            final byte[] barBytes = bars.toString().getBytes(StandardCharsets.UTF_8);
            if (!options.omitBarsFile) {
                Files.write(directory.resolve("bars/d1.csv"), barBytes);
            }
            final JsonObject dataset = new JsonObject();
            dataset.addProperty("id", DATASET);
            dataset.addProperty("asset", "FIXTURE");
            dataset.addProperty("status", "complete");
            final JsonArray traces = new JsonArray();
            if (!options.omitTrace) {
                traces.add("traces/d1-real.jsonl");
            }
            if (options.nullTrace) {
                traces.add("traces/d1-null.jsonl");
            }
            dataset.add("traces", traces);
            if (options.datasetSource != null) {
                final JsonObject source = new JsonObject();
                source.addProperty("sha256", options.datasetSource);
                dataset.add("source", source);
            }
            if (!options.omitPriceBars) {
                final JsonObject priceBars = new JsonObject();
                priceBars.addProperty("path", "bars/d1.csv");
                priceBars.addProperty("sha256", ReplayArtifact.sha256(barBytes));
                priceBars.addProperty("rows", BARS);
                dataset.add("priceBars", priceBars);
            }
            final JsonObject coverage = new JsonObject();
            coverage.addProperty("status", "complete");
            coverage.addProperty("message", "");
            dataset.add("coverage", coverage);
            final JsonObject run = new JsonObject();
            run.addProperty("artifactSchemaVersion", options.runSchema);
            run.addProperty("revision", "fixture-revision");
            run.addProperty("status", options.status);
            final JsonObject identity = new JsonObject();
            identity.addProperty("fingerprint", FINGERPRINT);
            run.add("identity", identity);
            final JsonObject runTrace = new JsonObject();
            runTrace.addProperty("mode", options.nullTrace ? "selected-null-member" : "real");
            runTrace.add("block", options.nullTrace ? new JsonPrimitive(options.nullBlockLength) : JsonNull.INSTANCE);
            runTrace.add("member", options.nullTrace ? new JsonPrimitive(0) : JsonNull.INSTANCE);
            run.add("trace", runTrace);
            final JsonArray datasets = new JsonArray();
            datasets.add(dataset);
            run.add("datasets", datasets);
            run.add("recipe", JsonParser.parseString("{\"name\":\"explore\"}"));
            run.add("configuration", JsonParser.parseString("""
                    {"partitions":[{"name":"calibration"}],
                     "detector":{"name":"fractal-w5","factory":"fractal","params":[5]},
                     "robustnessDetectors":[],"activeRules":["wave2-origin","wave3-not-shortest"],
                     "momentum":{"type":"RSI","barCount":14},"competingModes":["3+3"],
                     "null":{"blockLengths":[20],"ensembleSize":5,"seed":42}}
                    """));
            Files.writeString(directory.resolve("comparisons.csv"), comparisons(), StandardCharsets.UTF_8);
            run.addProperty("comparisonsSha256",
                    ReplayArtifact.sha256(Files.readAllBytes(directory.resolve("comparisons.csv"))));
            writeTrace(directory.resolve("traces/d1-real.jsonl"), options, false);
            if (options.nullTrace) {
                writeTrace(directory.resolve("traces/d1-null.jsonl"), options, true);
            }
            final JsonObject traceSha256 = new JsonObject();
            for (final var trace : traces) {
                final String relative = trace.getAsString();
                traceSha256.addProperty(relative,
                        ReplayArtifact.sha256(Files.readAllBytes(directory.resolve(relative))));
            }
            dataset.add("traceSha256", traceSha256);
            Files.writeString(directory.resolve("run.json"), run.toString(), StandardCharsets.UTF_8);
            return directory;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void refreshComparisonsDigest(final Path directory) throws IOException {
        final Path metadata = directory.resolve("run.json");
        final JsonObject run = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
        run.addProperty("comparisonsSha256",
                ReplayArtifact.sha256(Files.readAllBytes(directory.resolve("comparisons.csv"))));
        Files.writeString(metadata, run.toString(), StandardCharsets.UTF_8);
    }

    static void refreshTraceDigest(final Path directory, final String relative) throws IOException {
        final Path metadata = directory.resolve("run.json");
        final JsonObject run = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
        run.getAsJsonArray("datasets")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("traceSha256")
                .addProperty(relative, ReplayArtifact.sha256(Files.readAllBytes(directory.resolve(relative))));
        Files.writeString(metadata, run.toString(), StandardCharsets.UTF_8);
    }

    private static String comparisons() {
        final List<String> metrics = List.of("ambiguousRate", "completeOccupancyRate", "noMatchRate",
                "confirmationLagBars", "labelStabilityJaccard", "evidencePassRate", "jointPassRate");
        final StringBuilder csv = new StringBuilder(HEADER).append('\n');
        csv.append(row("h2", "all-rules", List.of("wave2-origin", "wave3-not-shortest"), "ambiguousRate")).append('\n');
        csv.append(row("h1", "topology-only", List.of(), "ambiguousRate")).append('\n');
        csv.append(row("competing", "competing-3+3", List.of(), "ambiguousRate")).append('\n');
        for (final String mode : List.of("all-rules", "h1", "competing-3+3", "topology-only", "+wave2-origin",
                "+wave3-not-shortest")) {
            final String section = "h1".equals(mode) ? "h1" : mode.startsWith("competing-") ? "competing" : "h2";
            final String recordedMode = "h1".equals(mode) ? "topology-only" : mode;
            final List<String> rules = mode.startsWith("+") ? List.of(mode.substring(1))
                    : "all-rules".equals(mode) ? List.of("wave2-origin", "wave3-not-shortest") : List.of();
            for (final String metric : metrics) {
                if (rules.isEmpty() && ("evidencePassRate".equals(metric) || "jointPassRate".equals(metric))
                        || "ambiguousRate".equals(metric)
                                && ("all-rules".equals(mode) || "h1".equals(mode) || "competing-3+3".equals(mode))) {
                    continue;
                }
                csv.append(row(section, recordedMode, rules, metric)).append('\n');
            }
        }
        return csv.toString();
    }

    private static String row(final String section, final String mode, final List<String> rules, final String metric) {
        final boolean competing = "competing".equals(section);
        final String grammar = competing ? "3+3" : "h1".equals(section) ? "MOTIVE_5" : "CYCLE_5_3";
        final boolean rate = !"confirmationLagBars".equals(metric) && !"labelStabilityJaccard".equals(metric);
        final long denominator = switch (metric) {
        case "completeOccupancyRate", "ambiguousRate", "noMatchRate" -> 50;
        case "evidencePassRate" -> 29L * rules.size();
        case "jointPassRate" -> 29;
        default -> 0;
        };
        final long numerator = switch (metric) {
        case "completeOccupancyRate" -> competing ? 0 : 29;
        case "ambiguousRate" -> competing ? 27 : 8;
        case "noMatchRate" -> 13;
        case "evidencePassRate" -> rules.contains("wave2-origin") ? 29 : 0;
        case "jointPassRate" -> rules.equals(List.of("wave2-origin")) ? 29 : 0;
        default -> 0;
        };
        final double observed;
        if (rate) {
            observed = numerator / (double) denominator;
        } else if ("labelStabilityJaccard".equals(metric)) {
            observed = (competing ? 46.0d : 47.0d) / 49;
        } else if (competing) {
            observed = Double.NaN;
        } else if ("h1".equals(section)) {
            observed = 1.5d;
        } else {
            double sum = 0;
            for (int i = 0; i < 29; i++) {
                sum += 12.0d / 9;
            }
            observed = sum / 29;
        }
        final String value = Double.isNaN(observed) ? "" : Double.toString(observed);
        final String block = competing ? "" : "20";
        final String key = String.join("|", DATASET, section, mode, "fractal-w5", "calibration", metric,
                competing ? "b-" : "b20");
        return String.join(",", key, DATASET, "FIXTURE", section, mode, grammar, String.join(";", rules), "fractal-w5",
                competing ? "unmatched-recognizer" : rules.isEmpty() ? "kernel-topology" : "kernel-topology+evidence",
                "calibration", metric, block, value, rate ? Long.toString(numerator) : "",
                rate ? Long.toString(denominator) : "", competing ? "" : Double.isNaN(observed) ? "0" : "5",
                competing ? "" : "5", competing ? "" : Double.isNaN(observed) ? "5" : "0", competing ? "" : value,
                competing ? "" : value, competing ? "" : value, competing || Double.isNaN(observed) ? "" : "0",
                competing || Double.isNaN(observed) ? "" : "1",
                Double.isNaN(observed) ? "unavailable" : competing ? "not-comparable" : "available");
    }

    private static void writeTrace(final Path file, final Options options, final boolean nullMember)
            throws IOException {
        final StringBuilder out = new StringBuilder();
        final JsonObject header = new JsonObject();
        header.addProperty("schema", options.traceSchema);
        header.addProperty("dataset", DATASET);
        header.addProperty("revision", "fixture-revision");
        header.addProperty("fingerprint", FINGERPRINT);
        if (options.traceSource != null) {
            header.addProperty("sourceSha256", options.traceSource);
        }
        header.addProperty("traceMode", nullMember ? "selected-null-member" : "real");
        header.addProperty("nullBlockLength", nullMember ? options.nullBlockLength : -1);
        header.addProperty("nullMemberIndex", nullMember ? 0 : -1);
        out.append(header).append('\n');
        int records = 0;
        for (int asOf = FIRST_AS_OF; asOf < BARS; asOf++) {
            final JsonObject rules = record(asOf, "all-rules", true, options);
            final JsonObject topology = record(asOf, "topology-only", false, options);
            if (nullMember) {
                asNullMember(rules, "all-rules", asOf, options);
                asNullMember(topology, "MOTIVE_5", asOf, options);
            }
            out.append(rules).append('\n');
            out.append(topology).append('\n');
            records += 2;
            if (!nullMember) {
                out.append(competingRecord(asOf)).append('\n');
                records++;
            }
            for (final String mode : List.of("topology-only", "+wave2-origin", "+wave3-not-shortest")) {
                final List<String> active = mode.startsWith("+") ? List.of(mode.substring(1)) : List.of();
                final JsonObject ablation = cycleRecord(asOf, mode, active, options);
                if (nullMember) {
                    asNullMember(ablation, mode, asOf, options);
                }
                out.append(ablation).append('\n');
                records++;
            }
        }
        if (!options.truncateTrace) {
            out.append("{\"complete\":true,\"records\":").append(records).append("}\n");
        }
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
    }

    /** Rewrites a real record into the producer's null-member stream format. */
    private static void asNullMember(final JsonObject record, final String mode, final int asOf,
            final Options options) {
        record.addProperty("section", "null");
        record.addProperty("mode", mode);
        record.addProperty("nullBlockLength", options.nullBlockLength);
        record.addProperty("nullMemberIndex", 0);
        if (!options.nullTraceWithoutBars) {
            final JsonObject bar = new JsonObject();
            bar.addProperty("begin", START.plus(asOf, ChronoUnit.DAYS).toString());
            bar.addProperty("end", START.plus(asOf + 1L, ChronoUnit.DAYS).toString());
            bar.addProperty("open", Integer.toString(NULL_BAR_OFFSET + close(asOf) - 1));
            bar.addProperty("high", Integer.toString(NULL_BAR_OFFSET + high(asOf)));
            bar.addProperty("low", Integer.toString(NULL_BAR_OFFSET + low(asOf)));
            bar.addProperty("close", Integer.toString(NULL_BAR_OFFSET + close(asOf)));
            bar.addProperty("volume", "1");
            record.add("bar", bar);
        }
    }

    private static JsonObject cycleRecord(final int asOf, final String mode, final List<String> active,
            final Options options) {
        final JsonObject record = record(asOf, mode, true, options);
        final JsonArray ids = new JsonArray();
        active.forEach(ids::add);
        record.add("activeRules", ids);
        for (final var element : record.getAsJsonArray("candidates")) {
            final JsonObject candidate = element.getAsJsonObject();
            final JsonArray rules = new JsonArray();
            for (final var rule : candidate.getAsJsonArray("rules")) {
                if (active.contains(rule.getAsJsonObject().get("id").getAsString())) {
                    rules.add(rule);
                }
            }
            candidate.add("rules", rules);
            refreshVersion(candidate);
        }
        return record;
    }

    /** One recorded as-of line of a stream. */
    static JsonObject record(final int asOf, final String mode, final boolean rules, final Options options) {
        final JsonObject record = new JsonObject();
        record.addProperty("dataset", DATASET);
        record.addProperty("section", rules ? "h2" : "h1");
        record.addProperty("mode", mode);
        record.addProperty("grammar", rules ? "CYCLE_5_3" : "MOTIVE_5");
        final JsonArray active = new JsonArray();
        if (rules) {
            active.add("wave2-origin");
            active.add("wave3-not-shortest");
        }
        record.add("activeRules", active);
        record.addProperty("detector", "fractal-w5");
        record.addProperty("partition", "calibration");
        record.addProperty("asOfIndex", asOf);
        record.addProperty("nullBlockLength", -1);
        record.addProperty("nullMemberIndex", -1);
        record.addProperty("asOfTime", START.plus(asOf + 1L, ChronoUnit.DAYS).toString());
        record.addProperty("kind", "topology");
        final List<int[]> confirmed = new ArrayList<>();
        for (final int[] pivot : rules ? CYCLE_PIVOTS : PIVOTS) {
            if (pivot[2] <= asOf) {
                confirmed.add(pivot);
            }
        }
        final JsonArray pivots = new JsonArray();
        for (final int[] pivot : confirmed) {
            final JsonObject object = point(pivot);
            object.addProperty("confirmationIndex", pivot[2]);
            pivots.add(object);
        }
        if (options.leakFuturePivot && asOf == 20) {
            final JsonObject leaked = point(new int[] { 30, 1, 33 });
            leaked.addProperty("confirmationIndex", 33);
            pivots.add(leaked);
        }
        record.add("pivots", pivots);
        final JsonArray candidates = new JsonArray();
        if (asOf >= 23) {
            candidates.add(candidate(0, asOf >= 33, rules));
        }
        if (asOf >= 33 && asOf <= 40) {
            candidates.add(candidate(1, false, rules));
        }
        record.add("candidates", candidates);
        record.addProperty("status",
                candidates.size() >= 2 ? "AMBIGUOUS" : candidates.size() == 1 ? "COMPLETE" : "NO_MATCH");
        record.add("direction", candidates.size() == 1 ? new JsonPrimitive("BULLISH") : JsonNull.INSTANCE);
        record.addProperty("formingStartBarIndex", -1);
        record.addProperty("formingEndBarIndex", -1);
        record.addProperty("explanation", "");
        refreshTopologyLabels(record);
        return record;
    }

    /**
     * One as-of line of the {@code competing-3+3} stream in the exact shape of
     * {@code StudyRunner.evaluateAlternativeGrammar}: kind {@code alternative}, a
     * lowercase outcome as status, a null direction, an empty candidates array and
     * the sorted match labels. The first {@code ambiguous} record is bar 33.
     */
    static JsonObject competingRecord(final int asOf) {
        final String outcome;
        final List<String> labels;
        if (asOf < 23) {
            outcome = "no-match";
            labels = List.of("no-match");
        } else if (asOf < 33) {
            outcome = "forming";
            labels = List.of("BULLISH:2-20");
        } else if (asOf < 43) {
            outcome = "ambiguous";
            labels = List.of("BEARISH:5-30", "BULLISH:2-30");
        } else {
            outcome = "ambiguous";
            labels = List.of("BEARISH:5-40", "BULLISH:12-40", "BULLISH:2-40");
        }
        final JsonObject record = new JsonObject();
        record.addProperty("dataset", DATASET);
        record.addProperty("section", "competing");
        record.addProperty("mode", "competing-3+3");
        record.addProperty("grammar", "3+3");
        record.add("activeRules", new JsonArray());
        record.addProperty("detector", "fractal-w5");
        record.addProperty("partition", "calibration");
        record.addProperty("asOfIndex", asOf);
        record.addProperty("nullBlockLength", -1);
        record.addProperty("nullMemberIndex", -1);
        record.addProperty("asOfTime", START.plus(asOf + 1L, ChronoUnit.DAYS).toString());
        record.addProperty("kind", "alternative");
        record.addProperty("status", outcome);
        final JsonArray pivots = new JsonArray();
        for (final int[] pivot : PIVOTS) {
            if (pivot[2] <= asOf) {
                final JsonObject object = point(pivot);
                object.addProperty("confirmationIndex", pivot[2]);
                pivots.add(object);
            }
        }
        record.add("pivots", pivots);
        record.add("candidates", new JsonArray());
        record.add("direction", JsonNull.INSTANCE);
        final JsonArray labelArray = new JsonArray();
        labels.forEach(labelArray::add);
        record.add("labels", labelArray);
        return record;
    }

    private static JsonObject candidate(final int skip, final boolean revised, final boolean rules) {
        final JsonObject candidate = new JsonObject();
        candidate.addProperty("direction", skip == 0 ? "BULLISH" : "BEARISH");
        final JsonArray placement = new JsonArray();
        final int[][] pivots = rules ? CYCLE_PIVOTS : PIVOTS;
        for (int i = skip; i < skip + (rules ? 9 : 6); i++) {
            placement.add(point(pivots[i]));
        }
        candidate.add("placement", placement);
        final JsonArray ruleArray = new JsonArray();
        if (rules) {
            final JsonObject rule = new JsonObject();
            rule.addProperty("id", "wave2-origin");
            rule.addProperty("state", "PASS");
            rule.addProperty("score", 1);
            final JsonArray observations = new JsonArray();
            observations.add("wave 2 stayed above the origin");
            if (revised) {
                observations.add("wave 2 evidence confirmed at bar 33");
            }
            rule.add("observations", observations);
            rule.addProperty("explanation",
                    "wave 2 may not retrace the whole of wave 1 (" + (skip == 0 ? "A" : "B") + ")");
            ruleArray.add(rule);
            final JsonObject second = new JsonObject();
            second.addProperty("id", "wave3-not-shortest");
            second.addProperty("state", "PENDING");
            second.add("score", JsonNull.INSTANCE);
            second.add("observations", new JsonArray());
            second.addProperty("explanation", "wave 3 needs later-leg evidence");
            ruleArray.add(second);
        }
        candidate.add("rules", ruleArray);
        refreshIdentity(candidate, rules ? "CYCLE_5_3" : "MOTIVE_5");
        return candidate;
    }

    static void refreshVersion(final JsonObject candidate) {
        candidate.addProperty("version", candidate.get("candidateKey").getAsString() + "@"
                + digest(candidate.getAsJsonArray("rules").toString()));
    }

    static void refreshIdentity(final JsonObject record) {
        for (final var element : record.getAsJsonArray("candidates")) {
            refreshIdentity(element.getAsJsonObject(), record.get("grammar").getAsString());
        }
        if ("topology".equals(record.get("kind").getAsString())) {
            refreshTopologyLabels(record);
        }
    }

    private static void refreshTopologyLabels(final JsonObject record) {
        final TreeSet<String> labels = new TreeSet<>();
        switch (record.get("status").getAsString()) {
        case "COMPLETE", "AMBIGUOUS" -> {
            for (final var element : record.getAsJsonArray("candidates")) {
                final JsonObject candidate = element.getAsJsonObject();
                final JsonArray placement = candidate.getAsJsonArray("placement");
                labels.add(candidate.get("direction").getAsString() + ":"
                        + placement.get(0).getAsJsonObject().get("index").getAsInt() + "-"
                        + placement.get(placement.size() - 1).getAsJsonObject().get("index").getAsInt());
            }
        }
        case "FORMING" -> {
            if (!record.get("direction").isJsonNull()) {
                labels.add("forming:" + record.get("direction").getAsString() + ":"
                        + record.get("formingStartBarIndex").getAsInt() + "-"
                        + record.get("formingEndBarIndex").getAsInt());
            }
        }
        case "INVALIDATED" -> labels.add("invalidated:" + record.get("explanation").getAsString());
        case "INSUFFICIENT_HISTORY" -> labels.add("insufficient-history");
        default -> {
        }
        }
        final JsonArray recordedLabels = new JsonArray();
        labels.forEach(recordedLabels::add);
        record.add("labels", recordedLabels);
    }

    private static void refreshIdentity(final JsonObject candidate, final String grammar) {
        final StringBuilder identity = new StringBuilder(grammar).append('|')
                .append(candidate.get("direction").getAsString());
        for (final var point : candidate.getAsJsonArray("placement")) {
            final JsonObject pivot = point.getAsJsonObject();
            identity.append('|')
                    .append(pivot.get("index").getAsInt())
                    .append(':')
                    .append(pivot.get("type").getAsString())
                    .append(':')
                    .append(pivot.get("price").getAsString());
        }
        candidate.addProperty("candidateKey", digest(identity.toString()));
        refreshVersion(candidate);
    }

    private static String digest(final String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)), 0,
                            16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every Java platform", e);
        }
    }

    private static JsonObject point(final int[] pivot) {
        final JsonObject object = new JsonObject();
        object.addProperty("index", pivot[0]);
        object.addProperty("price", Integer.toString(pivot[1] == 1 ? high(pivot[0]) : low(pivot[0])));
        object.addProperty("type", pivot[1] == 1 ? "HIGH" : "LOW");
        return object;
    }

    static int close(final int i) {
        return 100 + (i * 37) % 23;
    }

    static int high(final int i) {
        return close(i) + 20;
    }

    static int low(final int i) {
        return close(i) - 20;
    }
}
