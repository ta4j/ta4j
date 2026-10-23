/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import ta4jexamples.charting.replay.ReplayArtifact.PriceBar;

/**
 * Byte-offset index over one {@code elliott-research-trace/1} JSONL file.
 *
 * <p>
 * The scan validates the header and the completion footer, then retains only a
 * compact {@link Entry} per as-of record of the requested families. Records are
 * re-read from disk on demand, so cursor movement never rescans the file and
 * the parsed trace is never held in memory.
 */
final class ReplayTraceIndex {

    private static final Set<String> TOPOLOGY_STATUSES = Set.of("INSUFFICIENT_HISTORY", "NO_MATCH", "FORMING",
            "COMPLETE", "AMBIGUOUS", "INVALIDATED");

    private static final Set<String> ALTERNATIVE_STATUSES = Set.of("insufficient-history", "no-match", "forming",
            "complete", "ambiguous");

    private static final Set<String> EVIDENCE_STATES = Set.of("PASS", "FAIL", "PENDING", "UNAVAILABLE",
            "NOT_APPLICABLE");

    /** Identity of one recorded as-of stream inside a trace. */
    record Family(String section, String mode, String grammar, String detector, String partition) {

        static Family of(final JsonObject record) {
            return new Family(text(record, "section"), text(record, "mode"), text(record, "grammar"),
                    text(record, "detector"), text(record, "partition"));
        }
    }

    /**
     * Location and cheap summary of one as-of record.
     *
     * @param signature SHA-256 of the record content without the as-of coordinates;
     *                  consecutive equal signatures mean no state change happened
     *                  between the two as-of bars
     * @param digest    SHA-256 of the whole record, used to detect a record that
     *                  changed on disk after it was indexed
     * @param bar       the price bar recorded with a null-member record, or null
     *                  when the record carries none (real traces)
     */
    record Entry(int asOfIndex, long offset, int length, String signature, String digest, String status,
            int candidateCount, PriceBar bar) {
    }

    private final Path file;
    private final String display;
    private final JsonObject header;
    private final long recordCount;
    private final Map<Family, List<Entry>> entries;
    private final Map<Family, MetricContribution> contributions;

    private ReplayTraceIndex(final Path file, final String display, final JsonObject header, final long recordCount,
            final Map<Family, List<Entry>> entries, final Map<Family, MetricContribution> contributions) {
        this.file = file;
        this.display = display;
        this.header = header;
        this.recordCount = recordCount;
        this.entries = entries;
        this.contributions = contributions;
    }

    /**
     * Reads only the header line of a trace file.
     *
     * @param file    the trace file
     * @param display the artifact-relative name used in messages
     * @return the parsed header
     */
    static JsonObject readHeader(final Path file, final String display) {
        try (InputStream in = Files.newInputStream(file)) {
            final ByteArrayOutputStream line = new ByteArrayOutputStream();
            int next;
            while ((next = in.read()) >= 0 && next != '\n') {
                line.write(next);
            }
            return parseHeader(line.toString(StandardCharsets.UTF_8), display);
        } catch (IOException e) {
            throw new ReplayArtifactException("cannot read trace " + display + ": " + e.getMessage(), e);
        }
    }

