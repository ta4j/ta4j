/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.confluence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.confluence.ConfluenceReport;
import org.ta4j.core.analysis.confluence.ConfluenceScoringEngine;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.adx.MinusDIIndicator;
import org.ta4j.core.indicators.adx.PlusDIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandWidthIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.bollinger.PercentBIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.TrendLineSegment;
import org.ta4j.core.indicators.supportresistance.BounceCountResistanceIndicator;
import org.ta4j.core.indicators.supportresistance.BounceCountSupportIndicator;
import org.ta4j.core.indicators.supportresistance.PriceClusterResistanceIndicator;
import org.ta4j.core.indicators.supportresistance.PriceClusterSupportIndicator;
import org.ta4j.core.indicators.supportresistance.TrendLineResistanceIndicator;
import org.ta4j.core.indicators.supportresistance.TrendLineSupportIndicator;
import org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Generates a Phase-1 {@link ConfluenceReport} from a single daily
 * {@link BarSeries} using built-in ta4j indicators.
 *
 * <p>
 * This generator is intentionally heuristic and deterministic. It provides a
 * practical bridge between the core confluence contract and runnable examples
 * while the walk-forward calibration pipeline is under active development.
 *
 * @since 0.22.3
 */
public final class ConfluenceReportGenerator {

    private static final int MINIMUM_BAR_COUNT = 260;
    private static final int STRUCTURE_LOOKBACK = 252;
    private static final double EPSILON = 1.0e-9d;

    private final ConfluenceScoringEngine scoringEngine;
    private final LevelConfidenceCalculator levelConfidenceCalculator;
    private final Clock clock;

    /**
     * Creates a generator with default family-cap and correlation-penalty policy.
     *
     * @since 0.22.3
     */
    public ConfluenceReportGenerator() {
        this(defaultScoringEngine(), new LevelConfidenceCalculator());
    }

    /**
     * Creates a generator with a custom clock and default scoring/level-confidence.
     * Useful for tests that need deterministic data-confidence from fixed
     * historical fixtures.
     *
     * @param clock reference instant for "now" (e.g. for data freshness)
     * @since 0.22.3
     */
    public ConfluenceReportGenerator(Clock clock) {
        this(defaultScoringEngine(), new LevelConfidenceCalculator(), clock);
    }

    /**
     * Creates a generator with injected scoring and level-confidence components.
     *
     * @param scoringEngine             confluence scoring engine
     * @param levelConfidenceCalculator level confidence scorer
     * @since 0.22.3
     */
    public ConfluenceReportGenerator(ConfluenceScoringEngine scoringEngine,
            LevelConfidenceCalculator levelConfidenceCalculator) {
        this(scoringEngine, levelConfidenceCalculator, Clock.systemUTC());
    }

    /**
     * Creates a generator with injected scoring, level-confidence, and clock. The
     * clock is used when computing data freshness for confidence so that tests can
     * obtain deterministic results with fixed historical fixtures.
     *
     * @param scoringEngine             confluence scoring engine
     * @param levelConfidenceCalculator level confidence scorer
     * @param clock                     reference instant for "now" (e.g. for data
     *                                  freshness)
     * @since 0.22.3
     */
    public ConfluenceReportGenerator(ConfluenceScoringEngine scoringEngine,
            LevelConfidenceCalculator levelConfidenceCalculator, Clock clock) {
        this.scoringEngine = Objects.requireNonNull(scoringEngine, "scoringEngine cannot be null");
        this.levelConfidenceCalculator = Objects.requireNonNull(levelConfidenceCalculator,
                "levelConfidenceCalculator cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    /**
     * Generates a confluence report for the latest bar in the provided series.
     *
     * @param ticker instrument symbol
     * @param series source bar series
     * @return populated confluence report
     * @since 0.22.3
     */
    public ConfluenceReport generate(String ticker, BarSeries series) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("ticker cannot be null or blank");
        }
        Objects.requireNonNull(series, "series cannot be null");
        if (series.getBarCount() < MINIMUM_BAR_COUNT) {
            throw new IllegalArgumentException(
                    "series must contain at least " + MINIMUM_BAR_COUNT + " bars for confluence generation");
        }

        int endIndex = series.getEndIndex();
        IndicatorSet indicators = IndicatorSet.create(series);
        double close = latestFinite(indicators.closePrice, endIndex, series.getLastBar().getClosePrice().doubleValue());
        double atr = latestFinite(indicators.atr14, endIndex, Math.max(EPSILON, close * 0.01d));
        double adx = latestFinite(indicators.adx14, endIndex, 20.0d);
        double plusDi = latestFinite(indicators.plusDi14, endIndex, 20.0d);
        double minusDi = latestFinite(indicators.minusDi14, endIndex, 20.0d);

        LevelContext levelContext = buildLevels(series, indicators, close, atr, endIndex);
        List<ConfluenceReport.PillarScore> pillars = buildPillars(indicators, endIndex, close, atr, adx, plusDi,
                minusDi, levelContext);

        ConfluenceScoringEngine.ConfluenceScores confluenceScores = scoringEngine.score(pillars);
        double atrPctPercentile = percentileRank(indicators.atrPctIndicator, endIndex, STRUCTURE_LOOKBACK);
        double adaptiveRangeThreshold = adaptiveRangeThreshold(atrPctPercentile);

        List<ConfluenceReport.HorizonProbability> probabilities = buildHorizonProbabilities(
                confluenceScores.decorrelatedScore(), adx, plusDi, minusDi, adaptiveRangeThreshold);
        ConfluenceReport.ConfidenceBreakdown breakdown = buildConfidenceBreakdown(series, confluenceScores, adx,
                adaptiveRangeThreshold, probabilities);

        ConfluenceReport.ValidationMetadata validationMetadata = new ConfluenceReport.ValidationMetadata(
                "uncalibrated-v1", "phase1-heuristic",
                series.getLastBar().getEndTime().atZone(ZoneOffset.UTC).toLocalDate().toString(), null, null, null,
                null,
                List.of("Calibration pipeline pending: probabilities are heuristic and not yet reliability-calibrated"));

        ConfluenceReport.Snapshot snapshot = new ConfluenceReport.Snapshot(ticker.trim(), timeframe(series),
                series.getLastBar().getEndTime(), series.getBarCount(), close, confluenceScores.rawScore(),
                confluenceScores.decorrelatedScore(), confluenceScores.correlationPenalty());

        List<String> narrative = buildNarrative(snapshot, probabilities, levelContext.levelConfidences());
        Map<String, String> extensions = buildExtensions(adx, adaptiveRangeThreshold, atrPctPercentile, plusDi,
                minusDi);

        return new ConfluenceReport(snapshot, pillars, levelContext.levelConfidences(), probabilities, breakdown,
                validationMetadata, narrative, extensions);
    }

