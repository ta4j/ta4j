/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.aroon;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AroonFacadeTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BaseBarSeries data;

    public AroonFacadeTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void init() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withName("Aroon data").build();
        Instant now = Instant.now();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(1)))
                .openPrice(168.28)
                .closePrice(169.87)
                .highPrice(167.15)
                .lowPrice(169.64)
                .volume(0)
                .add();

        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(2))))
                .openPrice(168.84)
                .closePrice(169.36)
                .highPrice(168.20)
                .lowPrice(168.71)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(3))))
                .openPrice(168.88)
                .closePrice(169.29)
                .highPrice(166.41)
                .lowPrice(167.74)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(4))))
                .openPrice(168.00)
                .closePrice(168.38)
                .highPrice(166.18)
                .lowPrice(166.32)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(5))))
                .openPrice(166.89)
                .closePrice(167.70)
                .highPrice(166.33)
                .lowPrice(167.24)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(6))))
                .openPrice(165.25)
                .closePrice(168.43)
                .highPrice(165)
                .lowPrice(168.05)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(7))))
                .openPrice(168.17)
                .closePrice(170.18)
                .highPrice(167.63)
                .lowPrice(169.92)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime((now.plus(Duration.ofDays(8))))
                .openPrice(170.42)
                .closePrice(172.15)
                .highPrice(170.06)
                .lowPrice(171.97)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(9)))
                .openPrice(172.41)
                .closePrice(172.92)
                .highPrice(171.31)
                .lowPrice(172.02)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(10)))
                .openPrice(171.2)
                .closePrice(172.39)
                .highPrice(169.55)
                .lowPrice(170.72)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(11)))
                .openPrice(170.91)
                .closePrice(172.48)
                .highPrice(169.57)
                .lowPrice(172.09)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(12)))
                .openPrice(171.8)
                .closePrice(173.31)
                .highPrice(170.27)
                .lowPrice(173.21)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(13)))
                .openPrice(173.09)
                .closePrice(173.49)
                .highPrice(170.8)
                .lowPrice(170.95)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(14)))
                .openPrice(172.41)
                .closePrice(173.89)
                .highPrice(172.2)
                .lowPrice(173.51)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(15)))
                .openPrice(173.87)
                .closePrice(174.17)
                .highPrice(175)
                .lowPrice(96)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(16)))
                .openPrice(173)
                .closePrice(173.17)
                .highPrice(172.06)
                .lowPrice(173.05)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(17)))
                .openPrice(172.26)
                .closePrice(172.28)
                .highPrice(170.5)
                .lowPrice(170.96)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(18)))
                .openPrice(170.88)
                .closePrice(172.34)
                .highPrice(170.26)
                .lowPrice(171.64)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(19)))
                .openPrice(171.85)
                .closePrice(172.07)
                .highPrice(169.34)
                .lowPrice(170.01)
                .volume(0)
                .add();
        data.barBuilder()
                .endTime(now.plus(Duration.ofDays(20)))
                .openPrice(170.75)
                .closePrice(172.56)
                .highPrice(170.36)
                .lowPrice(172.52)
                .volume(0)
                .add(); // FB, daily, 9.19.'17

    }

    @Test
    public void testCreation() {
        final AroonFacade facade = new AroonFacade(data, 5);
        assertEquals(data, facade.down().getBarSeries());
    }

    @Test
    public void testNumericFacadesSameAsDefaultIndicators() {
        final AroonDownIndicator aroonDownIndicator = new AroonDownIndicator(data, 5);
        final AroonUpIndicator aroonUpIndicator = new AroonUpIndicator(data, 5);
        final AroonOscillatorIndicator aroonOscillatorIndicator = new AroonOscillatorIndicator(data, 5);

        final AroonFacade facade = new AroonFacade(data, 5);
        final NumericIndicator aroonUpNumeric = facade.up();
        final NumericIndicator aroonDownNumeric = facade.down();
        final NumericIndicator oscillatorNumeric = facade.oscillator();

        for (int i = data.getBeginIndex(); i <= data.getEndIndex(); i++) {
            assertNumEquals(aroonDownIndicator.getValue(i), aroonDownNumeric.getValue(i));
            assertNumEquals(aroonUpIndicator.getValue(i), aroonUpNumeric.getValue(i));
            assertNumEquals(aroonOscillatorIndicator.getValue(i), oscillatorNumeric.getValue(i));
        }
    }
}
