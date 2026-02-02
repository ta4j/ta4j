/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.elliott.ElliottChannelIndicator;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioGenerator;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottSwingCompressor;
import org.ta4j.core.indicators.elliott.ElliottSwingIndicator;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AbstractRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.strategy.named.NamedStrategy;

/**
 * High-reward Elliott Wave strategy that trades only high-confidence impulse
 * scenarios with favorable risk/reward and trend/momentum alignment.
 *
 * <p>
 * Entry criteria:
 * <ul>
 * <li>Impulse scenario in wave 3 or wave 5</li>
 * <li>Confidence above the minimum threshold</li>
 * <li>Directional trend bias alignment</li>
 * <li>Risk/reward meets the minimum threshold using the wave 2/4 stop and the
 * furthest Fibonacci target</li>
 * <li>Wave 2/4 time alternation exceeds the minimum ratio</li>
 * <li>Trend (SMA) and momentum (RSI or MACD) confirmation</li>
 * </ul>
 *
 * <p>
 * Exit criteria:
 * <ul>
 * <li>Scenario invalidation or completion</li>
 * <li>Corrective swing stop breached (wave 2/4)</li>
 * <li>Target reached</li>
 * <li>Trend/momentum breakdown</li>
 * <li>Time-based stop after an extended wave 3 duration</li>
 * </ul>
 *
 * @since 0.22.2
 */
public class HighRewardElliottWaveStrategy extends NamedStrategy {

    static {
        registerImplementation(HighRewardElliottWaveStrategy.class);
    }

    private static final SignalDirection DEFAULT_DIRECTION = SignalDirection.BULLISH;
    private static final ElliottDegree DEFAULT_DEGREE = ElliottDegree.PRIMARY;
    private static final double DEFAULT_MIN_CONFIDENCE = 0.35;
    private static final double DEFAULT_MIN_RISK_REWARD = 2.0;
    private static final double DEFAULT_MIN_ALTERNATION_RATIO = 1.50;
    private static final double DEFAULT_MIN_TREND_BIAS_STRENGTH = 0.10;
    private static final int DEFAULT_TREND_SMA_PERIOD = 100;
    private static final int DEFAULT_RSI_PERIOD = 14;
    private static final double DEFAULT_RSI_THRESHOLD = 50.0;
    private static final int DEFAULT_MACD_FAST = 12;
    private static final int DEFAULT_MACD_SLOW = 26;

    private static final int DEFAULT_ATR_PERIOD = 14;
    private static final double DEFAULT_MIN_RELATIVE_SWING = 0.10;
    private static final double ANALYZER_MIN_CONFIDENCE = 0.20;
    private static final int DEFAULT_SCENARIO_SWING_WINDOW = 5;

    private static final double MAX_WAVE_DURATION_MULTIPLIER = 1.5;
    private static final int PARAMETER_COUNT = 12;

    /**
     * Builds the strategy with default parameters.
     *
     * @param series bar series to analyze
     */
    public HighRewardElliottWaveStrategy(final BarSeries series) {
        this(series, Config.defaults());
    }

    /**
     * Builds the strategy using serialized label parameters.
     *
     * @param series bar series to analyze
     * @param params serialized parameters (see
     *               {@link Config#fromParameters(String...)})
     */
    public HighRewardElliottWaveStrategy(final BarSeries series, final String... params) {
        this(series, Config.fromParameters(params));
    }

    HighRewardElliottWaveStrategy(final BarSeries series, final Config config,
            final Indicator<ElliottScenarioSet> scenarioIndicator) {
        this(series, config, scenarioIndicator, buildRules(series, config, scenarioIndicator));
    }

    private HighRewardElliottWaveStrategy(final BarSeries series, final Config config) {
        this(series, config, buildScenarioIndicator(series, config));
    }

    private HighRewardElliottWaveStrategy(final BarSeries series, final Config config,
            final Indicator<ElliottScenarioSet> scenarioIndicator, final RuleBundle rules) {
        super(buildLabel(config), rules.entryRule(), rules.exitRule(), rules.unstableBars());
    }

    private static RuleBundle buildRules(final BarSeries series, final Config config,
            final Indicator<ElliottScenarioSet> scenarioIndicator) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");

