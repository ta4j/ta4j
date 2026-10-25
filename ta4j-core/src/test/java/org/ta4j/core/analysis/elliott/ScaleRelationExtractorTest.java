/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ta4j.core.analysis.elliott.ScaleRelationFixtures.series;

import java.util.List;

import org.junit.jupiter.api.Test;

class ScaleRelationExtractorTest {

    @Test
    void extractorRejectsSameScaleAndReversedScales() {
        final ScaleRelationExtractor extractor = new ScaleRelationExtractor(ScaleRelation.Policy.defaults(),
                ScaleRelationFixtures.passingRules(), ScaleRelationFixtures.identity());
        final ScaleRelation.Scale coarse = new ScaleRelation.Scale("coarse", 0);
        final ScaleRelation.Scale fine = new ScaleRelation.Scale("fine", 1);

        assertThrows(IllegalArgumentException.class,
                () -> extractor.extract(coarse, coarse, List.of(), List.of(), series(5)));
        assertThrows(IllegalArgumentException.class,
                () -> extractor.extract(fine, coarse, List.of(), List.of(), series(5)));
    }
}
