/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import java.util.List;

import ta4jexamples.charting.replay.ReplayFrame.Candidate;
import ta4jexamples.charting.replay.ReplayFrame.Pivot;
import ta4jexamples.charting.replay.ReplayFrame.Rule;

/**
 * Renders the text view of a {@link ReplayFrame}.
 *
 * <p>
 * The text is a pure function of the frame, so it lists exactly the pivots,
 * candidates and rule results the chart draws, plus the ones the overlay cap
 * left out.
 */
final class ReplayEvidenceText {

    private ReplayEvidenceText() {
    }

    /**
     * @param frame the frame
     * @return multi-line evidence text ending in the frame digest
     */
    static String render(final ReplayFrame frame) {
        final StringBuilder out = new StringBuilder();
        final ReplayTraceIndex.Family family = frame.family();
        out.append("Replay ").append(frame.comparisonKey()).append('\n');
        out.append("  family:    ").append(family.section()).append(" / ").append(family.mode()).append(" / ")
                .append(family.grammar()).append(" / ").append(family.detector()).append(" / ")
                .append(family.partition()).append('\n');
        out.append("  as-of:     bar ").append(frame.cursor()).append(" @ ").append(frame.asOfTime()).append('\n');
        out.append("  state:     ").append(frame.kind()).append(' ').append(frame.status());
        if (!frame.direction().isEmpty()) {
            out.append(' ').append(frame.direction());
        }
        out.append(frame.initial() ? "  (initial recorded state; no earlier record to compare with)"
                : frame.transition() ? "  (state changed at this bar)" : "  (unchanged since the previous recorded bar)")
                .append('\n');
        if (!frame.labels().isEmpty()) {
            out.append("  labels:    ").append(String.join(", ", frame.labels())).append('\n');
        }
        out.append("  window:    bars ").append(frame.windowStart()).append("..").append(frame.cursor())
                .append("  price ").append(frame.axis().low()).append("..").append(frame.axis().high()).append('\n');
        out.append("  rules:     ").append(frame.activeRules().isEmpty() ? "none active" : String.join(", ", frame.activeRules()))
                .append('\n');
        appendPivots(out, frame);
        appendCandidates(out, frame);
        if (frame.suppressedFuture() > 0) {
            out.append("  causality guard removed ").append(frame.suppressedFuture())
                    .append(" point(s) that were not yet confirmed at this bar; the artifact is inconsistent\n");
        }
        if (!frame.ruleEvidenceHint().isEmpty()) {
            out.append("  note: ").append(frame.ruleEvidenceHint()).append('\n');
        }
        out.append("  frame digest: ").append(frame.digest()).append('\n');
        return out.toString();
    }

    private static void appendPivots(final StringBuilder out, final ReplayFrame frame) {
        out.append("  pivots confirmed by bar ").append(frame.cursor()).append(": ").append(frame.pivots().size())
                .append('\n');
        final List<Pivot> pivots = frame.pivots();
        final int shown = Math.min(pivots.size(), 12);
        if (shown < pivots.size()) {
            out.append("    ... ").append(pivots.size() - shown).append(" earlier pivot(s) omitted\n");
        }
        for (int i = pivots.size() - shown; i < pivots.size(); i++) {
            final Pivot pivot = pivots.get(i);
            out.append("    ").append(pivot.type()).append(" bar ").append(pivot.index()).append(" @ ")
                    .append(pivot.price()).append("  confirmed at bar ").append(pivot.confirmationIndex());
            if (pivot.newlyConfirmed()) {
                out.append("  NEW");
            }
            if (!pivot.inWindow()) {
                out.append("  (left of window)");
            }
            out.append('\n');
        }
    }

    private static void appendCandidates(final StringBuilder out, final ReplayFrame frame) {
        out.append("  candidates retained: ").append(frame.candidates().size()).append("  drawn: ")
                .append(frame.overlayed()).append(" (cap ").append(frame.overlayCap()).append(')');
        if (frame.truncated() > 0) {
            out.append("  NOT DRAWN: ").append(frame.truncated()).append(" (raise --overlay-cap or select one)");
        }
        out.append('\n');
        if (frame.clippedPoints() > 0) {
            out.append("  ").append(frame.clippedPoints()).append(" overlay point(s) lie left of the window\n");
        }
        for (final Candidate candidate : frame.candidates()) {
            out.append("  #").append(candidate.ordinal() + 1).append(candidate.selected() ? " * " : "   ")
                    .append(candidate.candidateKey()).append(' ').append(candidate.direction())
                    .append(candidate.overlayed() ? "" : " [not drawn]").append("\n      version ")
                    .append(candidate.version()).append("\n      placement ");
            final List<ReplayFrame.Point> placement = candidate.placement();
            for (int i = 0; i < placement.size(); i++) {
                final ReplayFrame.Point point = placement.get(i);
                out.append(i == 0 ? "" : " -> ").append(point.type()).append('@').append(point.index()).append('=')
                        .append(point.price());
            }
            out.append('\n');
            appendRules(out, candidate);
        }
    }

    private static void appendRules(final StringBuilder out, final Candidate candidate) {
        if (candidate.rules().isEmpty()) {
            out.append("      rules: not evaluated in this mode\n");
            return;
        }
        for (final Rule rule : candidate.rules()) {
            out.append("      ").append(rule.id()).append(' ').append(rule.state());
            if (!rule.score().isEmpty()) {
                out.append(" score ").append(rule.score());
            }
            out.append(" - ").append(rule.explanation()).append('\n');
            for (final String observation : rule.observations()) {
                out.append("          ").append(observation).append('\n');
            }
        }
    }
}