        if (scenarioIndicator.getBarSeries() != series) {
            throw new IllegalArgumentException("scenarioIndicator must use the same BarSeries instance");
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator trendSma = new SMAIndicator(close, config.trendSmaPeriod());
        RSIIndicator rsi = new RSIIndicator(close, config.rsiPeriod());
        MACDIndicator macd = new MACDIndicator(close, config.macdFastPeriod(), config.macdSlowPeriod());

        Rule trendRule = config.direction().isBullish() ? new OverIndicatorRule(close, trendSma)
                : new UnderIndicatorRule(close, trendSma);

        Rule momentumRule = config.direction().isBullish()
                ? new OverIndicatorRule(rsi, config.rsiThreshold()).or(new OverIndicatorRule(macd, 0))
                : new UnderIndicatorRule(rsi, config.rsiThreshold()).or(new UnderIndicatorRule(macd, 0));

        Rule entryRule = trendRule.and(momentumRule).and(new AbstractRule() {
            @Override
            public boolean isSatisfied(final int index, final TradingRecord record) {
                ElliottScenarioSet scenarios = scenarioIndicator.getValue(index);
                ElliottScenario base = scenarios.base().orElse(null);
                Num closePrice = close.getValue(index);
                TrendBias bias = computeTrendBias(scenarios);
                boolean satisfied = isEntrySignal(config, base, closePrice, bias);
                traceIsSatisfied(index, satisfied);
                return satisfied;
            }
        });

        Rule exitRule = new AbstractRule() {
            @Override
            public boolean isSatisfied(final int index, final TradingRecord record) {
                if (record == null || record.isClosed()) {
                    traceIsSatisfied(index, false);
                    return false;
                }
                ElliottScenarioSet scenarios = scenarioIndicator.getValue(index);
                ElliottScenario base = scenarios.base().orElse(null);
                Num closePrice = close.getValue(index);
                TrendBias bias = computeTrendBias(scenarios);
                boolean satisfied = isExitSignal(config, base, closePrice, bias, trendRule, momentumRule, record,
                        index);
                traceIsSatisfied(index, satisfied);
                return satisfied;
            }
        };

        int unstableBars = calculateUnstableBars(config);
        return new RuleBundle(entryRule, exitRule, unstableBars);
    }

    private static boolean isEntrySignal(final Config config, final ElliottScenario base, final Num closePrice,
            final TrendBias bias) {
        if (base == null || closePrice == null || Num.isNaNOrNull(closePrice)) {
            return false;
        }
        if (base.type() != ScenarioType.IMPULSE || !isImpulseEntryPhase(base.currentPhase())) {
            return false;
        }
        if (!base.confidence().isAboveThreshold(config.minConfidence())) {
            return false;
        }
        if (!base.hasKnownDirection() || !directionMatches(config.direction(), base)) {
            return false;
        }
        if (bias.isNeutral()) {
            return false;
        }
        if (bias.strength() < config.minTrendBiasStrength()) {
            return false;
        }
        if (!directionMatches(config.direction(), bias)) {
            return false;
        }

        if (!meetsAlternationRequirement(base, config.minAlternationRatio())) {
            return false;
        }

        return hasFavorableRiskReward(base, closePrice, config);
    }

    private static boolean isExitSignal(final Config config, final ElliottScenario base, final Num closePrice,
            final TrendBias bias, final Rule trendRule, final Rule momentumRule, final TradingRecord record,
            final int index) {
        if (base == null || closePrice == null || Num.isNaNOrNull(closePrice)) {
            return true;
        }
        if (!base.hasKnownDirection() || !directionMatches(config.direction(), base)) {
            return true;
        }
        if (base.expectsCompletion()) {
            return true;
        }
        if (base.isInvalidatedBy(closePrice)) {
            return true;
        }
        if (hasReachedTarget(base, closePrice, config.direction())) {
            return true;
        }
        if (hasStopViolation(base, closePrice, config.direction())) {
            return true;
        }

        if (bias.isNeutral() || !directionMatches(config.direction(), bias)) {
            return true;
        }
        if (bias.strength() < config.minTrendBiasStrength()) {
            return true;
        }

        if (!trendRule.isSatisfied(index, record) || !momentumRule.isSatisfied(index, record)) {
            return true;
        }

        return isTimeStopTriggered(record, base, index);
    }

