/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.IndicatorFamilyManager;
import org.ta4j.core.indicators.IndicatorFamilyResult;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.PPOIndicator;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.StochasticOscillatorDIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.WilliamsRIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.aroon.AroonOscillatorIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.HMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.averages.WMAIndicator;
import org.ta4j.core.indicators.bollinger.PercentBIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;
import org.ta4j.core.num.Num;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Demonstrates indicator-family analysis over an ossified S&P 500 weekly data
 * set.
 * <p>
 * The demo builds a deliberately mixed indicator catalog, runs the same catalog
 * through several similarity thresholds, and logs how broad groupings at lower
 * thresholds split into tighter near-duplicate families at higher thresholds.
 *
 * @since 0.22.7
 */
public final class IndicatorFamilyAnalysisDemo {

    static final String SP500_WEEKLY_RESOURCE = "YahooFinance-SP500-PT7D-19500103_20260310.json";
    static final List<Double> SIMILARITY_THRESHOLDS = List.of(0.80, 0.90, 0.97);

    private static final Logger LOG = LogManager.getLogger(IndicatorFamilyAnalysisDemo.class);
    private static final int DEFAULT_ANALYSIS_BAR_COUNT = 780;
    private static final int TOP_PAIR_COUNT = 8;

    private IndicatorFamilyAnalysisDemo() {
    }

    /**
     * Runs the analysis demo.
     *
     * @param args ignored
     * @since 0.22.7
     */
    public static void main(String[] args) {
        BarSeries series = loadSeries();
        Map<String, Indicator<Num>> indicators = buildIndicators(series);
        List<AnalysisPass> passes = analyze(series, indicators);

        LOG.info("Indicator family analysis: dataset={}, bars={}, indicators={}", SP500_WEEKLY_RESOURCE,
                series.getBarCount(), indicators.size());
        for (AnalysisPass pass : passes) {
            LOG.info("{}", describe(pass));
        }
    }

    static BarSeries loadSeries() {
        try (InputStream resourceStream = IndicatorFamilyAnalysisDemo.class.getClassLoader()
                .getResourceAsStream(SP500_WEEKLY_RESOURCE)) {
            if (resourceStream == null) {
                throw new IllegalStateException("Classpath resource not found: " + SP500_WEEKLY_RESOURCE);
            }
            BarSeries loadedSeries = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(resourceStream);
            if (loadedSeries == null || loadedSeries.isEmpty()) {
                throw new IllegalStateException("Unable to load non-empty series from: " + SP500_WEEKLY_RESOURCE);
            }
            int endIndexExclusive = loadedSeries.getEndIndex() + 1;
            int startIndex = Math.max(loadedSeries.getBeginIndex(), endIndexExclusive - DEFAULT_ANALYSIS_BAR_COUNT);
            return loadedSeries.getSubSeries(startIndex, endIndexExclusive);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to close resource stream for: " + SP500_WEEKLY_RESOURCE, exception);
        }
    }

    static Map<String, Indicator<Num>> buildIndicators(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        HighPriceIndicator high = new HighPriceIndicator(series);
        LowPriceIndicator low = new LowPriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);
        TypicalPriceIndicator typicalPrice = new TypicalPriceIndicator(series);
        StochasticOscillatorKIndicator stochasticK = new StochasticOscillatorKIndicator(series, 14);

        LinkedHashMap<String, Indicator<Num>> indicators = new LinkedHashMap<>();
        indicators.put("close", close);
        indicators.put("high", high);
        indicators.put("low", low);
        indicators.put("volume", volume);
        indicators.put("typicalPrice", typicalPrice);
        indicators.put("sma10", new SMAIndicator(close, 10));
        indicators.put("sma20", new SMAIndicator(close, 20));
        indicators.put("sma50", new SMAIndicator(close, 50));
        indicators.put("ema12", new EMAIndicator(close, 12));
        indicators.put("ema26", new EMAIndicator(close, 26));
        indicators.put("wma20", new WMAIndicator(close, 20));
        indicators.put("hma20", new HMAIndicator(close, 20));
        indicators.put("rsi14", new RSIIndicator(close, 14));
        indicators.put("stochasticK14", stochasticK);
        indicators.put("stochasticD14", new StochasticOscillatorDIndicator(stochasticK));
        indicators.put("macd12_26", new MACDIndicator(close, 12, 26));
        indicators.put("ppo12_26", new PPOIndicator(close, 12, 26));
        indicators.put("roc20", new ROCIndicator(close, 20));
        indicators.put("cci20", new CCIIndicator(series, 20));
        indicators.put("williamsR14", new WilliamsRIndicator(series, 14));
        indicators.put("atr14", new ATRIndicator(series, 14));
        indicators.put("adx14", new ADXIndicator(series, 14));
        indicators.put("aroonOscillator25", new AroonOscillatorIndicator(series, 25));
        indicators.put("percentB20", new PercentBIndicator(close, 20, 2.0));
        indicators.put("standardDeviation20", new StandardDeviationIndicator(close, 20));
        indicators.put("moneyFlowIndex14", new MoneyFlowIndexIndicator(series, 14));
        indicators.put("onBalanceVolume", new OnBalanceVolumeIndicator(series));
        return indicators;
    }

    static List<AnalysisPass> analyze(BarSeries series, Map<String, Indicator<Num>> indicators) {
        IndicatorFamilyManager manager = new IndicatorFamilyManager(series);
        List<AnalysisPass> passes = new ArrayList<>(SIMILARITY_THRESHOLDS.size());
        for (double threshold : SIMILARITY_THRESHOLDS) {
            passes.add(new AnalysisPass(threshold, manager.analyze(indicators, threshold)));
        }
        return List.copyOf(passes);
    }

    static String describe(AnalysisPass pass) {
        IndicatorFamilyResult result = pass.result();
        StringBuilder builder = new StringBuilder();
        builder.append("threshold=")
                .append(String.format(Locale.ROOT, "%.2f", pass.threshold()))
                .append(", stableIndex=")
                .append(result.stableIndex())
                .append(", families=")
                .append(result.families().size())
                .append(System.lineSeparator())
                .append("Families:")
                .append(System.lineSeparator());
        for (IndicatorFamilyResult.Family family : result.families()) {
            builder.append("  ")
                    .append(family.familyId())
                    .append(" (")
                    .append(family.indicatorNames().size())
                    .append("): ")
                    .append(String.join(", ", family.indicatorNames()))
                    .append(System.lineSeparator());
        }
        builder.append("Most similar pairs: ")
                .append(topPairs(result.pairSimilarities()))
                .append(System.lineSeparator())
                .append("Interpretation: lower thresholds reveal broad behavior families; higher thresholds keep only the tightest substitutes together.");
        return builder.toString();
    }

    private static String topPairs(List<IndicatorFamilyResult.PairSimilarity> pairSimilarities) {
        StringJoiner joiner = new StringJoiner("; ");
        pairSimilarities.stream()
                .sorted(Comparator.comparingDouble(IndicatorFamilyResult.PairSimilarity::similarity).reversed())
                .limit(TOP_PAIR_COUNT)
                .forEach(pair -> joiner.add(pair.firstIndicatorName() + "/" + pair.secondIndicatorName() + "="
                        + String.format(Locale.ROOT, "%.3f", pair.similarity())));
        return joiner.toString();
    }

    record AnalysisPass(double threshold, IndicatorFamilyResult result) {
    }
}
