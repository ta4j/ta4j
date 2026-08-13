/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.swing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class SwingDetectorResultTest {

    private static final NumFactory FACTORY = DoubleNumFactory.getInstance();
    private static final ElliottDegree DEGREE = ElliottDegree.PRIMARY;

    private Num num(double value) {
        return FACTORY.numOf(value);
    }

    private SwingPivot pivot(int index, double price, SwingPivotType type) {
        return new SwingPivot(index, num(price), type);
    }

    private ElliottSwing swing(int fromIndex, int toIndex, double fromPrice, double toPrice) {
        return new ElliottSwing(fromIndex, toIndex, num(fromPrice), num(toPrice), DEGREE);
    }

    @Test
    void rejectsPivotsAndSwingsWithDifferentChains() {
        List<SwingPivot> pivots = List.of(pivot(1, 100, SwingPivotType.LOW), pivot(2, 110, SwingPivotType.HIGH));
        List<ElliottSwing> swings = List.of(swing(1, 2, 100, 90));

        assertThatThrownBy(() -> new SwingDetectorResult(pivots, swings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inconsistent");
    }

    @Test
    void rejectsPivotsAndSwingsOfDifferentLengths() {
        List<SwingPivot> pivots = List.of(pivot(1, 100, SwingPivotType.LOW), pivot(2, 110, SwingPivotType.HIGH));
        List<ElliottSwing> swings = List.of(swing(1, 2, 100, 110), swing(2, 3, 110, 105));

        assertThatThrownBy(() -> new SwingDetectorResult(pivots, swings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inconsistent");
    }

    @Test
    void rejectsContradictoryTypePriceSequences() {
        List<SwingPivot> pivots = List.of(pivot(1, 100, SwingPivotType.HIGH), pivot(2, 101, SwingPivotType.LOW));
        List<ElliottSwing> swings = List.of(swing(1, 2, 100, 101));

        assertThatThrownBy(() -> new SwingDetectorResult(pivots, swings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inconsistent");
    }

    @Test
    void rejectsUnorderedPivotChain() {
        List<SwingPivot> pivots = List.of(pivot(2, 110, SwingPivotType.HIGH), pivot(1, 100, SwingPivotType.LOW));
        List<ElliottSwing> swings = List.of(swing(1, 2, 100, 110));

        assertThatThrownBy(() -> new SwingDetectorResult(pivots, swings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inconsistent");
    }

    @Test
    void acceptsConsistentPivotsAndSwings() {
        List<SwingPivot> pivots = List.of(pivot(1, 100, SwingPivotType.LOW), pivot(2, 110, SwingPivotType.HIGH),
                pivot(3, 105, SwingPivotType.LOW));
        List<ElliottSwing> swings = List.of(swing(1, 2, 100, 110), swing(2, 3, 110, 105));

        SwingDetectorResult result = new SwingDetectorResult(pivots, swings);

        assertThat(result.pivots()).isEqualTo(pivots);
        assertThat(result.swings()).isEqualTo(swings);
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    void fromSwingsRoundTripsUnchanged() {
        List<ElliottSwing> swings = List.of(swing(1, 2, 100, 110), swing(2, 3, 110, 105));

        SwingDetectorResult result = SwingDetectorResult.fromSwings(swings);

        assertThat(result.swings()).isEqualTo(swings);
        assertThat(result.pivots()).containsExactly(pivot(1, 100, SwingPivotType.LOW),
                pivot(2, 110, SwingPivotType.HIGH), pivot(3, 105, SwingPivotType.LOW));
        assertThat(new SwingDetectorResult(result.pivots(), result.swings())).isEqualTo(result);
    }

    @Test
    void fromPivotsNormalizesAndDerivesSwings() {
        List<SwingPivot> pivots = List.of(pivot(2, 110, SwingPivotType.HIGH), pivot(1, 100, SwingPivotType.LOW),
                pivot(3, 105, SwingPivotType.LOW));

        SwingDetectorResult result = SwingDetectorResult.fromPivots(pivots, DEGREE);

        assertThat(result.pivots()).containsExactly(pivot(1, 100, SwingPivotType.LOW),
                pivot(2, 110, SwingPivotType.HIGH), pivot(3, 105, SwingPivotType.LOW));
        assertThat(result.swings()).containsExactly(swing(1, 2, 100, 110), swing(2, 3, 110, 105));
        assertThat(new SwingDetectorResult(result.pivots(), result.swings())).isEqualTo(result);
    }

    @Test
    void singleViewConstructionsStillWork() {
        List<SwingPivot> pivots = List.of(pivot(1, 100, SwingPivotType.LOW), pivot(2, 110, SwingPivotType.HIGH));
        List<ElliottSwing> swings = List.of(swing(1, 2, 100, 110));

        SwingDetectorResult pivotsOnly = new SwingDetectorResult(pivots, List.of());
        SwingDetectorResult swingsOnly = new SwingDetectorResult(List.of(), swings);

        assertThat(pivotsOnly.pivots()).isEqualTo(pivots);
        assertThat(pivotsOnly.swings()).isEmpty();
        assertThat(pivotsOnly.isEmpty()).isTrue();
        assertThat(swingsOnly.pivots()).isEmpty();
        assertThat(swingsOnly.swings()).isEqualTo(swings);
    }

    @Test
    void nullArgumentsNormalizeToEmptyLists() {
        SwingDetectorResult result = new SwingDetectorResult(null, null);

        assertThat(result.pivots()).isEmpty();
        assertThat(result.swings()).isEmpty();
        assertThat(result.isEmpty()).isTrue();
    }
}