    private static ConfluenceScoringEngine defaultScoringEngine() {
        Map<String, ConfluenceScoringEngine.FamilyPolicy> policies = new LinkedHashMap<>();
        policies.put("structure", new ConfluenceScoringEngine.FamilyPolicy(0.28d, 0.10d));
        policies.put("trend", new ConfluenceScoringEngine.FamilyPolicy(0.26d, 0.06d));
        policies.put("momentum", new ConfluenceScoringEngine.FamilyPolicy(0.22d, 0.06d));
        policies.put("volatility", new ConfluenceScoringEngine.FamilyPolicy(0.18d, 0.08d));
        policies.put("participation", new ConfluenceScoringEngine.FamilyPolicy(0.16d, 0.06d));
        policies.put("macro", new ConfluenceScoringEngine.FamilyPolicy(0.10d, 0.00d));
        return new ConfluenceScoringEngine(policies);
    }

    private List<ConfluenceReport.PillarScore> buildPillars(IndicatorSet indicators, int endIndex, double close,
            double atr, double adx, double plusDi, double minusDi, LevelContext levelContext) {
        List<ConfluenceReport.PillarScore> pillars = new ArrayList<>();
        pillars.add(structurePillar(levelContext, close));
        pillars.add(trendPillar(indicators, endIndex, close, atr));
        pillars.add(momentumPillar(indicators, endIndex, atr));
        pillars.add(volatilityPillar(indicators, endIndex, adx, plusDi, minusDi));
        pillars.add(participationPillar(indicators, endIndex));
        pillars.add(macroPlaceholderPillar());
        return pillars;
    }

    private ConfluenceReport.PillarScore structurePillar(LevelContext levelContext, double close) {
        double support = levelContext.supportAnchor();
        double resistance = levelContext.resistanceAnchor();
        double positionScore = 50.0d;
        if (Double.isFinite(support) && Double.isFinite(resistance) && resistance > support) {
            positionScore = clamp(100.0d * ((close - support) / (resistance - support)), 0.0d, 100.0d);
        }
        double trendlineScore = clamp(levelContext.trendlineQuality() * 100.0d, 0.0d, 100.0d);
        double agreementScore = clamp(levelContext.averageAgreement() * 100.0d, 0.0d, 100.0d);
        double weightedScore = 0.45d * positionScore + 0.35d * trendlineScore + 0.20d * agreementScore;
        ConfluenceReport.Direction direction = directionFromCenter(weightedScore);

        List<ConfluenceReport.FeatureContribution> features = List.of(
                feature("price-position", positionScore, 0.45d, direction,
                        "Price location between dominant support and resistance anchors"),
                feature("trendline-quality", trendlineScore, 0.35d, directionFromCenter(trendlineScore),
                        "Active trendline fit quality from swing-touch and violation scoring"),
                feature("method-agreement", agreementScore, 0.20d, ConfluenceReport.Direction.NEUTRAL,
                        "Cross-method agreement between trendline, cluster, and bounce levels"));

        List<String> explanations = List.of(
                String.format(Locale.US, "Support %.2f vs resistance %.2f anchors", support, resistance),
                String.format(Locale.US, "Structural agreement %.1f%%", agreementScore),
                "Structure score blends level position, trendline quality, and method agreement");

        return new ConfluenceReport.PillarScore(ConfluenceReport.Pillar.STRUCTURE, "structure", weightedScore, 0.25d,
                direction, features, explanations);
    }

    private ConfluenceReport.PillarScore trendPillar(IndicatorSet indicators, int endIndex, double close, double atr) {
        double sma20 = latestFinite(indicators.sma20, endIndex, close);
        double sma50 = latestFinite(indicators.sma50, endIndex, close);
        double sma200 = latestFinite(indicators.sma200, endIndex, close);
        double ema21 = latestFinite(indicators.ema21, endIndex, close);
        int ema21PrevIndex = Math.max(indicators.series.getBeginIndex(), endIndex - 5);
        double ema21Prev = latestFinite(indicators.ema21, ema21PrevIndex, Double.NaN);

        double stackScore = movingAverageStackScore(close, sma20, sma50, sma200);
        double emaSlopeScore = Double.isFinite(ema21Prev)
                ? 50.0d + clamp(((ema21 - ema21Prev) / Math.max(EPSILON, atr)) * 22.0d, -45.0d, 45.0d)
                : 50.0d;
        double distanceScore = 50.0d + clamp(((close - sma200) / Math.max(EPSILON, sma200)) * 800.0d, -45.0d, 45.0d);
        double weightedScore = 0.45d * stackScore + 0.30d * emaSlopeScore + 0.25d * distanceScore;
        ConfluenceReport.Direction direction = directionFromCenter(weightedScore);

        List<ConfluenceReport.FeatureContribution> features = List.of(
                feature("ma-stack", stackScore, 0.45d, directionFromCenter(stackScore),
                        "Close and moving-average stack alignment (20/50/200)"),
                feature("ema21-slope", emaSlopeScore, 0.30d, directionFromCenter(emaSlopeScore),
                        "EMA(21) slope normalized by ATR"),
                feature("distance-from-sma200", distanceScore, 0.25d, directionFromCenter(distanceScore),
                        "Distance from the 200-day baseline trend"));

        List<String> explanations = List.of(String.format(Locale.US, "SMA20 %.2f, SMA50 %.2f, SMA200 %.2f, EMA21 %.2f",
                sma20, sma50, sma200, ema21), "Trend score uses stack alignment, EMA slope, and baseline distance");

        return new ConfluenceReport.PillarScore(ConfluenceReport.Pillar.TREND, "trend", weightedScore, 0.25d, direction,
                features, explanations);
    }

