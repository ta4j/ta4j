/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.ta4j.core.Bar;

/**
 * Package-private streaming sink for per-bar study observations.
 *
 * <p>
 * {@link StudyRunner} invokes the observer once per evaluated in-partition bar
 * and study mode, after the bar has been classified and immediately before the
 * same outcome is folded into the mode's metrics. Observers are read-only
 * witnesses: they receive the exact analysis and rule evidence the metrics
 * consume and can never alter the report. Callbacks arrive on the evaluating
 * thread in deterministic evaluation order, so an observer that streams each
 * observation to disk never needs to retain members times bars in memory.
 * </p>
 *
 * @since 0.25.1
 */
interface StudyObserver {

    /**
     * Receives one kernel-grammar observation.
     *
     * @param scope             study section and mode that recorded the bar
     * @param partition         locked partition name
     * @param recordedIndex     bar index in source coordinates
     * @param asOfEnd           end time of the evaluated bar
     * @param visiblePivots     confirmed pivots visible as of the bar, in source
     *                          coordinates
     * @param analysis          topology analysis folded into the metrics, with
     *                          candidate placements in source coordinates
     * @param candidateEvidence active-rule evidence per analysis candidate, in
     *                          candidate order; empty inner lists for topology-only
     *                          modes
     */
    void topology(Scope scope, String partition, int recordedIndex, Instant asOfEnd, List<ConfirmedPivot> visiblePivots,
            TopologyAnalysis analysis, List<List<RuleEvidence>> candidateEvidence);

    /**
     * Receives one alternative-grammar or change-point baseline observation.
     *
     * @param scope         study section and mode that recorded the bar
     * @param partition     locked partition name
     * @param recordedIndex bar index in source coordinates
     * @param asOfEnd       end time of the evaluated bar
     * @param visiblePivots confirmed pivots visible as of the bar; empty for the
     *                      pivot-free change-point baseline
     * @param outcome       recorded outcome ({@code complete}, {@code ambiguous},
     *                      {@code forming}, {@code no-match}, or
     *                      {@code insufficient-history})
     * @param labels        placement or change labels behind the outcome
     */
    void alternative(Scope scope, String partition, int recordedIndex, Instant asOfEnd,
            List<ConfirmedPivot> visiblePivots, String outcome, Set<String> labels);

    /**
     * Receives the price bar of a null ensemble member immediately before the
     * {@link #topology} observation recorded for the same as-of bar. Null members
     * are regenerated resampled tapes whose prices exist nowhere in the dataset;
     * observers that make a null-member trace replayable persist them with the
     * observation. The default ignores the bar.
     *
     * @param scope         study section and mode about to record the bar
     * @param partition     locked partition name
     * @param recordedIndex bar index in source coordinates
     * @param bar           member bar evaluated at {@code recordedIndex}
     * @since 0.26.1
     */
    default void nullBar(Scope scope, String partition, int recordedIndex, Bar bar) {
        // real-data observers have no use for member prices
    }

    /**
     * Identity of the study mode that recorded an observation.
     *
     * @param section         report section ({@code h1}, {@code h2},
     *                        {@code competing}, {@code robustness}, or
     *                        {@code null})
     * @param mode            report mode name
     * @param grammar         evaluated grammar name
     * @param activeRules     active relationship rule ids in evaluation order
     * @param detector        detector configuration name
     * @param nullBlockLength null block length, or {@code -1} for real data
     * @param nullMemberIndex null ensemble member index, or {@code -1} for real
     *                        data
     * @since 0.25.1
     */
    record Scope(String section, String mode, String grammar, List<String> activeRules, String detector,
            int nullBlockLength, int nullMemberIndex) {

        public Scope {
            Objects.requireNonNull(section, "section");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(grammar, "grammar");
            activeRules = List.copyOf(Objects.requireNonNull(activeRules, "activeRules"));
            Objects.requireNonNull(detector, "detector");
        }

        static Scope real(final String section, final String mode, final String grammar, final List<String> activeRules,
                final String detector) {
            return new Scope(section, mode, grammar, activeRules, detector, -1, -1);
        }
    }
}
