/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.rules;

import org.ta4j.core.analysis.elliott.topology.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the preregistered one-rule-at-a-time ablation modes.
 *
 * <p>
 * Each single-rule mode owns a one-element rule list. The driver never mutates
 * or shares a caller-owned mutable list, which makes rule isolation explicit in
 * the resulting report.
 * </p>
 */
public final class RuleAblation {

    private static final List<String> FROZEN_RULE_IDS = List.of("wave2-origin", "wave3-not-shortest",
            "wave4-nonoverlap", "wave5-divergence");

    private static final List<String> FROZEN_MODE_NAMES = List.of("topology-only", "+wave2-origin",
            "+wave3-not-shortest", "+wave4-nonoverlap", "+wave5-divergence", "classical-all");

    private RuleAblation() {
    }

    public static List<Mode> modes() {
        return modes(ClassicalRelationshipRules.classicalRelationships());
    }

    /**
     * Returns the frozen classical ladder in protocol order, independent of
     * additional caller-supplied relationship rules.
     *
     * @param suppliedRules rules bound by the frozen runner
     * @return topology-only, one-rule, and classical-all modes
     */
    public static List<Mode> frozenModes(final List<RelationshipRule> suppliedRules) {
        final List<RelationshipRule> supplied = List.copyOf(Objects.requireNonNull(suppliedRules, "suppliedRules"));
        final List<RelationshipRule> frozenRules = new ArrayList<>(4);
        for (final String ruleId : FROZEN_RULE_IDS) {
            final RelationshipRule rule = supplied.stream()
                    .filter(candidate -> ruleId.equals(candidate.id()))
                    .findFirst()
                    .orElseThrow(
                            () -> new IllegalArgumentException("frozen protocol requires relationship rule " + ruleId));
            frozenRules.add(rule);
        }
        return modes(frozenRules);
    }

    public static List<String> frozenModeNames() {
        return FROZEN_MODE_NAMES;
    }

    public static List<Mode> modes(final List<RelationshipRule> suppliedRules) {
        Objects.requireNonNull(suppliedRules, "suppliedRules");
        final List<RelationshipRule> rules = List.copyOf(suppliedRules);
        final List<Mode> modes = new ArrayList<>(rules.size() + 2);
        modes.add(new Mode("topology-only", List.of()));
        for (final RelationshipRule rule : rules) {
            Objects.requireNonNull(rule, "suppliedRules contains null");
            modes.add(new Mode("+" + rule.id(), List.of(rule)));
        }
        modes.add(new Mode("classical-all", rules));
        return List.copyOf(modes);
    }

    /** One immutable ablation mode and its active rules. */
    public static record Mode(String name, List<RelationshipRule> rules) {
        public Mode {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("ablation mode name must not be blank");
            }
            rules = rules == null ? List.of() : List.copyOf(rules);
        }

        public List<String> ruleIds() {
            return rules.stream().map(RelationshipRule::id).toList();
        }
    }
}
