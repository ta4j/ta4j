/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.util.List;

/**
 * Factory for the hard classical relationship rules.
 */
final class ClassicalRelationshipRules {

    private ClassicalRelationshipRules() {
    }

    static List<RelationshipRule> classicalRelationships() {
        return List.of(new Wave2OriginRule(), new Wave3NotShortestRule(), new Wave4NonOverlapRule());
    }
}