    private ConfluenceReport.PillarScore momentumPillar(IndicatorSet indicators, int endIndex, double atr) {
        double rsi = latestFinite(indicators.rsi14, endIndex, 50.0d);
        double macdHist = latestFinite(indicators.macdHistogram, endIndex, 0.0d);
        double roc20 = latestFinite(indicators.roc20, endIndex, 0.0d);

        double rsiScore = clamp(rsi, 0.0d, 100.0d);
        double macdScore = 50.0d + clamp((macdHist / Math.max(EPSILON, atr)) * 70.0d, -45.0d, 45.0d);
        double rocScore = 50.0d + clamp(roc20 * 1.6d, -45.0d, 45.0d);
        double weightedScore = 0.35d * rsiScore + 0.35d * macdScore + 0.30d * rocScore;
        ConfluenceReport.Direction direction = directionFromCenter(weightedScore);

        List<ConfluenceReport.FeatureContribution> features = List.of(
                feature("rsi14", rsiScore, 0.35d, directionFromCenter(rsiScore), "RSI momentum location around 50"),
                feature("macd-histogram", macdScore, 0.35d, directionFromCenter(macdScore),
                        "MACD histogram normalized by ATR"),
                feature("roc20", rocScore, 0.30d, directionFromCenter(rocScore), "20-bar rate of change"));

        List<String> explanations = List.of(
                String.format(Locale.US, "RSI %.2f, MACD hist %.4f, ROC20 %.2f%%", rsi, macdHist, roc20),
                "Momentum score blends oscillator, trend acceleration, and rate-of-change");

        return new ConfluenceReport.PillarScore(ConfluenceReport.Pillar.MOMENTUM, "momentum", weightedScore, 0.20d,
                direction, features, explanations);
    }

    private ConfluenceReport.PillarScore volatilityPillar(IndicatorSet indicators, int endIndex, double adx,
            double plusDi, double minusDi) {
        double percentB = latestFinite(indicators.percentB20, endIndex, 0.5d);
        double bandwidth = latestFinite(indicators.bandWidth20, endIndex, 0.0d);
        double atrPct = latestFinite(indicators.atrPctIndicator, endIndex, 0.01d);
        double bandWidthPercentile = percentileRank(indicators.bandWidth20, endIndex, STRUCTURE_LOOKBACK);
        double atrPctPercentile = percentileRank(indicators.atrPctIndicator, endIndex, STRUCTURE_LOOKBACK);

        double percentBScore = clamp(percentB * 100.0d, 0.0d, 100.0d);
        double adxDirectionScore = 50.0d + clamp((plusDi - minusDi) * 0.80d, -40.0d, 40.0d);
        double regimeScore = clamp(100.0d * (0.5d * bandWidthPercentile + 0.5d * atrPctPercentile), 0.0d, 100.0d);
        double weightedScore = 0.40d * percentBScore + 0.35d * adxDirectionScore + 0.25d * regimeScore;
        ConfluenceReport.Direction direction = directionFromCenter(weightedScore);

        List<ConfluenceReport.FeatureContribution> features = List.of(
                feature("percent-b", percentBScore, 0.40d, directionFromCenter(percentBScore),
                        "Bollinger %B position within upper/lower bands"),
                feature("directional-movement", adxDirectionScore, 0.35d, directionFromCenter(adxDirectionScore),
                        "Directional movement spread (+DI vs -DI)"),
                feature("volatility-regime", regimeScore, 0.25d, ConfluenceReport.Direction.NEUTRAL,
                        "ATR and Bollinger bandwidth percentiles"));

        List<String> explanations = List.of(
                String.format(Locale.US, "ADX %.2f (+DI %.2f / -DI %.2f)", adx, plusDi, minusDi),
                String.format(Locale.US, "PercentB %.3f, Bandwidth %.2f, ATR%% %.3f", percentB, bandwidth, atrPct),
                "Volatility score combines location, directional movement, and regime context");

        return new ConfluenceReport.PillarScore(ConfluenceReport.Pillar.VOLATILITY, "volatility", weightedScore, 0.15d,
                direction, features, explanations);
    }

