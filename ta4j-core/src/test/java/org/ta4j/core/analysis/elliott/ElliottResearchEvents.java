/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;

/**
 * Causal once-per-candidate event enrollment over the study observation stream.
 *
 * <p>
 * The recorder is a {@link StudyObserver}: it only witnesses the observations
 * the runner already produced and never feeds anything back into recognition.
 * A complete or ambiguous {@code MOTIVE_5} candidate of the {@code h1}
 * hypothesis stream (or of the selected null member's {@code MOTIVE_5} stream)
 * becomes one {@link Event} at the first observation that shows its full pivot
 * placement; later observations of the same placement never enroll again, and a
 * changed placement is a new event. Streams are keyed by
 * {@code (null block, null member, partition)}, so labels derived from an event
 * can never read beyond its own partition tape and null members keep their
 * identity.
 * </p>
 *
 * <p>
 * For every enrolled event the recorder tracks, through the longest configured
 * horizon, the facts that follow causally from later observations of the same
 * stream: <em>withdrawal</em> (a pivot of the placement is no longer visible),
 * <em>display retirement</em> (the placement is no longer reported although
 * every pivot is still visible; informational only), <em>pivot invalidation</em>
 * (a later confirmed counter-pivot beyond the motive origin price), and
 * <em>correction completion</em> (the first observation of the selected
 * structural mode whose {@code CYCLE_5_3} candidate starts with exactly the
 * enrolled six pivots and has no failing, pending, or unavailable active rule).
 * Horizon classification itself lives in {@link ElliottResearchOutcomes}.
 * Instances are not thread-safe.
 * </p>
 *
 * @since 0.26.1
 */
final class ElliottResearchEvents implements StudyObserver {

    /** Pivot count of an enrolled motive placement. */
    static final int MOTIVE_PIVOTS = 6;

    private final String structuralMode;
    private final int maxHorizon;
    private final Map<StreamKey, Stream> streams = new LinkedHashMap<>();
    private final Map<StreamKey, Tape> nullTapes = new HashMap<>();
    private Tape realTape;

    /**
     * @param structuralMode study mode whose {@code CYCLE_5_3} observations define
     *                       correction completion
     * @param maxHorizon     longest horizon in bars whose lifecycle is tracked
     */
    ElliottResearchEvents(final String structuralMode, final int maxHorizon) {
        this.structuralMode = Objects.requireNonNull(structuralMode, "structuralMode");
        if (maxHorizon < 1) {
            throw new IllegalArgumentException("maxHorizon must be positive, was " + maxHorizon);
        }
        this.maxHorizon = maxHorizon;
    }

    /**
     * Tees one observation stream into several observers; {@code null} members are
     * skipped.
     *
     * @param observers receiving observers in call order
     * @return the single observer, a tee, or {@code null} when none is present
     */
    static StudyObserver tee(final StudyObserver... observers) {
        final List<StudyObserver> present = new ArrayList<>(observers.length);
        for (final StudyObserver observer : observers) {
            if (observer != null) {
                present.add(observer);
            }
        }
        if (present.isEmpty()) {
            return null;
        }
        if (present.size() == 1) {
            return present.get(0);
        }
        return new StudyObserver() {
            @Override
            public void topology(final Scope scope, final String partition, final int recordedIndex,
                    final Instant asOfEnd, final List<ConfirmedPivot> visiblePivots, final TopologyAnalysis analysis,
                    final List<List<RuleEvidence>> candidateEvidence) {
                present.forEach(observer -> observer.topology(scope, partition, recordedIndex, asOfEnd, visiblePivots,
                        analysis, candidateEvidence));
            }

            @Override
            public void alternative(final Scope scope, final String partition, final int recordedIndex,
                    final Instant asOfEnd, final List<ConfirmedPivot> visiblePivots, final String outcome,
                    final Set<String> labels) {
                present.forEach(observer -> observer.alternative(scope, partition, recordedIndex, asOfEnd,
                        visiblePivots, outcome, labels));
            }

            @Override
            public void nullTape(final int nullBlockLength, final int nullMemberIndex, final String partition,
                    final int sourceOffset, final BarSeries member) {
                present.forEach(observer -> observer.nullTape(nullBlockLength, nullMemberIndex, partition,
                        sourceOffset, member));
            }
        };
    }

    /**
     * Binds the real tape that real-stream labels read. The series is read only for
     * labelling after the run; recognition never sees it through this recorder.
     *
     * @param series real source series
     */
    void bindRealTape(final BarSeries series) {
        this.realTape = new Tape(series, 0);
    }

