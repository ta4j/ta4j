/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import java.time.Instant;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ta4jexamples.charting.replay.ReplayArtifact.ComparisonRow;
import ta4jexamples.charting.replay.ReplayArtifact.Dataset;
import ta4jexamples.charting.replay.ReplayArtifact.PriceBar;
import ta4jexamples.charting.replay.ReplayTraceIndex.Entry;
import ta4jexamples.charting.replay.ReplayTraceIndex.Family;

/**
 * Cursor over the recorded as-of stream behind one comparison row.
 *
 * <p>
 * A session never replays detection: it only moves between records that the
 * research recorder already wrote, so every frame shows what was knowable at
 * that bar and nothing later. Every navigation method returns the new
 * {@link ReplayFrame}; the selected candidate key survives navigation, even
 * when the candidate is absent at the new cursor.
 */
final class ReplaySession {

    /** Default visible bar count. */
    static final int DEFAULT_WINDOW = 120;
    /** Default maximum simultaneous candidate overlays. */
    static final int DEFAULT_OVERLAY_CAP = 8;
    /** Most recent records inspected by {@link #candidateTimeline(String)}. */
    static final int TIMELINE_LOOKBACK = 5_000;

    private static final int FRAME_CACHE_SIZE = 32;

    /** One distinct recorded version of a candidate. */
    record TimelineEntry(int asOfIndex, String version, boolean present) {
    }

