/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Configures which Elliott scenario patterns are enabled.
 *
 * @since 0.22.2
 */
public final class PatternSet {

    private final EnumSet<ScenarioType> enabledTypes;

    private PatternSet(final EnumSet<ScenarioType> enabledTypes) {
        this.enabledTypes = EnumSet.copyOf(enabledTypes);
    }

    /**
     * Enables all scenario types.
     *
     * @return pattern set with all patterns enabled
     * @since 0.22.2
     */
    public static PatternSet all() {
        return new PatternSet(EnumSet.allOf(ScenarioType.class));
    }

    /**
     * Enables only the specified scenario types.
     *
     * @param types scenario types to enable
     * @return pattern set
     * @since 0.22.2
     */
    public static PatternSet of(final ScenarioType... types) {
        Objects.requireNonNull(types, "types");
        EnumSet<ScenarioType> set = EnumSet.noneOf(ScenarioType.class);
        for (ScenarioType type : types) {
            if (type != null) {
                set.add(type);
            }
        }
        return new PatternSet(set);
    }

    /**
     * Enables all patterns except the specified types.
     *
     * @param types scenario types to disable
     * @return pattern set
     * @since 0.22.2
     */
    public static PatternSet without(final ScenarioType... types) {
        PatternSet set = all();
        if (types != null) {
            for (ScenarioType type : types) {
                if (type != null) {
                    set.enabledTypes.remove(type);
                }
            }
        }
        return new PatternSet(set.enabledTypes);
    }

    /**
     * @param type scenario type
     * @return {@code true} if this pattern type is enabled
     * @since 0.22.2
     */
    public boolean allows(final ScenarioType type) {
        if (type == null) {
            return false;
        }
        return enabledTypes.contains(type);
    }

    /**
     * @return immutable view of enabled types
     * @since 0.22.2
     */
    public Set<ScenarioType> enabledTypes() {
        return Set.copyOf(enabledTypes);
    }
}
