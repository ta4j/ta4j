/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ta4jexamples.charting.replay.ReplayArtifact.PriceBar;
import ta4jexamples.charting.replay.ReplayTraceIndex.Family;

/**
 * Immutable, as-observed semantic state of one replay cursor.
 *
 * <p>
 * The frame is the single source for the chart, the evidence text and the JSON
 * export, so the three can only disagree if this projection is wrong. It is
 * built from one recorded as-of record and the bars up to the cursor; nothing
 * after the cursor can reach it. Pivots whose confirmation index lies after the
 * cursor are dropped and counted in {@link #suppressedFuture()}, which stays
 * zero for artifacts produced by the research recorder.
 *
 * @param comparisonKey      the {@code comparisons.csv} key the replay was
 *                           opened from
 * @param family             recorded stream (section, mode, grammar, detector,
 *                           partition)
 * @param activeRules        rule ids active in the recorded mode
 * @param cursor             as-of bar index
 * @param asOfTime           recorded as-of timestamp
 * @param kind               record kind ({@code topology} or {@code alternative})
 * @param status             recorded topology/alternative status
 * @param direction          recorded direction or empty
 * @param windowStart        first visible bar index
 * @param pivots             confirmed pivots, oldest first
 * @param candidates         every retained alternative in recorded order
 * @param labels             recorded alternative labels
 * @param overlayCap         maximum simultaneous overlays
 * @param overlayed          number of candidates drawn
 * @param truncated          number of candidates not drawn because of the cap
 * @param axis               price bounds of the visible window
 * @param suppressedFuture   pivot/placement points removed by the causality
 *                           guard
 * @param clippedPoints      overlay points left of the visible window
 * @param transition         whether this record differs from the previous
 *                           recorded one
 * @param selectedCandidate  selected candidate key or empty
 * @param ruleEvidenceHint   pointer for modes that evaluate no rules or empty
 */