    private static boolean hasFavorableRiskReward(final ElliottScenario base, final Num closePrice,
            final Config config) {
        Num stop = selectStop(base);
        Num target = selectTarget(base, config.direction());
        if (Num.isNaNOrNull(stop) || Num.isNaNOrNull(target)) {
            return false;
        }

        Num risk;
        Num reward;
        if (config.direction().isBullish()) {
            if (!closePrice.isGreaterThan(stop) || !target.isGreaterThan(closePrice)) {
                return false;
            }
            risk = closePrice.minus(stop);
            reward = target.minus(closePrice);
        } else {
            if (!stop.isGreaterThan(closePrice) || !closePrice.isGreaterThan(target)) {
                return false;
            }
            risk = stop.minus(closePrice);
            reward = closePrice.minus(target);
        }

        if (risk.isLessThanOrEqual(closePrice.getNumFactory().zero())) {
            return false;
        }

        Num rr = reward.dividedBy(risk);
        return rr.isGreaterThanOrEqual(closePrice.getNumFactory().numOf(config.minRiskReward()));
    }

    private static boolean hasReachedTarget(final ElliottScenario base, final Num closePrice,
            final SignalDirection direction) {
        Num target = selectTarget(base, direction);
        if (Num.isNaNOrNull(target)) {
            return false;
        }
        return direction.isBullish() ? closePrice.isGreaterThanOrEqual(target) : closePrice.isLessThanOrEqual(target);
    }

    private static boolean hasStopViolation(final ElliottScenario base, final Num closePrice,
            final SignalDirection direction) {
        Num stop = selectStop(base);
        if (Num.isNaNOrNull(stop)) {
            return false;
        }
        return direction.isBullish() ? closePrice.isLessThanOrEqual(stop) : closePrice.isGreaterThanOrEqual(stop);
    }

    private static boolean meetsAlternationRequirement(final ElliottScenario base, final double minAlternationRatio) {
        List<ElliottSwing> swings = base.swings();
        if (swings == null || swings.size() < 4) {
            return false;
        }
        ElliottPhase phase = base.currentPhase();
        if (phase != null && !phase.isImpulse()) {
            return false;
        }
        ElliottSwing wave2 = swings.get(1);
        ElliottSwing wave4 = swings.get(3);
        int wave2Bars = wave2.length();
        int wave4Bars = wave4.length();
        if (wave2Bars <= 0 || wave4Bars <= 0) {
            return false;
        }
        double ratio = (double) wave4Bars / wave2Bars;
        if (Double.isNaN(ratio) || ratio <= 0.0) {
            return false;
        }
        double normalized = ratio >= 1.0 ? ratio : 1.0 / ratio;
        return normalized >= minAlternationRatio;
    }

    private static Num selectStop(final ElliottScenario base) {
        List<ElliottSwing> swings = base.swings();
        if (swings == null || swings.isEmpty()) {
            return base.invalidationPrice();
        }
        ElliottPhase phase = base.currentPhase();
        int correctiveIndex = phase == ElliottPhase.WAVE3 ? 1 : phase == ElliottPhase.WAVE5 ? 3 : -1;
        if (correctiveIndex >= 0 && swings.size() > correctiveIndex) {
            ElliottSwing corrective = swings.get(correctiveIndex);
            return corrective.toPrice();
        }
        return base.invalidationPrice();
    }

    private static Num selectTarget(final ElliottScenario base, final SignalDirection direction) {
        Num selected = Num.isValid(base.primaryTarget()) ? base.primaryTarget() : null;
        List<Num> targets = base.fibonacciTargets();
        if (targets == null || targets.isEmpty()) {
            return selected;
        }
        for (Num target : targets) {
            if (!Num.isValid(target)) {
                continue;
            }
            if (selected == null) {
                selected = target;
                continue;
            }
            if (direction.isBullish()) {
                if (target.isGreaterThan(selected)) {
                    selected = target;
                }
            } else if (target.isLessThan(selected)) {
                selected = target;
            }
        }
        return selected;
    }

    private static boolean isTimeStopTriggered(final TradingRecord record, final ElliottScenario base,
            final int index) {
        Trade entry = record.getCurrentPosition() == null ? null : record.getCurrentPosition().getEntry();
        if (entry == null) {
            return false;
        }
        int barsOpen = index - entry.getIndex();
        if (barsOpen <= 0) {
            return false;
        }
        List<ElliottSwing> swings = base.swings();
        if (swings.size() < 3) {
            return false;
        }
        int wave3Bars = swings.get(2).length();
        if (wave3Bars <= 0) {
            return false;
        }
        double maxBars = wave3Bars * MAX_WAVE_DURATION_MULTIPLIER;
        return barsOpen >= Math.ceil(maxBars);
    }