    private ConfluenceReport.PillarScore participationPillar(IndicatorSet indicators, int endIndex) {
        double mfi = latestFinite(indicators.mfi14, endIndex, 50.0d);
        double obvNow = latestFinite(indicators.obv, endIndex, 0.0d);
        double obvPast = latestFinite(indicators.obv, Math.max(indicators.series.getBeginIndex(), endIndex - 20),
                obvNow);
        double volNow = latestFinite(indicators.volume, endIndex, 0.0d);
        double volSma20 = latestFinite(indicators.volumeSma20, endIndex, Math.max(1.0d, volNow));

        double mfiScore = clamp(mfi, 0.0d, 100.0d);
        double obvDeltaNormalized = (obvNow - obvPast) / Math.max(1.0d, Math.abs(obvPast));
        double obvScore = 50.0d + clamp(obvDeltaNormalized * 800.0d, -40.0d, 40.0d);
        double relativeVolumeScore = 50.0d + clamp(((volNow / Math.max(1.0d, volSma20)) - 1.0d) * 40.0d, -35.0d, 35.0d);
        double weightedScore = 0.40d * mfiScore + 0.35d * obvScore + 0.25d * relativeVolumeScore;
        ConfluenceReport.Direction direction = directionFromCenter(weightedScore);

        List<ConfluenceReport.FeatureContribution> features = List.of(
                feature("mfi14", mfiScore, 0.40d, directionFromCenter(mfiScore),
                        "Money flow index as volume-weighted momentum"),
                feature("obv-slope", obvScore, 0.35d, directionFromCenter(obvScore), "20-bar OBV slope"),
                feature("relative-volume", relativeVolumeScore, 0.25d, ConfluenceReport.Direction.NEUTRAL,
                        "Current volume relative to SMA(20)"));

        List<String> explanations = List.of(
                String.format(Locale.US, "MFI %.2f, OBV delta %.4f, volume ratio %.2f", mfi, obvDeltaNormalized,
                        volNow / Math.max(1.0d, volSma20)),
                "Participation score tracks money flow, volume trend, and activity expansion");

        return new ConfluenceReport.PillarScore(ConfluenceReport.Pillar.PARTICIPATION, "participation", weightedScore,
                0.10d, direction, features, explanations);
    }

    private ConfluenceReport.PillarScore macroPlaceholderPillar() {
        List<ConfluenceReport.FeatureContribution> features = List
                .of(feature("macro-placeholder", 50.0d, 1.0d, ConfluenceReport.Direction.NEUTRAL,
                        "Phase-1 placeholder; intermarket and macro feeds are not wired yet"));
        List<String> explanations = List.of("Macro/intermarket pillar is intentionally placeholder in Phase 1");
        return new ConfluenceReport.PillarScore(ConfluenceReport.Pillar.MACRO_INTERMARKET, "macro", 50.0d, 0.05d,
                ConfluenceReport.Direction.NEUTRAL, features, explanations);
    }

    private LevelContext buildLevels(BarSeries series, IndicatorSet indicators, double close, double atr,
            int endIndex) {
        List<RawLevel> rawLevels = new ArrayList<>();

        TrendLineSegment supportSegment = indicators.trendSupport.getCurrentSegment();
        TrendLineSegment resistanceSegment = indicators.trendResistance.getCurrentSegment();
        double trendSupportLevel = latestFinite(indicators.trendSupport, endIndex, Double.NaN);
        double trendResistanceLevel = latestFinite(indicators.trendResistance, endIndex, Double.NaN);
        addTrendlineRawLevel(rawLevels, ConfluenceReport.LevelType.SUPPORT, "trendline-support", trendSupportLevel,
                supportSegment, close);
        addTrendlineRawLevel(rawLevels, ConfluenceReport.LevelType.RESISTANCE, "trendline-resistance",
                trendResistanceLevel, resistanceSegment, close);

        double clusterSupportLevel = latestFinite(indicators.clusterSupport, endIndex, Double.NaN);
        double clusterResistanceLevel = latestFinite(indicators.clusterResistance, endIndex, Double.NaN);
        if (Double.isFinite(clusterSupportLevel)) {
            int interactionIndex = indicators.clusterSupport.getClusterIndex(endIndex);
            int touches = countTouches(series, ConfluenceReport.LevelType.SUPPORT, clusterSupportLevel,
                    Math.max(atr * 0.30d, close * 0.002d), STRUCTURE_LOOKBACK);
            rawLevels.add(new RawLevel(ConfluenceReport.LevelType.SUPPORT, "cluster-support", clusterSupportLevel,
                    0.62d, touches, interactionIndex, "Dominant support cluster from close-price density"));
        }
        if (Double.isFinite(clusterResistanceLevel)) {
            int interactionIndex = indicators.clusterResistance.getClusterIndex(endIndex);
            int touches = countTouches(series, ConfluenceReport.LevelType.RESISTANCE, clusterResistanceLevel,
                    Math.max(atr * 0.30d, close * 0.002d), STRUCTURE_LOOKBACK);
            rawLevels.add(
                    new RawLevel(ConfluenceReport.LevelType.RESISTANCE, "cluster-resistance", clusterResistanceLevel,
                            0.62d, touches, interactionIndex, "Dominant resistance cluster from close-price density"));
        }

        double bounceSupportLevel = latestFinite(indicators.bounceSupport, endIndex, Double.NaN);
        double bounceResistanceLevel = latestFinite(indicators.bounceResistance, endIndex, Double.NaN);
        if (Double.isFinite(bounceSupportLevel)) {
            int interactionIndex = indicators.bounceSupport.getBounceIndex(endIndex);
            int touches = countTouches(series, ConfluenceReport.LevelType.SUPPORT, bounceSupportLevel,
                    Math.max(atr * 0.35d, close * 0.0025d), STRUCTURE_LOOKBACK);
            rawLevels.add(new RawLevel(ConfluenceReport.LevelType.SUPPORT, "bounce-support", bounceSupportLevel, 0.57d,
                    touches, interactionIndex, "Support inferred from repeated down-to-up bounce transitions"));
        }
        if (Double.isFinite(bounceResistanceLevel)) {
            int interactionIndex = indicators.bounceResistance.getBounceIndex(endIndex);
            int touches = countTouches(series, ConfluenceReport.LevelType.RESISTANCE, bounceResistanceLevel,
                    Math.max(atr * 0.35d, close * 0.0025d), STRUCTURE_LOOKBACK);
            rawLevels.add(new RawLevel(ConfluenceReport.LevelType.RESISTANCE, "bounce-resistance",
                    bounceResistanceLevel, 0.57d, touches, interactionIndex,
                    "Resistance inferred from repeated up-to-down bounce transitions"));
        }

        ensureFallbackLevels(rawLevels, close, atr, endIndex);

        List<ConfluenceReport.LevelConfidence> scoredLevels = new ArrayList<>();
        double proximityThreshold = Math.max(atr * 0.75d, close * 0.004d);
        int lookbackBars = Math.min(STRUCTURE_LOOKBACK, series.getBarCount());
        for (RawLevel rawLevel : rawLevels) {
            double agreement = levelAgreement(rawLevel, rawLevels, proximityThreshold);
            LevelConfidenceCalculator.LevelSample sample = new LevelConfidenceCalculator.LevelSample(rawLevel.type(),
                    rawLevel.name(), rawLevel.level(), close, atr, rawLevel.structural(), rawLevel.touchCount(),
                    rawLevel.lastInteractionIndex(), endIndex, lookbackBars, agreement, rawLevel.rationale());
            scoredLevels.add(levelConfidenceCalculator.score(sample));
        }

        double supportAnchor = weightedAnchor(scoredLevels, ConfluenceReport.LevelType.SUPPORT);
        double resistanceAnchor = weightedAnchor(scoredLevels, ConfluenceReport.LevelType.RESISTANCE);
        double trendlineQuality = average(List.of(scoreOrZero(supportSegment), scoreOrZero(resistanceSegment)));
        double averageAgreement = scoredLevels.stream()
                .mapToDouble(ConfluenceReport.LevelConfidence::agreement)
                .average()
                .orElse(0.5d);

        return new LevelContext(List.copyOf(scoredLevels), supportAnchor, resistanceAnchor, trendlineQuality,
                averageAgreement);
    }

