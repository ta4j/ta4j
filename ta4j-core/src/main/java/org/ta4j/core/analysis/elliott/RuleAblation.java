/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the preregistered one-rule-at-a-time ablation modes.
 *
 * <p>Each single-rule mode owns a one-element rule list. The driver never
 * mutates or shares a caller-owned mutable list, which makes rule isolation
 * explicit in the resulting report.</p>
 */
final class RuleAblation {

    private RuleAblation() {
    }
    static List<Mode> modes() {
        return modes(ClassicalRelationshipRules.classicalRelationships());
    }

    static List<Mode> modes(final List<RelationshipRule> suppliedRules) {
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
    record Mode(String name, List<RelationshipRule> rules) {
        Mode {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("ablation mode name must not be blank");
            }
            rules = rules == null ? List.of() : List.copyOf(rules);
        }

        List<String> ruleIds() {
            return rules.stream().map(RelationshipRule::id).toList();
        }
    }
}