record ReplayFrame(String comparisonKey, Family family, List<String> activeRules, int cursor, String asOfTime,
        String kind, String status, String direction, int windowStart, List<Pivot> pivots, List<Candidate> candidates,
        List<String> labels, int overlayCap, int overlayed, int truncated, Axis axis, int suppressedFuture,
        int clippedPoints, boolean transition, String selectedCandidate, String ruleEvidenceHint) {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** A pivot visible at the cursor. */
    record Pivot(int index, String price, String type, int confirmationIndex, boolean newlyConfirmed,
            boolean inWindow) {
    }

    /** One leg endpoint of a candidate. */
    record Point(int index, String price, String type, boolean inWindow) {
    }

    /** One rule evaluation recorded for a candidate. */
    record Rule(String id, String state, String score, List<String> observations, String explanation) {
    }

    /** One retained alternative at the cursor. */
    record Candidate(int ordinal, String candidateKey, String version, String direction, List<Point> placement,
            List<Rule> rules, boolean overlayed, boolean selected) {
    }

    /** Price bounds derived from the visible window only. */
    record Axis(String low, String high) {
    }

    ReplayFrame {
        activeRules = List.copyOf(activeRules);
        pivots = List.copyOf(pivots);
        candidates = List.copyOf(candidates);
        labels = List.copyOf(labels);
    }

    /**
     * Projects one recorded as-of record into a frame.
     *
     * @param comparisonKey    the comparison key being replayed
     * @param family           the family the record belongs to
     * @param activeRules      the rules active in the recorded mode
     * @param record           the recorded as-of record
     * @param bars             all retained bars of the dataset; only
     *                         {@code [windowStart, cursor]} is read
     * @param transition       whether the record differs from its predecessor
     * @param selected         selected candidate key or empty
     * @param window           visible bar count
     * @param cap              maximum simultaneous overlays
     * @param ruleEvidenceHint pointer shown when the mode evaluates no rules
     * @return the frame
     */
    static ReplayFrame project(final String comparisonKey, final Family family, final List<String> activeRules,
            final JsonObject record, final List<PriceBar> bars, final boolean transition, final String selected,
            final int window, final int cap, final String ruleEvidenceHint) {
        Objects.requireNonNull(selected, "selected");
        if (window < 1 || cap < 1) {
            throw new IllegalArgumentException("window and overlay cap must be positive");
        }
        final int cursor = record.get("asOfIndex").getAsInt();
        if (cursor >= bars.size()) {
            throw new ReplayArtifactException("trace records as-of " + cursor + " but the retained price bars end at "
                    + (bars.size() - 1) + "; the bundle is inconsistent. Regenerate the run.");
        }
        final int windowStart = Math.max(0, cursor - window + 1);
        int suppressed = 0;
        final List<Pivot> pivots = new ArrayList<>();
        final List<Integer> rejected = new ArrayList<>();
        for (final JsonElement element : array(record, "pivots")) {
            final JsonObject pivot = element.getAsJsonObject();
            final int index = pivot.get("index").getAsInt();
            final int confirmation = pivot.get("confirmationIndex").getAsInt();
            if (index > cursor || confirmation > cursor) {
                suppressed++;
                rejected.add(index);
                continue;
            }
            pivots.add(new Pivot(index, text(pivot, "price"), text(pivot, "type"), confirmation,
                    confirmation == cursor, index >= windowStart));
        }
        final List<Candidate> candidates = new ArrayList<>();
        final JsonArray rawCandidates = array(record, "candidates");
        final List<Integer> overlaySlots = overlaySlots(rawCandidates, selected, cap);
        int clipped = 0;
        for (int ordinal = 0; ordinal < rawCandidates.size(); ordinal++) {
            final JsonObject candidate = rawCandidates.get(ordinal).getAsJsonObject();
            final boolean overlay = overlaySlots.contains(ordinal);
            final List<Point> placement = new ArrayList<>();
            for (final JsonElement element : array(candidate, "placement")) {
                final JsonObject point = element.getAsJsonObject();
                final int index = point.get("index").getAsInt();
                if (index > cursor || rejected.contains(index)) {
                    suppressed++;
                    continue;
                }
                final boolean inWindow = index >= windowStart;
                if (overlay && !inWindow) {
                    clipped++;
                }
                placement.add(new Point(index, text(point, "price"), text(point, "type"), inWindow));
            }
            final List<Rule> rules = new ArrayList<>();
            for (final JsonElement element : array(candidate, "rules")) {
                final JsonObject rule = element.getAsJsonObject();
                final List<String> observations = new ArrayList<>();
                array(rule, "observations").forEach(observation -> observations.add(observation.getAsString()));
                rules.add(new Rule(text(rule, "id"), text(rule, "state"), rule.has("score") && !rule.get("score").isJsonNull()
                        ? rule.get("score").getAsString()
                        : "", List.copyOf(observations), text(rule, "explanation")));
            }
            final String key = text(candidate, "candidateKey");
            candidates.add(new Candidate(ordinal, key, text(candidate, "version"), text(candidate, "direction"),
                    List.copyOf(placement), List.copyOf(rules), overlay, key.equals(selected)));
        }
        final List<String> labels = new ArrayList<>();
        array(record, "labels").forEach(label -> labels.add(label.getAsString()));
        final int overlayed = overlaySlots.size();
        return new ReplayFrame(comparisonKey, family, activeRules, cursor, text(record, "asOfTime"),
                text(record, "kind"), text(record, "status"), text(record, "direction"), windowStart, pivots, candidates,
                labels, cap, overlayed, rawCandidates.size() - overlayed, axis(bars, windowStart, cursor, pivots, candidates),
                suppressed, clipped, transition, selected, ruleEvidenceHint);
    }

    /** Selected candidate first, then recorded order, until the cap is filled. */
    private static List<Integer> overlaySlots(final JsonArray candidates, final String selected, final int cap) {
        final List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < candidates.size() && slots.size() < cap; i++) {
            if (text(candidates.get(i).getAsJsonObject(), "candidateKey").equals(selected)) {
                slots.add(i);
            }
        }
        for (int i = 0; i < candidates.size() && slots.size() < cap; i++) {
            if (!slots.contains(i)) {
                slots.add(i);
            }
        }
        return slots;
    }

    private static Axis axis(final List<PriceBar> bars, final int from, final int to, final List<Pivot> pivots,
            final List<Candidate> candidates) {
        BigDecimal low = null;
        BigDecimal high = null;
        final List<String> values = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            values.add(bars.get(i).low());
            values.add(bars.get(i).high());
        }
        pivots.stream().filter(Pivot::inWindow).forEach(pivot -> values.add(pivot.price()));
        candidates.stream()
                .filter(Candidate::overlayed)
                .flatMap(candidate -> candidate.placement().stream())
                .filter(Point::inWindow)
                .forEach(point -> values.add(point.price()));
        for (final String value : values) {
            final BigDecimal number = new BigDecimal(value);
            low = low == null || number.compareTo(low) < 0 ? number : low;
            high = high == null || number.compareTo(high) > 0 ? number : high;
        }
        return new Axis(low.toPlainString(), high.toPlainString());
    }

    /**
     * @return the candidate with the given key, if present at this cursor
     */
    java.util.Optional<Candidate> candidate(final String candidateKey) {
        return candidates.stream().filter(candidate -> candidate.candidateKey().equals(candidateKey)).findFirst();
    }

    /**
     * Serialises the frame with a fixed field order so equal frames are
     * byte-identical.
     *
     * @return pretty-printed JSON ending in a newline
     */
    String toSemanticJson() {
        final JsonObject root = new JsonObject();
        root.addProperty("schema", "elliott-replay-frame/1");
        root.addProperty("comparisonKey", comparisonKey);
        final JsonObject familyObject = new JsonObject();
        familyObject.addProperty("section", family.section());
        familyObject.addProperty("mode", family.mode());
        familyObject.addProperty("grammar", family.grammar());
        familyObject.addProperty("detector", family.detector());
        familyObject.addProperty("partition", family.partition());
        root.add("family", familyObject);
        root.add("activeRules", strings(activeRules));
        root.addProperty("cursor", cursor);
        root.addProperty("asOfTime", asOfTime);
        root.addProperty("kind", kind);
        root.addProperty("status", status);
        root.addProperty("direction", direction);
        root.addProperty("transition", transition);
        final JsonObject window = new JsonObject();
        window.addProperty("start", windowStart);
        window.addProperty("end", cursor);
        root.add("window", window);
        final JsonObject axisObject = new JsonObject();
        axisObject.addProperty("low", axis.low());
        axisObject.addProperty("high", axis.high());
        root.add("axis", axisObject);
        final JsonArray pivotArray = new JsonArray();
        for (final Pivot pivot : pivots) {
            final JsonObject object = new JsonObject();
            object.addProperty("index", pivot.index());
            object.addProperty("price", pivot.price());
            object.addProperty("type", pivot.type());
            object.addProperty("confirmationIndex", pivot.confirmationIndex());
            object.addProperty("newlyConfirmed", pivot.newlyConfirmed());
            object.addProperty("inWindow", pivot.inWindow());
            pivotArray.add(object);
        }
        root.add("pivots", pivotArray);
        final JsonObject overlay = new JsonObject();
        overlay.addProperty("cap", overlayCap);
        overlay.addProperty("total", candidates.size());
        overlay.addProperty("drawn", overlayed);
        overlay.addProperty("truncated", truncated);
        overlay.addProperty("clippedPoints", clippedPoints);
        root.add("overlay", overlay);
        root.addProperty("selectedCandidate", selectedCandidate);
        final JsonArray candidateArray = new JsonArray();
        for (final Candidate candidate : candidates) {
            candidateArray.add(candidateJson(candidate));
        }
        root.add("candidates", candidateArray);
        root.add("labels", strings(labels));
        root.addProperty("suppressedFuture", suppressedFuture);
        root.addProperty("ruleEvidenceHint", ruleEvidenceHint);
        return GSON.toJson(root) + "\n";
    }

    private static JsonObject candidateJson(final Candidate candidate) {
        final JsonObject object = new JsonObject();
        object.addProperty("ordinal", candidate.ordinal());
        object.addProperty("candidateKey", candidate.candidateKey());
        object.addProperty("version", candidate.version());
        object.addProperty("direction", candidate.direction());
        object.addProperty("overlayed", candidate.overlayed());
        object.addProperty("selected", candidate.selected());
        final JsonArray placement = new JsonArray();
        for (final Point point : candidate.placement()) {
            final JsonObject pointObject = new JsonObject();
            pointObject.addProperty("index", point.index());
            pointObject.addProperty("price", point.price());
            pointObject.addProperty("type", point.type());
            pointObject.addProperty("inWindow", point.inWindow());
            placement.add(pointObject);
        }
        object.add("placement", placement);
        final JsonArray rules = new JsonArray();
        for (final Rule rule : candidate.rules()) {
            final JsonObject ruleObject = new JsonObject();
            ruleObject.addProperty("id", rule.id());
            ruleObject.addProperty("state", rule.state());
            ruleObject.addProperty("score", rule.score());
            ruleObject.add("observations", strings(rule.observations()));
            ruleObject.addProperty("explanation", rule.explanation());
            rules.add(ruleObject);
        }
        object.add("rules", rules);
        return object;
    }

    /**
     * @return SHA-256 of {@link #toSemanticJson()}
     */
    String digest() {
        return ReplayArtifact.sha256(toSemanticJson().getBytes(StandardCharsets.UTF_8));
    }

    private static JsonArray strings(final List<String> values) {
        final JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static JsonArray array(final JsonObject object, final String name) {
        final JsonElement value = object.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String text(final JsonObject object, final String name) {
        final JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