    private static void ensureFallbackLevels(List<RawLevel> rawLevels, double close, double atr, int endIndex) {
        boolean hasSupport = rawLevels.stream().anyMatch(level -> level.type() == ConfluenceReport.LevelType.SUPPORT);
        boolean hasResistance = rawLevels.stream()
                .anyMatch(level -> level.type() == ConfluenceReport.LevelType.RESISTANCE);
        double distance = Math.max(atr * 1.5d, close * 0.008d);
        if (!hasSupport) {
            rawLevels.add(
                    new RawLevel(ConfluenceReport.LevelType.SUPPORT, "atr-fallback-support", close - distance, 0.35d, 1,
                            endIndex, "Fallback support derived from ATR distance when structural methods are sparse"));
        }
        if (!hasResistance) {
            rawLevels.add(new RawLevel(ConfluenceReport.LevelType.RESISTANCE, "atr-fallback-resistance",
                    close + distance, 0.35d, 1, endIndex,
                    "Fallback resistance derived from ATR distance when structural methods are sparse"));
        }
    }

    private static void addTrendlineRawLevel(List<RawLevel> rawLevels, ConfluenceReport.LevelType type, String name,
            double level, TrendLineSegment segment, double close) {
        if (!Double.isFinite(level) || segment == null) {
            return;
        }
        double structural = clamp(scoreOrZero(segment), 0.0d, 1.0d);
        int interactionIndex = Math.max(segment.firstIndex, segment.secondIndex);
        String rationale = String.format(Locale.US, "Trendline score %.3f (%d touches / %d outside)",
                scoreOrZero(segment), segment.touchCount, segment.outsideCount);
        rawLevels.add(new RawLevel(type, name, level, structural, Math.max(2, segment.touchCount), interactionIndex,
                rationale + String.format(Locale.US, ", distance %.2f%%", ((level - close) / close) * 100.0d)));
    }

    private List<ConfluenceReport.HorizonProbability> buildHorizonProbabilities(double decorrelatedScore, double adx,
            double plusDi, double minusDi, double adaptiveRangeThreshold) {
        double directionalBias = clamp((decorrelatedScore - 50.0d) / 50.0d + ((plusDi - minusDi) / 100.0d), -1.0d,
                1.0d);
        double trendStrength = clamp(adx / Math.max(1.0d, adaptiveRangeThreshold + 18.0d), 0.0d, 1.0d);

        ConfluenceReport.HorizonProbability oneMonth = distributionFor(ConfluenceReport.Horizon.ONE_MONTH,
                directionalBias, trendStrength, 0.28d, "uncalibrated-v1");
        ConfluenceReport.HorizonProbability threeMonth = distributionFor(ConfluenceReport.Horizon.THREE_MONTH,
                clamp(directionalBias * 1.10d, -1.0d, 1.0d), clamp(trendStrength * 1.10d, 0.0d, 1.0d), 0.20d,
                "uncalibrated-v1");

        return List.of(oneMonth, threeMonth);
    }

    private ConfluenceReport.ConfidenceBreakdown buildConfidenceBreakdown(BarSeries series,
            ConfluenceScoringEngine.ConfluenceScores confluenceScores, double adx, double adaptiveRangeThreshold,
            List<ConfluenceReport.HorizonProbability> probabilities) {
        double modelConfidence = clamp(Math.abs(confluenceScores.decorrelatedScore() - 50.0d) * 2.0d, 0.0d, 100.0d);
        double calibrationConfidence = clamp(35.0d + (series.getBarCount() / 1500.0d) * 30.0d, 0.0d, 70.0d);

        double thresholdDistance = Math.abs(adx - adaptiveRangeThreshold);
        double regimeConfidence = clamp(45.0d + thresholdDistance * 2.0d, 0.0d, 92.0d);

        Instant barTime = series.getLastBar().getEndTime();
        long ageDays = Math.max(0L, Duration.between(barTime, clock.instant()).toDays());
        double freshness = clamp(1.0d - (ageDays / 14.0d), 0.0d, 1.0d);
        double depth = clamp(series.getBarCount() / 1200.0d, 0.0d, 1.0d);
        double dataConfidence = clamp(100.0d * (0.55d * freshness + 0.45d * depth), 0.0d, 100.0d);

        double finalConfidence = clamp(0.45d * modelConfidence + 0.25d * calibrationConfidence
                + 0.20d * regimeConfidence + 0.10d * dataConfidence, 0.0d, 100.0d);

        List<String> notes = List.of("Final confidence blends model/calibration/regime/data components (45/25/20/10)",
                String.format(Locale.US, "Adaptive range threshold %.2f vs ADX %.2f", adaptiveRangeThreshold, adx),
                String.format(Locale.US, "1M P(up)=%.3f, 3M P(up)=%.3f", probabilities.get(0).upProbability(),
                        probabilities.get(1).upProbability()));
        return new ConfluenceReport.ConfidenceBreakdown(modelConfidence, calibrationConfidence, regimeConfidence,
                dataConfidence, finalConfidence, notes);
    }

