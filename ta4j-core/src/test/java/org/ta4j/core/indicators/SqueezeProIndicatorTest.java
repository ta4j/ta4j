/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.ta4j.core.num.NaN.NaN;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.SqueezeProIndicator.SqueezeLevel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SqueezeProIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private static final int BAR_COUNT = 20;
    private static final double BB_MULTIPLIER = 2.0;
    private static final double KC_HIGH = 1.0;
    private static final double KC_MID = 1.5;
    private static final double KC_LOW = 2.0;

    private static final String AAPL_90BARS_2025_12_05_CSV = """
            Date,Open,High,Low,Close,Volume
            2025-07-31,208.49,209.84,207.16,207.57,80698431
            2025-08-01,210.865,213.58,201.5,202.38,104434473
            2025-08-04,204.505,207.88,201.675,203.35,75109298
            2025-08-05,203.4,205.34,202.16,202.92,44155079
            2025-08-06,205.63,215.38,205.59,213.25,108483103
            2025-08-07,218.875,220.85,216.58,220.03,90224834
            2025-08-08,220.83,231,219.25,229.35,113853967
            2025-08-11,227.92,229.56,224.76,227.18,61806132
            2025-08-12,228.005,230.8,227.07,229.65,55672301
            2025-08-13,231.07,235,230.43,233.33,69878546
            2025-08-14,234.055,235.12,230.85,232.78,51916275
            2025-08-15,234,234.28,229.335,231.59,56038657
            2025-08-18,231.7,233.12,230.11,230.89,37476188
            2025-08-19,231.275,232.87,229.35,230.56,39402564
            2025-08-20,229.98,230.47,225.77,226.01,42263865
            2025-08-21,226.27,226.52,223.7804,224.9,30621249
            2025-08-22,226.17,229.09,225.41,227.76,42477811
            2025-08-25,226.48,229.3,226.23,227.16,30983133
            2025-08-26,226.87,229.49,224.69,229.31,54575107
            2025-08-27,228.61,230.9,228.26,230.49,31259513
            2025-08-28,230.82,233.41,229.335,232.56,38074700
            2025-08-29,232.51,233.38,231.37,232.14,39418437
            2025-09-02,229.25,230.85,226.97,229.72,44075638
            2025-09-03,237.21,238.85,234.36,238.47,66427835
            2025-09-04,238.45,239.8999,236.74,239.78,47549429
            2025-09-05,239.995,241.32,238.4901,239.69,54870397
            2025-09-08,239.3,240.15,236.34,237.88,48999495
            2025-09-09,237,238.7805,233.36,234.35,66313918
            2025-09-10,232.185,232.42,225.95,226.79,83440810
            2025-09-11,226.875,230.45,226.65,230.03,50208578
            2025-09-12,229.22,234.51,229.02,234.07,55824216
            2025-09-15,237,238.19,235.03,236.7,42699524
            2025-09-16,237.175,241.22,236.3235,238.15,63421099
            2025-09-17,238.97,240.1,237.7301,238.99,46508017
            2025-09-18,239.97,241.2,236.65,237.88,44249576
            2025-09-19,241.225,246.3,240.2106,245.5,163741314
            2025-09-22,248.3,256.64,248.12,256.08,105517416
            2025-09-23,255.875,257.34,253.58,254.43,60275187
            2025-09-24,255.22,255.74,251.04,252.31,42303710
            2025-09-25,253.205,257.17,251.712,256.87,55202075
            2025-09-26,254.095,257.6,253.78,255.46,46076258
            2025-09-29,254.56,255,253.01,254.43,40127687
            2025-09-30,254.855,255.919,253.11,254.63,37704259
            2025-10-01,255.04,258.79,254.93,255.45,48713940
            2025-10-02,256.575,258.18,254.15,257.13,42630239
            2025-10-03,254.665,259.24,253.95,258.02,49155614
            2025-10-06,257.99,259.07,255.05,256.69,44664118
            2025-10-07,256.805,257.4,255.43,256.48,31955776
            2025-10-08,256.52,258.52,256.11,258.06,36496895
            2025-10-09,257.805,258,253.14,254.04,38322012
            2025-10-10,254.94,256.38,244,245.27,61999098
            2025-10-13,249.38,249.69,245.56,247.66,38142942
            2025-10-14,246.6,248.845,244.7,247.77,35477986
            2025-10-15,249.485,251.82,247.47,249.34,33893611
            2025-10-16,248.25,249.04,245.13,247.45,39776974
            2025-10-17,248.02,253.38,247.27,252.29,49146961
            2025-10-20,255.885,264.375,255.63,262.24,90483029
            2025-10-21,261.88,265.29,261.83,262.77,46695948
            2025-10-22,262.65,262.85,255.43,258.45,45015254
            2025-10-23,259.94,260.62,258.0101,259.58,32754941
            2025-10-24,261.19,264.13,259.18,262.82,38253717
            2025-10-27,264.88,269.12,264.6501,268.81,44888152
            2025-10-28,268.985,269.89,268.15,269,41534759
            2025-10-29,269.275,271.41,267.11,269.7,51086742
            2025-10-30,271.99,274.14,268.48,271.4,69886534
            2025-10-31,276.99,277.32,269.16,270.37,86167123
            2025-11-03,270.42,270.85,266.25,269.05,50194583
            2025-11-04,268.325,271.486,267.615,270.04,49274846
            2025-11-05,268.61,271.7,266.93,270.14,43683072
            2025-11-06,267.89,273.4,267.89,269.77,51204045
            2025-11-07,269.795,272.29,266.77,268.47,48227365
            2025-11-10,268.96,273.73,267.455,269.43,41312412
            2025-11-11,269.81,275.91,269.8,275.25,46208318
            2025-11-12,275,275.73,271.7,273.47,48397982
            2025-11-13,274.11,276.699,272.09,272.95,49602794
            2025-11-14,271.05,275.96,269.6,272.41,47431331
            2025-11-17,268.815,270.49,265.73,267.46,45018260
            2025-11-18,269.99,270.71,265.32,267.44,45677278
            2025-11-19,265.525,272.21,265.5,268.56,40424492
            2025-11-20,270.83,275.43,265.92,266.25,45823568
            2025-11-21,265.95,273.33,265.67,271.49,59030832
            2025-11-24,270.9,277,270.9,275.92,65585796
            2025-11-25,275.27,280.38,275.25,276.97,46914220
            2025-11-26,276.96,279.53,276.63,277.55,33431423
            2025-11-28,277.26,279,275.9865,278.85,20135620
            2025-12-01,278.01,283.42,276.14,283.1,46587722
            2025-12-02,283,287.4,282.6301,286.19,53669532
            2025-12-03,286.2,288.62,283.3,284.15,43538687
            2025-12-04,284.095,284.73,278.59,280.7,43989056
            2025-12-05,280.54,281.14,278.05,278.78,47265845
            """;

    private static final String NEE_90BARS_2025_12_05_CSV = """
            Date,Open,High,Low,Close,Volume
            2025-07-31,70.41,71.2162,69.685,71.06,8845220
            2025-08-01,71.36,71.79,70.34,70.4,6628715
            2025-08-04,70.5,71.205,70.18,70.53,6543554
            2025-08-05,70.86,71.935,70.655,71.18,7178889
            2025-08-06,71.51,71.51,70.49,70.54,7720218
            2025-08-07,70.67,72.66,70.67,72.58,7418564
            2025-08-08,72.58,73,72.2302,72.41,5575995
            2025-08-11,72.785,73.015,71.12,72.45,8420031
            2025-08-12,72.65,72.78,71.14,71.86,8671482
            2025-08-13,71.95,72.31,71.2,72.3,8122597
            2025-08-14,71.85,72.51,71.3501,72.24,6780960
            2025-08-15,72.23,76.695,71.78,75.41,23398404
            2025-08-18,76.11,77.31,75.31,75.72,12568355
            2025-08-19,75.855,76.575,75.45,76.51,8047317
            2025-08-20,76.65,77.26,75.83,76.18,11012036
            2025-08-21,76.47,77.42,75.585,76.08,8637112
            2025-08-22,76.65,77.09,75.425,76.32,13129288
            2025-08-25,75.9,76.2,75.11,75.32,6338079
            2025-08-26,75.38,75.75,74.43,74.84,7162661
            2025-08-27,74.92,75.01,73.4,73.89,10119019
            2025-08-28,73.4,73.48,71.9,72.09,10917220
            2025-08-29,72.73,72.83,71.82,72.05,11280520
            2025-09-02,72.27,72.66,71.49,72.65,8661508
            2025-09-03,72.14,72.6277,71.3557,71.63,7722299
            2025-09-04,72.05,72.5,70.16,70.87,14596843
            2025-09-05,71.25,71.73,70.255,70.9,10067583
            2025-09-08,70.83,70.84,69.24,69.77,12596529
            2025-09-09,69.685,70.315,69.54,70.07,7294486
            2025-09-10,70.095,71.33,69.62,71.04,9254413
            2025-09-11,70.975,71.44,70.51,71.32,7418268
            2025-09-12,71.15,72.49,70.81,71.64,9345589
            2025-09-15,71.58,72.2,71.3,71.5,10399059
            2025-09-16,71.29,71.54,69.765,69.83,7930212
            2025-09-17,70.28,71.01,70.1,70.31,10799593
            2025-09-18,70,71.0851,69.3652,70.79,8488453
            2025-09-19,70.9,71.4,70.37,71.08,14819314
            2025-09-22,71.4,72.54,71.27,72.35,9856721
            2025-09-23,72.1,72.62,71.725,72.32,7795855
            2025-09-24,72.23,73.88,72.0508,73.83,11281752
            2025-09-25,74.155,74.91,73.59,74.65,12916529
            2025-09-26,74.79,75.86,74.63,75.85,8271468
            2025-09-29,75.53,76.29,75.27,76.21,10365967
            2025-09-30,76.46,76.7,75.12,75.49,9392710
            2025-10-01,76.325,78.7,76.22,78.67,15044766
            2025-10-02,78.515,78.89,77.535,78.18,9266552
            2025-10-03,78.45,81.365,77.65,80.06,12116576
            2025-10-06,80.145,82.365,80.145,82.11,15767639
            2025-10-07,83.01,84.61,82.41,83.21,16109689
            2025-10-08,83.755,84.39,82.8707,84.04,13775573
            2025-10-09,84.46,84.82,83.45,83.71,7486115
            2025-10-10,83.9,84.83,83.195,83.35,10285599
            2025-10-13,83.125,85.03,83.0095,84.3,8424314
            2025-10-14,84.2,84.84,83.43,84.64,8912866
            2025-10-15,85.07,86.49,84.83,85.79,9268628
            2025-10-16,86.24,86.74,84.975,85.05,8333744
            2025-10-17,85.45,85.82,84.17,84.53,9142163
            2025-10-20,85.205,85.34,84.31,84.77,5225666
            2025-10-21,85.08,85.11,83.03,83.99,7282198
            2025-10-22,84.07,84.22,82.47,82.84,9208885
            2025-10-23,83.745,83.89,82.57,83.25,5630797
            2025-10-24,83.585,84.7758,83.36,84.41,6249998
            2025-10-27,83.985,87.29,83.79,86.03,18741036
            2025-10-28,87.52,87.53,82.91,83.57,13998070
            2025-10-29,83.305,83.7912,81.045,81.76,11681472
            2025-10-30,81.815,82.5808,81.44,81.64,7620145
            2025-10-31,81.35,82.025,80.8617,81.4,9407303
            2025-11-03,81.3,82.095,80.6,81.78,8508152
            2025-11-04,81.78,82.205,80.8,81.69,6474545
            2025-11-05,81.5,82.63,81.11,82.14,7290988
            2025-11-06,82.1,82.965,81.99,82,6212724
            2025-11-07,81.91,84,81.62,83.93,9167105
            2025-11-10,84.285,85.465,83.69,84.77,11701677
            2025-11-11,84.69,85.83,84.55,85.76,11733862
            2025-11-12,85.55,86.255,85.01,85.89,7604779
            2025-11-13,85.75,85.93,83.93,83.99,11859265
            2025-11-14,83.58,84.49,82.5301,83.88,8352489
            2025-11-17,83.69,85.77,83.69,85.75,11607949
            2025-11-18,86.14,86.69,84.62,84.64,11061206
            2025-11-19,85.03,85.34,83.92,84.27,11138730
            2025-11-20,84.76,86.49,84.2,84.3,10966990
            2025-11-21,83.89,84.67,81.6405,83.48,13930126
            2025-11-24,83.67,84.79,83.01,84.23,13291482
            2025-11-25,84.615,85.34,84.18,84.83,7916846
            2025-11-26,85.2,85.69,84.69,85.54,6550698
            2025-11-28,85.435,86.47,85.15,86.29,4123323
            2025-12-01,85.59,85.67,84.39,84.65,7987007
            2025-12-02,84.95,85.28,84.2535,84.58,7086954
            2025-12-03,84.68,85.97,84.5,84.95,9615114
            2025-12-04,84.79,84.81,82.74,83.39,12489644
            2025-12-05,83.545,84.195,83.06,83.13,8194564
            """;

    public SqueezeProIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesLazyBearMomentumAndSqueezeLevels() {
        BarSeries series = buildReferenceSeries();
        var indicator = new SqueezeProIndicator(series, BAR_COUNT, BB_MULTIPLIER, KC_HIGH, KC_MID, KC_LOW);

        ReferenceValues reference = computeReference(series);

        for (int i = 0; i < BAR_COUNT - 1; i++) {
            assertThat(indicator.getValue(i).isNaN()).isTrue();
            assertEquals(SqueezeLevel.NONE, indicator.getSqueezeLevel(i));
        }

        for (int i = BAR_COUNT - 1; i < series.getBarCount(); i++) {
            Num expected = reference.momentumValues().get(i);
            Num actual = indicator.getValue(i);
            assertThat(actual.minus(expected).abs().doubleValue()).isLessThan(1e-9);
            assertEquals("Unexpected squeeze level at index " + i, reference.squeezeLevels().get(i),
                    indicator.getSqueezeLevel(i));
        }
    }

    @Test
    public void isInSqueezeReflectsCompressionLevels() {
        BarSeries series = buildReferenceSeries();
        var indicator = new SqueezeProIndicator(series, BAR_COUNT, BB_MULTIPLIER, KC_HIGH, KC_MID, KC_LOW);

        assertThat(indicator.isInSqueeze(BAR_COUNT)).isTrue(); // high squeeze
        assertThat(indicator.getSqueezeLevel(BAR_COUNT + 11)).isEqualTo(SqueezeLevel.MID);
        assertThat(indicator.isInSqueeze(BAR_COUNT + 12)).isTrue();
        assertThat(indicator.getSqueezeLevel(BAR_COUNT + 12)).isEqualTo(SqueezeLevel.LOW);
        assertThat(indicator.isInSqueeze(BAR_COUNT + 13)).isFalse();
    }

    @Test
    public void matchesTradingViewLazyBearAaplOn20251205() {
        LoadedSeries loaded = loadDailySeriesFromCsv("AAPL 90 bars ending 2025-12-05", AAPL_90BARS_2025_12_05_CSV);
        int index = loaded.indexOf(LocalDate.parse("2025-12-05"));

        var indicator = new SqueezeProIndicator(loaded.series(), BAR_COUNT);

        assertThat(indicator.getSqueezeLevel(index)).isEqualTo(SqueezeLevel.NONE);
        assertThat(indicator.getValue(index).minus(numFactory.numOf(5.21003)).abs().doubleValue()).isLessThan(1e-6);
    }

    @Test
    public void matchesTradingViewLazyBearNeeOn20251205() {
        LoadedSeries loaded = loadDailySeriesFromCsv("NEE 90 bars ending 2025-12-05", NEE_90BARS_2025_12_05_CSV);
        int index = loaded.indexOf(LocalDate.parse("2025-12-05"));

        var indicator = new SqueezeProIndicator(loaded.series(), BAR_COUNT);

        assertThat(indicator.getSqueezeLevel(index)).isEqualTo(SqueezeLevel.HIGH);
        assertThat(indicator.getValue(index).minus(numFactory.numOf(0.2166785714)).abs().doubleValue())
                .isLessThan(1e-6);
        assertThat(indicator.getValue(index).isGreaterThan(numFactory.zero())).isTrue();
    }

    private BarSeries buildReferenceSeries() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < BAR_COUNT; i++) {
            series.barBuilder().openPrice(100).highPrice(100.2).lowPrice(99.8).closePrice(100).add();
        }

        double[] alternating = new double[] { 100.5, 99.5, 100.5, 99.5, 100.5, 99.5, 100.5, 99.5, 100.5, 99.5 };
        for (double close : alternating) {
            series.barBuilder().openPrice(close).highPrice(close + 0.2).lowPrice(close - 0.2).closePrice(close).add();
        }

        for (int i = 0; i < 10; i++) {
            double close = 100.5 + i * 1.2;
            series.barBuilder().openPrice(close).highPrice(close + 0.2).lowPrice(close - 0.2).closePrice(close).add();
        }

        return series;
    }

    private ReferenceValues computeReference(BarSeries series) {
        int size = series.getBarCount();
        List<Num> closes = new ArrayList<>(size);
        List<Num> highs = new ArrayList<>(size);
        List<Num> lows = new ArrayList<>(size);
        List<Num> trueRanges = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Bar bar = series.getBar(i);
            Num high = bar.getHighPrice();
            Num low = bar.getLowPrice();
            Num close = bar.getClosePrice();
            closes.add(close);
            highs.add(high);
            lows.add(low);
            if (i == 0) {
                trueRanges.add(high.minus(low).abs());
            } else {
                Num prevClose = series.getBar(i - 1).getClosePrice();
                Num hl = high.minus(low).abs();
                Num hc = high.minus(prevClose).abs();
                Num lc = prevClose.minus(low).abs();
                trueRanges.add(hl.max(hc).max(lc));
            }
        }

        List<Num> closeSma = rollingAverage(closes);
        List<Num> closeStdDev = rollingStandardDeviation(closes, closeSma);
        List<Num> trSma = rollingAverage(trueRanges);
        List<Num> highestHigh = rollingHighest(highs);
        List<Num> lowestLow = rollingLowest(lows);

        List<Num> detrended = new ArrayList<>(size);
        Num two = numFactory.numOf(2);
        for (int i = 0; i < size; i++) {
            Num averageHighLow = highestHigh.get(i).plus(lowestLow.get(i)).dividedBy(two);
            Num averageHighLowAndSma = averageHighLow.plus(closeSma.get(i)).dividedBy(two);
            detrended.add(closes.get(i).minus(averageHighLowAndSma));
        }

        List<Num> momentumValues = new ArrayList<>(size);
        List<SqueezeLevel> squeezeLevels = new ArrayList<>(size);
        Num bbMultiplier = numFactory.numOf(BB_MULTIPLIER);
        Num kcHigh = numFactory.numOf(KC_HIGH);
        Num kcMid = numFactory.numOf(KC_MID);
        Num kcLow = numFactory.numOf(KC_LOW);

        for (int i = 0; i < size; i++) {
            if (i < BAR_COUNT - 1) {
                squeezeLevels.add(SqueezeLevel.NONE);
                momentumValues.add(NaN);
                continue;
            }

            Num basis = closeSma.get(i);
            Num bbUpper = basis.plus(closeStdDev.get(i).multipliedBy(bbMultiplier));
            Num bbLower = basis.minus(closeStdDev.get(i).multipliedBy(bbMultiplier));

            squeezeLevels.add(determineLevel(bbLower, bbUpper, basis, trSma.get(i), kcHigh, kcMid, kcLow));
            momentumValues.add(linearRegression(detrended, i));
        }

        return new ReferenceValues(momentumValues, squeezeLevels);
    }

    private List<Num> rollingAverage(List<Num> values) {
        List<Num> averages = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            int start = Math.max(0, i - BAR_COUNT + 1);
            Num sum = numFactory.zero();
            for (int j = start; j <= i; j++) {
                sum = sum.plus(values.get(j));
            }
            averages.add(sum.dividedBy(numFactory.numOf(i - start + 1)));
        }
        return averages;
    }

    private List<Num> rollingStandardDeviation(List<Num> values, List<Num> averages) {
        List<Num> stdDeviations = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            int start = Math.max(0, i - BAR_COUNT + 1);
            Num mean = averages.get(i);
            Num variance = numFactory.zero();
            for (int j = start; j <= i; j++) {
                Num delta = values.get(j).minus(mean);
                variance = variance.plus(delta.pow(2));
            }
            Num samples = numFactory.numOf(i - start + 1);
            stdDeviations.add(variance.dividedBy(samples).sqrt());
        }
        return stdDeviations;
    }

    private List<Num> rollingHighest(List<Num> values) {
        List<Num> highest = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            int start = Math.max(0, i - BAR_COUNT + 1);
            Num candidate = values.get(start);
            for (int j = start + 1; j <= i; j++) {
                Num next = values.get(j);
                if (next.isGreaterThan(candidate)) {
                    candidate = next;
                }
            }
            highest.add(candidate);
        }
        return highest;
    }

    private List<Num> rollingLowest(List<Num> values) {
        List<Num> lowest = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            int start = Math.max(0, i - BAR_COUNT + 1);
            Num candidate = values.get(start);
            for (int j = start + 1; j <= i; j++) {
                Num next = values.get(j);
                if (next.isLessThan(candidate)) {
                    candidate = next;
                }
            }
            lowest.add(candidate);
        }
        return lowest;
    }

    private Num linearRegression(List<Num> values, int index) {
        int start = Math.max(0, index - BAR_COUNT + 1);
        int observations = index - start + 1;
        if (observations < 2) {
            return NaN;
        }
        Num sumX = numFactory.zero();
        Num sumY = numFactory.zero();
        for (int i = start; i <= index; i++) {
            sumX = sumX.plus(numFactory.numOf(i));
            sumY = sumY.plus(values.get(i));
        }

        Num n = numFactory.numOf(observations);
        Num meanX = sumX.dividedBy(n);
        Num meanY = sumY.dividedBy(n);

        Num xx = numFactory.zero();
        Num xy = numFactory.zero();
        for (int i = start; i <= index; i++) {
            Num x = numFactory.numOf(i);
            Num xDiff = x.minus(meanX);
            Num yDiff = values.get(i).minus(meanY);
            xx = xx.plus(xDiff.pow(2));
            xy = xy.plus(xDiff.multipliedBy(yDiff));
        }
        Num slope = xy.dividedBy(xx);
        Num intercept = meanY.minus(slope.multipliedBy(meanX));
        return slope.multipliedBy(numFactory.numOf(index)).plus(intercept);
    }

    private SqueezeLevel determineLevel(Num bbLower, Num bbUpper, Num basis, Num trAverage, Num kcHigh, Num kcMid,
            Num kcLow) {
        Num upperHigh = basis.plus(trAverage.multipliedBy(kcHigh));
        Num lowerHigh = basis.minus(trAverage.multipliedBy(kcHigh));
        if (bbLower.isGreaterThan(lowerHigh) && bbUpper.isLessThan(upperHigh)) {
            return SqueezeLevel.HIGH;
        }

        Num upperMid = basis.plus(trAverage.multipliedBy(kcMid));
        Num lowerMid = basis.minus(trAverage.multipliedBy(kcMid));
        if (bbLower.isGreaterThan(lowerMid) && bbUpper.isLessThan(upperMid)) {
            return SqueezeLevel.MID;
        }

        Num upperLow = basis.plus(trAverage.multipliedBy(kcLow));
        Num lowerLow = basis.minus(trAverage.multipliedBy(kcLow));
        if (bbLower.isGreaterThan(lowerLow) && bbUpper.isLessThan(upperLow)) {
            return SqueezeLevel.LOW;
        }
        return SqueezeLevel.NONE;
    }

    private static final class ReferenceValues {
        private final List<Num> momentumValues;
        private final List<SqueezeLevel> squeezeLevels;

        private ReferenceValues(List<Num> momentumValues, List<SqueezeLevel> squeezeLevels) {
            this.momentumValues = momentumValues;
            this.squeezeLevels = squeezeLevels;
        }

        List<Num> momentumValues() {
            return momentumValues;
        }

        List<SqueezeLevel> squeezeLevels() {
            return squeezeLevels;
        }
    }

    private record LoadedSeries(BarSeries series, Map<LocalDate, Integer> indexByDate) {

        int indexOf(LocalDate date) {
            Integer index = indexByDate.get(date);
            if (index == null) {
                throw new IllegalArgumentException("No bar found for " + date);
            }
            return index;
        }
    }

    private LoadedSeries loadDailySeriesFromCsv(String seriesName, String csv) {
        BarSeries series = new MockBarSeriesBuilder().withName(seriesName).withNumFactory(numFactory).build();
        Map<LocalDate, Integer> indexByDate = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            String line = reader.readLine(); // header
            if (line == null) {
                throw new IllegalArgumentException("Empty CSV test dataset: " + seriesName);
            }

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length < 6) {
                    throw new IllegalArgumentException("Invalid CSV line: " + line);
                }

                LocalDate date = LocalDate.parse(parts[0]);
                double open = Double.parseDouble(parts[1]);
                double high = Double.parseDouble(parts[2]);
                double low = Double.parseDouble(parts[3]);
                double close = Double.parseDouble(parts[4]);
                double volume = Double.parseDouble(parts[5]);

                series.barBuilder()
                        .timePeriod(Duration.ofDays(1))
                        .endTime(date.atStartOfDay().toInstant(ZoneOffset.UTC))
                        .openPrice(open)
                        .highPrice(high)
                        .lowPrice(low)
                        .closePrice(close)
                        .volume(volume)
                        .add();

                indexByDate.put(date, series.getEndIndex());
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading CSV test dataset: " + seriesName, e);
        }

        return new LoadedSeries(series, indexByDate);
    }
}
