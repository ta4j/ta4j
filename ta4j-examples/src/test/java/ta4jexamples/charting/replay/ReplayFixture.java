/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Writes a small but complete {@code elliott-research-run/1} bundle in the
 * persisted format of the research recorder, so the replay adapter is tested
 * against the file contract and not against test classes of another module.
 *
 * <p>
 * The bundle holds {@value #BARS} daily bars and two recorded streams: the
 * {@code all-rules} stream ({@link #RULES_KEY}) and the {@code topology-only}
 * stream ({@link #TOPOLOGY_KEY}). Confirmed pivots: LOW 5 (confirmed at 8),
 * HIGH 12 (15), LOW 20 (23), HIGH 30 (33), LOW 40 (43). Candidate {@code c-A}
 * exists from bar 23 and is revised at bar 33 ({@code v1} to {@code v2});
 * candidate {@code c-B} exists on bars 33 to 40.
 */
final class ReplayFixture {

    static final int BARS = 60;
    static final int FIRST_AS_OF = 10;
    static final String DATASET = "d1";
    static final String FINGERPRINT = "fixture-fingerprint";
    static final String RULES_KEY = "d1|h2|all-rules|fractal-w5|calibration|ambiguousRate|b20";
    static final String TOPOLOGY_KEY = "d1|h1|topology-only|fractal-w5|calibration|ambiguousRate|b20";
    static final Instant START = Instant.parse("2020-01-01T00:00:00Z");

    private static final int[][] PIVOTS = { { 5, 0, 8 }, { 12, 1, 15 }, { 20, 0, 23 }, { 30, 1, 33 }, { 40, 0, 43 } };
    /** Price offset of recorded null-member bars versus the real bars. */
    static final int NULL_BAR_OFFSET = 1000;
    private static final String HEADER = "key,dataset,asset,section,mode,grammar,activeRules,detector,policy,partition,metric,nullBlockLength,observed";

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
            final JsonArray datasets = new JsonArray();
            datasets.add(dataset);
            run.add("datasets", datasets);
            Files.writeString(directory.resolve("run.json"), run.toString(), StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("comparisons.csv"), HEADER + "\n" + row(RULES_KEY, "all-rules",
                    "wave2-origin;wave3-not-shortest") + "\n" + row(TOPOLOGY_KEY, "topology-only", "") + "\n",
                    StandardCharsets.UTF_8);
            writeTrace(directory.resolve("traces/d1-real.jsonl"), options, false);
            if (options.nullTrace) {
                writeTrace(directory.resolve("traces/d1-null.jsonl"), options, true);
            }
            return directory;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String row(final String key, final String mode, final String rules) {
        return String.join(",", key, DATASET, "FIXTURE", rules.isEmpty() ? "h1" : "h2", mode, "MOTIVE_5", rules,
                "fractal-w5", "kernel-topology",
                "calibration", "ambiguousRate", "20", "0.5");
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

    /** One recorded as-of line of a stream. */
    static JsonObject record(final int asOf, final String mode, final boolean rules, final Options options) {
        final JsonObject record = new JsonObject();
        record.addProperty("dataset", DATASET);
        record.addProperty("section", rules ? "h2" : "h1");
        record.addProperty("mode", mode);
        record.addProperty("grammar", "MOTIVE_5");
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
        record.addProperty("kind", "alternative");
        final List<int[]> confirmed = new ArrayList<>();
        for (final int[] pivot : PIVOTS) {
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
            candidates.add(candidate("c-A", asOf >= 33 ? "v2" : "v1", confirmed, 0, rules));
        }
        if (asOf >= 33 && asOf <= 40) {
            candidates.add(candidate("c-B", "v1", confirmed, 1, rules));
        }
        record.add("candidates", candidates);
        record.addProperty("status", candidates.size() >= 2 ? "AMBIGUOUS" : candidates.size() == 1 ? "COMPLETE" : "NONE");
        record.addProperty("direction", candidates.isEmpty() ? "" : "BULLISH");
        final JsonArray labels = new JsonArray();
        if (candidates.size() >= 2) {
            labels.add("ambiguous");
        }
        record.add("labels", labels);
        return record;
    }

    private static JsonObject candidate(final String key, final String version, final List<int[]> confirmed,
            final int skip, final boolean rules) {
        final JsonObject candidate = new JsonObject();
        candidate.addProperty("candidateKey", key);
        candidate.addProperty("version", version);
        candidate.addProperty("direction", "BULLISH");
        final JsonArray placement = new JsonArray();
        for (int i = skip; i < confirmed.size(); i++) {
            placement.add(point(confirmed.get(i)));
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
            rule.add("observations", observations);
            rule.addProperty("explanation", "wave 2 may not retrace the whole of wave 1 (" + key + ")");
            ruleArray.add(rule);
        }
        candidate.add("rules", ruleArray);
        return candidate;
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
        return close(i) + 2;
    }

    static int low(final int i) {
        return close(i) - 2;
    }
}
