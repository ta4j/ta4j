/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.analysis.elliott.swing.SwingPivotType;
import org.ta4j.core.num.DoubleNum;

class PivotHistoryTest {

    @Test
    void collapsesConsecutiveSameTypePivotsToTheMoreExtremePrice() {
        final PivotHistory history = PivotHistory.of(List.of( //
                new ConfirmedPivot(0, 0, DoubleNum.valueOf(5), SwingPivotType.LOW),
                new ConfirmedPivot(2, 2, DoubleNum.valueOf(3), SwingPivotType.LOW),
                new ConfirmedPivot(4, 4, DoubleNum.valueOf(8), SwingPivotType.HIGH)));

        assertThat(history.size()).isEqualTo(2);
        assertThat(history.pivots().get(0).pivotIndex()).isEqualTo(2);
        assertThat(history.pivots().get(0).price()).isEqualTo(DoubleNum.valueOf(3));
        // The survivor keeps its own confirmation provenance.
        assertThat(history.pivots().get(0).confirmationIndex()).isEqualTo(2);
    }

    @Test
    void collapsesHighsToTheHigherExtreme() {
        final PivotHistory history = PivotHistory.of(List.of( //
                new ConfirmedPivot(1, 1, DoubleNum.valueOf(7), SwingPivotType.HIGH),
                new ConfirmedPivot(3, 3, DoubleNum.valueOf(9), SwingPivotType.HIGH)));

        assertThat(history.size()).isEqualTo(1);
        assertThat(history.pivots().get(0).pivotIndex()).isEqualTo(3);
        assertThat(history.pivots().get(0).price()).isEqualTo(DoubleNum.valueOf(9));
    }

    @Test
    void keepsTheLaterPivotOnEqualExtremes() {
        final PivotHistory history = PivotHistory.of(List.of( //
                new ConfirmedPivot(0, 0, DoubleNum.valueOf(5), SwingPivotType.LOW),
                new ConfirmedPivot(2, 2, DoubleNum.valueOf(5), SwingPivotType.LOW)));

        assertThat(history.size()).isEqualTo(1);
        assertThat(history.pivots().get(0).pivotIndex()).isEqualTo(2);
    }

    @Test
    void rejectsNonIncreasingIndices() {
        assertThatThrownBy(() -> PivotHistory.of(List.of( //
                new ConfirmedPivot(4, 4, DoubleNum.valueOf(5), SwingPivotType.LOW),
                new ConfirmedPivot(4, 5, DoubleNum.valueOf(6), SwingPivotType.HIGH))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("strictly increase");
    }

    @Test
    void asOfExcludesPivotsConfirmedLaterThanTheObservationPoint() {
        final PivotHistory history = PivotHistory.of(List.of( //
                new ConfirmedPivot(0, 0, DoubleNum.valueOf(5), SwingPivotType.LOW),
                new ConfirmedPivot(3, 10, DoubleNum.valueOf(8), SwingPivotType.HIGH)));

        assertThat(history.asOf(9)).hasSize(1);
        assertThat(history.asOf(10)).hasSize(2);
    }
}
