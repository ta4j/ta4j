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
    void rejectsNonFinitePivotPrices() {
        for (final double invalidPrice : new double[] { Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY }) {
            assertThatThrownBy(() -> new ConfirmedPivot(0, 0, DoubleNum.valueOf(invalidPrice), SwingPivotType.LOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("price must be finite");
        }
    }

    @Test
    void asOfExcludesPivotsConfirmedLaterThanTheObservationPoint() {
        final PivotHistory history = PivotHistory.of(List.of( //
                new ConfirmedPivot(0, 0, DoubleNum.valueOf(5), SwingPivotType.LOW),
                new ConfirmedPivot(3, 10, DoubleNum.valueOf(8), SwingPivotType.HIGH)));

        assertThat(history.asOf(9)).hasSize(1);
        assertThat(history.asOf(10)).hasSize(2);
    }

    @Test
    void keepsLaterConfirmedPivotsVisibleWhenConfirmationsAreNonMonotonic() {
        // Re-admission after reconsideration legitimately confirms an older
        // pivot LATER than a newer one; the causal view must not stop at the
        // first invisible entry.
        final ConfirmedPivot olderLate = new ConfirmedPivot(0, 10, DoubleNum.valueOf(10), SwingPivotType.LOW);
        final ConfirmedPivot newerEarly = new ConfirmedPivot(5, 5, DoubleNum.valueOf(20), SwingPivotType.HIGH);
        final PivotHistory history = PivotHistory.of(List.of(olderLate, newerEarly));

        assertThat(history.asOf(4)).isEmpty();
        assertThat(history.asOf(5)).extracting(ConfirmedPivot::pivotIndex).containsExactly(5);
        assertThat(history.asOf(9)).extracting(ConfirmedPivot::pivotIndex).containsExactly(5);
        assertThat(history.asOf(10)).extracting(ConfirmedPivot::pivotIndex).containsExactly(0, 5);
    }

    @Test
    void renormalizesAsOfViewsWhenInteriorPivotIsHidden() {
        // With HIGH@5 still unconfirmed, the visible LOW@0/LOW@8 run must
        // collapse instead of leaking a same-type sequence to consumers.
        final PivotHistory history = PivotHistory
                .of(List.of(new ConfirmedPivot(0, 1, DoubleNum.valueOf(10), SwingPivotType.LOW),
                        new ConfirmedPivot(5, 10, DoubleNum.valueOf(20), SwingPivotType.HIGH),
                        new ConfirmedPivot(8, 8, DoubleNum.valueOf(5), SwingPivotType.LOW),
                        new ConfirmedPivot(9, 9, DoubleNum.valueOf(25), SwingPivotType.HIGH)));

        final List<ConfirmedPivot> view = history.asOf(9);
        assertThat(view).extracting(ConfirmedPivot::pivotIndex).containsExactly(8, 9);
        assertThat(view).extracting(ConfirmedPivot::type).containsExactly(SwingPivotType.LOW, SwingPivotType.HIGH);
        assertThat(view.get(0).price()).isEqualTo(DoubleNum.valueOf(5));
    }
}