    /** @return the streams in first-observation order */
    List<Stream> streams() {
        return List.copyOf(streams.values());
    }

    /**
     * @param stream observed stream
     * @return the tape that stream's labels read, or {@code null} when none was
     *         announced
     */
    Tape tapeOf(final Stream stream) {
        return stream.key().real() ? realTape : nullTapes.get(stream.key());
    }

    @Override
    public void nullTape(final int nullBlockLength, final int nullMemberIndex, final String partition,
            final int sourceOffset, final BarSeries member) {
        nullTapes.put(new StreamKey(nullBlockLength, nullMemberIndex, partition), new Tape(member, sourceOffset));
    }

    @Override
    public void topology(final Scope scope, final String partition, final int recordedIndex, final Instant asOfEnd,
            final List<ConfirmedPivot> visiblePivots, final TopologyAnalysis analysis,
            final List<List<RuleEvidence>> candidateEvidence) {
        final boolean real = scope.nullBlockLength() < 0;
        final String motive = TopologyGrammar.MOTIVE_5.name();
        final String cycle = TopologyGrammar.CYCLE_5_3.name();
        if (motive.equals(scope.grammar()) && (real ? "h1".equals(scope.section())
                : "null".equals(scope.section()) && motive.equals(scope.mode()))) {
            enroll(scope, partition, recordedIndex, asOfEnd, visiblePivots, analysis, candidateEvidence);
        } else if (cycle.equals(scope.grammar()) && structuralMode.equals(scope.mode())
                && (real ? "h2".equals(scope.section()) : "null".equals(scope.section()))) {
            complete(scope, partition, recordedIndex, analysis, candidateEvidence);
        }
    }

    @Override
    public void alternative(final Scope scope, final String partition, final int recordedIndex, final Instant asOfEnd,
            final List<ConfirmedPivot> visiblePivots, final String outcome, final Set<String> labels) {
        // alternative grammars never define events
    }

    private void enroll(final Scope scope, final String partition, final int index, final Instant asOfEnd,
            final List<ConfirmedPivot> visible, final TopologyAnalysis analysis,
            final List<List<RuleEvidence>> evidence) {
        final Stream stream = streams.computeIfAbsent(
                new StreamKey(scope.nullBlockLength(), scope.nullMemberIndex(), partition), Stream::new);
        stream.observe(index);
        final List<TopologyCandidate> candidates = analysis.candidates();
        final List<String> placements = new ArrayList<>(candidates.size());
        for (final TopologyCandidate candidate : candidates) {
            placements.add(placement(candidate.direction(), candidate.pivots()));
        }
        final Iterator<Event> tracked = stream.tracked.iterator();
        while (tracked.hasNext()) {
            final Event event = tracked.next();
            if (index > event.enrollIndex + maxHorizon) {
                tracked.remove();
                continue;
            }
            if (!visibleAll(event.pivots, visible)) {
                event.withdrawnIndex = index;
                tracked.remove();
                continue;
            }
            if (event.retiredIndex < 0 && !placements.contains(event.placement)) {
                event.retiredIndex = index;
            }
            if (event.pivotInvalidationIndex < 0 && invalidatedBy(event, visible)) {
                event.pivotInvalidationIndex = index;
            }
        }
        if (analysis.status() != TopologyStatus.COMPLETE && analysis.status() != TopologyStatus.AMBIGUOUS) {
            return;
        }
        final List<String> motiveKeys = new ArrayList<>(candidates.size());
        final List<Integer> motiveSlots = new ArrayList<>(candidates.size());
        for (int slot = 0; slot < candidates.size(); slot++) {
            final TopologyCandidate candidate = candidates.get(slot);
            if (candidate.grammar() == TopologyGrammar.MOTIVE_5 && candidate.pivots().size() == MOTIVE_PIVOTS
                    && !stream.byPlacement.containsKey(placements.get(slot))) {
                motiveSlots.add(slot);
            }
        }
        if (motiveSlots.isEmpty()) {
            return;
        }
        for (final TopologyCandidate candidate : candidates) {
            if (candidate.grammar() == TopologyGrammar.MOTIVE_5) {
                motiveKeys.add(ElliottResearchTrace.candidateKey(candidate));
            }
        }
        for (final int slot : motiveSlots) {
            final TopologyCandidate candidate = candidates.get(slot);
            final String key = ElliottResearchTrace.candidateKey(candidate);
            final List<RuleEvidence> rules = slot < evidence.size() ? evidence.get(slot) : List.of();
            final String version = ElliottResearchTrace.version(key, ElliottResearchTrace.rulesJson(rules));
            final TreeSet<String> tied = new TreeSet<>(motiveKeys);
            tied.remove(key);
            final Event event = new Event(stream.key, key, version, candidate.direction(), candidate.pivots(),
                    placements.get(slot), index, asOfEnd, analysis.status(), List.copyOf(tied));
            if (invalidatedBy(event, visible)) {
                event.pivotInvalidationIndex = index;
            }
            stream.events.add(event);
            stream.tracked.add(event);
            stream.byPlacement.put(event.placement, event);
        }
    }

