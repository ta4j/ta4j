/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.zigzag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;

public class ZigZagStateIndicatorTest extends AbstractIndicatorTest<Indicator<ZigZagState>, ZigZagState> {

    private BarSeries series;
    private final Num reversalThreshold = numOf(5.0);

    public ZigZagStateIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
    }

    @Test
    public void shouldInitializeWithUndefinedTrend() {
        series.barBuilder().closePrice(100).add();
        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        final ZigZagState state = indicator.getValue(0);
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.UNDEFINED);
        assertThat(state.getLastHighIndex()).isEqualTo(-1);
        assertThat(state.getLastLowIndex()).isEqualTo(-1);
        assertThat(state.getLastExtremeIndex()).isEqualTo(0);
        assertThat(state.getLastExtremePrice()).isEqualByComparingTo(numOf(100));
    }

    @Test
    public void shouldRejectNullHighPriceBeforeConstructorDelegation() {
        final Indicator<Num> low = new LowPriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);

        final IllegalArgumentException threeArgument = org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new ZigZagStateIndicator(null, low, threshold));
        final IllegalArgumentException fourArgument = org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new ZigZagStateIndicator(null, low, low, threshold));

        assertThat(threeArgument).hasMessage("highPrice must not be null");
        assertThat(fourArgument).hasMessage("highPrice must not be null");
    }

    @Test
    public void shouldDetectUpTrendAfterInitialRise() {
        // Price: 100, 102, 105, 108
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(102).add();
        series.barBuilder().closePrice(105).add();
        series.barBuilder().closePrice(108).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        final ZigZagState state = indicator.getValue(3);
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.UP);
        assertThat(state.getLastExtremeIndex()).isEqualTo(3);
        assertThat(state.getLastExtremePrice()).isEqualByComparingTo(numOf(108));
    }

    @Test
    public void shouldDetectDownTrendAfterInitialFall() {
        // Price: 100, 98, 95, 92
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(98).add();
        series.barBuilder().closePrice(95).add();
        series.barBuilder().closePrice(92).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        final ZigZagState state = indicator.getValue(3);
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.DOWN);
        assertThat(state.getLastExtremeIndex()).isEqualTo(3);
        assertThat(state.getLastExtremePrice()).isEqualByComparingTo(numOf(92));
    }

    @Test
    public void shouldConfirmSwingHighWhenReversalThresholdMet() {
        // Price: 100, 105, 110 (high), 108, 103 (reversal >= 5)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(105).add();
        series.barBuilder().closePrice(110).add(); // high
        series.barBuilder().closePrice(108).add();
        series.barBuilder().closePrice(103).add(); // reversal: 110 - 103 = 7 >= 5

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        final ZigZagState state = indicator.getValue(4);
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.DOWN);
        assertThat(state.getLastHighIndex()).isEqualTo(2);
        assertThat(state.getLastHighPrice()).isEqualByComparingTo(numOf(110));
        assertThat(state.getLastExtremeIndex()).isEqualTo(4);
        assertThat(state.getLastExtremePrice()).isEqualByComparingTo(numOf(103));
    }

    @Test
    public void shouldNotConfirmSwingHighWhenReversalThresholdNotMet() {
        // Price: 100, 105, 110 (high), 108, 107 (reversal: 110 - 107 = 3 < 5)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(105).add();
        series.barBuilder().closePrice(110).add(); // high
        series.barBuilder().closePrice(108).add();
        series.barBuilder().closePrice(107).add(); // reversal: 110 - 107 = 3 < 5

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        final ZigZagState state = indicator.getValue(4);
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.UP);
        assertThat(state.getLastHighIndex()).isEqualTo(-1); // Not confirmed yet
        assertThat(state.getLastExtremeIndex()).isEqualTo(2); // Still tracking the high
        assertThat(state.getLastExtremePrice()).isEqualByComparingTo(numOf(110));
    }

    @Test
    public void shouldConfirmSwingLowWhenReversalThresholdMet() {
        // Price: 100, 95, 90 (low), 92, 97 (reversal >= 5)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(95).add();
        series.barBuilder().closePrice(90).add(); // low
        series.barBuilder().closePrice(92).add();
        series.barBuilder().closePrice(97).add(); // reversal: 97 - 90 = 7 >= 5

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        final ZigZagState state = indicator.getValue(4);
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.UP);
        assertThat(state.getLastLowIndex()).isEqualTo(2);
        assertThat(state.getLastLowPrice()).isEqualByComparingTo(numOf(90));
        assertThat(state.getLastExtremeIndex()).isEqualTo(4);
        assertThat(state.getLastExtremePrice()).isEqualByComparingTo(numOf(97));
    }

    @Test
    public void shouldExtendUpLegWhenPriceContinuesRising() {
        // Price: 100, 105, 110, 115 (extends up-leg)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(105).add();
        series.barBuilder().closePrice(110).add();
        series.barBuilder().closePrice(115).add(); // Extends the up-leg

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        final ZigZagState state = indicator.getValue(3);
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.UP);
        assertThat(state.getLastExtremeIndex()).isEqualTo(3);
        assertThat(state.getLastExtremePrice()).isEqualByComparingTo(numOf(115));
    }

    @Test
    public void shouldExtendDownLegWhenPriceContinuesFalling() {
        // Price: 100, 95, 90, 85 (extends down-leg)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(95).add();
        series.barBuilder().closePrice(90).add();
        series.barBuilder().closePrice(85).add(); // Extends the down-leg

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        final ZigZagState state = indicator.getValue(3);
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.DOWN);
        assertThat(state.getLastExtremeIndex()).isEqualTo(3);
        assertThat(state.getLastExtremePrice()).isEqualByComparingTo(numOf(85));
    }

    @Test
    public void shouldTrackMultipleSwingHighsAndLows() {
        // Price: 100, 110 (high1), 105, 95 (low1), 100, 115 (high2), 110, 90 (low2), 95
        // (reversal)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(110).add(); // high1
        series.barBuilder().closePrice(105).add(); // reversal: 110 - 105 = 5 >= 5
        series.barBuilder().closePrice(95).add(); // low1
        series.barBuilder().closePrice(100).add(); // reversal: 100 - 95 = 5 >= 5
        series.barBuilder().closePrice(115).add(); // high2
        series.barBuilder().closePrice(110).add(); // reversal: 115 - 110 = 5 >= 5
        series.barBuilder().closePrice(90).add(); // low2
        series.barBuilder().closePrice(95).add(); // reversal: 95 - 90 = 5 >= 5

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        // After first swing high
        ZigZagState state = indicator.getValue(2);
        assertThat(state.getLastHighIndex()).isEqualTo(1);
        assertThat(state.getLastHighPrice()).isEqualByComparingTo(numOf(110));

        // After first swing low
        state = indicator.getValue(4);
        assertThat(state.getLastLowIndex()).isEqualTo(3);
        assertThat(state.getLastLowPrice()).isEqualByComparingTo(numOf(95));

        // After second swing high
        state = indicator.getValue(6);
        assertThat(state.getLastHighIndex()).isEqualTo(5);
        assertThat(state.getLastHighPrice()).isEqualByComparingTo(numOf(115));
        assertThat(state.getLastLowIndex()).isEqualTo(3); // Previous low still tracked

        // After second swing low (at index 7, low is not yet confirmed, need reversal)
        state = indicator.getValue(7);
        assertThat(state.getLastLowIndex()).isEqualTo(3); // Still previous low, new one not confirmed yet
        assertThat(state.getLastHighIndex()).isEqualTo(5); // Previous high still tracked

        // After second swing low is confirmed
        state = indicator.getValue(8);
        assertThat(state.getLastLowIndex()).isEqualTo(7);
        assertThat(state.getLastLowPrice()).isEqualByComparingTo(numOf(90));
        assertThat(state.getLastHighIndex()).isEqualTo(5); // Previous high still tracked
    }

    @Test
    public void shouldWorkWithHighPriceIndicator() {
        // High prices: 100, 105, 110, 108, 103
        series.barBuilder().highPrice(100).closePrice(100).add();
        series.barBuilder().highPrice(105).closePrice(105).add();
        series.barBuilder().highPrice(110).closePrice(110).add();
        series.barBuilder().highPrice(108).closePrice(108).add();
        series.barBuilder().highPrice(103).closePrice(103).add();

        final Indicator<Num> price = new HighPriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        final ZigZagState state = indicator.getValue(4);
        assertThat(state.getLastHighIndex()).isEqualTo(2);
        assertThat(state.getLastHighPrice()).isEqualByComparingTo(numOf(110));
    }

    @Test
    public void shouldWorkWithLowPriceIndicator() {
        // Low prices: 100, 95, 90, 92, 97
        series.barBuilder().lowPrice(100).closePrice(100).add();
        series.barBuilder().lowPrice(95).closePrice(95).add();
        series.barBuilder().lowPrice(90).closePrice(90).add();
        series.barBuilder().lowPrice(92).closePrice(92).add();
        series.barBuilder().lowPrice(97).closePrice(97).add();

        final Indicator<Num> price = new LowPriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        final ZigZagState state = indicator.getValue(4);
        assertThat(state.getLastLowIndex()).isEqualTo(2);
        assertThat(state.getLastLowPrice()).isEqualByComparingTo(numOf(90));
    }

    @Test
    public void shouldUseIntrabarHighsAndLowsForPivotsAndReversals() {
        series.barBuilder().highPrice(101).lowPrice(99).closePrice(100).add();
        series.barBuilder().highPrice(111).lowPrice(108).closePrice(109).add();
        series.barBuilder().highPrice(109).lowPrice(104).closePrice(104).add();
        series.barBuilder().highPrice(105).lowPrice(95).closePrice(100).add();

        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(new HighPriceIndicator(series),
                new LowPriceIndicator(series), 5);
        final ZigZagState state = indicator.getValue(2);

        assertThat(state.getLastHighIndex()).isEqualTo(1);
        assertThat(state.getLastHighPrice()).isEqualByComparingTo(numOf(111));
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.DOWN);
        final ZigZagState extendedState = indicator.getValue(3);
        assertThat(extendedState.getLastLowIndex()).isEqualTo(3);
        assertThat(extendedState.getLastLowPrice()).isEqualByComparingTo(numOf(95));
        assertThat(extendedState.getTrend()).isEqualTo(ZigZagTrend.UP);
        assertThat(extendedState.getLastExtremePrice()).isEqualByComparingTo(numOf(105));
    }

    @Test
    public void shouldConfirmSwingHighAfterSameBarHighExtension() {
        series.barBuilder().highPrice(101).lowPrice(99).closePrice(100).add();
        series.barBuilder().highPrice(106).lowPrice(104).closePrice(105).add();
        series.barBuilder().highPrice(112).lowPrice(100).closePrice(105).add();

        final ZigZagState state = new ZigZagStateIndicator(new HighPriceIndicator(series),
                new LowPriceIndicator(series), 5).getValue(2);

        assertThat(state.getLastHighIndex()).isEqualTo(2);
        assertThat(state.getLastHighPrice()).isEqualByComparingTo(numOf(112));
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.DOWN);
    }

    @Test
    public void shouldConfirmSwingLowAfterSameBarLowExtension() {
        series.barBuilder().highPrice(101).lowPrice(99).closePrice(100).add();
        series.barBuilder().highPrice(96).lowPrice(94).closePrice(95).add();
        series.barBuilder().highPrice(102).lowPrice(88).closePrice(95).add();

        final ZigZagState state = new ZigZagStateIndicator(new HighPriceIndicator(series),
                new LowPriceIndicator(series), 5).getValue(2);

        assertThat(state.getLastLowIndex()).isEqualTo(2);
        assertThat(state.getLastLowPrice()).isEqualByComparingTo(numOf(88));
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.UP);
    }

    @Test
    public void shouldWaitForThresholdBeforeChoosingInitialDirection() {
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(102).add();
        series.barBuilder().closePrice(105).add();

        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(new ClosePriceIndicator(series), 5);

        assertThat(indicator.getValue(1).getTrend()).isEqualTo(ZigZagTrend.UNDEFINED);
        assertThat(indicator.getValue(2).getTrend()).isEqualTo(ZigZagTrend.UP);
        assertThat(indicator.getValue(2).getLastLowIndex()).isZero();
    }

    @Test
    public void shouldWaitForUnambiguousDirectionWhenInitialRangeConfirmsBothWays() {
        series.barBuilder().highPrice(110).lowPrice(90).closePrice(100).add();
        series.barBuilder().highPrice(110).lowPrice(90).closePrice(100).add();
        series.barBuilder().highPrice(110).lowPrice(100).closePrice(107).add();

        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(new HighPriceIndicator(series),
                new LowPriceIndicator(series), 5);

        assertThat(indicator.getValue(1).getTrend()).isEqualTo(ZigZagTrend.UNDEFINED);
        assertThat(indicator.getValue(2).getTrend()).isEqualTo(ZigZagTrend.UP);
        assertThat(indicator.getValue(2).getLastLowIndex()).isZero();
    }

    @Test
    public void shouldTrackLatestUnambiguousInitialExtremeDuringWarmUp() {
        series.barBuilder().highPrice(100).lowPrice(90).closePrice(95).add();
        series.barBuilder().highPrice(105).lowPrice(91).closePrice(97).add();
        series.barBuilder().highPrice(104).lowPrice(85).closePrice(93).add();

        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(new HighPriceIndicator(series),
                new LowPriceIndicator(series), 20);

        final ZigZagState extendedHigh = indicator.getValue(1);
        assertThat(extendedHigh.getTrend()).isEqualTo(ZigZagTrend.UNDEFINED);
        assertThat(extendedHigh.getLastExtremeIndex()).isOne();
        assertThat(extendedHigh.getLastExtremePrice()).isEqualByComparingTo(numOf(105));

        final ZigZagState extendedLow = indicator.getValue(2);
        assertThat(extendedLow.getTrend()).isEqualTo(ZigZagTrend.UNDEFINED);
        assertThat(extendedLow.getLastExtremeIndex()).isEqualTo(2);
        assertThat(extendedLow.getLastExtremePrice()).isEqualByComparingTo(numOf(85));
    }

    @Test
    public void shouldPreserveFiniteInitialCandidateWhenOtherSideBecomesAvailable() {
        series.barBuilder().highPrice(110).lowPrice(NaN.NaN).closePrice(100).add();
        series.barBuilder().highPrice(105).lowPrice(95).closePrice(100).add();

        final Indicator<Num> high = new HighPriceIndicator(series);
        final Indicator<Num> low = new LowPriceIndicator(series);
        final ZigZagState state = new ZigZagStateIndicator(high, low, 20).getValue(1);

        assertThat(state.getInitialHighIndex()).isZero();
        assertThat(state.getInitialHighPrice()).isEqualByComparingTo(numOf(110));
        assertThat(state.getInitialLowIndex()).isOne();
        assertThat(state.getInitialLowPrice()).isEqualByComparingTo(numOf(95));
    }

    @Test
    public void shouldSupportDynamicThreshold() {
        // Price: 100, 110, 105 (reversal: 5, threshold: 5)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(110).add();
        series.barBuilder().closePrice(105).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        // Threshold varies: 3, 3, 5
        final Indicator<Num> threshold = new CachedIndicator<Num>(price) {
            @Override
            protected Num calculate(int index) {
                return index < 2 ? numOf(3) : numOf(5);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        // At index 2, threshold is 5, reversal is 5, so it should confirm
        final ZigZagState state = indicator.getValue(2);
        assertThat(state.getLastHighIndex()).isEqualTo(1);
        assertThat(state.getLastHighPrice()).isEqualByComparingTo(numOf(110));
    }

    @Test
    public void shouldAnchorDynamicThresholdAtCandidateExtreme() {
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(110).add();
        series.barBuilder().closePrice(107).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new CachedIndicator<Num>(price) {
            @Override
            protected Num calculate(int index) {
                return index == 2 ? numOf(1) : numOf(5);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        final ZigZagState state = new ZigZagStateIndicator(price, threshold).getValue(2);

        assertThat(state.getLastHighIndex()).isEqualTo(-1);
        assertThat(state.getTrend()).isEqualTo(ZigZagTrend.UP);
    }

    @Test
    public void shouldReturnZeroUnstableBars() {
        series.barBuilder().closePrice(100).add();
        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(0);
    }

    @Test
    public void shouldHandleExactThresholdMatch() {
        // Price: 100, 110, 105 (reversal exactly equals threshold: 5)
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(110).add();
        series.barBuilder().closePrice(105).add(); // reversal: 110 - 105 = 5

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator indicator = new ZigZagStateIndicator(price, threshold);

        final ZigZagState state = indicator.getValue(2);
        assertThat(state.getLastHighIndex()).isEqualTo(1);
        assertThat(state.getLastHighPrice()).isEqualByComparingTo(numOf(110));
    }

    @Test
    public void shouldSerializeToDescriptor() {
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(105).add();
        series.barBuilder().closePrice(110).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator original = new ZigZagStateIndicator(price, threshold);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        final ComponentDescriptor descriptor = original.toDescriptor();
        assertThat(descriptor.getType()).isEqualTo("ZigZagStateIndicator");
        assertThat(descriptor.getComponents()).hasSize(4);
        assertThat(descriptor.getComponents())
                .anySatisfy(component -> component.getType().equals("ClosePriceIndicator"));
        assertThat(descriptor.getComponents()).anySatisfy(component -> component.getType().equals("ConstantIndicator"));
    }

    @Test
    public void shouldSerializeToJson() {
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(105).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator original = new ZigZagStateIndicator(price, threshold);

        final String json = original.toJson();
        assertThat(json).contains("ZigZagStateIndicator");
        assertThat(json).contains("ClosePriceIndicator");
        assertThat(json).contains("ConstantIndicator");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRoundTripSerializeAndDeserialize() {
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(105).add();
        series.barBuilder().closePrice(110).add();
        series.barBuilder().closePrice(108).add();
        series.barBuilder().closePrice(103).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator original = new ZigZagStateIndicator(price, threshold);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        final String json = original.toJson();
        final Indicator<ZigZagState> restored = (Indicator<ZigZagState>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(ZigZagStateIndicator.class);
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());

        // Verify the restored indicator produces the same values
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            final ZigZagState originalState = original.getValue(i);
            final ZigZagState restoredState = restored.getValue(i);
            assertThat(restoredState.getLastHighIndex()).isEqualTo(originalState.getLastHighIndex());
            assertThat(restoredState.getLastLowIndex()).isEqualTo(originalState.getLastLowIndex());
            assertThat(restoredState.getTrend()).isEqualTo(originalState.getTrend());
            if (originalState.getLastHighPrice() != null) {
                assertThat(restoredState.getLastHighPrice()).isEqualByComparingTo(originalState.getLastHighPrice());
            }
            if (originalState.getLastLowPrice() != null) {
                assertThat(restoredState.getLastLowPrice()).isEqualByComparingTo(originalState.getLastLowPrice());
            }
        }
    }

    @Test
    public void shouldNotSerializeTransientStateFields() {
        series.barBuilder().closePrice(100).add();
        series.barBuilder().closePrice(105).add();

        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> threshold = new ConstantIndicator<>(series, reversalThreshold);
        final ZigZagStateIndicator original = new ZigZagStateIndicator(price, threshold);

        // Use the indicator to populate stateful fields
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            original.getValue(i);
        }

        final ComponentDescriptor descriptor = original.toDescriptor();

        // Verify that only constructor parameters (price and reversalAmount indicators)
        // are serialized
        // State fields should not be serialized
        assertThat(descriptor.getParameters()).doesNotContainKey("price");
        assertThat(descriptor.getParameters()).doesNotContainKey("reversalAmount");
        assertThat(descriptor.getComponents()).hasSize(4); // High, low, confirmation, and threshold components
    }
}