    private static ConfluenceReport.HorizonProbability distributionFor(ConfluenceReport.Horizon horizon,
            double directionalBias, double trendStrength, double baselineRange, String calibrationMethod) {
        double rangeProbability = clamp(baselineRange + (1.0d - trendStrength) * 0.30d, 0.08d, 0.75d);
        double directionalMass = 1.0d - rangeProbability;
        double up = directionalMass * ((directionalBias + 1.0d) / 2.0d);
        double down = directionalMass - up;

        up = clamp(up, 0.02d, 0.96d);
        down = clamp(down, 0.02d, 0.96d);
        double total = up + down + rangeProbability;
        up /= total;
        down /= total;
        rangeProbability /= total;
        return new ConfluenceReport.HorizonProbability(horizon, up, down, rangeProbability, false, calibrationMethod);
    }

    private static List<String> buildNarrative(ConfluenceReport.Snapshot snapshot,
            List<ConfluenceReport.HorizonProbability> probabilities, List<ConfluenceReport.LevelConfidence> levels) {
        List<ConfluenceReport.LevelConfidence> supports = levels.stream()
                .filter(level -> level.type() == ConfluenceReport.LevelType.SUPPORT)
                .sorted((a, b) -> Double.compare(b.confidence(), a.confidence()))
                .limit(2)
                .toList();
        List<ConfluenceReport.LevelConfidence> resistances = levels.stream()
                .filter(level -> level.type() == ConfluenceReport.LevelType.RESISTANCE)
                .sorted((a, b) -> Double.compare(b.confidence(), a.confidence()))
                .limit(2)
                .toList();

        ConfluenceReport.HorizonProbability oneMonth = probabilities.get(0);
        ConfluenceReport.HorizonProbability threeMonth = probabilities.get(1);
        return List.of(
                String.format(Locale.US, "1M bias: up %.1f%% / down %.1f%% / range %.1f%%",
                        oneMonth.upProbability() * 100.0d, oneMonth.downProbability() * 100.0d,
                        oneMonth.rangeProbability() * 100.0d),
                String.format(Locale.US, "3M bias: up %.1f%% / down %.1f%% / range %.1f%%",
                        threeMonth.upProbability() * 100.0d, threeMonth.downProbability() * 100.0d,
                        threeMonth.rangeProbability() * 100.0d),
                String.format(Locale.US, "Top supports: %s", renderLevels(supports)),
                String.format(Locale.US, "Top resistances: %s", renderLevels(resistances)),
                String.format(Locale.US, "Raw %.1f / decorrelated %.1f (penalty %.1f)", snapshot.rawConfluenceScore(),
                        snapshot.decorrelatedConfluenceScore(), snapshot.correlationPenalty()));
    }

    private static String renderLevels(List<ConfluenceReport.LevelConfidence> levels) {
        if (levels.isEmpty()) {
            return "none";
        }
        List<String> rendered = new ArrayList<>();
        for (ConfluenceReport.LevelConfidence level : levels) {
            rendered.add(String.format(Locale.US, "%s %.2f (%.0f)", level.name(), level.level(), level.confidence()));
        }
        return String.join(", ", rendered);
    }

    private static Map<String, String> buildExtensions(double adx, double adaptiveRangeThreshold,
            double atrPctPercentile, double plusDi, double minusDi) {
        Map<String, String> extensions = new LinkedHashMap<>();
        extensions.put("regime.adx", format(adx));
        extensions.put("regime.rangeThreshold", format(adaptiveRangeThreshold));
        extensions.put("regime.atrPctPercentile", format(atrPctPercentile));
        extensions.put("regime.plusDI", format(plusDi));
        extensions.put("regime.minusDI", format(minusDi));
        extensions.put("pillar.macro.status", "placeholder");
        return extensions;
    }

    private static int countTouches(BarSeries series, ConfluenceReport.LevelType type, double level, double tolerance,
            int lookback) {
        int endIndex = series.getEndIndex();
        int startIndex = Math.max(series.getBeginIndex(), endIndex - lookback + 1);
        int touches = 0;
        for (int i = startIndex; i <= endIndex; i++) {
            double probe = type == ConfluenceReport.LevelType.SUPPORT ? series.getBar(i).getLowPrice().doubleValue()
                    : series.getBar(i).getHighPrice().doubleValue();
            if (Math.abs(probe - level) <= tolerance) {
                touches++;
            }
        }
        return touches;
    }

    private static double levelAgreement(RawLevel target, List<RawLevel> levels, double threshold) {
        int peers = 0;
        int matches = 0;
        for (RawLevel peer : levels) {
            if (peer == target || peer.type() != target.type()) {
                continue;
            }
            peers++;
            if (Math.abs(peer.level() - target.level()) <= threshold) {
                matches++;
            }
        }
        if (peers == 0) {
            return 0.50d;
        }
        return clamp((double) matches / (double) peers, 0.0d, 1.0d);
    }