    private final ReplayArtifact artifact;
    private final ComparisonRow row;
    private final Dataset dataset;
    private final List<PriceBar> bars;
    private final int firstBar;
    private final ReplayTraceIndex trace;
    private final String traceFile;
    private final Family family;
    private final List<Entry> entries;
    private final int window;
    private final int overlayCap;
    private final String ruleEvidenceHint;
    private final Map<String, ReplayFrame> cache = new LinkedHashMap<>(16, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, ReplayFrame> eldest) {
            return size() > FRAME_CACHE_SIZE;
        }
    };

    private int position;
    private String selected = "";

    private ReplaySession(final ReplayArtifact artifact, final ComparisonRow row, final Dataset dataset,
            final List<PriceBar> bars, final int firstBar, final ReplayTraceIndex trace, final String traceFile,
            final Family family, final List<Entry> entries, final int window, final int overlayCap,
            final String ruleEvidenceHint) {
        this.artifact = artifact;
        this.row = row;
        this.dataset = dataset;
        this.bars = bars;
        this.firstBar = firstBar;
        this.trace = trace;
        this.traceFile = traceFile;
        this.family = family;
        this.entries = entries;
        this.window = window;
        this.overlayCap = overlayCap;
        this.ruleEvidenceHint = ruleEvidenceHint;
    }

    /**
     * Opens a session on the comparison row {@code key}.
     *
     * @param artifact   the run bundle
     * @param key        comparison key from {@code comparisons.csv}
     * @param traceMode  {@link ReplayArtifact#TRACE_MODE_REAL} or
     *                   {@link ReplayArtifact#TRACE_MODE_NULL_MEMBER}
     * @param window     visible bar count
     * @param overlayCap maximum simultaneous candidate overlays
     * @return a session positioned on the default cursor (first ambiguous record,
     *         else first record)
     */
    static ReplaySession open(final ReplayArtifact artifact, final String key, final String traceMode, final int window,
            final int overlayCap) {
        Objects.requireNonNull(artifact, "artifact");
        if (window < 1) {
            throw new ReplayArtifactException("--window must be at least 1 bar, got " + window);
        }
        if (overlayCap < 1) {
            throw new ReplayArtifactException("--overlay-cap must be at least 1, got " + overlayCap);
        }
        final ComparisonRow row = artifact.comparison(key);
        final Dataset dataset = artifact.dataset(row.dataset());
        final String traceFile = artifact.selectTrace(dataset, traceMode);
        final boolean nullMember = ReplayArtifact.TRACE_MODE_NULL_MEMBER.equals(traceMode);
        final Family family = nullMember ? nullFamily(row) : row.family();
        final ReplayTraceIndex trace = ReplayTraceIndex.scan(artifact.resolveInside(traceFile), traceFile,
                family::equals);
        if (nullMember) {
            requireBlockLength(row, trace, traceFile);
        }
        final List<Entry> entries = trace.entries(family);
        if (entries.isEmpty()) {
            throw new ReplayArtifactException("trace " + traceFile + " holds no as-of records for key '" + key + "' ("
                    + family + "); the row was not recorded in this trace mode. Try --trace "
                    + (ReplayArtifact.TRACE_MODE_REAL.equals(traceMode) ? ReplayArtifact.TRACE_MODE_NULL_MEMBER
                            : ReplayArtifact.TRACE_MODE_REAL));
        }
        final List<PriceBar> bars = nullMember ? recordedBars(entries, traceFile) : artifact.bars(dataset);
        final int firstBar = nullMember ? entries.getFirst().asOfIndex() : 0;
        final String hint = hint(artifact, row);
        final ReplaySession session = new ReplaySession(artifact, row, dataset, bars, firstBar, trace, traceFile,
                family, entries, window, overlayCap, hint);
        session.position = session.defaultPosition();
        return session;
    }

    /**
     * Maps a comparison row to the stream the producer records for its null
     * ensemble member: section {@code null}, with the grammar name as mode except
     * for H2 ablation modes, which keep their own name. Competing and robustness
     * rows have no null-member counterpart.
     */
    private static Family nullFamily(final ComparisonRow row) {
        if (!"h1".equals(row.section()) && !"h2".equals(row.section())) {
            throw new ReplayArtifactException("key '" + row.key() + "' is a '" + row.section()
                    + "' row; the selected null member records only h1 and h2 rows. Replay it with --trace real");
        }
        if (row.nullBlockLength() < 1) {
            throw new ReplayArtifactException("key '" + row.key()
                    + "' carries no null block length, so no null-member stream corresponds to it; replay it with --trace real");
        }
        final String mode = "h2".equals(row.section()) ? row.mode() : row.grammar();
        return new Family("null", mode, row.grammar(), row.detector(), row.partition());
    }

    private static void requireBlockLength(final ComparisonRow row, final ReplayTraceIndex trace,
            final String traceFile) {
        final int recorded = trace.header().get("nullBlockLength").getAsInt();
        if (recorded != row.nullBlockLength()) {
            throw new ReplayArtifactException("key '" + row.key() + "' was measured with null block length "
                    + row.nullBlockLength() + " but " + traceFile + " records block length " + recorded
                    + "; regenerate the null trace with --trace selected-null-member --block " + row.nullBlockLength());
        }
    }

    /** The member's own prices, exactly as the producer recorded them. */
    private static List<PriceBar> recordedBars(final List<Entry> entries, final String traceFile) {
        final List<PriceBar> recorded = new ArrayList<>(entries.size());
        final int first = entries.getFirst().asOfIndex();
        for (final Entry entry : entries) {
            final PriceBar bar = entry.bar();
            if (bar == null || entry.asOfIndex() != first + recorded.size()) {
                throw new ReplayArtifactException("null-member trace " + traceFile + " does not record a price bar for "
                        + "every consecutive as-of (problem at bar " + entry.asOfIndex()
                        + "); it predates bar recording or is incomplete. Regenerate it with --trace selected-null-member");
            }
            recorded.add(bar);
        }
        return new RecordedBars(first, List.copyOf(recorded));
    }

    private static String hint(final ReplayArtifact artifact, final ComparisonRow row) {
        if (!row.activeRules().isEmpty()) {
            return "";
        }
        final String base = "mode '" + row.mode()
                + "' evaluates no rules, so candidates carry no rule evidence (not evaluated)";
        return artifact.ruleEvidenceRow(row)
                .map(rules -> base + "; recorded rule evidence for the same detector and partition is in key "
                        + rules.key())
                .orElse(base + "; the run holds no all-rules ablation for this detector and partition");
    }

    /**
     * Status the research recorder writes for an ambiguous record: the
     * {@code TopologyStatus} name on {@code topology} records, and the lowercase
     * outcome {@code StudyRunner} passes to the observer on {@code alternative}
     * records (the competing {@code 3+3}/{@code 5+5} grammars). Neither side
     * exposes a shared constant, so both producer spellings are matched exactly.
     */
    private static boolean isAmbiguous(final String status) {
        return "AMBIGUOUS".equals(status) || "ambiguous".equals(status);
    }

    /**
     * First ambiguous record, else the first one carrying 2+ candidates, else the
     * first.
     */
    private int defaultPosition() {
        for (int i = 0; i < entries.size(); i++) {
            if (isAmbiguous(entries.get(i).status())) {
                return i;
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).candidateCount() >= 2) {
                return i;
            }
        }
        return 0;
    }

    ComparisonRow row() {
        return row;
    }

    Dataset dataset() {
        return dataset;
    }

    Family family() {
        return family;
    }

    String traceFile() {
        return traceFile;
    }

    ReplayTraceIndex trace() {
        return trace;
    }

    List<PriceBar> bars() {
        return bars;
    }

    int window() {
        return window;
    }

    int overlayCap() {
        return overlayCap;
    }

    int recordCount() {
        return entries.size();
    }

    int firstAsOf() {
        return entries.getFirst().asOfIndex();
    }

    int lastAsOf() {
        return entries.getLast().asOfIndex();
    }

    int cursor() {
        return entries.get(position).asOfIndex();
    }

    String selectedCandidate() {
        return selected;
    }

    ReplayArtifact artifact() {
        return artifact;
    }

    /**
     * @return the frame at the current cursor
     */
    ReplayFrame frame() {
        final String cacheKey = position + "|" + selected;
        final ReplayFrame cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        final JsonObject record = trace.read(entries.get(position));
        final boolean initial = position == 0;
        final boolean transition = !initial
                && !entries.get(position).signature().equals(entries.get(position - 1).signature());
        final ReplayFrame frame = ReplayFrame.project(row.key(), family, row.activeRules(), record, bars, initial,
                transition, selected, window, overlayCap, ruleEvidenceHint, firstBar);
        cache.put(cacheKey, frame);
        return frame;
    }

    /**
     * Moves to the latest recorded as-of at or before {@code asOfIndex}.
     *
     * @param asOfIndex a bar index
     * @return the frame at the new cursor
     */
    ReplayFrame seek(final int asOfIndex) {
        if (asOfIndex < firstAsOf()) {
            throw new ReplayArtifactException("no record at or before bar " + asOfIndex
                    + "; the first recorded as-of is " + firstAsOf() + " (last " + lastAsOf() + ")");
        }
        int low = 0;
        int high = entries.size() - 1;
        while (low < high) {
            final int mid = (low + high + 1) >>> 1;
            if (entries.get(mid).asOfIndex() <= asOfIndex) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        position = low;
        return frame();
    }

    /**
     * Moves to the latest recorded as-of whose bar had ended by {@code instant}.
     *
     * @param instant a point in time
     * @return the frame at the new cursor
     */
    ReplayFrame seek(final Instant instant) {
        int found = -1;
        int low = firstBar;
        int high = bars.size() - 1;
        while (low <= high) {
            final int mid = (low + high) >>> 1;
            if (bars.get(mid).end().isAfter(instant)) {
                high = mid - 1;
            } else {
                found = mid;
                low = mid + 1;
            }
        }
        if (found < 0) {
            throw new ReplayArtifactException(
                    "instant " + instant + " precedes the first retained bar ending " + bars.get(firstBar).end());
        }
        return seek(found);
    }

    /**
     * Steps to the adjacent recorded as-of.
     *
     * @param delta {@code -1} or {@code +1} records
     * @return the frame at the new cursor; unchanged at either end
     */
    ReplayFrame stepBar(final int delta) {
        position = Math.max(0, Math.min(entries.size() - 1, position + delta));
        return frame();
    }

    /**
     * Steps to the next ({@code +}) or previous ({@code -}) record whose state
     * differs from its predecessor. The first record of the stream has no
     * predecessor, so it is an initial state and never a transition target.
     *
     * @param direction positive forward, negative backward
     * @return the frame at the new cursor; unchanged when no transition exists in
     *         that direction
     */
    ReplayFrame stepTransition(final int direction) {
        if (direction > 0) {
            for (int i = position + 1; i < entries.size(); i++) {
                if (!entries.get(i).signature().equals(entries.get(i - 1).signature())) {
                    position = i;
                    break;
                }
            }
        } else {
            for (int i = position - 1; i > 0; i--) {
                if (!entries.get(i).signature().equals(entries.get(i - 1).signature())) {
                    position = i;
                    break;
                }
            }
        }
        return frame();
    }

    /**
     * Selects a candidate by key or by 1-based ordinal at the current cursor.
     *
     * @param keyOrOrdinal a {@code candidateKey} or ordinal
     * @return the frame with the selection applied
     */
    ReplayFrame select(final String keyOrOrdinal) {
        final ReplayFrame current = frame();
        String key = keyOrOrdinal;
        if (keyOrOrdinal.matches("\\d{1,3}")) {
            final int ordinal = Integer.parseInt(keyOrOrdinal);
            if (ordinal < 1 || ordinal > current.candidates().size()) {
                throw new ReplayArtifactException("no candidate #" + ordinal + " at bar " + current.cursor() + "; "
                        + current.candidates().size() + " retained");
            }
            key = current.candidates().get(ordinal - 1).candidateKey();
        } else if (current.candidate(key).isEmpty()) {
            throw new ReplayArtifactException("candidate '" + key + "' is not retained at bar " + current.cursor()
                    + "; retained: " + current.candidates().stream().map(ReplayFrame.Candidate::candidateKey).toList());
        }
        selected = key;
        return frame();
    }

    /**
     * Selects a candidate key without requiring it at the current cursor, so a
     * recorded key can be followed across bars.
     *
     * @param key the candidate key
     * @return the current frame
     */
    ReplayFrame follow(final String key) {
        selected = Objects.requireNonNull(key, "key");
        return frame();
    }

    /**
     * @return the frame with no candidate selected
     */
    ReplayFrame clearSelection() {
        selected = "";
        return frame();
    }

    /**
     * Lists the distinct recorded versions of a candidate up to the cursor, newest
     * last. A revised candidate keeps its key but changes its version, so earlier
     * versions stay readable without any recomputation.
     *
     * <p>
     * Only the most recent {@link #TIMELINE_LOOKBACK} records are inspected, and
     * the record just before that window seeds the comparison, so the window start
     * never reports a change that did not happen. {@link #timelineStartAsOf()}
     * tells whether and where earlier history was left out.
     *
     * @param candidateKey the candidate key
     * @return changes of presence/version, oldest first
     */
    List<TimelineEntry> candidateTimeline(final String candidateKey) {
        return candidateTimeline(candidateKey, TIMELINE_LOOKBACK);
    }

    List<TimelineEntry> candidateTimeline(final String candidateKey, final int lookback) {
        final List<TimelineEntry> timeline = new ArrayList<>();
        final int from = timelineFrom(lookback);
        String lastVersion = from > 0 ? versionAt(entries.get(from - 1), candidateKey) : null;
        for (int i = from; i <= position; i++) {
            final Entry entry = entries.get(i);
            final String version = versionAt(entry, candidateKey);
            if (!Objects.equals(version, lastVersion)) {
                if (version != null || lastVersion != null) {
                    timeline.add(new TimelineEntry(entry.asOfIndex(), version == null ? "" : version, version != null));
                }
                lastVersion = version;
            }
        }
        return List.copyOf(timeline);
    }

    /**
     * @return the as-of index of the oldest record {@link #candidateTimeline}
     *         inspects, or {@code -1} when it covers the whole stream up to the
     *         cursor
     */
    int timelineStartAsOf() {
        return timelineStartAsOf(TIMELINE_LOOKBACK);
    }

    int timelineStartAsOf(final int lookback) {
        final int from = timelineFrom(lookback);
        return from == 0 ? -1 : entries.get(from).asOfIndex();
    }

    private int timelineFrom(final int lookback) {
        return Math.max(0, position - lookback + 1);
    }

    private String versionAt(final Entry entry, final String candidateKey) {
        if (entry.candidateCount() == 0) {
            return null;
        }
        final JsonElement candidates = trace.read(entry).get("candidates");
        if (candidates != null && candidates.isJsonArray()) {
            for (final JsonElement element : candidates.getAsJsonArray()) {
                final JsonObject candidate = element.getAsJsonObject();
                if (candidateKey.equals(candidate.get("candidateKey").getAsString())) {
                    return candidate.get("version").getAsString();
                }
            }
        }
        return null;
    }

    /**
     * Bars of a null ensemble member. Members are regenerated resampled tapes whose
     * prices exist only in the trace, so this list holds just the bars the trace
     * recorded, addressed by their bar index; indices before {@code firstBar} are
     * not retained.
     */
    private static final class RecordedBars extends AbstractList<PriceBar> {
        private final int firstBar;
        private final List<PriceBar> recorded;

        RecordedBars(final int firstBar, final List<PriceBar> recorded) {
            this.firstBar = firstBar;
            this.recorded = recorded;
        }

        @Override
        public PriceBar get(final int index) {
            if (index < firstBar || index >= size()) {
                throw new IndexOutOfBoundsException(
                        "bar " + index + " is outside the recorded null-member bars " + firstBar + ".." + (size() - 1));
            }
            return recorded.get(index - firstBar);
        }

        @Override
        public int size() {
            return firstBar + recorded.size();
        }
    }
}
