/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Regression tests for the overflow-safe range arithmetic of
 * {@link OffsetNumBuffer}: terminal-value index arithmetic must never wrap into
 * a spurious in-window position, and ranges disjoint from the window must be
 * ignored.
 */
@RunWith(Parameterized.class)
public class OffsetNumBufferTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public OffsetNumBufferTest(NumFactory numFactory) {
        super(numFactory);
    }

    private static final int START = 5;
    private static final int END = 10;

    private OffsetNumBuffer newBuffer() {
        return new OffsetNumBuffer(START, END, numFactory.numOf(10), numFactory.zero());
    }

    @Test
    public void singleIndexOpsRejectTerminalWrappedPositions() {
        OffsetNumBuffer buffer = newBuffer();

        // MIN_VALUE - START wraps to a positive int under int arithmetic; the
        // buffer must still treat these as out-of-window instead of touching
        // wrapped slots.
        assertNumEquals(numFactory.zero(), buffer.get(Integer.MIN_VALUE));
        buffer.add(Integer.MIN_VALUE, numFactory.numOf(100));
        buffer.multiply(Integer.MIN_VALUE, numFactory.numOf(2));
        assertNumEquals(numFactory.numOf(10), buffer.get(START));

        for (int i = START; i <= END; i++) {
            assertNumEquals(numFactory.numOf(10), buffer.get(i));
        }
    }

    @Test
    public void addRangeIgnoresDisjointAndInvertedRanges() {
        OffsetNumBuffer below = newBuffer();
        below.addRange(Integer.MIN_VALUE, Integer.MIN_VALUE, numFactory.numOf(100));
        assertWindowUntouched(below);

        OffsetNumBuffer above = newBuffer();
        above.addRange(END + 1, Integer.MAX_VALUE, numFactory.numOf(100));
        assertWindowUntouched(above);

        OffsetNumBuffer inverted = newBuffer();
        inverted.addRange(END, START - 1, numFactory.numOf(100));
        assertWindowUntouched(inverted);
    }

    @Test
    public void multiplyRangeIgnoresDisjointAndInvertedRanges() {
        Num factor = numFactory.numOf(3);

        OffsetNumBuffer below = newBuffer();
        below.multiplyRange(Integer.MIN_VALUE, Integer.MIN_VALUE, factor);
        assertWindowUntouched(below);

        OffsetNumBuffer above = newBuffer();
        above.multiplyRange(END + 1, Integer.MAX_VALUE, factor);
        assertWindowUntouched(above);

        OffsetNumBuffer inverted = newBuffer();
        inverted.multiplyRange(END, START - 1, factor);
        assertWindowUntouched(inverted);
    }

    @Test
    public void addRangeClampsPartialOverlap() {
        OffsetNumBuffer buffer = newBuffer();
        buffer.addRange(0, END - 1, numFactory.one());

        assertNumEquals(numFactory.zero(), buffer.get(START - 1));
        for (int i = START; i <= END - 1; i++) {
            assertNumEquals(numFactory.numOf(11), buffer.get(i));
        }
        assertNumEquals(numFactory.numOf(10), buffer.get(END));
    }

    private void assertWindowUntouched(OffsetNumBuffer buffer) {
        for (int i = START; i <= END; i++) {
            assertNumEquals(numFactory.numOf(10), buffer.get(i));
        }
    }
}