    private static boolean isImpulseEntryPhase(final ElliottPhase phase) {
        return phase == ElliottPhase.WAVE3 || phase == ElliottPhase.WAVE5;
    }

    private static boolean directionMatches(final SignalDirection direction, final ElliottScenario scenario) {
        return direction.isBullish() ? scenario.isBullish() : scenario.isBearish();
    }

    private static boolean directionMatches(final SignalDirection direction, final TrendBias bias) {
        if (bias == null || bias.direction() == null) {
            return false;
        }
        return direction.isBullish() == bias.direction().isBullish();
    }

    private static TrendBias computeTrendBias(final ElliottScenarioSet scenarios) {
        if (scenarios == null || scenarios.isEmpty()) {
            return TrendBias.unknown();
        }
        double bullishScore = 0.0;
        double bearishScore = 0.0;
        for (ElliottScenario scenario : scenarios.all()) {
            if (scenario == null || !scenario.hasKnownDirection()) {
                continue;
            }
            double score = scenario.confidenceScore().doubleValue();
            if (Double.isNaN(score) || score <= 0.0) {
                continue;
            }
            if (scenario.isBullish()) {
                bullishScore += score;
            } else {
                bearishScore += score;
            }
        }
        double total = bullishScore + bearishScore;
        if (total <= 0.0) {
            return TrendBias.unknown();
        }
        double strength = Math.abs(bullishScore - bearishScore) / total;
        SignalDirection direction = bullishScore >= bearishScore ? SignalDirection.BULLISH : SignalDirection.BEARISH;
        return new TrendBias(direction, strength);
    }

    private static Indicator<ElliottScenarioSet> buildScenarioIndicator(final BarSeries series, final Config config) {
        ElliottSwingIndicator swingIndicator = ElliottSwingIndicator.zigZag(series, config.degree());
        ElliottChannelIndicator channelIndicator = new ElliottChannelIndicator(swingIndicator);
        double minConfidence = Math.min(config.minConfidence(), ANALYZER_MIN_CONFIDENCE);
        ElliottScenarioGenerator generator = new ElliottScenarioGenerator(series.numFactory(), minConfidence,
                ElliottScenarioGenerator.DEFAULT_MAX_SCENARIOS);
        ElliottSwingCompressor compressor = new ElliottSwingCompressor(new ClosePriceIndicator(series),
                config.minRelativeSwing(), 0);
        return new ScenarioSetIndicator(series, swingIndicator, channelIndicator, generator, compressor,
                DEFAULT_SCENARIO_SWING_WINDOW);
    }

    private static String buildLabel(final Config config) {
        return NamedStrategy.buildLabel(HighRewardElliottWaveStrategy.class, config.labelParts());
    }

    private static int calculateUnstableBars(final Config config) {
        int unstable = Math.max(config.trendSmaPeriod(), Math.max(config.rsiPeriod(), config.macdSlowPeriod()));
        unstable = Math.max(unstable, DEFAULT_ATR_PERIOD);
        return unstable;
    }