    private void complete(final Scope scope, final String partition, final int index, final TopologyAnalysis analysis,
            final List<List<RuleEvidence>> evidence) {
        final Stream stream = streams.get(new StreamKey(scope.nullBlockLength(), scope.nullMemberIndex(), partition));
        if (stream == null || stream.events.isEmpty() || analysis.candidates().isEmpty()) {
            return;
        }
        while (stream.cursor < stream.events.size()
                && index > stream.events.get(stream.cursor).enrollIndex + maxHorizon) {
            stream.cursor++;
        }
        final List<TopologyCandidate> candidates = analysis.candidates();
        for (int at = stream.cursor; at < stream.events.size(); at++) {
            final Event event = stream.events.get(at);
            if (event.enrollIndex > index) {
                break;
            }
            if (event.completionIndex >= 0) {
                continue;
            }
            for (int slot = 0; slot < candidates.size(); slot++) {
                final TopologyCandidate candidate = candidates.get(slot);
                final List<RuleEvidence> rules = slot < evidence.size() ? evidence.get(slot) : List.of();
                if (candidate.grammar() == TopologyGrammar.CYCLE_5_3 && candidate.direction() == event.direction
                        && startsWith(candidate.pivots(), event.pivots) && rulesSatisfied(rules)) {
                    event.completionIndex = index;
                    event.completionVersion = ElliottResearchTrace.version(
                            ElliottResearchTrace.candidateKey(candidate), ElliottResearchTrace.rulesJson(rules));
                    break;
                }
            }
        }
    }