    private static double weightedAnchor(List<ConfluenceReport.LevelConfidence> levels,
            ConfluenceReport.LevelType type) {
        double weightedSum = 0.0d;
        double weightTotal = 0.0d;
        for (ConfluenceReport.LevelConfidence level : levels) {
            if (level.type() != type) {
                continue;
            }
            double weight = Math.max(EPSILON, level.confidence());
            weightedSum += level.level() * weight;
            weightTotal += weight;
        }
        if (weightTotal <= 0.0d) {
            return Double.NaN;
        }
        return weightedSum / weightTotal;
    }

    private static double percentileRank(org.ta4j.core.Indicator<Num> indicator, int endIndex, int window) {
        BarSeries series = indicator.getBarSeries();
        int startIndex = Math.max(series.getBeginIndex(), endIndex - window + 1);
        double current = latestFinite(indicator, endIndex, Double.NaN);
        if (!Double.isFinite(current)) {
            return 0.5d;
        }
        int valid = 0;
        int lessOrEqual = 0;
        for (int i = startIndex; i <= endIndex; i++) {
            double value = indicator.getValue(i).doubleValue();
            if (!Double.isFinite(value)) {
                continue;
            }
            valid++;
            if (value <= current) {
                lessOrEqual++;
            }
        }
        if (valid == 0) {
            return 0.5d;
        }
        return clamp((double) lessOrEqual / (double) valid, 0.0d, 1.0d);
    }

    private static double adaptiveRangeThreshold(double atrPctPercentile) {
        return 18.0d + (1.0d - clamp(atrPctPercentile, 0.0d, 1.0d)) * 10.0d;
    }

    private static double movingAverageStackScore(double close, double sma20, double sma50, double sma200) {
        int bullishVotes = 0;
        if (close > sma20) {
            bullishVotes++;
        }
        if (sma20 > sma50) {
            bullishVotes++;
        }
        if (sma50 > sma200) {
            bullishVotes++;
        }
        if (close > sma200) {
            bullishVotes++;
        }

        int bearishVotes = 0;
        if (close < sma20) {
            bearishVotes++;
        }
        if (sma20 < sma50) {
            bearishVotes++;
        }
        if (sma50 < sma200) {
            bearishVotes++;
        }
        if (close < sma200) {
            bearishVotes++;
        }

        if (bullishVotes == 4) {
            return 100.0d;
        }
        if (bearishVotes == 4) {
            return 0.0d;
        }
        return 50.0d + (bullishVotes - bearishVotes) * 12.5d;
    }

    private static ConfluenceReport.FeatureContribution feature(String name, double score, double weight,
            ConfluenceReport.Direction direction, String rationale) {
        return new ConfluenceReport.FeatureContribution(name, clamp(score, 0.0d, 100.0d), weight, direction, rationale);
    }

    private static ConfluenceReport.Direction directionFromCenter(double score) {
        if (score >= 55.0d) {
            return ConfluenceReport.Direction.BULLISH;
        }
        if (score <= 45.0d) {
            return ConfluenceReport.Direction.BEARISH;
        }
        return ConfluenceReport.Direction.NEUTRAL;
    }

    private static double scoreOrZero(TrendLineSegment segment) {
        if (segment == null || !Double.isFinite(segment.score)) {
            return 0.0d;
        }
        return segment.score;
    }

    private static double average(List<Double> values) {
        double sum = 0.0d;
        int count = 0;
        for (double value : values) {
            if (Double.isFinite(value)) {
                sum += value;
                count++;
            }
        }
        if (count == 0) {
            return 0.0d;
        }
        return sum / count;
    }

    private static String timeframe(BarSeries series) {
        Duration period = series.getLastBar().getTimePeriod();
        if (period == null || period.isZero() || period.isNegative()) {
            return "PT1D";
        }
        return period.toString();
    }