    private static String formatDouble(final double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    enum SignalDirection {
        BULLISH, BEARISH;

        boolean isBullish() {
            return this == BULLISH;
        }
    }

    static final class Config {

        private final SignalDirection direction;
        private final ElliottDegree degree;
        private final double minConfidence;
        private final double minRiskReward;
        private final double minAlternationRatio;
        private final double minTrendBiasStrength;
        private final int trendSmaPeriod;
        private final int rsiPeriod;
        private final double rsiThreshold;
        private final int macdFastPeriod;
        private final int macdSlowPeriod;
        private final double minRelativeSwing;

        Config(final SignalDirection direction, final ElliottDegree degree, final double minConfidence,
                final double minRiskReward, final double minAlternationRatio, final double minTrendBiasStrength,
                final int trendSmaPeriod, final int rsiPeriod, final double rsiThreshold, final int macdFastPeriod,
                final int macdSlowPeriod, final double minRelativeSwing) {
            this.direction = Objects.requireNonNull(direction, "direction");
            this.degree = Objects.requireNonNull(degree, "degree");
            if (minConfidence <= 0.0 || minConfidence > 1.0) {
                throw new IllegalArgumentException("minConfidence must be in (0.0, 1.0]");
            }
            if (minRiskReward <= 0.0) {
                throw new IllegalArgumentException("minRiskReward must be positive");
            }
            if (minAlternationRatio < 1.0) {
                throw new IllegalArgumentException("minAlternationRatio must be >= 1.0");
            }
            if (minTrendBiasStrength < 0.0 || minTrendBiasStrength > 1.0) {
                throw new IllegalArgumentException("minTrendBiasStrength must be in [0.0, 1.0]");
            }
            if (trendSmaPeriod <= 0) {
                throw new IllegalArgumentException("trendSmaPeriod must be positive");
            }
            if (rsiPeriod <= 0) {
                throw new IllegalArgumentException("rsiPeriod must be positive");
            }
            if (rsiThreshold < 0.0 || rsiThreshold > 100.0) {
                throw new IllegalArgumentException("rsiThreshold must be in [0.0, 100.0]");
            }
            if (macdFastPeriod <= 0 || macdSlowPeriod <= 0) {
                throw new IllegalArgumentException("MACD periods must be positive");
            }
            if (macdFastPeriod >= macdSlowPeriod) {
                throw new IllegalArgumentException("macdFastPeriod must be less than macdSlowPeriod");
            }
            if (minRelativeSwing <= 0.0 || minRelativeSwing > 1.0) {
                throw new IllegalArgumentException("minRelativeSwing must be in (0.0, 1.0]");
            }
            this.minConfidence = minConfidence;
            this.minRiskReward = minRiskReward;
            this.minAlternationRatio = minAlternationRatio;
            this.minTrendBiasStrength = minTrendBiasStrength;
            this.trendSmaPeriod = trendSmaPeriod;
            this.rsiPeriod = rsiPeriod;
            this.rsiThreshold = rsiThreshold;
            this.macdFastPeriod = macdFastPeriod;
            this.macdSlowPeriod = macdSlowPeriod;
            this.minRelativeSwing = minRelativeSwing;
        }

        static Config defaults() {
            return new Config(DEFAULT_DIRECTION, DEFAULT_DEGREE, DEFAULT_MIN_CONFIDENCE, DEFAULT_MIN_RISK_REWARD,
                    DEFAULT_MIN_ALTERNATION_RATIO, DEFAULT_MIN_TREND_BIAS_STRENGTH, DEFAULT_TREND_SMA_PERIOD,
                    DEFAULT_RSI_PERIOD, DEFAULT_RSI_THRESHOLD, DEFAULT_MACD_FAST, DEFAULT_MACD_SLOW,
                    DEFAULT_MIN_RELATIVE_SWING);
        }

        static Config fromParameters(final String... params) {
            if (params == null) {
                throw new IllegalArgumentException("Params cannot be null");
            }
            if (params.length == 0) {
                return defaults();
            }
            if (params.length != PARAMETER_COUNT) {
                throw new IllegalArgumentException("Expected " + PARAMETER_COUNT
                        + " parameters (direction, degree, minConfidence, minRiskReward, minAlternationRatio, "
                        + "minTrendBiasStrength, trendSmaPeriod, rsiPeriod, rsiThreshold, macdFastPeriod, "
                        + "macdSlowPeriod, minRelativeSwing), but got " + params.length);
            }

            SignalDirection direction = parseEnum(params[0], SignalDirection.class, "direction");
            ElliottDegree degree = parseEnum(params[1], ElliottDegree.class, "degree");
            double minConfidence = parseDouble(params[2], "minConfidence");
            double minRiskReward = parseDouble(params[3], "minRiskReward");
            double minAlternation = parseDouble(params[4], "minAlternationRatio");
            double minTrendBias = parseDouble(params[5], "minTrendBiasStrength");
            int trendSma = parseInt(params[6], "trendSmaPeriod");
            int rsiPeriod = parseInt(params[7], "rsiPeriod");
            double rsiThreshold = parseDouble(params[8], "rsiThreshold");
            int macdFast = parseInt(params[9], "macdFastPeriod");
            int macdSlow = parseInt(params[10], "macdSlowPeriod");
            double minRelativeSwing = parseDouble(params[11], "minRelativeSwing");

            return new Config(direction, degree, minConfidence, minRiskReward, minAlternation, minTrendBias, trendSma,
                    rsiPeriod, rsiThreshold, macdFast, macdSlow, minRelativeSwing);
        }

        String[] labelParts() {
            return new String[] { direction.name(), degree.name(), formatDouble(minConfidence),
                    formatDouble(minRiskReward), formatDouble(minAlternationRatio), formatDouble(minTrendBiasStrength),
                    String.valueOf(trendSmaPeriod), String.valueOf(rsiPeriod), formatDouble(rsiThreshold),
                    String.valueOf(macdFastPeriod), String.valueOf(macdSlowPeriod), formatDouble(minRelativeSwing) };
        }

        SignalDirection direction() {
            return direction;
        }

        ElliottDegree degree() {
            return degree;
        }

        double minConfidence() {
            return minConfidence;
        }

        double minRiskReward() {
            return minRiskReward;
        }

        double minAlternationRatio() {
            return minAlternationRatio;
        }

        double minTrendBiasStrength() {
            return minTrendBiasStrength;
        }

        int trendSmaPeriod() {
            return trendSmaPeriod;
        }

        int rsiPeriod() {
            return rsiPeriod;
        }

        double rsiThreshold() {
            return rsiThreshold;
        }

        int macdFastPeriod() {
            return macdFastPeriod;
        }

        int macdSlowPeriod() {
            return macdSlowPeriod;
        }

        double minRelativeSwing() {
            return minRelativeSwing;
        }

        private static int parseInt(final String value, final String label) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid " + label + " value: '" + value + "'", ex);
            }
        }

