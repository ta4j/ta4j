/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ScaleRelationTest {

    @Test
    void selfCyclicReversedAndNonAdjacentLinksAreRejected() {
        final List<ScaleRelation.Scale> chain = List.of(new ScaleRelation.Scale("a", 0),
                new ScaleRelation.Scale("b", 1), new ScaleRelation.Scale("c", 2));

        assertEquals(List.of(new ScaleRelation.Link("a", "b"), new ScaleRelation.Link("b", "c")),
                ScaleRelation.adjacentLinks(chain));
        ScaleRelation.validateLinks(chain, ScaleRelation.adjacentLinks(chain));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> ScaleRelation.validateLinks(chain, List.of(new ScaleRelation.Link("a", "a")))).getMessage()
                .contains("self link"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> ScaleRelation.validateLinks(chain,
                        List.of(new ScaleRelation.Link("a", "b"), new ScaleRelation.Link("b", "a"))))
                .getMessage()
                .contains("cyclic link"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> ScaleRelation.validateLinks(chain, List.of(new ScaleRelation.Link("c", "b")))).getMessage()
                .contains("reversed link"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> ScaleRelation.validateLinks(chain, List.of(new ScaleRelation.Link("a", "c")))).getMessage()
                .contains("non-adjacent"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> ScaleRelation.validateLinks(chain, List.of(new ScaleRelation.Link("a", "z")))).getMessage()
                .contains("undeclared"));
    }
}
