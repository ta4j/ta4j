/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.named.conflict;

import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.strategy.named.NamedStrategy;

/**
 * Two concrete named strategies sharing the simple name
 * {@code ConflictingStrategy}. They live outside every default scan package, so
 * only tests that scan this package explicitly observe the conflict.
 */
public final class ConflictingStrategies {

    private ConflictingStrategies() {
    }

    /** First holder. */
    public static final class First {

        private First() {
        }

        /** First strategy named {@code ConflictingStrategy}. */
        public static final class ConflictingStrategy extends NamedStrategy {

            private ConflictingStrategy() {
                super("ConflictingStrategy", new FixedRule(1), new FixedRule(2), 0);
            }
        }
    }

    /** Second holder. */
    public static final class Second {

        private Second() {
        }

        /** Second strategy named {@code ConflictingStrategy}. */
        public static final class ConflictingStrategy extends NamedStrategy {

            private ConflictingStrategy() {
                super("ConflictingStrategy", new FixedRule(1), new FixedRule(2), 0);
            }
        }
    }
}
