/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class KinematicKalmanForecastStateTest extends AbstractIndicatorTest<KinematicKalmanForecastState, Num> {

    public KinematicKalmanForecastStateTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void stableStateNormalizesEveryValueToThePositionFactory() {
        KinematicKalmanForecastState state = stable(2, 3, 10, 1, 2, -0.5, 3, 0.1, 0.2);

        assertTrue(state.isStable());
        assertEquals(2, state.index());
        assertEquals(3, state.observationCount());
        assertEquals(numFactory.getClass(), state.position().getNumFactory().getClass());
        assertEquals(numFactory.getClass(), state.velocity().getNumFactory().getClass());
        assertEquals(numOf(-0.5), state.positionVelocityCovariance());
    }

    @Test
    public void unstableFactoryUsesOnlyNanValues() {
        KinematicKalmanForecastState state = KinematicKalmanForecastState.unstable(4, 2);

        assertFalse(state.isStable());
        assertEquals(4, state.index());
        assertEquals(2, state.observationCount());
        assertTrue(state.position().isNaN());
        assertTrue(state.measurementNoise().isNaN());
    }

    @Test
    public void rejectsInvalidMetadataAndStableValues() {
        assertThrows(IllegalArgumentException.class, () -> KinematicKalmanForecastState.unstable(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> KinematicKalmanForecastState.unstable(0, -1));
        assertThrows(IllegalArgumentException.class, () -> stable(0, 0, 1, 0, 1, 0, 1, 0.1, 0.2));
        assertThrows(NullPointerException.class, () -> new KinematicKalmanForecastState(0, 1, true, null, numOf(0),
                numOf(1), numOf(0), numOf(1), numOf(0.1), numOf(0.2)));
        assertThrows(IllegalArgumentException.class, () -> KinematicKalmanForecastState.stable(0, 1, NaN.NaN, numOf(0),
                numOf(1), numOf(0), numOf(1), numOf(0.1), numOf(0.2)));
        assertThrows(IllegalArgumentException.class, () -> stable(0, 1, 1, 0, -1, 0, 1, 0.1, 0.2));
        assertThrows(IllegalArgumentException.class, () -> stable(0, 1, 1, 0, 1, 2, 1, 0.1, 0.2));
        assertThrows(IllegalArgumentException.class, () -> stable(0, 1, 1, 0, 1, 0, 1, 0, 0.2));
    }

    @Test
    public void rejectsEveryNonNanFieldOnAnUnstableState() {
        for (int finiteField = 0; finiteField < 7; finiteField++) {
            Num[] values = { NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN, NaN.NaN };
            values[finiteField] = numOf(0);
            assertThrows(IllegalArgumentException.class, () -> new KinematicKalmanForecastState(0, 0, false, values[0],
                    values[1], values[2], values[3], values[4], values[5], values[6]));
        }
    }

    @Test
    public void rejectsValuesThatUnderflowThePositionFactory() {
        Num position = DoubleNumFactory.getInstance().numOf(1);
        Num tinyVelocity = DecimalNumFactory.getInstance(40).numOf("1E-10000");
        Num zero = DoubleNumFactory.getInstance().zero();
        Num one = DoubleNumFactory.getInstance().one();

        assertThrows(IllegalArgumentException.class,
                () -> KinematicKalmanForecastState.stable(0, 1, position, tinyVelocity, one, zero, one,
                        DoubleNumFactory.getInstance().numOf(0.1), DoubleNumFactory.getInstance().numOf(0.2)));
    }

    private KinematicKalmanForecastState stable(int index, int observationCount, Number position, Number velocity,
            Number positionVariance, Number covariance, Number velocityVariance, Number processNoise,
            Number measurementNoise) {
        return KinematicKalmanForecastState.stable(index, observationCount, numOf(position), numOf(velocity),
                numOf(positionVariance), numOf(covariance), numOf(velocityVariance), numOf(processNoise),
                numOf(measurementNoise));
    }
}
