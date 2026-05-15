/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
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
import org.ta4j.core.indicators.aroon.AroonDownIndicator;
import org.ta4j.core.indicators.aroon.AroonOscillatorIndicator;
import org.ta4j.core.indicators.aroon.AroonUpIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.HMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.averages.WMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandWidthIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.bollinger.PercentBIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;
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
    private static final int BROAD_BASELINE_BAR_COUNT = 160;
    private static final int DEFAULT_CORRELATION_WINDOW = 120;
    private static final int BROAD_BASELINE_PARALLELISM = 1;
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
        BarSeries broadBaselineSeries = recentSubSeries(series, BROAD_BASELINE_BAR_COUNT);
        BroadBaselineCatalog broadBaselineCatalog = buildBroadBaselineCatalog(broadBaselineSeries);
        List<AnalysisPass> broadBaselinePasses = analyzeBroadBaseline(broadBaselineSeries,
                broadBaselineCatalog.indicators());

        LOG.info("Curated indicator family analysis: dataset={}, bars={}, indicators={}", SP500_WEEKLY_RESOURCE,
                series.getBarCount(), indicators.size());
        for (AnalysisPass pass : passes) {
            LOG.info("{}", describe(pass));
        }
        LOG.info("Broad baseline catalog: indicators={}, skipped={}", broadBaselineCatalog.indicators().size(),
                String.join("; ", broadBaselineCatalog.skippedIndicators()));
        for (AnalysisPass pass : broadBaselinePasses) {
            LOG.info("Broad baseline summary: {}", describeSummary(pass));
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

    static BarSeries recentSubSeries(BarSeries series, int barCount) {
        int endIndexExclusive = series.getEndIndex() + 1;
        int startIndex = Math.max(series.getBeginIndex(), endIndexExclusive - barCount);
        return series.getSubSeries(startIndex, endIndexExclusive);
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

    static BroadBaselineCatalog buildBroadBaselineCatalog(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator sma20 = new SMAIndicator(close, 20);
        BollingerBandsMiddleIndicator bollingerMiddle = new BollingerBandsMiddleIndicator(sma20);
        StandardDeviationIndicator standardDeviation20 = new StandardDeviationIndicator(close, 20);
        BollingerBandsUpperIndicator bollingerUpper = new BollingerBandsUpperIndicator(bollingerMiddle,
                standardDeviation20);
        BollingerBandsLowerIndicator bollingerLower = new BollingerBandsLowerIndicator(bollingerMiddle,
                standardDeviation20);
        VWAPIndicator vwap20 = new VWAPIndicator(series, 20);

        LinkedHashMap<String, Indicator<Num>> indicators = new LinkedHashMap<>(buildIndicators(series));
        indicators.put("aroonUp25", new AroonUpIndicator(series, 25));
        indicators.put("aroonDown25", new AroonDownIndicator(series, 25));
        indicators.put("bollingerMiddle20", bollingerMiddle);
        indicators.put("bollingerUpper20", bollingerUpper);
        indicators.put("bollingerLower20", bollingerLower);
        indicators.put("bollingerWidth20",
                new BollingerBandWidthIndicator(bollingerUpper, bollingerMiddle, bollingerLower));
        indicators.put("vwap20", vwap20);

        List<String> skippedIndicators = List.of(
                "Boolean signal indicators: candle patterns, crossings, trend flags, Renko direction markers",
                "State indicators: Elliott, MACD-V momentum state, Wyckoff, and ZigZag state outputs",
                "Context-heavy or high-cost numeric indicators: pivot/reversal, support/resistance profile, volume-index, and anchored-session variants");
        return new BroadBaselineCatalog(indicators, skippedIndicators);
    }

    static List<AnalysisPass> analyze(BarSeries series, Map<String, Indicator<Num>> indicators) {
        IndicatorFamilyManager manager = new IndicatorFamilyManager(series);
        List<AnalysisPass> passes = new ArrayList<>(SIMILARITY_THRESHOLDS.size());
        for (double threshold : SIMILARITY_THRESHOLDS) {
            passes.add(new AnalysisPass(threshold, manager.analyze(indicators, threshold)));
        }
        return List.copyOf(passes);
    }

    static List<AnalysisPass> analyzeBroadBaseline(BarSeries series, Map<String, Indicator<Num>> indicators) {
        IndicatorFamilyManager manager = new IndicatorFamilyManager(series, DEFAULT_CORRELATION_WINDOW,
                BROAD_BASELINE_PARALLELISM);
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
                    .append(", representative=")
                    .append(family.representativeIndicatorName())
                    .append(", averageInternalSimilarity=")
                    .append(format(family.averageInternalSimilarity()))
                    .append(", minimumInternalSimilarity=")
                    .append(format(family.minimumInternalSimilarity()))
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

    static String describeSummary(AnalysisPass pass) {
        IndicatorFamilyResult result = pass.result();
        return "threshold=" + String.format(Locale.ROOT, "%.2f", pass.threshold()) + ", stableIndex="
                + result.stableIndex() + ", families=" + result.families().size() + ", weakestFamily="
                + weakestFamily(result.families()) + ", mostSimilarPairs=" + topPairs(result.pairSimilarities());
    }

    private static String weakestFamily(List<IndicatorFamilyResult.Family> families) {
        IndicatorFamilyResult.Family weakest = families.stream()
                .min(Comparator.comparingDouble(family -> family.minimumInternalSimilarity().doubleValue()))
                .orElseThrow();
        return weakest.familyId() + "(" + weakest.indicatorNames().size() + ", min="
                + format(weakest.minimumInternalSimilarity()) + ", representative="
                + weakest.representativeIndicatorName() + ")";
    }

    private static String topPairs(List<IndicatorFamilyResult.PairSimilarity> pairSimilarities) {
        StringJoiner joiner = new StringJoiner("; ");
        pairSimilarities.stream()
                .sorted(Comparator
                        .comparingDouble((IndicatorFamilyResult.PairSimilarity pair) -> pair.similarity().doubleValue())
                        .reversed())
                .limit(TOP_PAIR_COUNT)
                .forEach(pair -> joiner.add(pair.firstIndicatorName() + "/" + pair.secondIndicatorName() + "="
                        + format(pair.similarity()) + " signedAvg=" + format(pair.signedAverageSimilarity())
                        + " latest=" + format(pair.latestSignedSimilarity()) + " samples=" + pair.sampleCount()));
        return joiner.toString();
    }

    private static String format(Num value) {
        return String.format(Locale.ROOT, "%.3f", value.doubleValue());
    }

    record AnalysisPass(double threshold, IndicatorFamilyResult result) {
    }

    record BroadBaselineCatalog(Map<String, Indicator<Num>> indicators, List<String> skippedIndicators) {

        BroadBaselineCatalog {
            indicators = Collections.unmodifiableMap(new LinkedHashMap<>(indicators));
            skippedIndicators = List.copyOf(skippedIndicators);
        }
    }
}
