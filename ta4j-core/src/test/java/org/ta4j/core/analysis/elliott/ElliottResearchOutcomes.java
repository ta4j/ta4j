/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.analysis.elliott.ElliottResearchEvents.Event;
import org.ta4j.core.analysis.elliott.ElliottResearchEvents.Stream;
import org.ta4j.core.analysis.elliott.ElliottResearchEvents.Tape;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.num.Num;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Future-aware, offline outcome labels and aggregates for
 * {@link ElliottResearchEvents}.
 *
 * <p>
 * Two questions are answered independently for every enrolled event and every
 * configured horizon {@code h}, with decision index {@code d}:
 * </p>
 * <ul>
 * <li><strong>Structural</strong>: the first absorbing structural result
 * {@code r} is the earliest of correction completion, withdrawal, and
 * invalidation. {@code r <= d} is
 * {@link Structural#ALREADY_RESOLVED already resolved at enrollment} and
 * leaves the prospective denominator. {@code r - d <= h} (a confirmation exactly
 * at the boundary is included, one bar later is not) is a success or an
 * invalidation/withdrawal. Otherwise a stream that observed bar {@code d + h}
 * reports a {@link Structural#HORIZON_EXPIRED full-horizon failure}; a stream
 * that ended earlier is right-censored. A tie between an invalidation and a
 * completion on the same observation resolves as invalidation.</li>
 * <li><strong>Price</strong>: raw and motive-direction-aligned close-to-close
 * return and future-only excursions over {@code d+1..d+h}, only when the whole
 * window lies inside the stream's partition. Structural resolution never makes
 * a truncated price window available. Same-bar target and invalidation touches
 * are {@link Touch#UNRESOLVED_INTRABAR_ORDER unresolved} under the research
 * policy; the legacy policy, which equals the released
 * {@code ElliottWaveOutcomeLabeler} same-bar rule, is reported alongside.</li>
 * </ul>
 *
 * <p>
 * Aggregates are additive {@link Tally tallies} per stream (real data or one
 * identified null member), partition, and horizon, plus an {@code ALL} row that
 * sums a stream's partitions. Resolved rate is {@code success / resolved}; the
 * assumption-free cohort bounds are {@code success / prospective} to
 * {@code (success + censored) / prospective}. Candidate and horizon events
 * overlap, so the deterministic non-overlapping cohort is a sensitivity view
 * only and no significance is computed. Nothing here is read by recognition.
 * </p>
 *
 * @since 0.26.1
 */
final class ElliottResearchOutcomes {

    static final String SCHEMA = "ta4j-elliott-research-events/1";
    static final String EVENTS_FILE = "events.jsonl";
    static final String OUTCOMES_FILE = "outcomes.csv";
    static final String SUMMARY_FILE = "outcomes-summary.csv";
    static final String ALL_PARTITIONS = "ALL";
    /** Trailing close-to-close lookback of the frozen momentum-direction baseline. */
    static final int MOMENTUM_BARS = 20;
    static final List<Integer> DEFAULT_HORIZONS = List.of(5, 20, 60);
    static final String DEFAULT_STRUCTURAL_MODE = "classical-all";

    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final List<String> OUTCOMES_HEADER = List.of("dataset", "stream", "partition", "candidateKey",
            "version", "direction", "decisionIndex", "decisionTime", "ambiguous", "carriedIn", "horizon", "structural",
            "cause", "resolutionIndex", "priceStatus", "priceReason", "rawReturn", "alignedReturn", "favorableExcursion",
            "adverseExcursion", "researchTouch", "legacyTouch");
    private static final List<String> SUMMARY_HEADER = List.of("dataset", "stream", "partition", "horizon",
            "structuralMode", "invalidation", "enrolled", "alreadyResolved", "prospective", "success", "invalidated",
            "expired", "censored", "resolved", "resolvedRate", "lowerBound", "upperBound", "priceAvailable",
            "priceBeyondPartition", "priceInvalid", "priceNoTape", "meanRaw", "meanAligned", "meanFavorable",
            "meanAdverse", "upRate", "researchTarget", "researchInvalidation", "researchUnresolved", "researchNeither",
            "legacyTarget", "legacyInvalidation", "legacyNeither", "unconditionalDates", "unconditionalMeanRaw",
            "unconditionalUpRate", "momentumAllDates", "momentumAllMeanAligned", "momentumEventDates",
            "momentumEventMeanAligned", "cohortKept", "cohortDiscarded", "cohortSuccess", "cohortInvalidated",
            "cohortExpired", "cohortCensored", "cohortResolvedRate");

    private ElliottResearchOutcomes() {
    }

    /** Terminal structural state of one event at one horizon. */
    enum Structural {
        CORRECTION_COMPLETED("correction-completed"), INVALIDATED_OR_WITHDRAWN("invalidated/withdrawn"),
        HORIZON_EXPIRED("horizon-expired-without-completion"), CENSORED("censored"),
        ALREADY_RESOLVED("already-resolved-at-enrollment");

        private final String id;

        Structural(final String id) {
            this.id = id;
        }

        String id() {
            return id;
        }
    }

    /** First structural level touched inside the price window. */
    enum Touch {
        TARGET_FIRST("target-first"), INVALIDATION_FIRST("invalidation-first"),
        UNRESOLVED_INTRABAR_ORDER("unresolved-intrabar-order"), NEITHER("neither");

        private final String id;

        Touch(final String id) {
            this.id = id;
        }

        String id() {
            return id;
        }
    }

    /** How motive invalidation is declared, identically for real and null data. */
    enum Invalidation {
        /** A later confirmed counter-pivot beyond the motive origin price. */
        ORIGIN_PIVOT("origin-pivot"),
        /** Any later bar extreme beyond the motive origin price. */
        ORIGIN_PRICE("origin-price");

        private final String id;

        Invalidation(final String id) {
            this.id = id;
        }

        String id() {
            return id;
        }

        static Invalidation parse(final String text) {
            for (final Invalidation value : values()) {
                if (value.id.equals(text)) {
                    return value;
                }
            }
            throw new IllegalArgumentException(
                    "outcomes.invalidation '" + text + "' must be one of origin-pivot, origin-price");
        }
    }

    /**
     * Declared outcome recipe settings. Horizons are example bar counts, not tuned
     * values.
     *
     * @param horizons       strictly increasing positive horizons in bars
     * @param structuralMode H2 study mode whose complete cycle candidates define
     *                       correction completion
     * @param invalidation   invalidation policy
     */
    record Settings(List<Integer> horizons, String structuralMode, Invalidation invalidation) {

        Settings {
            horizons = List.copyOf(Objects.requireNonNull(horizons, "horizons"));
            Objects.requireNonNull(structuralMode, "structuralMode");
            Objects.requireNonNull(invalidation, "invalidation");
            if (horizons.isEmpty()) {
                throw new IllegalArgumentException("outcomes.horizons must not be empty");
            }
            int previous = 0;
            for (final int horizon : horizons) {
                if (horizon <= previous) {
                    throw new IllegalArgumentException(
                            "outcomes.horizons must be positive and strictly increasing, was " + horizons);
                }
                previous = horizon;
            }
            if (!RuleAblation.frozenModeNames().contains(structuralMode)) {
                throw new IllegalArgumentException("outcomes.structuralMode '" + structuralMode
                        + "' must be one of " + RuleAblation.frozenModeNames());
            }
        }

        static Settings defaults() {
            return new Settings(DEFAULT_HORIZONS, DEFAULT_STRUCTURAL_MODE, Invalidation.ORIGIN_PIVOT);
        }

        /** Parses the optional recipe object; absent fields keep their defaults. */
        static Settings parse(final JsonObject json) {
            final Settings defaults = defaults();
            if (json == null) {
                return defaults;
            }
            for (final String key : json.keySet()) {
                if (!List.of("horizons", "structuralMode", "invalidation").contains(key)) {
                    throw new IllegalArgumentException("outcomes has unknown field '" + key + "'");
                }
            }
            List<Integer> horizons = defaults.horizons();
            if (json.has("horizons")) {
                if (!json.get("horizons").isJsonArray()) {
                    throw new IllegalArgumentException("outcomes.horizons must be an array of integers");
                }
                final List<Integer> parsed = new ArrayList<>();
                for (final JsonElement element : json.getAsJsonArray("horizons")) {
                    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()
                            || element.getAsDouble() != Math.rint(element.getAsDouble())
                            || Math.abs(element.getAsDouble()) > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException("outcomes.horizons must be an array of integers");
                    }
                    parsed.add(element.getAsInt());
                }
                horizons = parsed;
            }
            String mode = defaults.structuralMode();
            if (json.has("structuralMode")) {
                mode = text(json, "structuralMode");
            }
            Invalidation invalidation = defaults.invalidation();
            if (json.has("invalidation")) {
                invalidation = Invalidation.parse(text(json, "invalidation"));
            }
            return new Settings(horizons, mode, invalidation);
        }

        private static String text(final JsonObject json, final String field) {
            final JsonElement element = json.get(field);
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("outcomes." + field + " must be a string");
            }
            return element.getAsString();
        }

        int maxHorizon() {
            return horizons.get(horizons.size() - 1);
        }

        JsonObject toJson() {
            final JsonObject json = new JsonObject();
            final JsonArray array = new JsonArray();
            horizons.forEach(array::add);
            json.add("horizons", array);
            json.addProperty("structuralMode", structuralMode);
            json.addProperty("invalidation", invalidation.id());
            json.addProperty("momentumBars", MOMENTUM_BARS);
            return json;
        }
    }

    /**
     * Price-window facts of one event at one horizon.
     *
     * @param available     whether the whole future window was observed
     * @param reason        {@code null} when available, else
     *                      {@code HORIZON_BEYOND_PARTITION}, {@code INVALID_PRICE},
     *                      or {@code NO_TAPE}
     * @param raw           {@code C[d+h]/C[d]-1}
     * @param aligned       raw return signed by motive direction
     * @param favorable     maximum favorable excursion over {@code d+1..d+h}
     * @param adverse       maximum adverse excursion over {@code d+1..d+h}
     * @param researchTouch target/invalidation order, same bar unresolved
     * @param legacyTouch   same, with same-bar invalidation first
     */
    record Price(boolean available, String reason, double raw, double aligned, double favorable, double adverse,
            Touch researchTouch, Touch legacyTouch) {

        static Price unavailable(final String reason) {
            return new Price(false, reason, Double.NaN, Double.NaN, Double.NaN, Double.NaN, null, null);
        }
    }

    /**
     * One event's labels at one horizon.
     *
     * @param event           enrolled event
     * @param horizon         horizon in bars
     * @param structural      terminal structural state
     * @param cause           {@code completed}, {@code withdrawn},
     *                        {@code pivot-invalidation},
     *                        {@code origin-price-invalidation}, or {@code none}
     * @param resolutionIndex index of the absorbing result, or {@code -1}
     * @param price           price-window facts
     */
    record Label(Event event, int horizon, Structural structural, String cause, int resolutionIndex, Price price) {

        /**
         * @return first index at which the structural label is administratively known,
         *         or {@link Integer#MAX_VALUE} for a censored label that never
         *         resolves inside its stream
         */
        int structuralAvailableIndex() {
            return switch (structural) {
            case CORRECTION_COMPLETED, INVALIDATED_OR_WITHDRAWN -> resolutionIndex;
            case ALREADY_RESOLVED -> Math.max(resolutionIndex, event.enrollIndex);
            case HORIZON_EXPIRED -> event.enrollIndex + horizon;
            case CENSORED -> Integer.MAX_VALUE;
            };
        }

        /**
         * @return first index at which the price label is administratively known, or
         *         {@link Integer#MAX_VALUE} when it is unavailable
         */
        int priceAvailableIndex() {
            return price.available() ? event.enrollIndex + horizon : Integer.MAX_VALUE;
        }
    }

    /** An event with its labels per configured horizon. */
    record Evaluated(Event event, List<Label> labels) {
        Evaluated {
            labels = List.copyOf(labels);
        }
    }

    /**
     * Keeps only labels whose structural result is administratively available by a
     * future fit cutoff, so a fitted model never trains on labels that resolve
     * after the cutoff.
     *
     * @param labels      candidate training labels
     * @param cutoffIndex last source index a fit may have observed
     * @return labels resolved at or before the cutoff
     */
    static List<Label> purgeStructural(final List<Label> labels, final int cutoffIndex) {
        return labels.stream().filter(label -> label.structuralAvailableIndex() <= cutoffIndex).toList();
    }

    /**
     * Keeps only labels whose whole price window ended at or before the cutoff.
     *
     * @param labels      candidate training labels
     * @param cutoffIndex last source index a fit may have observed
     * @return labels with an available price window ending at or before the cutoff
     */
    static List<Label> purgePrice(final List<Label> labels, final int cutoffIndex) {
        return labels.stream().filter(label -> label.priceAvailableIndex() <= cutoffIndex).toList();
    }

    /** Additive counters of one stream, partition, and horizon. */
    static final class Tally {
        long enrolled;
        long alreadyResolved;
        long success;
        long invalidated;
        long expired;
        long censored;
        long priceAvailable;
        long beyondPartition;
        long invalidPrice;
        long noTape;
        double sumRaw;
        double sumAligned;
        double sumFavorable;
        double sumAdverse;
        long up;
        long researchTarget;
        long researchInvalidation;
        long researchUnresolved;
        long researchNeither;
        long legacyTarget;
        long legacyInvalidation;
        long legacyNeither;
        long unconditionalDates;
        double unconditionalSum;
        long unconditionalUp;
        long momentumAllDates;
        double momentumAllSum;
        long momentumEventDates;
        double momentumEventSum;
        long cohortKept;
        long cohortDiscarded;
        long cohortSuccess;
        long cohortInvalidated;
        long cohortExpired;
        long cohortCensored;

        long prospective() {
            return enrolled - alreadyResolved;
        }

        long resolved() {
            return success + invalidated + expired;
        }

        double resolvedRate() {
            return ratio(success, resolved());
        }

        double lowerBound() {
            return ratio(success, prospective());
        }

        double upperBound() {
            return ratio(success + censored, prospective());
        }

        double cohortResolvedRate() {
            return ratio(cohortSuccess, cohortSuccess + cohortInvalidated + cohortExpired);
        }

        void add(final Tally other) {
            enrolled += other.enrolled;
            alreadyResolved += other.alreadyResolved;
            success += other.success;
            invalidated += other.invalidated;
            expired += other.expired;
            censored += other.censored;
            priceAvailable += other.priceAvailable;
            beyondPartition += other.beyondPartition;
            invalidPrice += other.invalidPrice;
            noTape += other.noTape;
            sumRaw += other.sumRaw;
            sumAligned += other.sumAligned;
            sumFavorable += other.sumFavorable;
            sumAdverse += other.sumAdverse;
            up += other.up;
            researchTarget += other.researchTarget;
            researchInvalidation += other.researchInvalidation;
            researchUnresolved += other.researchUnresolved;
            researchNeither += other.researchNeither;
            legacyTarget += other.legacyTarget;
            legacyInvalidation += other.legacyInvalidation;
            legacyNeither += other.legacyNeither;
            unconditionalDates += other.unconditionalDates;
            unconditionalSum += other.unconditionalSum;
            unconditionalUp += other.unconditionalUp;
            momentumAllDates += other.momentumAllDates;
            momentumAllSum += other.momentumAllSum;
            momentumEventDates += other.momentumEventDates;
            momentumEventSum += other.momentumEventSum;
            cohortKept += other.cohortKept;
            cohortDiscarded += other.cohortDiscarded;
            cohortSuccess += other.cohortSuccess;
            cohortInvalidated += other.cohortInvalidated;
            cohortExpired += other.cohortExpired;
            cohortCensored += other.cohortCensored;
        }

        void structural(final Structural state, final boolean cohort) {
            if (cohort) {
                switch (state) {
                case CORRECTION_COMPLETED -> cohortSuccess++;
                case INVALIDATED_OR_WITHDRAWN -> cohortInvalidated++;
                case HORIZON_EXPIRED -> cohortExpired++;
                case CENSORED -> cohortCensored++;
                case ALREADY_RESOLVED -> {
                    // already-resolved events never enter the prospective cohort
                }
                }
                return;
            }
            enrolled++;
            switch (state) {
            case CORRECTION_COMPLETED -> success++;
            case INVALIDATED_OR_WITHDRAWN -> invalidated++;
            case HORIZON_EXPIRED -> expired++;
            case CENSORED -> censored++;
            case ALREADY_RESOLVED -> alreadyResolved++;
            }
        }

        void price(final Price price) {
            if (!price.available()) {
                switch (price.reason()) {
                case "HORIZON_BEYOND_PARTITION" -> beyondPartition++;
                case "INVALID_PRICE" -> invalidPrice++;
                default -> noTape++;
                }
                return;
            }
            priceAvailable++;
            sumRaw += price.raw();
            sumAligned += price.aligned();
            sumFavorable += price.favorable();
            sumAdverse += price.adverse();
            if (price.raw() > 0.0d) {
                up++;
            }
            switch (price.researchTouch()) {
            case TARGET_FIRST -> researchTarget++;
            case INVALIDATION_FIRST -> researchInvalidation++;
            case UNRESOLVED_INTRABAR_ORDER -> researchUnresolved++;
            case NEITHER -> researchNeither++;
            }
            switch (price.legacyTouch()) {
            case TARGET_FIRST -> legacyTarget++;
            case INVALIDATION_FIRST -> legacyInvalidation++;
            default -> legacyNeither++;
            }
        }

        List<String> cells() {
            final List<String> cells = new ArrayList<>();
            cells.add(Long.toString(enrolled));
            cells.add(Long.toString(alreadyResolved));
            cells.add(Long.toString(prospective()));
            cells.add(Long.toString(success));
            cells.add(Long.toString(invalidated));
            cells.add(Long.toString(expired));
            cells.add(Long.toString(censored));
            cells.add(Long.toString(resolved()));
            cells.add(plain(resolvedRate()));
            cells.add(plain(lowerBound()));
            cells.add(plain(upperBound()));
            cells.add(Long.toString(priceAvailable));
            cells.add(Long.toString(beyondPartition));
            cells.add(Long.toString(invalidPrice));
            cells.add(Long.toString(noTape));
            cells.add(plain(ratio(sumRaw, priceAvailable)));
            cells.add(plain(ratio(sumAligned, priceAvailable)));
            cells.add(plain(ratio(sumFavorable, priceAvailable)));
            cells.add(plain(ratio(sumAdverse, priceAvailable)));
            cells.add(plain(ratio(up, priceAvailable)));
            cells.add(Long.toString(researchTarget));
            cells.add(Long.toString(researchInvalidation));
            cells.add(Long.toString(researchUnresolved));
            cells.add(Long.toString(researchNeither));
            cells.add(Long.toString(legacyTarget));
            cells.add(Long.toString(legacyInvalidation));
            cells.add(Long.toString(legacyNeither));
            cells.add(Long.toString(unconditionalDates));
            cells.add(plain(ratio(unconditionalSum, unconditionalDates)));
            cells.add(plain(ratio(unconditionalUp, unconditionalDates)));
            cells.add(Long.toString(momentumAllDates));
            cells.add(plain(ratio(momentumAllSum, momentumAllDates)));
            cells.add(Long.toString(momentumEventDates));
            cells.add(plain(ratio(momentumEventSum, momentumEventDates)));
            cells.add(Long.toString(cohortKept));
            cells.add(Long.toString(cohortDiscarded));
            cells.add(Long.toString(cohortSuccess));
            cells.add(Long.toString(cohortInvalidated));
            cells.add(Long.toString(cohortExpired));
            cells.add(Long.toString(cohortCensored));
            cells.add(plain(cohortResolvedRate()));
            return cells;
        }
    }

    /**
     * One summary line.
     *
     * @param stream    {@code real} or {@code null-b<block>-m<member>}
     * @param partition partition name or {@link #ALL_PARTITIONS}
     * @param horizon   horizon in bars
     * @param tally     counters
     */
    record SummaryRow(String stream, String partition, int horizon, Tally tally) {
    }

    /**
     * All outcome output of one dataset.
     *
     * @param dataset  dataset id
     * @param settings recipe settings the labels were produced under
     * @param events   evaluated events in stream order
     * @param summary  per stream, partition, and horizon aggregates, partition rows
     *                 before the {@code ALL} row of each stream and horizon
     */
    record Result(String dataset, Settings settings, List<Evaluated> events, List<SummaryRow> summary) {
        Result {
            events = List.copyOf(events);
            summary = List.copyOf(summary);
        }

        /** @return the aggregate row of one stream and horizon, or {@code null} */
        SummaryRow all(final String stream, final int horizon) {
            return summary.stream()
                    .filter(row -> row.stream().equals(stream) && row.horizon() == horizon
                            && ALL_PARTITIONS.equals(row.partition()))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Labels every recorded event and aggregates the labels.
     *
     * @param dataset  dataset id
     * @param recorder recorder that observed the run
     * @param settings declared recipe settings; {@code maxHorizon} must not exceed
     *                 the recorder's tracked lifecycle
     * @return labelled events and summaries
     */
    static Result evaluate(final String dataset, final ElliottResearchEvents recorder, final Settings settings) {
        final List<Evaluated> evaluated = new ArrayList<>();
        final Map<String, Map<Integer, Tally>> totals = new LinkedHashMap<>();
        final List<SummaryRow> rows = new ArrayList<>();
        final Map<String, List<Stream>> byStream = new LinkedHashMap<>();
        for (final Stream stream : recorder.streams()) {
            byStream.computeIfAbsent(stream.key().label(), label -> new ArrayList<>()).add(stream);
        }
        for (final Map.Entry<String, List<Stream>> entry : byStream.entrySet()) {
            final Map<Integer, Tally> streamTotals = new LinkedHashMap<>();
            totals.put(entry.getKey(), streamTotals);
            for (final Stream stream : entry.getValue()) {
                final Tape tape = recorder.tapeOf(stream);
                final List<List<Label>> labels = new ArrayList<>();
                final List<Event> events = stream.events();
                final int[] originPrice = new int[events.size()];
                for (int at = 0; at < events.size(); at++) {
                    originPrice[at] = settings.invalidation() == Invalidation.ORIGIN_PRICE
                            ? originPriceIndex(events.get(at), tape, stream.lastObserved(), settings.maxHorizon())
                            : -1;
                    labels.add(new ArrayList<>());
                }
                for (final int horizon : settings.horizons()) {
                    final Tally tally = new Tally();
                    final List<Label> atHorizon = new ArrayList<>(events.size());
                    for (int at = 0; at < events.size(); at++) {
                        final Label label = label(events.get(at), horizon, stream.lastObserved(), tape, settings,
                                originPrice[at]);
                        labels.get(at).add(label);
                        atHorizon.add(label);
                        tally.structural(label.structural(), false);
                        tally.price(label.price());
                    }
                    cohort(atHorizon, horizon, tally);
                    comparators(stream, tape, atHorizon, horizon, tally);
                    rows.add(new SummaryRow(entry.getKey(), stream.key().partition(), horizon, tally));
                    streamTotals.computeIfAbsent(horizon, key -> new Tally()).add(tally);
                }
                for (int at = 0; at < events.size(); at++) {
                    evaluated.add(new Evaluated(events.get(at), labels.get(at)));
                }
            }
            for (final int horizon : settings.horizons()) {
                rows.add(new SummaryRow(entry.getKey(), ALL_PARTITIONS, horizon, streamTotals.get(horizon)));
            }
        }
        rows.sort(Comparator.comparing((SummaryRow row) -> streamRank(byStream, row.stream()))
                .thenComparingInt(row -> row.horizon())
                .thenComparing(row -> ALL_PARTITIONS.equals(row.partition()) ? 1 : 0));
        return new Result(dataset, settings, evaluated, rows);
    }

    private static int streamRank(final Map<String, List<Stream>> byStream, final String stream) {
        int rank = 0;
        for (final String label : byStream.keySet()) {
            if (label.equals(stream)) {
                return rank;
            }
            rank++;
        }
        return rank;
    }

    /**
     * Labels one event at one horizon.
     *
     * @param event               enrolled event
     * @param horizon             horizon in bars
     * @param lastObserved        last index the event's stream observed; no result
     *                            beyond it is known
     * @param tape                price tape, or {@code null}
     * @param settings            recipe settings
     * @param originPriceIndex    first origin-price breach index, or {@code -1};
     *                            only consulted for the origin-price policy
     * @return the label
     */
    static Label label(final Event event, final int horizon, final int lastObserved, final Tape tape,
            final Settings settings, final int originPriceIndex) {
        final int decision = event.enrollIndex;
        int resolution = Integer.MAX_VALUE;
        String cause = "none";
        if (event.withdrawnIndex >= 0) {
            resolution = event.withdrawnIndex;
            cause = "withdrawn";
        }
        final int invalidation = settings.invalidation() == Invalidation.ORIGIN_PIVOT ? event.pivotInvalidationIndex
                : originPriceIndex;
        if (invalidation >= 0 && invalidation < resolution) {
            resolution = invalidation;
            cause = settings.invalidation() == Invalidation.ORIGIN_PIVOT ? "pivot-invalidation"
                    : "origin-price-invalidation";
        }
        if (event.completionIndex >= 0 && event.completionIndex < resolution) {
            resolution = event.completionIndex;
            cause = "completed";
        }
        final Structural structural;
        final int resolutionIndex;
        if (resolution != Integer.MAX_VALUE && resolution <= decision) {
            structural = Structural.ALREADY_RESOLVED;
            resolutionIndex = resolution;
        } else if (resolution != Integer.MAX_VALUE && resolution - decision <= horizon) {
            structural = "completed".equals(cause) ? Structural.CORRECTION_COMPLETED
                    : Structural.INVALIDATED_OR_WITHDRAWN;
            resolutionIndex = resolution;
        } else {
            cause = "none";
            resolutionIndex = -1;
            structural = horizon <= lastObserved - decision ? Structural.HORIZON_EXPIRED : Structural.CENSORED;
        }
        return new Label(event, horizon, structural, cause, resolutionIndex, price(event, horizon, lastObserved, tape));
    }

    private static int originPriceIndex(final Event event, final Tape tape, final int lastObserved,
            final int maxHorizon) {
        if (tape == null) {
            return -1;
        }
        final boolean bullish = event.direction == WaveDirection.BULLISH;
        final Num origin = event.origin().price();
        final int limit = (int) Math.min((long) Math.min(lastObserved, tape.last()),
                (long) event.enrollIndex + maxHorizon);
        for (int index = Math.max(event.end().pivotIndex() + 1, tape.first()); index <= limit; index++) {
            final Num extreme = bullish ? low(tape, index) : high(tape, index);
            if (extreme != null && (bullish ? extreme.isLessThan(origin) : extreme.isGreaterThan(origin))) {
                return index;
            }
        }
        return -1;
    }

    private static Price price(final Event event, final int horizon, final int lastObserved, final Tape tape) {
        if (tape == null) {
            return Price.unavailable("NO_TAPE");
        }
        final int decision = event.enrollIndex;
        final int limit = Math.min(lastObserved, tape.last());
        if (decision < tape.first() || horizon > limit - decision) {
            return Price.unavailable("HORIZON_BEYOND_PARTITION");
        }
        if (!eligibleWindow(tape, decision, horizon)) {
            return Price.unavailable("INVALID_PRICE");
        }
        final Num start = close(tape, decision);
        final Num end = close(tape, decision + horizon);
        final boolean bullish = event.direction == WaveDirection.BULLISH;
        final Num target = event.wave4().price();
        final Num invalidation = event.end().price();
        Num maxHigh = null;
        Num minLow = null;
        Touch research = Touch.NEITHER;
        Touch legacy = Touch.NEITHER;
        for (int index = decision + 1; index <= decision + horizon; index++) {
            final Num high = high(tape, index);
            final Num low = low(tape, index);
            maxHigh = maxHigh == null || high.isGreaterThan(maxHigh) ? high : maxHigh;
            minLow = minLow == null || low.isLessThan(minLow) ? low : minLow;
            if (research == Touch.NEITHER) {
                final boolean targetTouched = bullish ? !low.isGreaterThan(target) : !high.isLessThan(target);
                final boolean invalidationTouched = bullish ? !high.isLessThan(invalidation)
                        : !low.isGreaterThan(invalidation);
                if (targetTouched && invalidationTouched) {
                    research = Touch.UNRESOLVED_INTRABAR_ORDER;
                    legacy = Touch.INVALIDATION_FIRST;
                } else if (targetTouched) {
                    research = Touch.TARGET_FIRST;
                    legacy = Touch.TARGET_FIRST;
                } else if (invalidationTouched) {
                    research = Touch.INVALIDATION_FIRST;
                    legacy = Touch.INVALIDATION_FIRST;
                }
            }
        }
        final Num one = start.getNumFactory().one();
        final Num raw = end.dividedBy(start).minus(one);
        final Num up = maxHigh.dividedBy(start).minus(one);
        final Num down = minLow.dividedBy(start).minus(one);
        final double rawValue = raw.doubleValue();
        final double favorable;
        final double adverse;
        final double aligned;
        if (bullish) {
            aligned = rawValue;
            favorable = up.doubleValue();
            adverse = down.doubleValue();
        } else {
            aligned = raw.negate().doubleValue();
            favorable = down.negate().doubleValue();
            adverse = up.negate().doubleValue();
        }
        return new Price(true, null, rawValue, aligned, favorable, adverse, research, legacy);
    }

    /**
     * Whether a decision date's whole forward window is priced: positive, non-NaN
     * closes at the decision and horizon bars and a positive, non-NaN high and low
     * on every bar in between. Event price labels and comparator dates share this
     * policy so base rates and event outcomes cover the same availability.
     */
    private static boolean eligibleWindow(final Tape tape, final int decision, final int horizon) {
        if (close(tape, decision) == null || close(tape, decision + horizon) == null) {
            return false;
        }
        for (int index = decision + 1; index <= decision + horizon; index++) {
            if (high(tape, index) == null || low(tape, index) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Deterministic non-overlapping sensitivity cohort: prospective events in
     * decision order with the candidate key breaking same-time ties; an event is
     * skipped while the previously kept event's horizon is still running.
     */
    static void cohort(final List<Label> labels, final int horizon, final Tally tally) {
        final List<Label> prospective = labels.stream()
                .filter(label -> label.structural() != Structural.ALREADY_RESOLVED)
                .sorted(Comparator.comparingInt((Label label) -> label.event().enrollIndex)
                        .thenComparing(label -> label.event().candidateKey))
                .toList();
        long nextFree = Long.MIN_VALUE;
        for (final Label label : prospective) {
            if (label.event().enrollIndex >= nextFree) {
                tally.cohortKept++;
                tally.structural(label.structural(), true);
                nextFree = (long) label.event().enrollIndex + horizon;
            } else {
                tally.cohortDiscarded++;
            }
        }
    }

    /**
     * Unconditional and momentum-direction price comparators over the stream's
     * eligible decision dates: every observed date whose full {@code h} window lies
     * inside the partition.
     */
    private static void comparators(final Stream stream, final Tape tape, final List<Label> labels, final int horizon,
            final Tally tally) {
        if (tape == null || stream.firstObserved() < 0) {
            return;
        }
        final int limit = Math.min(stream.lastObserved(), tape.last());
        final int first = Math.max(stream.firstObserved(), tape.first());
        for (int date = first; (long) date + horizon <= limit; date++) {
            if (!eligibleWindow(tape, date, horizon)) {
                continue;
            }
            final Num start = close(tape, date);
            final Num end = close(tape, date + horizon);
            final double raw = end.dividedBy(start).minus(start.getNumFactory().one()).doubleValue();
            tally.unconditionalDates++;
            tally.unconditionalSum += raw;
            if (raw > 0.0d) {
                tally.unconditionalUp++;
            }
            final int sign = momentumSign(tape, date);
            if (sign != 0) {
                tally.momentumAllDates++;
                tally.momentumAllSum += sign * raw;
            }
        }
        for (final Label label : labels) {
            if (!label.price().available()) {
                continue;
            }
            final int sign = momentumSign(tape, label.event().enrollIndex);
            if (sign != 0) {
                tally.momentumEventDates++;
                tally.momentumEventSum += sign * label.price().raw();
            }
        }
    }

    /** Sign of the trailing close change over {@link #MOMENTUM_BARS}; 0 if unknown or flat. */
    private static int momentumSign(final Tape tape, final int date) {
        if ((long) date - MOMENTUM_BARS < tape.first()) {
            return 0;
        }
        final Num now = close(tape, date);
        final Num before = close(tape, date - MOMENTUM_BARS);
        if (now == null || before == null) {
            return 0;
        }
        return Integer.signum(now.compareTo(before));
    }

    private static Num close(final Tape tape, final int index) {
        return valid(tape, index, 0);
    }

    private static Num high(final Tape tape, final int index) {
        return valid(tape, index, 1);
    }

    private static Num low(final Tape tape, final int index) {
        return valid(tape, index, 2);
    }

    private static Num valid(final Tape tape, final int index, final int field) {
        final Bar bar = tape.bar(index);
        final Num value = bar == null ? null : field == 0 ? bar.getClosePrice() : field == 1 ? bar.getHighPrice()
                : bar.getLowPrice();
        return value == null || value.isNaN() || !value.isPositive() ? null : value;
    }

    private static double ratio(final double numerator, final double denominator) {
        return denominator == 0.0d ? Double.NaN : numerator / denominator;
    }

    private static String plain(final double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        return value == 0.0d ? "0" : BigDecimal.valueOf(value).toPlainString();
    }

    private static String pivotType(final SwingPivotType type) {
        return type.name();
    }

    // ---------------------------------------------------------------- artifacts

    /**
     * Writes {@code events.jsonl}: a header, one record per event, then the footer
     * {@code {"complete":true,"records":N}}.
     *
     * @param path        target file
     * @param fingerprint recipe fingerprint of the run
     * @param settings    recipe settings
     * @param results     per-dataset results in dataset order
     * @throws IOException on write failure
     */
    static void writeEvents(final Path path, final String fingerprint, final Settings settings,
            final List<Result> results) throws IOException {
        final Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            final JsonObject header = new JsonObject();
            header.addProperty("schema", SCHEMA);
            header.addProperty("fingerprint", fingerprint);
            header.add("outcomes", settings.toJson());
            writer.write(JSON.toJson(header));
            writer.write('\n');
            long records = 0;
            for (final Result result : results) {
                for (final Evaluated evaluated : result.events()) {
                    writer.write(JSON.toJson(eventJson(result.dataset(), evaluated)));
                    writer.write('\n');
                    records++;
                }
            }
            final JsonObject footer = new JsonObject();
            footer.addProperty("complete", true);
            footer.addProperty("records", records);
            writer.write(JSON.toJson(footer));
            writer.write('\n');
        }
    }

    static JsonObject eventJson(final String dataset, final Evaluated evaluated) {
        final Event event = evaluated.event();
        final JsonObject json = new JsonObject();
        json.addProperty("kind", "event");
        json.addProperty("dataset", dataset);
        json.addProperty("stream", event.stream.label());
        json.addProperty("nullBlockLength", event.stream.nullBlockLength());
        json.addProperty("nullMemberIndex", event.stream.nullMemberIndex());
        json.addProperty("partition", event.stream.partition());
        json.addProperty("candidateKey", event.candidateKey);
        json.addProperty("version", event.version);
        json.addProperty("direction", event.direction.name());
        json.addProperty("decisionIndex", event.enrollIndex);
        json.addProperty("decisionTime", event.enrollTime.toString());
        json.addProperty("availableIndex", event.availableIndex());
        json.addProperty("carriedIn", event.carriedIn());
        json.addProperty("status", event.status.name());
        json.addProperty("ambiguous", event.ambiguous());
        final JsonArray tied = new JsonArray();
        event.tiedCandidateKeys.forEach(tied::add);
        json.add("tiedCandidateKeys", tied);
        final JsonArray pivots = new JsonArray();
        for (final ConfirmedPivot pivot : event.pivots) {
            final JsonObject item = new JsonObject();
            item.addProperty("pivotIndex", pivot.pivotIndex());
            item.addProperty("confirmationIndex", pivot.confirmationIndex());
            item.addProperty("type", pivotType(pivot.type()));
            item.addProperty("price", pivot.price().toString());
            pivots.add(item);
        }
        json.add("pivots", pivots);
        json.addProperty("originPrice", event.origin().price().toString());
        json.addProperty("targetPrice", event.wave4().price().toString());
        json.addProperty("invalidationPrice", event.end().price().toString());
        final JsonObject lifecycle = new JsonObject();
        lifecycle.add("withdrawnIndex", index(event.withdrawnIndex));
        lifecycle.add("displayRetiredIndex", index(event.retiredIndex));
        lifecycle.add("pivotInvalidationIndex", index(event.pivotInvalidationIndex));
        lifecycle.add("completionIndex", index(event.completionIndex));
        lifecycle.add("completionVersion",
                event.completionVersion == null ? JsonNull.INSTANCE : new JsonPrimitive(event.completionVersion));
        json.add("lifecycle", lifecycle);
        final JsonArray outcomes = new JsonArray();
        for (final Label label : evaluated.labels()) {
            final JsonObject item = new JsonObject();
            item.addProperty("horizon", label.horizon());
            item.addProperty("structural", label.structural().id());
            item.addProperty("cause", label.cause());
            item.add("resolutionIndex", index(label.resolutionIndex()));
            final Price price = label.price();
            final JsonObject priceJson = new JsonObject();
            priceJson.addProperty("status", price.available() ? "available" : "unavailable");
            priceJson.add("reason", price.reason() == null ? JsonNull.INSTANCE : new JsonPrimitive(price.reason()));
            priceJson.add("rawReturn", number(price.raw()));
            priceJson.add("alignedReturn", number(price.aligned()));
            priceJson.add("favorableExcursion", number(price.favorable()));
            priceJson.add("adverseExcursion", number(price.adverse()));
            priceJson.add("researchTouch",
                    price.researchTouch() == null ? JsonNull.INSTANCE : new JsonPrimitive(price.researchTouch().id()));
            priceJson.add("legacyTouch",
                    price.legacyTouch() == null ? JsonNull.INSTANCE : new JsonPrimitive(price.legacyTouch().id()));
            item.add("price", priceJson);
            outcomes.add(item);
        }
        json.add("outcomes", outcomes);
        return json;
    }

    private static JsonElement index(final int value) {
        return value < 0 ? JsonNull.INSTANCE : new JsonPrimitive(value);
    }

    private static JsonElement number(final double value) {
        return Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    }

    /** Writes {@code outcomes.csv}: one row per event and horizon. */
    static void writeOutcomes(final Path path, final List<Result> results) {
        final List<List<String>> table = new ArrayList<>();
        for (final Result result : results) {
            for (final Evaluated evaluated : result.events()) {
                final Event event = evaluated.event();
                for (final Label label : evaluated.labels()) {
                    final Price price = label.price();
                    table.add(List.of(result.dataset(), event.stream.label(), event.stream.partition(),
                            event.candidateKey, event.version, event.direction.name(),
                            Integer.toString(event.enrollIndex), event.enrollTime.toString(),
                            Boolean.toString(event.ambiguous()), Boolean.toString(event.carriedIn()),
                            Integer.toString(label.horizon()), label.structural().id(), label.cause(),
                            label.resolutionIndex() < 0 ? "" : Integer.toString(label.resolutionIndex()),
                            price.available() ? "available" : "unavailable", price.reason() == null ? "" : price.reason(),
                            plain(price.raw()), plain(price.aligned()), plain(price.favorable()),
                            plain(price.adverse()), price.researchTouch() == null ? "" : price.researchTouch().id(),
                            price.legacyTouch() == null ? "" : price.legacyTouch().id()));
                }
            }
        }
        ElliottResearchReport.writeTable(path, OUTCOMES_HEADER, table);
    }

    /** Writes {@code outcomes-summary.csv}: one row per stream, partition, and horizon. */
    static void writeSummary(final Path path, final List<Result> results) {
        final List<List<String>> table = new ArrayList<>();
        for (final Result result : results) {
            for (final SummaryRow row : result.summary()) {
                final List<String> cells = new ArrayList<>(List.of(result.dataset(), row.stream(), row.partition(),
                        Integer.toString(row.horizon()), result.settings().structuralMode(),
                        result.settings().invalidation().id()));
                cells.addAll(row.tally().cells());
                table.add(cells);
            }
        }
        ElliottResearchReport.writeTable(path, SUMMARY_HEADER, table);
    }

    /**
     * Compact Markdown section over the {@code ALL} rows of a written
     * {@code outcomes-summary.csv}, so {@code summarize} regenerates it from the
     * recorded artifact.
     *
     * @param summaryCsv {@code outcomes-summary.csv}
     * @return the Markdown section
     */
    static String summaryMarkdown(final Path summaryCsv) {
        final List<List<String>> table = ElliottResearchReport.parseCsv(summaryCsv);
        if (table.isEmpty() || !table.get(0).equals(SUMMARY_HEADER)) {
            throw new IllegalArgumentException(summaryCsv + ":1: unexpected header, expected " + SUMMARY_HEADER);
        }
        final StringBuilder text = new StringBuilder("## Forward outcomes\n\n");
        final List<List<String>> all = table.stream()
                .skip(1)
                .filter(cells -> ALL_PARTITIONS.equals(cells.get(SUMMARY_HEADER.indexOf("partition"))))
                .toList();
        if (all.isEmpty()) {
            return text.append("No dataset produced outcome evidence.\n").toString();
        }
        final List<String> first = all.get(0);
        text.append("Structural mode `")
                .append(first.get(SUMMARY_HEADER.indexOf("structuralMode")))
                .append("`; invalidation `")
                .append(first.get(SUMMARY_HEADER.indexOf("invalidation")))
                .append("`; horizons are recipe settings, not tuned values. Resolved rate is success / resolved;"
                        + " bounds are success / prospective to (success + censored) / prospective. Events overlap,"
                        + " so no significance is computed; cohort columns are a non-overlapping sensitivity view."
                        + " Per-partition rows and comparators are in `" + SUMMARY_FILE + "`.\n\n");
        final List<String> columns = List.of("enrolled", "alreadyResolved", "success", "invalidated", "expired",
                "censored", "resolvedRate", "lowerBound", "upperBound", "priceAvailable", "meanRaw", "meanAligned",
                "unconditionalMeanRaw", "cohortKept", "cohortDiscarded");
        text.append("| dataset | stream | h | enrolled | already resolved | success | invalidated/withdrawn | expired"
                + " | censored | resolved rate | lower | upper | price events | mean raw | mean aligned"
                + " | uncond. mean raw | cohort kept | cohort discarded |\n");
        text.append("|---|---|---:|").append("---:|".repeat(columns.size())).append('\n');
        for (final List<String> cells : all) {
            text.append("| ")
                    .append(cells.get(SUMMARY_HEADER.indexOf("dataset")))
                    .append(" | ")
                    .append(cells.get(SUMMARY_HEADER.indexOf("stream")))
                    .append(" | ")
                    .append(cells.get(SUMMARY_HEADER.indexOf("horizon")));
            for (final String column : columns) {
                final String cell = cells.get(SUMMARY_HEADER.indexOf(column));
                text.append(" | ").append(cell.isEmpty() ? "n/a" : rounded(cell));
            }
            text.append(" |\n");
        }
        return text.toString();
    }

    private static String rounded(final String cell) {
        if (!cell.contains(".")) {
            return cell;
        }
        try {
            return display(Double.parseDouble(cell));
        } catch (final NumberFormatException exception) {
            return cell;
        }
    }

    private static String display(final double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.4g", value) : "n/a";
    }
}
