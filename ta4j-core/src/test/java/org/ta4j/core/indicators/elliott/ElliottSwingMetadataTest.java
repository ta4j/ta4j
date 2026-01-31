/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.NumFactory;

/**
 * Tests for {@link ElliottSwingMetadata}.
 */
class ElliottSwingMetadataTest {

    private NumFactory numFactory;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
    }

    @Test
    void emptySwingList_shouldBeValidAndEmpty() {
        var metadata = ElliottSwingMetadata.of(List.of(), numFactory);

        assertThat(metadata.isValid()).isTrue();
        assertThat(metadata.isEmpty()).isTrue();
        assertThat(metadata.size()).isZero();
        assertThat(metadata.highestPrice()).isEqualByComparingTo(numFactory.zero());
        assertThat(metadata.lowestPrice()).isEqualByComparingTo(numFactory.zero());
    }

    @Test
    void nullSwingList_shouldBeValidAndEmpty() {
        var metadata = ElliottSwingMetadata.of(null, numFactory);

        assertThat(metadata.isValid()).isTrue();
        assertThat(metadata.isEmpty()).isTrue();
    }

    @Test
    void singleSwing_shouldCalculatePriceExtremes() {
        var swing = swing(0, 5, 100, 120);
        var metadata = ElliottSwingMetadata.of(List.of(swing), numFactory);

        assertThat(metadata.isValid()).isTrue();
        assertThat(metadata.size()).isEqualTo(1);
        assertThat(metadata.highestPrice()).isEqualByComparingTo(numFactory.numOf(120));
        assertThat(metadata.lowestPrice()).isEqualByComparingTo(numFactory.numOf(100));
    }

    @Test
    void multipleSwings_shouldTrackExtremes() {
        var swings = List.of(swing(0, 3, 100, 115), swing(3, 6, 115, 105), swing(6, 10, 105, 130));

        var metadata = ElliottSwingMetadata.of(swings, numFactory);

        assertThat(metadata.isValid()).isTrue();
        assertThat(metadata.size()).isEqualTo(3);
        assertThat(metadata.highestPrice()).isEqualByComparingTo(numFactory.numOf(130));
        assertThat(metadata.lowestPrice()).isEqualByComparingTo(numFactory.numOf(100));
    }

    @Test
    void swingWithNaNPrice_shouldBeInvalid() {
        var nanSwing = new ElliottSwing(0, 3, numFactory.numOf(100), NaN.NaN, ElliottDegree.MINOR);
        var metadata = ElliottSwingMetadata.of(List.of(nanSwing), numFactory);

        assertThat(metadata.isValid()).isFalse();
        assertThat(metadata.highestPrice()).isEqualByComparingTo(numFactory.zero());
        assertThat(metadata.lowestPrice()).isEqualByComparingTo(numFactory.zero());
    }

    @Test
    void leading_shouldReturnPrefix() {
        var swings = List.of(swing(0, 3, 100, 110), swing(3, 6, 110, 105), swing(6, 10, 105, 120));

        var metadata = ElliottSwingMetadata.of(swings, numFactory);

        assertThat(metadata.leading(2)).hasSize(2);
        assertThat(metadata.leading(2).get(0).fromPrice()).isEqualByComparingTo(numFactory.numOf(100));
        assertThat(metadata.leading(2).get(1).fromPrice()).isEqualByComparingTo(numFactory.numOf(110));
    }

    @Test
    void leading_withExcessiveLength_shouldReturnAll() {
        var swings = List.of(swing(0, 3, 100, 110), swing(3, 6, 110, 105));
        var metadata = ElliottSwingMetadata.of(swings, numFactory);

        assertThat(metadata.leading(10)).hasSize(2);
    }

    @Test
    void leading_withZeroOrNegative_shouldReturnEmpty() {
        var swings = List.of(swing(0, 3, 100, 110));
        var metadata = ElliottSwingMetadata.of(swings, numFactory);

        assertThat(metadata.leading(0)).isEmpty();
        assertThat(metadata.leading(-1)).isEmpty();
    }

    @Test
    void trailing_shouldReturnSuffix() {
        var swings = List.of(swing(0, 3, 100, 110), swing(3, 6, 110, 105), swing(6, 10, 105, 120));

        var metadata = ElliottSwingMetadata.of(swings, numFactory);

        assertThat(metadata.trailing(2)).hasSize(2);
        assertThat(metadata.trailing(2).get(0).fromPrice()).isEqualByComparingTo(numFactory.numOf(110));
        assertThat(metadata.trailing(2).get(1).fromPrice()).isEqualByComparingTo(numFactory.numOf(105));
    }

    @Test
    void trailing_withExcessiveLength_shouldReturnAll() {
        var swings = List.of(swing(0, 3, 100, 110));
        var metadata = ElliottSwingMetadata.of(swings, numFactory);

        assertThat(metadata.trailing(10)).hasSize(1);
    }

    @Test
    void subList_shouldReturnBoundedRange() {
        var swings = List.of(swing(0, 3, 100, 110), swing(3, 6, 110, 105), swing(6, 10, 105, 120),
                swing(10, 15, 120, 115));

        var metadata = ElliottSwingMetadata.of(swings, numFactory);

        var subList = metadata.subList(1, 3);
        assertThat(subList).hasSize(2);
        assertThat(subList.get(0).fromPrice()).isEqualByComparingTo(numFactory.numOf(110));
        assertThat(subList.get(1).fromPrice()).isEqualByComparingTo(numFactory.numOf(105));
    }

    @Test
    void subList_withOutOfBounds_shouldClamp() {
        var swings = List.of(swing(0, 3, 100, 110), swing(3, 6, 110, 105));
        var metadata = ElliottSwingMetadata.of(swings, numFactory);

        assertThat(metadata.subList(-5, 10)).hasSize(2);
        assertThat(metadata.subList(0, 100)).hasSize(2);
    }

    @Test
    void subList_withInvertedRange_shouldReturnEmpty() {
        var swings = List.of(swing(0, 3, 100, 110));
        var metadata = ElliottSwingMetadata.of(swings, numFactory);

        assertThat(metadata.subList(5, 3)).isEmpty();
    }

    @Test
    void swing_shouldReturnByIndex() {
        var swings = List.of(swing(0, 3, 100, 110), swing(3, 6, 110, 105));
        var metadata = ElliottSwingMetadata.of(swings, numFactory);

        assertThat(metadata.swing(0).fromPrice()).isEqualByComparingTo(numFactory.numOf(100));
        assertThat(metadata.swing(1).fromPrice()).isEqualByComparingTo(numFactory.numOf(110));
    }

    @Test
    void swing_outOfBounds_shouldThrow() {
        var swings = List.of(swing(0, 3, 100, 110));
        var metadata = ElliottSwingMetadata.of(swings, numFactory);

        assertThatThrownBy(() -> metadata.swing(5)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void swings_shouldReturnImmutableCopy() {
        var swings = List.of(swing(0, 3, 100, 110));
        var metadata = ElliottSwingMetadata.of(swings, numFactory);

        var result = metadata.swings();
        assertThat(result).hasSize(1);
        assertThatThrownBy(() -> result.add(swing(3, 6, 110, 105))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullNumFactory_shouldThrow() {
        assertThatThrownBy(() -> ElliottSwingMetadata.of(List.of(), null)).isInstanceOf(NullPointerException.class);
    }

    private ElliottSwing swing(int fromIndex, int toIndex, double fromPrice, double toPrice) {
        return new ElliottSwing(fromIndex, toIndex, numFactory.numOf(fromPrice), numFactory.numOf(toPrice),
                ElliottDegree.MINOR);
    }
}