    private static JsonObject parseHeader(final String line, final String display) {
        try {
            final JsonElement parsed = JsonParser.parseString(line);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().has("schema")) {
                throw new ReplayArtifactException("trace " + display + " does not start with a schema header line");
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new ReplayArtifactException("trace " + display + " has an unreadable header line: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Scans a trace and indexes the families accepted by {@code keep}.
     *
     * @param file    the trace file
     * @param display the artifact-relative name used in messages
     * @param keep    families to index
     * @param expectedRules ordered rule IDs recorded for each compared family
     * @param metric the selected comparison metric to reconstruct during the scan
     * @return the index
     */
    static ReplayTraceIndex scan(final Path file, final String display, final Predicate<Family> keep,
            final Map<Family, List<String>> expectedRules, final String metric) {
        Objects.requireNonNull(keep, "keep");
        Objects.requireNonNull(expectedRules, "expectedRules");
        Objects.requireNonNull(metric, "metric");
        final Scan scan = new Scan(display, keep, expectedRules, metric);
        try (InputStream in = Files.newInputStream(file)) {
            final byte[] buffer = new byte[1 << 16];
            final ByteArrayOutputStream line = new ByteArrayOutputStream();
            long consumed = 0;
            long lineStart = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                for (int i = 0; i < read; i++) {
                    if (buffer[i] != '\n') {
                        line.write(buffer[i]);
                        continue;
                    }
                    final long end = consumed + i;
                    scan.accept(line.toString(StandardCharsets.UTF_8), lineStart, (int) (end - lineStart));
                    line.reset();
                    lineStart = end + 1;
                }
                consumed += read;
            }
            if (line.size() > 0) {
                scan.accept(line.toString(StandardCharsets.UTF_8), lineStart, (int) (consumed - lineStart));
            }
        } catch (IOException e) {
            throw new ReplayArtifactException("cannot read trace " + display + ": " + e.getMessage(), e);
        }
        return scan.finish(file);
    }

    /** Mutable state of one scan pass. */
    private static final class Scan {
        private final String display;
        private final Predicate<Family> keep;
        private final Map<Family, List<String>> expectedRules;
        private final String metric;
        private final Map<Family, MetricContribution> contributions = new HashMap<>();
        private final Map<Family, Integer> lastAsOf = new HashMap<>();
        private final Map<Family, List<Entry>> entries = new LinkedHashMap<>();
        private JsonObject header;
        private JsonObject footer;
        private long records;
        private long lineNumber;

        Scan(final String display, final Predicate<Family> keep, final Map<Family, List<String>> expectedRules,
                final String metric) {
            this.display = display;
            this.keep = keep;
            this.expectedRules = expectedRules;
            this.metric = metric;
        }

        void accept(final String text, final long offset, final int length) {
            lineNumber++;
            if (header == null) {
                header = parseHeader(text, display);
            } else if (text.isBlank()) {
                return;
            } else if (footer != null) {
                throw new ReplayArtifactException(
                        "trace " + display + " has content after its completion footer; regenerate the run");
            } else {
                final JsonObject record = parseRecord(text, display);
                if (record.has("complete") && !record.has("kind")) {
                    footer = record;
                } else {
                    validateRecord(display, lineNumber, record);
                    final Family family = Family.of(record);
                    final List<String> rules = expectedRules.get(family);
                    if (rules != null) {
                        final JsonArray active = record.getAsJsonArray("activeRules");
                        if (active.size() != rules.size()) {
                            throw corrupt(display, lineNumber, "activeRules do not match comparisons.csv mode "
                                    + family.mode() + "; expected " + rules);
                        }
                        for (int i = 0; i < active.size(); i++) {
                            if (!rules.get(i).equals(active.get(i).getAsString())) {
                                throw corrupt(display, lineNumber, "activeRules do not match comparisons.csv mode "
                                        + family.mode() + "; expected " + rules);
                            }
                        }
                    }
                    final int asOf = record.get("asOfIndex").getAsInt();
                    final Integer previous = lastAsOf.put(family, asOf);
                    if (previous != null && asOf <= previous) {
                        throw corrupt(display, lineNumber, "asOfIndex " + asOf + " is out of order for " + family
                                + "; observations must strictly increase after " + previous);
                    }
                    records++;
                    if (keep.test(family)) {
                        MetricContribution contribution = contributions.get(family);
                        if (contribution == null) {
                            contribution = new MetricContribution(metric);
                            contributions.put(family, contribution);
                        }
                        contribution.observe(record);
                        index(entries, family, record, offset, length);
                    }
                }
            }
        }

        ReplayTraceIndex finish(final Path file) {
            if (header == null) {
                throw new ReplayArtifactException("trace " + display + " is empty; regenerate it with --trace real");
            }
            final JsonElement complete = footer == null ? null : footer.get("complete");
            if (complete == null || !complete.isJsonPrimitive() || !complete.getAsJsonPrimitive().isBoolean()
                    || !complete.getAsBoolean()) {
                throw new ReplayArtifactException("trace " + display
                        + " is truncated: no completion footer. Regenerate it with the original recipe and --trace real");
            }
            final JsonElement declared = footer.get("records");
            try {
                if (declared == null || !declared.isJsonPrimitive() || !declared.getAsJsonPrimitive().isNumber()
                        || declared.getAsBigDecimal().compareTo(BigDecimal.valueOf(records)) != 0) {
                    throw new ReplayArtifactException("trace " + display + " footer declares " + declared
                            + " records but " + records + " were read; regenerate the run");
                }
            } catch (ArithmeticException | NumberFormatException e) {
                throw new ReplayArtifactException("trace " + display + " footer declares unsupported record count "
                        + declared + "; regenerate the run", e);
            }
            return new ReplayTraceIndex(file, display, header, records, entries, contributions);
        }
    }

    /** Reconstructs only the requested metric, without retaining parsed records. */
    private static final class MetricContribution {
        private final String metric;
        private long numerator;
        private long denominator;
        private double sum;
        private Set<String> previousLabels;

        MetricContribution(final String metric) {
            this.metric = metric;
        }

        void observe(final JsonObject record) {
            final String status = text(record, "status");
            final boolean complete = "COMPLETE".equals(status) || "complete".equals(status);
            switch (metric) {
            case "completeOccupancyRate" -> count(complete);
            case "ambiguousRate" -> count("AMBIGUOUS".equals(status) || "ambiguous".equals(status));
            case "noMatchRate" -> count("NO_MATCH".equals(status) || "no-match".equals(status));
            case "evidencePassRate", "jointPassRate" -> {
                if (complete) {
                    for (final JsonElement element : record.getAsJsonArray("candidates")) {
                        final JsonArray rules = element.getAsJsonObject().getAsJsonArray("rules");
                        long passes = 0;
                        for (final JsonElement rule : rules) {
                            if ("PASS".equals(text(rule.getAsJsonObject(), "state"))) {
                                passes++;
                            }
                        }
                        if ("evidencePassRate".equals(metric)) {
                            numerator += passes;
                            denominator += rules.size();
                        } else if (!rules.isEmpty()) {
                            count(passes == rules.size());
                        }
                    }
                }
            }
            case "confirmationLagBars" -> {
                if (complete && "topology".equals(text(record, "kind"))) {
                    final JsonArray visible = record.getAsJsonArray("pivots");
                    for (final JsonElement element : record.getAsJsonArray("candidates")) {
                        final JsonArray placement = element.getAsJsonObject().getAsJsonArray("placement");
                        double lag = 0.0d;
                        int visibleIndex = 0;
                        for (final JsonElement point : placement) {
                            final int index = point.getAsJsonObject().get("index").getAsInt();
                            while (visible.get(visibleIndex).getAsJsonObject().get("index").getAsInt() < index) {
                                visibleIndex++;
                            }
                            lag += Math.max(0, visible.get(visibleIndex).getAsJsonObject()
                                    .get("confirmationIndex").getAsInt() - index);
                        }
                        sum += lag / placement.size();
                        denominator++;
                    }
                }
            }
            case "labelStabilityJaccard" -> {
                final JsonArray labels = record.getAsJsonArray("labels");
                final Set<String> current = labels.isEmpty() ? Set.of() : new HashSet<>();
                for (final JsonElement label : labels) {
                    current.add(label.getAsString());
                }
                if (previousLabels != null) {
                    int intersection = 0;
                    for (final String label : current) {
                        if (previousLabels.contains(label)) {
                            intersection++;
                        }
                    }
                    final int union = previousLabels.size() + current.size() - intersection;
                    sum += union == 0 ? 1.0d : intersection / (double) union;
                    denominator++;
                }
                previousLabels = current;
            }
            default -> throw new ReplayArtifactException("unsupported comparison metric '" + metric
                    + "'; regenerate the run with ElliottResearch");
            }
        }

        private void count(final boolean contributes) {
            numerator += contributes ? 1 : 0;
            denominator++;
        }

        String evidence(final ReplayArtifact.ComparisonRow row, final boolean nullMember, final String display) {
            final boolean ratio = !"confirmationLagBars".equals(metric) && !"labelStabilityJaccard".equals(metric);
            final double value = denominator == 0 ? Double.NaN : (ratio ? numerator : sum) / denominator;
            if (!nullMember && (Double.compare(value, row.observed()) != 0
                    || ratio && (row.numerator() == null || row.numerator() != numerator
                            || row.denominator() == null || row.denominator() != denominator))) {
                throw new ReplayArtifactException("trace " + display + " " + metric
                        + " contributions do not match the selected comparison row '" + row.key()
                        + "'; regenerate the run with ElliottResearch");
            }
            final String support = ratio ? numerator + "/" + denominator + " = " + value
                    : value + " from " + denominator + ("confirmationLagBars".equals(metric)
                            ? " complete candidate observations (candidate-pivot means)"
                            : " adjacent label-set transitions");
            return (nullMember ? "Selected null member " : "Captured real comparison scope ") + metric + ": "
                    + support + (nullMember ? " (not compared with the real comparison row)"
                            : denominator == 0 ? " -> unavailable (no contributions)" : " -> match");
        }
    }

    private static JsonObject parseRecord(final String text, final String display) {
        try {
            final JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new ReplayArtifactException("trace " + display + " contains a non-object line");
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new ReplayArtifactException("trace " + display + " contains an unreadable record: " + e.getMessage(),
                    e);
        }
    }

    private static void index(final Map<Family, List<Entry>> entries, final Family family, final JsonObject record,
            final long offset, final int length) {
        final int asOf = record.get("asOfIndex").getAsInt();
        final List<Entry> list = entries.computeIfAbsent(family, ignored -> new ArrayList<>());
        final JsonElement candidates = record.get("candidates");
        list.add(new Entry(asOf, offset, length, signature(record), sha256(record.toString()), text(record, "status"),
                candidates != null && candidates.isJsonArray() ? candidates.getAsJsonArray().size() : 0, bar(record)));
    }

    /** The recorded null-member price bar, or null when the trace carries none. */
    private static PriceBar bar(final JsonObject record) {
        final JsonElement element = record.get("bar");
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        final JsonObject bar = element.getAsJsonObject();
        return new PriceBar(record.get("asOfIndex").getAsInt(), Instant.parse(text(bar, "begin")),
                Instant.parse(text(bar, "end")), text(bar, "open"), text(bar, "high"), text(bar, "low"),
                text(bar, "close"), text(bar, "volume"));
    }

    /**
     * Rejects a record that deviates from the shape the research trace writer
     * emits, so a hand-edited or foreign line fails with the trace, line and field
     * instead of being projected as an empty layer.
     */
    private static void validateRecord(final String display, final long line, final JsonObject record) {
        final String kind = textOrNull(record, "kind");
        if (!"topology".equals(kind) && !"alternative".equals(kind)) {
            throw corrupt(display, line, "record kind " + record.get("kind") + " is not topology or alternative");
        }
        final String where = kind + " record field ";
        for (final String field : List.of("dataset", "section", "mode", "grammar", "detector", "partition", "asOfTime",
                "status")) {
            requireText(display, line, record, field, where);
            if (record.get(field).getAsString().isBlank()) {
                throw corrupt(display, line, where + field + " is blank");
            }
        }
        final String status = text(record, "status");
        if (!("topology".equals(kind) ? TOPOLOGY_STATUSES : ALTERNATIVE_STATUSES).contains(status)) {
            throw corrupt(display, line, where + "status " + record.get("status") + " is not a " + kind + " status");
        }
        for (final String field : List.of("nullBlockLength", "nullMemberIndex", "asOfIndex")) {
            requireInteger(display, line, record, field, where);
        }
        final int asOfIndex = record.get("asOfIndex").getAsInt();
        final JsonElement direction = record.get("direction");
        if (direction == null || !direction.isJsonNull() && textOrNull(record, "direction") == null) {
            throw corrupt(display, line, where + "direction is missing or not a string or null");
        }
        if ("topology".equals(kind) && (!direction.isJsonNull() && !isWaveDirection(direction.getAsString())
                || direction.isJsonNull() && "FORMING".equals(status))) {
            throw corrupt(display, line, where + "direction must be a wave direction, and must not be null for FORMING");
        }
        if ("alternative".equals(kind) && !direction.isJsonNull()) {
            throw corrupt(display, line, where + "direction must be null for an alternative outcome");
        }
        requireTexts(display, line, record, "activeRules", where);
        final JsonElement bar = record.get("bar");
        if (bar != null) {
            final JsonObject barObject = requireObject(display, line, bar, where + "bar");
            for (final String field : List.of("begin", "end", "open", "high", "low", "close", "volume")) {
                requireText(display, line, barObject, field, where + "bar.");
            }
            try {
                Instant.parse(barObject.get("begin").getAsString());
                Instant.parse(barObject.get("end").getAsString());
            } catch (DateTimeParseException e) {
                throw corrupt(display, line, where + "bar begin/end is not an ISO-8601 instant");
            }
        }
        final JsonArray pivots = requireArray(display, line, record, "pivots", where);
        final List<BigDecimal> visiblePrices = new ArrayList<>(pivots.size());
        int confirmedPivotCount = 0;
        for (int i = 0; i < pivots.size(); i++) {
            final String at = where + "pivots[" + i + "]";
            final JsonObject pivot = requireObject(display, line, pivots.get(i), at);
            visiblePrices.add(requirePlacement(display, line, pivot, at));
            final int confirmation = requireInteger(display, line, pivot, "confirmationIndex", at + ".");
            if (confirmation < pivot.get("index").getAsInt()) {
                throw corrupt(display, line, at + ".confirmationIndex must not precede index");
            }
            if (confirmation <= asOfIndex) {
                confirmedPivotCount++;
            }
            if (i > 0 && pivot.get("index").getAsInt() <= pivots.get(i - 1).getAsJsonObject().get("index").getAsInt()) {
                throw corrupt(display, line, at + ".index must strictly increase in the visible pivot view");
            }
            // Future pivots remain a projection warning, not an alternation failure.
            if (i > 0 && confirmation <= asOfIndex
                    && pivots.get(i - 1).getAsJsonObject().get("confirmationIndex").getAsInt() <= asOfIndex
                    && text(pivot, "type").equals(text(pivots.get(i - 1).getAsJsonObject(), "type"))) {
                throw corrupt(display, line, at + ".type must alternate in the visible pivot view");
            }
        }
        final JsonArray candidates = requireArray(display, line, record, "candidates", where);
        if ("topology".equals(kind) && (candidates.size() > 64
                || "COMPLETE".equals(status) && candidates.size() != 1
                || "AMBIGUOUS".equals(status) && candidates.size() < 2
                || !"COMPLETE".equals(status) && !"AMBIGUOUS".equals(status) && !candidates.isEmpty())) {
            throw corrupt(display, line, where
                    + "candidates must match status (COMPLETE: 1, AMBIGUOUS: 2-64, all other states: 0)");
        }
        int requiredPivots = 0;
        final Set<String> candidateKeys = candidates.size() > 1 ? new HashSet<>(candidates.size()) : null;
        for (int i = 0; i < candidates.size(); i++) {
            final String at = where + "candidates[" + i + "]";
            final JsonObject candidate = requireObject(display, line, candidates.get(i), at);
            for (final String field : List.of("candidateKey", "version", "direction")) {
                requireText(display, line, candidate, field, at + ".");
            }
            final JsonArray placement = requireArray(display, line, candidate, "placement", at + ".");
            final List<BigDecimal> placementPrices = new ArrayList<>(placement.size());
            for (int p = 0; p < placement.size(); p++) {
                placementPrices.add(requirePlacement(display, line, placement.get(p), at + ".placement[" + p + "]"));
            }
            final JsonArray rules = requireArray(display, line, candidate, "rules", at + ".");
            for (int r = 0; r < rules.size(); r++) {
                final String ruleAt = at + ".rules[" + r + "]";
                final JsonObject rule = requireObject(display, line, rules.get(r), ruleAt);
                for (final String field : List.of("id", "state", "explanation")) {
                    requireText(display, line, rule, field, ruleAt + ".");
                }
                if (!EVIDENCE_STATES.contains(text(rule, "state"))) {
                    throw corrupt(display, line,
                            ruleAt + ".state " + rule.get("state") + " is not a recorded rule state");
                }
                final JsonElement score = rule.get("score");
                if (score == null
                        || !score.isJsonNull() && !(score.isJsonPrimitive() && score.getAsJsonPrimitive().isNumber())) {
                    throw corrupt(display, line, ruleAt + ".score is missing or not a number or null");
                }
                requireTexts(display, line, rule, "observations", ruleAt + ".");
            }
            final StringBuilder identity = new StringBuilder(text(record, "grammar")).append('|')
                    .append(text(candidate, "direction"));
            for (final JsonElement point : placement) {
                final JsonObject pivot = point.getAsJsonObject();
                identity.append('|')
                        .append(pivot.get("index").getAsInt())
                        .append(':')
                        .append(text(pivot, "type"))
                        .append(':')
                        .append(text(pivot, "price"));
            }
            final String key = sha256(identity.toString()).substring(0, 32);
            if (!key.equals(text(candidate, "candidateKey"))) {
                throw corrupt(display, line, at + ".candidateKey does not match the recorded candidate");
            }
            if (candidateKeys != null && !candidateKeys.add(key)) {
                throw corrupt(display, line, at + ".candidateKey duplicates a retained candidate");
            }
            if (!(key + "@" + sha256(rules.toString()).substring(0, 32)).equals(text(candidate, "version"))) {
                throw corrupt(display, line, at + ".version does not match the recorded rule evidence");
            }
            final JsonArray activeRules = record.getAsJsonArray("activeRules");
            if (rules.size() != activeRules.size()) {
                throw corrupt(display, line, at + ".rules must match activeRules in size and order");
            }
            for (int r = 0; r < rules.size(); r++) {
                final JsonObject rule = rules.get(r).getAsJsonObject();
                final String ruleAt = at + ".rules[" + r + "]";
                if (!rule.get("id").equals(activeRules.get(r))) {
                    throw corrupt(display, line, ruleAt + ".id does not match activeRules[" + r + "]");
                }
                final JsonElement score = rule.get("score");
                if (!score.isJsonNull()) {
                    try {
                        final BigDecimal value = score.getAsBigDecimal();
                        if (!"PASS".equals(text(rule, "state")) || value.signum() < 0
                                || value.compareTo(BigDecimal.ONE) > 0) {
                            throw corrupt(display, line, ruleAt
                                    + ".score must be null unless PASS and otherwise within [0, 1]");
                        }
                    } catch (final NumberFormatException | ArithmeticException e) {
                        throw corrupt(display, line, ruleAt + ".score is not a supported finite number");
                    }
                }
            }
            if (!isWaveDirection(text(candidate, "direction"))) {
                throw corrupt(display, line, at + ".direction is not a wave direction");
            }
            if (requiredPivots == 0) {
                requiredPivots = requireGrammar(display, line, record, where);
            }
            if (placement.size() != requiredPivots) {
                throw corrupt(display, line, at + ".placement requires " + requiredPivots + " pivots");
            }
            for (int p = 1; p < placement.size(); p++) {
                final JsonObject previous = placement.get(p - 1).getAsJsonObject();
                final JsonObject current = placement.get(p).getAsJsonObject();
                if (current.get("index").getAsInt() <= previous.get("index").getAsInt()
                        || text(current, "type").equals(text(previous, "type"))) {
                    throw corrupt(display, line, at + ".placement must have strictly ordered, alternating pivots");
                }
            }
            final boolean bullish = "BULLISH".equals(text(candidate, "direction"));
            final boolean corrective = "CORRECTIVE_3".equals(text(record, "grammar"));
            final boolean startsLow = bullish != corrective;
            if (startsLow != "LOW".equals(text(placement.get(0).getAsJsonObject(), "type"))) {
                throw corrupt(display, line, at + ".placement has the wrong origin type for its grammar and direction");
            }
            for (int p = 1; p < placement.size(); p++) {
                final int leg = p - 1;
                final boolean rising = (corrective ? leg == 1 : leg % 2 == 0) == bullish;
                final int change = placementPrices.get(p).compareTo(placementPrices.get(p - 1));
                if (rising ? change <= 0 : change >= 0) {
                    throw corrupt(display, line, at + ".placement[" + p
                            + "] must move strictly in the expected direction for its grammar");
                }
            }
            int visibleIndex = 0;
            for (int p = 0; p < placement.size(); p++) {
                final JsonObject placed = placement.get(p).getAsJsonObject();
                final int index = placed.get("index").getAsInt();
                if (p > 0) {
                    visibleIndex++;
                }
                while (visibleIndex < pivots.size()
                        && pivots.get(visibleIndex).getAsJsonObject().get("index").getAsInt() < index) {
                    if (p > 0
                            && pivots.get(visibleIndex).getAsJsonObject().get("confirmationIndex").getAsInt() <= asOfIndex) {
                        throw corrupt(display, line,
                                at + ".placement[" + p + "] must follow the preceding visible pivot contiguously");
                    }
                    visibleIndex++;
                }
                if (visibleIndex == pivots.size()
                        || pivots.get(visibleIndex).getAsJsonObject().get("index").getAsInt() != index
                        || !text(pivots.get(visibleIndex).getAsJsonObject(), "type").equals(text(placed, "type"))
                        || visiblePrices.get(visibleIndex).compareTo(placementPrices.get(p)) != 0) {
                    throw corrupt(display, line, at + ".placement[" + p + "] does not match a visible pivot");
                }
            }
        }
        if ("topology".equals(kind)) {
            if (requiredPivots == 0) {
                requiredPivots = requireGrammar(display, line, record, where);
            }
            final boolean insufficient = "INSUFFICIENT_HISTORY".equals(status);
            // Future entries may be leaked additions or recorded members with bad
            // confirmation times. Reject only a history contradiction either way.
            if (insufficient && confirmedPivotCount >= 2 || !insufficient && pivots.size() < 2) {
                throw corrupt(display, line, where + "status " + status
                        + " conflicts with the confirmed/captured pivot history");
            }
            if ("COMPLETE".equals(status)) {
                if (direction.isJsonNull()
                        || !direction.equals(candidates.get(0).getAsJsonObject().get("direction"))) {
                    throw corrupt(display, line, where + "direction must match the COMPLETE candidate");
                }
            } else if (!"FORMING".equals(status) && !direction.isJsonNull()) {
                throw corrupt(display, line, where + "direction must be null for " + status);
            }
        }
        requireTexts(display, line, record, "labels", where);
        final Set<String> labels = new HashSet<>();
        final JsonArray recordedLabels = record.getAsJsonArray("labels");
        for (int i = 0; i < recordedLabels.size(); i++) {
            if (!labels.add(recordedLabels.get(i).getAsString())) {
                throw corrupt(display, line, where + "labels[" + i + "] duplicates a label");
            }
        }
        final Set<String> expectedLabels = "topology".equals(kind)
                ? topologyLabels(display, line, record, pivots, visiblePrices, candidates, requiredPivots, where)
                : alternativeLabels(display, line, record, pivots, visiblePrices, where);
        if (!labels.equals(expectedLabels)) {
            throw corrupt(display, line, where + "labels do not match the recorded outcome");
        }
    }

    private static Set<String> topologyLabels(final String display, final long line, final JsonObject record,
            final JsonArray pivots, final List<BigDecimal> prices, final JsonArray candidates, final int requiredPivots,
            final String where) {
        final String status = text(record, "status");
        final int formingStart = requireInteger(display, line, record, "formingStartBarIndex", where);
        final int formingEnd = requireInteger(display, line, record, "formingEndBarIndex", where);
        requireText(display, line, record, "explanation", where);
        if ("FORMING".equals(status)) {
            final int asOfIndex = record.get("asOfIndex").getAsInt();
            if (formingStart < 0 || formingEnd < formingStart || formingEnd > asOfIndex) {
                throw corrupt(display, line, where
                        + "formingStartBarIndex/formingEndBarIndex must be ordered within the as-of view");
            }
            int startPosition = -1;
            int endPosition = -1;
            int suffixSize = 0;
            for (int p = pivots.size() - 1; p >= 0; p--) {
                final JsonObject pivot = pivots.get(p).getAsJsonObject();
                // Captured suffix members retain their shape even when their confirmation
                // times are inconsistent. Only a future tail beyond the declared end is
                // omitted, leaving projection to suppress and warn about future members.
                if (pivot.get("index").getAsInt() > formingEnd
                        && pivot.get("confirmationIndex").getAsInt() > asOfIndex) {
                    continue;
                }
                if (endPosition < 0) {
                    endPosition = p;
                }
                if (pivot.get("index").getAsInt() < formingStart) {
                    break;
                }
                suffixSize++;
                if (pivot.get("index").getAsInt() == formingStart) {
                    startPosition = p;
                    break;
                }
            }
            if (startPosition < 0 || endPosition < 0
                    || pivots.get(endPosition).getAsJsonObject().get("index").getAsInt() != formingEnd || suffixSize < 3
                    || suffixSize >= requiredPivots) {
                throw corrupt(display, line, where
                        + "formingStartBarIndex/formingEndBarIndex must identify a contiguous trailing suffix of 3 to "
                        + (requiredPivots - 1)
                        + " captured pivots; only future-confirmed pivots may follow its declared end");
            }
            final boolean bullish = "BULLISH".equals(text(record, "direction"));
            final boolean corrective = "CORRECTIVE_3".equals(text(record, "grammar"));
            if ((bullish != corrective)
                    != "LOW".equals(text(pivots.get(startPosition).getAsJsonObject(), "type"))) {
                throw corrupt(display, line, where + "forming suffix has the wrong origin type for its direction");
            }
            int previous = startPosition;
            int leg = 0;
            for (int p = startPosition + 1; p <= endPosition; p++) {
                final JsonObject pivot = pivots.get(p).getAsJsonObject();
                final boolean rising = (corrective ? leg == 1 : leg % 2 == 0) == bullish;
                final int change = prices.get(p).compareTo(prices.get(previous));
                if (text(pivot, "type").equals(text(pivots.get(previous).getAsJsonObject(), "type"))
                        || (rising ? change <= 0 : change >= 0)) {
                    throw corrupt(display, line, where + "forming suffix must match its grammar and direction");
                }
                previous = p;
                leg++;
            }
        } else if (formingStart != -1 || formingEnd != -1) {
            throw corrupt(display, line, where
                    + "formingStartBarIndex/formingEndBarIndex must both be -1 outside FORMING");
        }
        return switch (status) {
        case "COMPLETE", "AMBIGUOUS" -> {
            final Set<String> labels = new HashSet<>();
            for (final JsonElement element : candidates) {
                final JsonObject candidate = element.getAsJsonObject();
                final JsonArray placement = candidate.getAsJsonArray("placement");
                labels.add(text(candidate, "direction") + ":"
                        + placement.get(0).getAsJsonObject().get("index").getAsInt() + "-"
                        + placement.get(placement.size() - 1).getAsJsonObject().get("index").getAsInt());
            }
            yield labels;
        }
        case "FORMING" -> Set.of("forming:" + text(record, "direction") + ":" + formingStart + "-" + formingEnd);
        case "INVALIDATED" -> Set.of("invalidated:" + text(record, "explanation"));
        case "INSUFFICIENT_HISTORY" -> Set.of("insufficient-history");
        case "NO_MATCH" -> Set.of();
        default -> throw corrupt(display, line, where + "status is not a topology status");
        };
    }

    private static Set<String> alternativeLabels(final String display, final long line, final JsonObject record,
            final JsonArray pivots, final List<BigDecimal> prices, final String where) {
        final String grammar = text(record, "grammar");
        final String status = text(record, "status");
        if ("change-point-baseline".equals(grammar)) {
            if (!pivots.isEmpty()) {
                throw corrupt(display, line, where + "pivots must be empty for change-point-baseline");
            }
            // Closes are not captured here: verify the recorded outcome's label,
            // without introducing a second price-based classification.
            return switch (status) {
            case "insufficient-history" -> Set.of("insufficient-history");
            case "complete" -> Set.of("change@" + record.get("asOfIndex").getAsInt());
            case "no-match" -> Set.of("stable");
            default -> throw corrupt(display, line, where + "status is not a change-point-baseline outcome");
            };
        }
        final int segmentLegs = switch (grammar) {
        case "3+3" -> 3;
        case "5+5" -> 5;
        case "MOTIVE_5", "CORRECTIVE_3", "CYCLE_5_3" -> throw corrupt(display, line,
                where + "grammar is a topology grammar but kind is alternative");
        default -> throw corrupt(display, line, where + "grammar is not a supported alternative grammar");
        };
        final Set<String> labels = new HashSet<>();
        final String expectedStatus;
        if (pivots.size() < 2) {
            expectedStatus = "insufficient-history";
            labels.add("insufficient-history");
        } else {
            final int required = segmentLegs * 2 + 1;
            final int horizon = Math.max(0, pivots.size() - required);
            for (int start = 0; start + required <= pivots.size(); start++) {
                if (start + required - 1 >= horizon
                        && matchesAlternative(pivots, prices, start, required, segmentLegs, true)) {
                    labels.add(pivots.get(start).getAsJsonObject().get("index").getAsInt() + "-"
                            + pivots.get(start + required - 1).getAsJsonObject().get("index").getAsInt());
                }
            }
            if (!labels.isEmpty()) {
                expectedStatus = labels.size() == 1 ? "complete" : "ambiguous";
            } else {
                for (int suffix = Math.min(pivots.size(), segmentLegs * 2); suffix >= segmentLegs + 1; suffix--) {
                    final int start = pivots.size() - suffix;
                    if (matchesAlternative(pivots, prices, start, suffix, segmentLegs, false)) {
                        final String direction = "LOW".equals(text(pivots.get(start).getAsJsonObject(), "type"))
                                ? "BULLISH" : "BEARISH";
                        labels.add(direction + ":" + pivots.get(start).getAsJsonObject().get("index").getAsInt() + "-"
                                + pivots.get(pivots.size() - 1).getAsJsonObject().get("index").getAsInt());
                    }
                }
                expectedStatus = labels.isEmpty() ? "no-match" : labels.size() == 1 ? "forming" : "ambiguous";
                if (labels.isEmpty()) {
                    labels.add("no-match");
                }
            }
        }
        if (!status.equals(expectedStatus)) {
            throw corrupt(display, line, where + "status does not match the visible pivots: expected " + expectedStatus);
        }
        return labels;
    }

    private static boolean matchesAlternative(final JsonArray pivots, final List<BigDecimal> prices, final int start,
            final int count, final int segmentLegs, final boolean complete) {
        final boolean bullish = "LOW".equals(text(pivots.get(start).getAsJsonObject(), "type"));
        for (int leg = 0; leg < count - 1; leg++) {
            if (text(pivots.get(start + leg).getAsJsonObject(), "type")
                    .equals(text(pivots.get(start + leg + 1).getAsJsonObject(), "type"))) {
                return false;
            }
            final int change = prices.get(start + leg + 1).compareTo(prices.get(start + leg));
            final int signed = bullish ? change : -change;
            final boolean positive = leg < segmentLegs ? leg % 2 == 0 : (leg - segmentLegs) % 2 != 0;
            if (signed == 0 ? complete || leg < count - 2 : positive ? signed < 0 : signed > 0) {
                return false;
            }
        }
        final int junction = start + segmentLegs;
        final String junctionType = text(pivots.get(junction).getAsJsonObject(), "type");
        for (int previous = start; previous < junction; previous++) {
            if (junctionType.equals(text(pivots.get(previous).getAsJsonObject(), "type"))) {
                final int change = prices.get(junction).compareTo(prices.get(previous));
                if ("HIGH".equals(junctionType) ? change <= 0 : change >= 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isWaveDirection(final String direction) {
        return "BULLISH".equals(direction) || "BEARISH".equals(direction);
    }

    private static int requireGrammar(final String display, final long line, final JsonObject record,
            final String where) {
        return switch (text(record, "grammar")) {
        case "MOTIVE_5" -> 6;
        case "CORRECTIVE_3" -> 4;
        case "CYCLE_5_3" -> 9;
        default -> throw corrupt(display, line, where + "grammar is not a topology grammar");
        };
    }

    private static BigDecimal requirePlacement(final String display, final long line, final JsonElement element,
            final String at) {
        final JsonObject placement = requireObject(display, line, element, at);
        final int index = requireInteger(display, line, placement, "index", at + ".");
        requireText(display, line, placement, "price", at + ".");
        requireText(display, line, placement, "type", at + ".");
        if (index < 0) {
            throw corrupt(display, line, at + ".index must be nonnegative");
        }
        if (!"HIGH".equals(text(placement, "type")) && !"LOW".equals(text(placement, "type"))) {
            throw corrupt(display, line, at + ".type must be HIGH or LOW");
        }
        try {
            return new BigDecimal(text(placement, "price"));
        } catch (final NumberFormatException e) {
            throw corrupt(display, line, at + ".price must be a supported finite number");
        }
    }

    private static JsonObject requireObject(final String display, final long line, final JsonElement element,
            final String at) {
        if (element == null || !element.isJsonObject()) {
            throw corrupt(display, line, at + " is not an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(final String display, final long line, final JsonObject owner,
            final String field, final String where) {
        final JsonElement value = owner.get(field);
        if (value == null || !value.isJsonArray()) {
            throw corrupt(display, line, where + field + " is missing or not an array");
        }
        return value.getAsJsonArray();
    }

    private static void requireTexts(final String display, final long line, final JsonObject owner, final String field,
            final String where) {
        final JsonArray array = requireArray(display, line, owner, field, where);
        for (int i = 0; i < array.size(); i++) {
            final JsonElement element = array.get(i);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw corrupt(display, line, where + field + "[" + i + "] is not a string");
            }
        }
    }

    private static void requireText(final String display, final long line, final JsonObject owner, final String field,
            final String where) {
        if (textOrNull(owner, field) == null) {
            throw corrupt(display, line, where + field + " is missing or not a string");
        }
    }

    private static int requireInteger(final String display, final long line, final JsonObject owner,
            final String field, final String where) {
        final JsonElement value = owner.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw corrupt(display, line, where + field + " is missing or not an integer");
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw corrupt(display, line, where + field + " is not an exact 32-bit integer");
        }
    }

    private static String textOrNull(final JsonObject object, final String name) {
        final JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString()
                : null;
    }

    private static ReplayArtifactException corrupt(final String display, final long line, final String message) {
        return new ReplayArtifactException("trace " + display + " line " + line + ": " + message
                + "; the trace is corrupt or foreign, regenerate the run");
    }

    private static String signature(final JsonObject record) {
        final JsonObject copy = record.deepCopy();
        copy.remove("asOfIndex");
        copy.remove("asOfTime");
        copy.remove("bar");
        return sha256(copy.toString());
    }

    private static String sha256(final String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every Java platform", e);
        }
    }

    JsonObject header() {
        return header;
    }

    long recordCount() {
        return recordCount;
    }

    String display() {
        return display;
    }

    /**
     * @param family the family
     * @return its entries ordered by as-of index, or an empty list
     */
    List<Entry> entries(final Family family) {
        return List.copyOf(entries.getOrDefault(family, List.of()));
    }

    String comparisonEvidence(final Family family, final ReplayArtifact.ComparisonRow row, final boolean nullMember) {
        return contributions.get(family).evidence(row, nullMember, display);
    }

    /**
     * Re-reads one record from disk.
     *
     * @param entry an entry previously returned by {@link #entries(Family)}
     * @return the parsed record
     */
    JsonObject read(final Entry entry) {
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            final byte[] bytes = new byte[entry.length()];
            in.seek(entry.offset());
            in.readFully(bytes);
            final JsonObject record = parseRecord(new String(bytes, StandardCharsets.UTF_8), display);
            if (!sha256(record.toString()).equals(entry.digest())) {
                throw new ReplayArtifactException("trace " + display + " changed on disk after it was indexed (record "
                        + "as-of " + entry.asOfIndex() + " differs from the scanned one); reopen the replay");
            }
            return record;
        } catch (IOException e) {
            throw new ReplayArtifactException("cannot read trace " + display + ": " + e.getMessage(), e);
        }
    }

    private static String text(final JsonObject object, final String name) {
        final JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