        private static double parseDouble(final String value, final String label) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid " + label + " value: '" + value + "'", ex);
            }
        }

        private static <E extends Enum<E>> E parseEnum(final String value, final Class<E> type, final String label) {
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException ex) {
                Set<String> allowed = enumNames(type);
                throw new IllegalArgumentException(
                        "Invalid " + label + " value: '" + value + "'. Valid values are: " + String.join(", ", allowed),
                        ex);
            }
        }

        private static <E extends Enum<E>> Set<String> enumNames(final Class<E> type) {
            if (type == null) {
                return Set.of();
            }
            return EnumSet.allOf(type).stream().map(Enum::name).collect(Collectors.toSet());
        }
    }

    private record RuleBundle(Rule entryRule, Rule exitRule, int unstableBars) {
    }

    private record TrendBias(SignalDirection direction, double strength) {

        private static TrendBias unknown() {
            return new TrendBias(null, 0.0);
        }

        private boolean isNeutral() {
            return direction == null || strength <= 0.0;
        }
    }

    private static final class ScenarioSetIndicator extends CachedIndicator<ElliottScenarioSet> {

        private final ElliottSwingIndicator swingIndicator;
        private final ElliottChannelIndicator channelIndicator;
        private final ElliottScenarioGenerator generator;
        private final ElliottSwingCompressor compressor;
        private final int scenarioSwingWindow;

        private ScenarioSetIndicator(final BarSeries series, final ElliottSwingIndicator swingIndicator,
                final ElliottChannelIndicator channelIndicator, final ElliottScenarioGenerator generator,
                final ElliottSwingCompressor compressor, final int scenarioSwingWindow) {
            super(series);
            this.swingIndicator = Objects.requireNonNull(swingIndicator, "swingIndicator");
            this.channelIndicator = Objects.requireNonNull(channelIndicator, "channelIndicator");
            this.generator = Objects.requireNonNull(generator, "generator");
            this.compressor = compressor;
            this.scenarioSwingWindow = scenarioSwingWindow;
        }

        @Override
        protected ElliottScenarioSet calculate(final int index) {
            final BarSeries series = getBarSeries();
            if (series.isEmpty()) {
                throw new IllegalArgumentException("series cannot be empty");
            }
            int clampedIndex = Math.max(series.getBeginIndex(), Math.min(index, series.getEndIndex()));
            List<ElliottSwing> swings = swingIndicator.getValue(clampedIndex);
            if (compressor != null) {
                swings = compressor.compress(swings);
            }
            if (swings.isEmpty()) {
                return ElliottScenarioSet.empty(clampedIndex);
            }
            List<ElliottSwing> recent = swings;
            if (scenarioSwingWindow > 0 && swings.size() > scenarioSwingWindow) {
                recent = List.copyOf(swings.subList(swings.size() - scenarioSwingWindow, swings.size()));
            }
            return generator.generate(recent, swingIndicator.getDegree(), channelIndicator.getValue(clampedIndex),
                    clampedIndex);
        }

        @Override
        public int getCountOfUnstableBars() {
            return swingIndicator.getCountOfUnstableBars();
        }
    }
}
