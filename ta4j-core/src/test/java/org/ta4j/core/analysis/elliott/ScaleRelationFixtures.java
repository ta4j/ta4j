/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectorResult;
import org.ta4j.core.analysis.elliott.swing.SwingPivot;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;

/**
 * Scripted two-scale fixture shared by the scale relation tests.
 *
 * <p>
 * The parent tape is a bullish five-wave impulse. The child tape subdivides
 * every parent leg: impulse legs into five waves and counter legs into three.
 * Both scales share their anchors exactly, so no relation depends on snapping.
 */
final class ScaleRelationFixtures {

    static final int PARENT_LAG = 2;
    static final int CHILD_LAG = 1;
    /** Bar at which the last parent pivot is confirmed. */
    static final int PARENT_COMPLETE = 52;

    static final StudyRunner.Partitions PARTITIONS = new StudyRunner.Partitions(
            List.of(new StudyRunner.Partition("calibration", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 12, 31))),
            LocalDate.of(2024, 1, 1));

    /** A scripted pivot. */
    record Pt(int index, double price, SwingPivotType type) {
    }

    static final List<Pt> PARENT = List.of(pt(0, 100, 'L'), pt(10, 120, 'H'), pt(15, 110, 'L'), pt(30, 150, 'H'),
            pt(36, 135, 'L'), pt(50, 170, 'H'));

    static final List<Pt> CHILD = List.of(pt(0, 100, 'L'), pt(2, 108, 'H'), pt(3, 104, 'L'), pt(7, 116, 'H'),
            pt(8, 112, 'L'), pt(10, 120, 'H'), pt(11, 114, 'L'), pt(13, 118, 'H'), pt(15, 110, 'L'), pt(18, 120, 'H'),
            pt(20, 114, 'L'), pt(26, 140, 'H'), pt(28, 130, 'L'), pt(30, 150, 'H'), pt(32, 140, 'L'),
            pt(33, 145, 'H'), pt(36, 135, 'L'), pt(40, 150, 'H'), pt(42, 142, 'L'), pt(47, 165, 'H'),
            pt(48, 158, 'L'), pt(50, 170, 'H'));

    private ScaleRelationFixtures() {
    }

    static Pt pt(final int index, final double price, final char type) {
        return new Pt(index, price, type == 'H' ? SwingPivotType.HIGH : SwingPivotType.LOW);
    }

    /** Copy of {@code base} with {@code replacement} in place of its same-index pivot. */
    static List<Pt> replacing(final List<Pt> base, final int index, final Pt replacement) {
        final List<Pt> copy = new ArrayList<>();
        for (final Pt pivot : base) {
            copy.add(pivot.index() == index ? replacement : pivot);
        }
        return copy;
    }

    /** Copy of {@code base} without the pivot at {@code index}. */
    static List<Pt> without(final List<Pt> base, final int index) {
        return base.stream().filter(pivot -> pivot.index() != index).toList();
    }

    /** Copy of {@code base} with extra pivots merged in bar order. */
    static List<Pt> with(final List<Pt> base, final Pt... extra) {
        final List<Pt> copy = new ArrayList<>(base);
        copy.addAll(List.of(extra));
        copy.sort(java.util.Comparator.comparingInt(Pt::index));
        return copy;
    }

    /** Series of {@code count} flat daily bars starting 2018-01-01. */
    static BarSeries series(final int count) {
        final BarSeries series = new BaseBarSeriesBuilder().withName("scale-relation").build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < count; index++) {
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1)))
                    .openPrice(100)
                    .highPrice(101)
                    .lowPrice(99)
                    .closePrice(100)
                    .volume(1)
                    .amount(100)
                    .trades(1)
                    .add();
        }
        return series;
    }

    /** Detector that reports each pivot once {@code lag} bars have passed. */
    static SwingDetector scripted(final List<Pt> pivots, final int lag) {
        return scripted(visibleAfter(pivots, pivot -> lag));
    }

    /** Visibility function: a pivot is reported from {@code index + lag} on. */
    static IntFunction<List<Pt>> visibleAfter(final List<Pt> pivots, final ToIntFunction<Pt> lag) {
        return asOf -> pivots.stream().filter(pivot -> pivot.index() + lag.applyAsInt(pivot) <= asOf).toList();
    }

    /** Detector whose reported pivots are an arbitrary function of the as-of bar. */
    static SwingDetector scripted(final IntFunction<List<Pt>> visibleAt) {
        return (series, index, degree) -> {
            final List<SwingPivot> pivots = new ArrayList<>();
            for (final Pt pivot : visibleAt.apply(index)) {
                if (pivot.index() <= series.getEndIndex()) {
                    pivots.add(new SwingPivot(pivot.index(), series.numFactory().numOf(pivot.price()), pivot.type()));
                }
            }
            return new SwingDetectorResult(pivots, List.of());
        };
    }

    static ScaleRelationStudy.ScaleInput input(final String name, final SwingDetector detector) {
        return new ScaleRelationStudy.ScaleInput(name, () -> detector);
    }

    static ScaleRelationStudy.ScaleInput input(final String name, final List<Pt> pivots, final int lag) {
        return input(name, scripted(pivots, lag));
    }

    static ScaleRelationExtractor.Identity identity() {
        return new ScaleRelationExtractor.Identity() {
            @Override
            public String key(final TopologyCandidate candidate) {
                return ElliottResearchTrace.candidateKey(candidate);
            }

            @Override
            public String version(final TopologyCandidate candidate, final List<RuleEvidence> evidence) {
                return ElliottResearchTrace.candidateVersion(candidate, evidence);
            }
        };
    }

    static RelationshipRule rule(final String id, final boolean passes) {
        return new RelationshipRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public RuleEvidence evaluate(final TopologyCandidate candidate) {
                return passes ? RuleEvidence.pass(id, List.of("synthetic"), "synthetic pass")
                        : RuleEvidence.fail(id, List.of("synthetic"), "synthetic fail");
            }
        };
    }

    static List<RelationshipRule> passingRules() {
        return List.of(rule("synthetic-pass", true));
    }

    static ScaleRelationStudy study(final List<ScaleRelationStudy.ScaleInput> inputs,
            final ScaleRelation.Policy policy) {
        return new ScaleRelationStudy(inputs, policy, passingRules(), identity(), PARTITIONS);
    }

    static List<ScaleRelationStudy.Frame> run(final ScaleRelationStudy study, final int bars, final int end) {
        final List<ScaleRelationStudy.Frame> frames = new ArrayList<>();
        study.run(series(bars), 0, end, frames::add);
        return frames;
    }

    /** Active edges of {@code frames}: every ACTIVE event up to {@code asOf}, replayed. */
    static List<ScaleRelation.Edge> activeAt(final List<ScaleRelationStudy.Frame> frames, final int asOf) {
        final java.util.Map<String, ScaleRelation.Edge> active = new java.util.TreeMap<>();
        for (final ScaleRelationStudy.Frame frame : frames) {
            if (frame.asOfIndex() > asOf) {
                break;
            }
            for (final ScaleRelation.Event event : frame.events()) {
                if (event.lifecycle() == ScaleRelation.Lifecycle.ACTIVE) {
                    active.put(event.edge().key(), event.edge());
                } else {
                    active.remove(event.edge().key());
                }
            }
        }
        return List.copyOf(active.values());
    }

    static List<ScaleRelation.Event> events(final List<ScaleRelationStudy.Frame> frames) {
        return frames.stream().flatMap(frame -> frame.events().stream()).toList();
    }
}
