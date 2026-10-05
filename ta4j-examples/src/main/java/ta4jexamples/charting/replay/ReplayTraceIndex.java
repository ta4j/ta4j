/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * @param bar       the price bar recorded with a null-member record, or null
     *                  when the record carries none (real traces)
     */
    record Entry(int asOfIndex, long offset, int length, String signature, String status, int candidateCount,
            PriceBar bar) {
    }

    private final Path file;
    private final String display;
    private final JsonObject header;
    private final long recordCount;
    private final Map<Family, List<Entry>> entries;

    private ReplayTraceIndex(final Path file, final String display, final JsonObject header, final long recordCount,
            final Map<Family, List<Entry>> entries) {
        this.file = file;
        this.display = display;
        this.header = header;
        this.recordCount = recordCount;
        this.entries = entries;
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
     * @return the index
     */
    static ReplayTraceIndex scan(final Path file, final String display, final Predicate<Family> keep) {
        Objects.requireNonNull(keep, "keep");
        final Scan scan = new Scan(display, keep);
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
        private final Map<Family, List<Entry>> entries = new LinkedHashMap<>();
        private JsonObject header;
        private JsonObject footer;
        private long records;
        private long lineNumber;

        Scan(final String display, final Predicate<Family> keep) {
            this.display = display;
            this.keep = keep;
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
                    records++;
                    index(entries, record, offset, length, keep, display);
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
            if (declared == null || !declared.isJsonPrimitive() || !declared.getAsJsonPrimitive().isNumber()
                    || declared.getAsLong() != records) {
                throw new ReplayArtifactException("trace " + display + " footer declares " + declared + " records but "
                        + records + " were read; regenerate the run");
            }
            return new ReplayTraceIndex(file, display, header, records, entries);
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

    private static void index(final Map<Family, List<Entry>> entries, final JsonObject record, final long offset,
            final int length, final Predicate<Family> keep, final String display) {
        if (!record.has("section") || !record.has("asOfIndex")) {
            throw new ReplayArtifactException("trace " + display + " contains a record without section/asOfIndex");
        }
        final Family family = Family.of(record);
        if (!keep.test(family)) {
            return;
        }
        final int asOf = record.get("asOfIndex").getAsInt();
        final List<Entry> list = entries.computeIfAbsent(family, ignored -> new ArrayList<>());
        if (!list.isEmpty() && list.getLast().asOfIndex() >= asOf) {
            throw new ReplayArtifactException("trace " + display + " records as-of " + asOf + " out of order for "
                    + family + "; regenerate the run");
        }
        final JsonElement candidates = record.get("candidates");
        list.add(new Entry(asOf, offset, length, signature(record), text(record, "status"),
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
        }
        for (final String field : List.of("nullBlockLength", "nullMemberIndex", "asOfIndex")) {
            requireInteger(display, line, record, field, where);
        }
        final JsonElement direction = record.get("direction");
        if (direction == null || !direction.isJsonNull() && textOrNull(record, "direction") == null) {
            throw corrupt(display, line, where + "direction is missing or not a string or null");
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
        for (int i = 0; i < pivots.size(); i++) {
            final JsonObject pivot = requirePlacement(display, line, pivots.get(i), where + "pivots[" + i + "]");
            requireInteger(display, line, pivot, "confirmationIndex", where + "pivots[" + i + "].");
        }
        final JsonArray candidates = requireArray(display, line, record, "candidates", where);
        for (int i = 0; i < candidates.size(); i++) {
            final String at = where + "candidates[" + i + "]";
            final JsonObject candidate = requireObject(display, line, candidates.get(i), at);
            for (final String field : List.of("candidateKey", "version", "direction")) {
                requireText(display, line, candidate, field, at + ".");
            }
            final JsonArray placement = requireArray(display, line, candidate, "placement", at + ".");
            for (int p = 0; p < placement.size(); p++) {
                requirePlacement(display, line, placement.get(p), at + ".placement[" + p + "]");
            }
            final JsonArray rules = requireArray(display, line, candidate, "rules", at + ".");
            for (int r = 0; r < rules.size(); r++) {
                final String ruleAt = at + ".rules[" + r + "]";
                final JsonObject rule = requireObject(display, line, rules.get(r), ruleAt);
                for (final String field : List.of("id", "state", "explanation")) {
                    requireText(display, line, rule, field, ruleAt + ".");
                }
                final JsonElement score = rule.get("score");
                if (score == null
                        || !score.isJsonNull() && !(score.isJsonPrimitive() && score.getAsJsonPrimitive().isNumber())) {
                    throw corrupt(display, line, ruleAt + ".score is missing or not a number or null");
                }
                requireTexts(display, line, rule, "observations", ruleAt + ".");
            }
        }
        if ("alternative".equals(kind)) {
            requireTexts(display, line, record, "labels", where);
        }
    }

    private static JsonObject requirePlacement(final String display, final long line, final JsonElement element,
            final String at) {
        final JsonObject placement = requireObject(display, line, element, at);
        requireInteger(display, line, placement, "index", at + ".");
        requireText(display, line, placement, "price", at + ".");
        requireText(display, line, placement, "type", at + ".");
        return placement;
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

    private static void requireInteger(final String display, final long line, final JsonObject owner,
            final String field, final String where) {
        final JsonElement value = owner.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || value.getAsDouble() != Math.rint(value.getAsDouble())) {
            throw corrupt(display, line, where + field + " is missing or not an integer");
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
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(copy.toString().getBytes(StandardCharsets.UTF_8)));
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
            if (!record.has("asOfIndex") || record.get("asOfIndex").getAsInt() != entry.asOfIndex()) {
                throw new ReplayArtifactException("trace " + display + " changed on disk after it was indexed (as-of "
                        + entry.asOfIndex() + " moved); reopen the replay");
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