    private static boolean rulesSatisfied(final List<RuleEvidence> rules) {
        for (final RuleEvidence rule : rules) {
            if (rule.state() != EvidenceState.PASS && rule.state() != EvidenceState.NOT_APPLICABLE) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(final List<ConfirmedPivot> cycle, final List<ConfirmedPivot> motive) {
        if (cycle.size() < motive.size()) {
            return false;
        }
        for (int at = 0; at < motive.size(); at++) {
            if (!samePivot(cycle.get(at), motive.get(at))) {
                return false;
            }
        }
        return true;
    }

    private static boolean samePivot(final ConfirmedPivot left, final ConfirmedPivot right) {
        return left.pivotIndex() == right.pivotIndex() && left.type() == right.type()
                && left.price().compareTo(right.price()) == 0;
    }

    /** Whether every placement pivot is still among the visible confirmed pivots. */
    private static boolean visibleAll(final List<ConfirmedPivot> placement, final List<ConfirmedPivot> visible) {
        for (int at = placement.size() - 1; at >= 0; at--) {
            final ConfirmedPivot wanted = placement.get(at);
            boolean found = false;
            for (int scan = visible.size() - 1; scan >= 0; scan--) {
                final ConfirmedPivot candidate = visible.get(scan);
                if (candidate.pivotIndex() < wanted.pivotIndex()) {
                    break;
                }
                if (samePivot(candidate, wanted)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a confirmed counter-pivot after the motive end sits beyond the motive
     * origin price.
     */
    private static boolean invalidatedBy(final Event event, final List<ConfirmedPivot> visible) {
        final ConfirmedPivot origin = event.origin();
        final ConfirmedPivot end = event.end();
        for (int scan = visible.size() - 1; scan >= 0; scan--) {
            final ConfirmedPivot pivot = visible.get(scan);
            if (pivot.pivotIndex() <= end.pivotIndex()) {
                break;
            }
            if (event.direction == WaveDirection.BULLISH
                    ? pivot.type() == SwingPivotType.LOW && pivot.price().isLessThan(origin.price())
                    : pivot.type() == SwingPivotType.HIGH && pivot.price().isGreaterThan(origin.price())) {
                return true;
            }
        }
        return false;
    }

    private static String placement(final WaveDirection direction, final List<ConfirmedPivot> pivots) {
        final StringBuilder text = new StringBuilder(direction.name());
        for (final ConfirmedPivot pivot : pivots) {
            text.append('|').append(pivot.pivotIndex()).append(':').append(pivot.type().name()).append(':').append(
                    pivot.price());
        }
        return text.toString();
    }

    /**
     * Price tape one stream's labels read, addressed in source coordinates.
     *
     * @param series       bars
     * @param sourceOffset source index of the series' index zero
     */
    record Tape(BarSeries series, int sourceOffset) {

        Tape {
            Objects.requireNonNull(series, "series");
        }

        int first() {
            return series.getBeginIndex() + sourceOffset;
        }

        int last() {
            return series.getEndIndex() + sourceOffset;
        }

        Bar bar(final int sourceIndex) {
            return series.getBar(sourceIndex - sourceOffset);
        }
    }

    /**
     * Identity of one observation stream: a real partition or one null member's
     * partition.
     *
     * @param nullBlockLength null block length, or {@code -1} for real data
     * @param nullMemberIndex null member index, or {@code -1} for real data
     * @param partition       locked partition name
     */
    record StreamKey(int nullBlockLength, int nullMemberIndex, String partition) {

        boolean real() {
            return nullBlockLength < 0;
        }

        /** @return {@code real} or {@code null-b<block>-m<member>} */
        String label() {
            return real() ? "real" : "null-b" + nullBlockLength + "-m" + nullMemberIndex;
        }
    }

    /** Events and observed extent of one stream. */
    static final class Stream {
        private final StreamKey key;
        private final List<Event> events = new ArrayList<>();
        private final List<Event> tracked = new ArrayList<>();
        private final Map<String, Event> byPlacement = new HashMap<>();
        private int first = -1;
        private int last = -1;
        private int cursor;

        Stream(final StreamKey key) {
            this.key = key;
        }

        private void observe(final int index) {
            if (first < 0) {
                first = index;
            }
            last = Math.max(last, index);
        }

        StreamKey key() {
            return key;
        }

        /** @return enrolled events in enrollment order */
        List<Event> events() {
            return List.copyOf(events);
        }

        /** @return first observed index in source coordinates */
        int firstObserved() {
            return first;
        }

        /**
         * @return last observed index in source coordinates: the partition's label
         *         horizon, beyond which no future bar may be read
         */
        int lastObserved() {
            return last;
        }
    }

    /**
     * One enrolled motive candidate with its frozen decision-time identity and the
     * lifecycle facts observed afterwards.
     */
    static final class Event {
        final StreamKey stream;
        final String candidateKey;
        final String version;
        final WaveDirection direction;
        final List<ConfirmedPivot> pivots;
        final int enrollIndex;
        final Instant enrollTime;
        final TopologyStatus status;
        final List<String> tiedCandidateKeys;
        private final String placement;
        int withdrawnIndex = -1;
        int retiredIndex = -1;
        int pivotInvalidationIndex = -1;
        int completionIndex = -1;
        String completionVersion;

        Event(final StreamKey stream, final String candidateKey, final String version, final WaveDirection direction,
                final List<ConfirmedPivot> pivots, final String placement, final int enrollIndex,
                final Instant enrollTime, final TopologyStatus status, final List<String> tiedCandidateKeys) {
            this.stream = stream;
            this.candidateKey = candidateKey;
            this.version = version;
            this.direction = direction;
            this.pivots = List.copyOf(pivots);
            this.placement = placement;
            this.enrollIndex = enrollIndex;
            this.enrollTime = enrollTime;
            this.status = status;
            this.tiedCandidateKeys = tiedCandidateKeys;
        }

        /** @return the motive's first pivot, whose price is the origin */
        ConfirmedPivot origin() {
            return pivots.get(0);
        }

        /** @return the wave-4 pivot, the correction target level */
        ConfirmedPivot wave4() {
            return pivots.get(MOTIVE_PIVOTS - 2);
        }

        /** @return the wave-5 pivot that ends the motive */
        ConfirmedPivot end() {
            return pivots.get(MOTIVE_PIVOTS - 1);
        }

        /**
         * @return first index at which the whole placement was confirmed; earlier than
         *         {@link #enrollIndex} when the placement was already confirmed before
         *         the partition's first observation
         */
        int availableIndex() {
            return end().confirmationIndex();
        }

        boolean carriedIn() {
            return availableIndex() < enrollIndex;
        }

        /**
         * @return true when the placement is ambiguity-tied at enrollment
         */
        boolean ambiguous() {
            return status == TopologyStatus.AMBIGUOUS;
        }
    }
}
