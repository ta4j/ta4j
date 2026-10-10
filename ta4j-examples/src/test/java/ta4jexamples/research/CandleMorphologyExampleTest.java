/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.research;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;

public class CandleMorphologyExampleTest {

    @Test
    public void documentedMorphologyHoldsOnLastBar() {
        BarSeries series = CandleMorphologyExample.buildSeries();
        Rule customMorphology = CandleMorphologyExample.customMorphology(series);

        Assertions.assertTrue(customMorphology.isSatisfied(series.getEndIndex()));
    }
}