    private static double latestFinite(org.ta4j.core.Indicator<Num> indicator, int index, double fallback) {
        BarSeries series = indicator.getBarSeries();
        int begin = series.getBeginIndex();
        for (int i = index; i >= begin; i--) {
            double value = indicator.getValue(i).doubleValue();
            if (Double.isFinite(value)) {
                return value;
            }
        }
        return fallback;
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private record RawLevel(ConfluenceReport.LevelType type, String name, double level, double structural,
            int touchCount, int lastInteractionIndex, String rationale) {
    }

    private record LevelContext(List<ConfluenceReport.LevelConfidence> levelConfidences, double supportAnchor,
            double resistanceAnchor, double trendlineQuality, double averageAgreement) {
    }

    private static final class IndicatorSet {
        private final BarSeries series;
        private final ClosePriceIndicator closePrice;
        private final SMAIndicator sma20;
        private final SMAIndicator sma50;
        private final SMAIndicator sma200;
        private final EMAIndicator ema21;
        private final RSIIndicator rsi14;
        private final NumericIndicator macdHistogram;
        private final ROCIndicator roc20;
        private final ATRIndicator atr14;
        private final NumericIndicator atrPctIndicator;
        private final ADXIndicator adx14;
        private final PlusDIIndicator plusDi14;
        private final MinusDIIndicator minusDi14;
        private final PercentBIndicator percentB20;
        private final BollingerBandWidthIndicator bandWidth20;
        private final OnBalanceVolumeIndicator obv;
        private final MoneyFlowIndexIndicator mfi14;
        private final VolumeIndicator volume;
        private final SMAIndicator volumeSma20;
        private final TrendLineSupportIndicator trendSupport;
        private final TrendLineResistanceIndicator trendResistance;
        private final PriceClusterSupportIndicator clusterSupport;
        private final PriceClusterResistanceIndicator clusterResistance;
        private final BounceCountSupportIndicator bounceSupport;
        private final BounceCountResistanceIndicator bounceResistance;

        private IndicatorSet(BarSeries series, ClosePriceIndicator closePrice, SMAIndicator sma20, SMAIndicator sma50,
                SMAIndicator sma200, EMAIndicator ema21, RSIIndicator rsi14, NumericIndicator macdHistogram,
                ROCIndicator roc20, ATRIndicator atr14, NumericIndicator atrPctIndicator, ADXIndicator adx14,
                PlusDIIndicator plusDi14, MinusDIIndicator minusDi14, PercentBIndicator percentB20,
                BollingerBandWidthIndicator bandWidth20, OnBalanceVolumeIndicator obv, MoneyFlowIndexIndicator mfi14,
                VolumeIndicator volume, SMAIndicator volumeSma20, TrendLineSupportIndicator trendSupport,
                TrendLineResistanceIndicator trendResistance, PriceClusterSupportIndicator clusterSupport,
                PriceClusterResistanceIndicator clusterResistance, BounceCountSupportIndicator bounceSupport,
                BounceCountResistanceIndicator bounceResistance) {
            this.series = series;
            this.closePrice = closePrice;
            this.sma20 = sma20;
            this.sma50 = sma50;
            this.sma200 = sma200;
            this.ema21 = ema21;
            this.rsi14 = rsi14;
            this.macdHistogram = macdHistogram;
            this.roc20 = roc20;
            this.atr14 = atr14;
            this.atrPctIndicator = atrPctIndicator;
            this.adx14 = adx14;
            this.plusDi14 = plusDi14;
            this.minusDi14 = minusDi14;
            this.percentB20 = percentB20;
            this.bandWidth20 = bandWidth20;
            this.obv = obv;
            this.mfi14 = mfi14;
            this.volume = volume;
            this.volumeSma20 = volumeSma20;
            this.trendSupport = trendSupport;
            this.trendResistance = trendResistance;
            this.clusterSupport = clusterSupport;
            this.clusterResistance = clusterResistance;
            this.bounceSupport = bounceSupport;
            this.bounceResistance = bounceResistance;
        }

        private static IndicatorSet create(BarSeries series) {
            ClosePriceIndicator close = new ClosePriceIndicator(series);
            SMAIndicator sma20 = new SMAIndicator(close, 20);
            SMAIndicator sma50 = new SMAIndicator(close, 50);
            SMAIndicator sma200 = new SMAIndicator(close, 200);
            EMAIndicator ema21 = new EMAIndicator(close, 21);
            RSIIndicator rsi14 = new RSIIndicator(close, 14);
            MACDIndicator macd = new MACDIndicator(close, 12, 26);
            NumericIndicator macdHistogram = macd.getHistogram(9);
            ROCIndicator roc20 = new ROCIndicator(close, 20);
            ATRIndicator atr14 = new ATRIndicator(series, 14);
            NumericIndicator atrPct = NumericIndicator.of(atr14).dividedBy(close).multipliedBy(100.0d);
            ADXIndicator adx14 = new ADXIndicator(series, 14);
            PlusDIIndicator plusDi14 = new PlusDIIndicator(series, 14);
            MinusDIIndicator minusDi14 = new MinusDIIndicator(series, 14);

            BollingerBandsMiddleIndicator bbMiddle = new BollingerBandsMiddleIndicator(new SMAIndicator(close, 20));
            StandardDeviationIndicator bbStd = new StandardDeviationIndicator(close, 20);
            BollingerBandsUpperIndicator bbUpper = new BollingerBandsUpperIndicator(bbMiddle, bbStd,
                    series.numFactory().numOf(2.0d));
            BollingerBandsLowerIndicator bbLower = new BollingerBandsLowerIndicator(bbMiddle, bbStd,
                    series.numFactory().numOf(2.0d));
            PercentBIndicator percentB20 = new PercentBIndicator(close, 20, 2.0d);
            BollingerBandWidthIndicator bandWidth20 = new BollingerBandWidthIndicator(bbUpper, bbMiddle, bbLower);

            OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(series);
            MoneyFlowIndexIndicator mfi14 = new MoneyFlowIndexIndicator(series, 14);
            VolumeIndicator volume = new VolumeIndicator(series);
            SMAIndicator volumeSma20 = new SMAIndicator(volume, 20);

            double closeNow = series.getLastBar().getClosePrice().doubleValue();
            double clusterToleranceValue = Math.max(closeNow * 0.0030d, 0.01d);
            double bounceBucketValue = Math.max(closeNow * 0.0035d, 0.01d);
            int lookback = Math.min(STRUCTURE_LOOKBACK, series.getBarCount());
            Num clusterTolerance = series.numFactory().numOf(clusterToleranceValue);
            Num bounceBucket = series.numFactory().numOf(bounceBucketValue);
            TrendLineSupportIndicator trendSupport = new TrendLineSupportIndicator(series, 2, lookback);
            TrendLineResistanceIndicator trendResistance = new TrendLineResistanceIndicator(series, 2, lookback);
            PriceClusterSupportIndicator clusterSupport = new PriceClusterSupportIndicator(series, lookback,
                    clusterTolerance);
            PriceClusterResistanceIndicator clusterResistance = new PriceClusterResistanceIndicator(series, lookback,
                    clusterTolerance);
            BounceCountSupportIndicator bounceSupport = new BounceCountSupportIndicator(close, lookback, bounceBucket);
            BounceCountResistanceIndicator bounceResistance = new BounceCountResistanceIndicator(close, lookback,
                    bounceBucket);

            return new IndicatorSet(series, close, sma20, sma50, sma200, ema21, rsi14, macdHistogram, roc20, atr14,
                    atrPct, adx14, plusDi14, minusDi14, percentB20, bandWidth20, obv, mfi14, volume, volumeSma20,
                    trendSupport, trendResistance, clusterSupport, clusterResistance, bounceSupport, bounceResistance);
        }
    }
}
