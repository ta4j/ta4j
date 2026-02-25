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
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.NotRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.rules.elliott.ElliottImpulsePhaseRule;
import org.ta4j.core.rules.elliott.ElliottScenarioAlternationRule;
import org.ta4j.core.rules.elliott.ElliottScenarioCompletionRule;
import org.ta4j.core.rules.elliott.ElliottScenarioConfidenceRule;
import org.ta4j.core.rules.elliott.ElliottScenarioDirectionRule;
import org.ta4j.core.rules.elliott.ElliottScenarioInvalidationRule;
import org.ta4j.core.rules.elliott.ElliottScenarioRiskRewardRule;
import org.ta4j.core.rules.elliott.ElliottScenarioStopViolationRule;
import org.ta4j.core.rules.elliott.ElliottScenarioTargetReachedRule;
import org.ta4j.core.rules.elliott.ElliottScenarioTimeStopRule;
import org.ta4j.core.rules.elliott.ElliottScenarioValidRule;
import org.ta4j.core.rules.elliott.ElliottTrendBiasRule;
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

    /**
     * Builds the strategy using a precomputed scenario indicator.
     *
     * @param series            bar series to analyze
     * @param config            strategy configuration
     * @param scenarioIndicator indicator supplying scenario sets
     */
    HighRewardElliottWaveStrategy(final BarSeries series, final Config config,
            final Indicator<ElliottScenarioSet> scenarioIndicator) {
        this(config, buildEntryRule(series, config, scenarioIndicator),
                buildExitRule(series, config, scenarioIndicator), calculateUnstableBars(config));
    }

    /**
     * Builds the strategy with the default scenario indicator pipeline.
     *
     * @param series bar series to analyze
     * @param config strategy configuration
     */
    private HighRewardElliottWaveStrategy(final BarSeries series, final Config config) {
        this(series, config, buildScenarioIndicator(series, config));
    }

    /**
     * Internal constructor that wires the prepared rules into the named strategy.
     *
     * @param config       strategy configuration
     * @param entryRule    precomputed entry rule
     * @param exitRule     precomputed exit rule
     * @param unstableBars unstable bar count for warm-up
     */
    private HighRewardElliottWaveStrategy(final Config config, final Rule entryRule, final Rule exitRule,
            final int unstableBars) {
        super(buildLabel(config), entryRule, exitRule, unstableBars);
    }

    /**
     * Builds the entry rule for the strategy.
     *
     * @param series            bar series backing indicators
     * @param config            strategy configuration
     * @param scenarioIndicator indicator supplying scenario sets
     * @return entry rule
     */
    private static Rule buildEntryRule(final BarSeries series, final Config config,
            final Indicator<ElliottScenarioSet> scenarioIndicator) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        validateScenarioIndicator(series, scenarioIndicator);

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator trendSma = new SMAIndicator(close, config.trendSmaPeriod());
        RSIIndicator rsi = new RSIIndicator(close, config.rsiPeriod());
        MACDIndicator macd = new MACDIndicator(close, config.macdFastPeriod(), config.macdSlowPeriod());

        Rule trendRule = config.direction().isBullish() ? new OverIndicatorRule(close, trendSma)
                : new UnderIndicatorRule(close, trendSma);

        Rule momentumRule = config.direction().isBullish()
                ? new OverIndicatorRule(rsi, config.rsiThreshold()).or(new OverIndicatorRule(macd, 0))
                : new UnderIndicatorRule(rsi, config.rsiThreshold()).or(new UnderIndicatorRule(macd, 0));

        Rule scenarioValidRule = new ElliottScenarioValidRule(scenarioIndicator, close);
        Rule impulsePhaseRule = new ElliottImpulsePhaseRule(scenarioIndicator, ElliottPhase.WAVE3, ElliottPhase.WAVE5);
        Rule confidenceRule = new ElliottScenarioConfidenceRule(scenarioIndicator, config.minConfidence());
        Rule directionRule = new ElliottScenarioDirectionRule(scenarioIndicator, config.direction().isBullish());
        Rule trendBiasRule = new ElliottTrendBiasRule(scenarioIndicator, config.direction().isBullish(),
                config.minTrendBiasStrength());
        Rule alternationRule = new ElliottScenarioAlternationRule(scenarioIndicator, config.minAlternationRatio());
        Rule riskRewardRule = new ElliottScenarioRiskRewardRule(scenarioIndicator, close,
                config.direction().isBullish(), config.minRiskReward());

        Rule entryRule = scenarioValidRule.and(impulsePhaseRule)
                .and(confidenceRule)
                .and(directionRule)
                .and(trendBiasRule)
                .and(alternationRule)
                .and(riskRewardRule)
                .and(trendRule)
                .and(momentumRule);

        return entryRule;
    }

    /**
     * Builds the exit rule for the strategy.
     *
     * @param series            bar series backing indicators
     * @param config            strategy configuration
     * @param scenarioIndicator indicator supplying scenario sets
     * @return exit rule
     */
    private static Rule buildExitRule(final BarSeries series, final Config config,
            final Indicator<ElliottScenarioSet> scenarioIndicator) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        validateScenarioIndicator(series, scenarioIndicator);

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator trendSma = new SMAIndicator(close, config.trendSmaPeriod());
        RSIIndicator rsi = new RSIIndicator(close, config.rsiPeriod());
        MACDIndicator macd = new MACDIndicator(close, config.macdFastPeriod(), config.macdSlowPeriod());

        Rule trendRule = config.direction().isBullish() ? new OverIndicatorRule(close, trendSma)
                : new UnderIndicatorRule(close, trendSma);

        Rule momentumRule = config.direction().isBullish()
                ? new OverIndicatorRule(rsi, config.rsiThreshold()).or(new OverIndicatorRule(macd, 0))
                : new UnderIndicatorRule(rsi, config.rsiThreshold()).or(new UnderIndicatorRule(macd, 0));

        Rule scenarioValidRule = new ElliottScenarioValidRule(scenarioIndicator, close);
        Rule directionRule = new ElliottScenarioDirectionRule(scenarioIndicator, config.direction().isBullish());
        Rule trendBiasRule = new ElliottTrendBiasRule(scenarioIndicator, config.direction().isBullish(),
                config.minTrendBiasStrength());

        Rule exitTriggers = new NotRule(scenarioValidRule).or(new NotRule(directionRule))
                .or(new ElliottScenarioCompletionRule(scenarioIndicator))
                .or(new ElliottScenarioInvalidationRule(scenarioIndicator, close))
                .or(new ElliottScenarioTargetReachedRule(scenarioIndicator, close, config.direction().isBullish()))
                .or(new ElliottScenarioStopViolationRule(scenarioIndicator, close, config.direction().isBullish()))
                .or(new NotRule(trendBiasRule))
                .or(new NotRule(trendRule.and(momentumRule)))
                .or(new ElliottScenarioTimeStopRule(scenarioIndicator, MAX_WAVE_DURATION_MULTIPLIER));

        return exitTriggers;
    }

    /**
     * Validates that the scenario indicator is bound to the same series instance as
     * the strategy.
     *
     * @param series            strategy series
     * @param scenarioIndicator scenario source indicator
     */
    private static void validateScenarioIndicator(final BarSeries series,
            final Indicator<ElliottScenarioSet> scenarioIndicator) {
        if (scenarioIndicator.getBarSeries() != series) {
            throw new IllegalArgumentException("scenarioIndicator must use the same BarSeries instance");
        }
    }

    /**
     * Builds the scenario indicator pipeline used by the strategy.
     *
     * @param series bar series to analyze
     * @param config strategy configuration
     * @return scenario indicator
     */
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

    /**
     * Builds the named-strategy label for the configured parameters.
     *
     * @param config strategy configuration
     * @return label string
     */
    private static String buildLabel(final Config config) {
        return NamedStrategy.buildLabel(HighRewardElliottWaveStrategy.class, config.labelParts());
    }

    /**
     * Calculates the number of unstable bars for indicator warm-up.
     *
     * @param config strategy configuration
     * @return unstable bar count
     */
    private static int calculateUnstableBars(final Config config) {
        int unstable = Math.max(config.trendSmaPeriod(), Math.max(config.rsiPeriod(), config.macdSlowPeriod()));
        unstable = Math.max(unstable, DEFAULT_ATR_PERIOD);
        return unstable;
    }

    /**
     * Formats a double for strategy labels without trailing zeros.
     *
     * @param value numeric value
     * @return formatted string
     */
    private static String formatDouble(final double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /**
     * Trade direction for the strategy.
     */
    enum SignalDirection {
        BULLISH, BEARISH;

        /**
         * @return {@code true} when the direction is bullish
         */
        boolean isBullish() {
            return this == BULLISH;
        }
    }

    /**
     * Immutable configuration for the strategy.
     */
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

        /**
         * Creates a configuration with the supplied parameters.
         *
         * @param direction            trade direction
         * @param degree               Elliott wave degree to analyze
         * @param minConfidence        minimum confidence threshold
         * @param minRiskReward        minimum risk/reward ratio
         * @param minAlternationRatio  minimum alternation duration ratio
         * @param minTrendBiasStrength minimum trend bias strength
         * @param trendSmaPeriod       SMA period for trend confirmation
         * @param rsiPeriod            RSI period for momentum confirmation
         * @param rsiThreshold         RSI threshold for momentum
         * @param macdFastPeriod       MACD fast period
         * @param macdSlowPeriod       MACD slow period
         * @param minRelativeSwing     minimum relative swing magnitude
         */
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

        /**
         * @return default configuration values
         */
        static Config defaults() {
            return new Config(DEFAULT_DIRECTION, DEFAULT_DEGREE, DEFAULT_MIN_CONFIDENCE, DEFAULT_MIN_RISK_REWARD,
                    DEFAULT_MIN_ALTERNATION_RATIO, DEFAULT_MIN_TREND_BIAS_STRENGTH, DEFAULT_TREND_SMA_PERIOD,
                    DEFAULT_RSI_PERIOD, DEFAULT_RSI_THRESHOLD, DEFAULT_MACD_FAST, DEFAULT_MACD_SLOW,
                    DEFAULT_MIN_RELATIVE_SWING);
        }

        /**
         * Parses a serialized parameter list into a configuration.
         *
         * @param params serialized parameters
         * @return parsed configuration
         */
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

        /**
         * @return label parts used for NamedStrategy labels
         */
        String[] labelParts() {
            return new String[] { direction.name(), degree.name(), formatDouble(minConfidence),
                    formatDouble(minRiskReward), formatDouble(minAlternationRatio), formatDouble(minTrendBiasStrength),
                    String.valueOf(trendSmaPeriod), String.valueOf(rsiPeriod), formatDouble(rsiThreshold),
                    String.valueOf(macdFastPeriod), String.valueOf(macdSlowPeriod), formatDouble(minRelativeSwing) };
        }

        /**
         * @return configured trade direction
         */
        SignalDirection direction() {
            return direction;
        }

        /**
         * @return configured Elliott wave degree
         */
        ElliottDegree degree() {
            return degree;
        }

        /**
         * @return minimum confidence threshold
         */
        double minConfidence() {
            return minConfidence;
        }

        /**
         * @return minimum risk/reward ratio
         */
        double minRiskReward() {
            return minRiskReward;
        }

        /**
         * @return minimum alternation ratio
         */
        double minAlternationRatio() {
            return minAlternationRatio;
        }

        /**
         * @return minimum trend bias strength
         */
        double minTrendBiasStrength() {
            return minTrendBiasStrength;
        }

        /**
         * @return trend SMA period
         */
        int trendSmaPeriod() {
            return trendSmaPeriod;
        }

        /**
         * @return RSI period
         */
        int rsiPeriod() {
            return rsiPeriod;
        }

        /**
         * @return RSI threshold
         */
        double rsiThreshold() {
            return rsiThreshold;
        }

        /**
         * @return MACD fast period
         */
        int macdFastPeriod() {
            return macdFastPeriod;
        }

        /**
         * @return MACD slow period
         */
        int macdSlowPeriod() {
            return macdSlowPeriod;
        }

        /**
         * @return minimum relative swing magnitude
         */
        double minRelativeSwing() {
            return minRelativeSwing;
        }

        /**
         * Parses an integer parameter.
         *
         * @param value parameter value
         * @param label parameter label
         * @return parsed integer
         */
        private static int parseInt(final String value, final String label) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid " + label + " value: '" + value + "'", ex);
            }
        }

        /**
         * Parses a double parameter.
         *
         * @param value parameter value
         * @param label parameter label
         * @return parsed double
         */
        private static double parseDouble(final String value, final String label) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid " + label + " value: '" + value + "'", ex);
            }
        }

        /**
         * Parses an enum parameter.
         *
         * @param value parameter value
         * @param type  enum type
         * @param label parameter label
         * @param <E>   enum type
         * @return parsed enum
         */
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

        /**
         * Returns all allowed enum names for an error message.
         *
         * @param type enum type
         * @param <E>  enum type
         * @return set of enum names
         */
        private static <E extends Enum<E>> Set<String> enumNames(final Class<E> type) {
            if (type == null) {
                return Set.of();
            }
            return EnumSet.allOf(type).stream().map(Enum::name).collect(Collectors.toSet());
        }
    }

    /**
     * Cached indicator that assembles scenario sets for each bar index.
     */
    private static final class ScenarioSetIndicator extends CachedIndicator<ElliottScenarioSet> {

        private final ElliottSwingIndicator swingIndicator;
        private final ElliottChannelIndicator channelIndicator;
        private final ElliottScenarioGenerator generator;
        private final ElliottSwingCompressor compressor;
        private final int scenarioSwingWindow;

        /**
         * Creates a scenario indicator with the supplied dependencies.
         *
         * @param series              bar series
         * @param swingIndicator      swing detector indicator
         * @param channelIndicator    channel indicator for scoring
         * @param generator           scenario generator
         * @param compressor          optional swing compressor
         * @param scenarioSwingWindow max number of swings to score
         */
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

        /**
         * Computes the scenario set for the provided index.
         *
         * @param index bar index
         * @return scenario set for the index
         */
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

        /**
         * @return the number of unstable bars for the underlying swing indicator
         */
        @Override
        public int getCountOfUnstableBars() {
            return swingIndicator.getCountOfUnstableBars();
        }
    }
}
