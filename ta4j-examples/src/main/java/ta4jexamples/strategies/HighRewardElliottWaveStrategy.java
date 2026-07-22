/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import java.util.Objects;
import java.util.Set;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.EmpiricalElliottWaveForecastIndicator;
import org.ta4j.core.indicators.elliott.EmpiricalElliottWaveForecastIndicator.WaveForecast;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AbstractRule;
import org.ta4j.core.rules.AverageTrueRangeTrailingStopLossRule;
import org.ta4j.core.rules.StopGainRule;
import org.ta4j.core.rules.StopLossRule;
import org.ta4j.core.rules.TrailingStopGainRule;
import org.ta4j.core.rules.WaitForRule;

/**
 * Trades empirically recurring intraday Elliott impulse phases while refusing
 * to invent a count when the series has no comparable historical structure.
 *
 * <h2>Root thesis</h2>
 * <p>
 * Elliott labels are useful here as a compact description of market position,
 * not as a claim that every chart must contain a deterministic five-wave
 * pattern. One-minute and five-minute crypto bars repeatedly alternate between
 * expansion and retracement. When the causal Elliott pipeline recognized that
 * structure in earlier bars, similar ATR-normalized return, range, and volume
 * conditions provide an empirical distribution over the likely impulse phase at
 * the current decision bar. The strategy acts only when that distribution is
 * sufficiently concentrated; no historical structure means no forecast and no
 * trade.
 *
 * <p>
 * Long entries target the asymmetric parts of a bullish impulse: the beginning
 * of wave 1 and confirmed turns from the bottoms of waves 2 and 4. Wave phase
 * alone is insufficient, so the entry must also confirm a three-bar local
 * trough. Exits require a corresponding local crest or a phase transition out
 * of waves 1, 3, and 5. Because any wave interpretation can fail abruptly,
 * those thesis exits are OR-composed with a fixed stop, profit target, trailing
 * gain protection, ATR trailing stop, and maximum holding time. The wave
 * forecast seeks opportunity; the composite stack controls the cost of being
 * wrong.
 *
 * <p>
 * This example intentionally has no legacy serialized-label constructor or
 * compatibility parser. Configure it through {@link Settings}; the earlier
 * direction, risk/reward, alternation, SMA, RSI, and MACD surfaces described a
 * different strategy and have been removed rather than deprecated.
 *
 * @since 0.22.2
 */
public final class HighRewardElliottWaveStrategy extends BaseStrategy {

    private static final String NAME = "HighRewardElliottWaveStrategy";

    /**
     * Builds the one-minute/five-minute strategy with conservative defaults.
     *
     * @param series intraday bar series
     */
    public HighRewardElliottWaveStrategy(final BarSeries series) {
        this(series, Settings.intradayDefaults());
    }

    /**
     * Builds the strategy with explicit forecast and risk settings.
     *
     * @param series   intraday bar series
     * @param settings strategy settings
     */
    public HighRewardElliottWaveStrategy(final BarSeries series, final Settings settings) {
        this(settings, prepare(series, settings,
                new EmpiricalElliottWaveForecastIndicator(series, settings.forecastSettings())));
    }

    HighRewardElliottWaveStrategy(final BarSeries series, final Settings settings,
            final Indicator<WaveForecast> forecast) {
        this(settings, prepare(series, settings, forecast));
    }

    private HighRewardElliottWaveStrategy(final Settings settings, final PreparedRules rules) {
        super(NAME, rules.entryRule(), rules.exitRule(), rules.unstableBars());
        Objects.requireNonNull(settings, "settings");
    }

    private static PreparedRules prepare(final BarSeries series, final Settings settings,
            final Indicator<WaveForecast> forecast) {
        Objects.requireNonNull(series, "series");
        Settings validatedSettings = Objects.requireNonNull(settings, "settings");
        Indicator<WaveForecast> validatedForecast = Objects.requireNonNull(forecast, "forecast");
        if (!IndicatorUtils.isSameSeries(series, validatedForecast.getBarSeries())) {
            throw new IllegalArgumentException("forecast must use the same BarSeries instance");
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Rule entry = new WaveEntryRule(close, validatedForecast, validatedSettings.minimumPhaseProbability());
        Rule wavePeak = new WavePeakExitRule(close, validatedForecast, validatedSettings.minimumPhaseProbability());
        Rule composite = new StopLossRule(close, validatedSettings.fixedStopLossPercentage())
                .or(new StopGainRule(close, validatedSettings.profitTargetPercentage()))
                .or(new TrailingStopGainRule(close,
                        series.numFactory().numOf(validatedSettings.trailingStopPercentage()),
                        validatedSettings.trailingLookbackBars()))
                .or(new AverageTrueRangeTrailingStopLossRule(series, close, validatedSettings.atrBarCount(),
                        validatedSettings.atrMultiplier(), validatedSettings.trailingLookbackBars()))
                .or(new WaitForRule(Trade.TradeType.BUY, validatedSettings.maximumHoldingBars()));
        return new PreparedRules(entry, wavePeak.or(composite), validatedForecast.getCountOfUnstableBars());
    }

    /**
     * Settings for the empirical entry and composite exit stack.
     *
     * @param forecastSettings        empirical Elliott forecast settings
     * @param minimumPhaseProbability minimum modal phase probability
     * @param fixedStopLossPercentage fixed loss from entry, in percent
     * @param profitTargetPercentage  direct target from entry, in percent
     * @param trailingStopPercentage  favorable-excursion giveback, in percent
     * @param trailingLookbackBars    trailing high and ATR lookback
     * @param atrBarCount             ATR window
     * @param atrMultiplier           ATR trailing-stop multiplier
     * @param maximumHoldingBars      timeout after entry
     */
    public record Settings(EmpiricalElliottWaveForecastIndicator.Settings forecastSettings,
            double minimumPhaseProbability, double fixedStopLossPercentage, double profitTargetPercentage,
            double trailingStopPercentage, int trailingLookbackBars, int atrBarCount, double atrMultiplier,
            int maximumHoldingBars) {

        public Settings {
            Objects.requireNonNull(forecastSettings, "forecastSettings");
            requireUnitInterval("minimumPhaseProbability", minimumPhaseProbability);
            requireNonNegative("fixedStopLossPercentage", fixedStopLossPercentage);
            requireNonNegative("profitTargetPercentage", profitTargetPercentage);
            requireNonNegative("trailingStopPercentage", trailingStopPercentage);
            if (trailingLookbackBars <= 0 || atrBarCount <= 0 || maximumHoldingBars <= 0) {
                throw new IllegalArgumentException("bar counts must be > 0");
            }
            if (!Double.isFinite(atrMultiplier) || atrMultiplier <= 0.0d) {
                throw new IllegalArgumentException("atrMultiplier must be finite and > 0");
            }
        }

        /**
         * @return defaults for liquid one-minute or five-minute crypto bars
         */
        public static Settings intradayDefaults() {
            return new Settings(EmpiricalElliottWaveForecastIndicator.Settings.intradayDefaults(), 0.55d, 1.5d, 4.0d,
                    1.25d, 120, 14, 3.0d, 360);
        }

        private static void requireUnitInterval(final String name, final double value) {
            if (!Double.isFinite(value) || value <= 0.0d || value > 1.0d) {
                throw new IllegalArgumentException(name + " must be in (0, 1]");
            }
        }

        private static void requireNonNegative(final String name, final double value) {
            if (!Double.isFinite(value) || value < 0.0d) {
                throw new IllegalArgumentException(name + " must be finite and >= 0");
            }
        }
    }

    private static final class WaveEntryRule extends AbstractRule {

        private static final Set<ElliottPhase> ENTRY_PHASES = Set.of(ElliottPhase.WAVE1, ElliottPhase.WAVE2,
                ElliottPhase.WAVE4);

        private final ClosePriceIndicator close;
        private final Indicator<WaveForecast> forecast;
        private final double minimumProbability;

        private WaveEntryRule(final ClosePriceIndicator close, final Indicator<WaveForecast> forecast,
                final double minimumProbability) {
            this.close = close;
            this.forecast = forecast;
            this.minimumProbability = minimumProbability;
        }

        @Override
        public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
            if (index <= close.getBarSeries().getBeginIndex() + 1) {
                return false;
            }
            WaveForecast current = forecast.getValue(index);
            ElliottPhase phase = current.mostLikelyPhase();
            boolean upwardTurn = close.getValue(index).isGreaterThan(close.getValue(index - 1))
                    && close.getValue(index - 1).isLessThanOrEqual(close.getValue(index - 2));
            boolean satisfied = current.isStable() && ENTRY_PHASES.contains(phase)
                    && meetsProbability(current, minimumProbability) && upwardTurn;
            traceIsSatisfied(index, satisfied);
            return satisfied;
        }
    }

    private static final class WavePeakExitRule extends AbstractRule {

        private static final Set<ElliottPhase> PEAK_PHASES = Set.of(ElliottPhase.WAVE1, ElliottPhase.WAVE3,
                ElliottPhase.WAVE5);

        private final ClosePriceIndicator close;
        private final Indicator<WaveForecast> forecast;
        private final double minimumProbability;

        private WavePeakExitRule(final ClosePriceIndicator close, final Indicator<WaveForecast> forecast,
                final double minimumProbability) {
            this.close = close;
            this.forecast = forecast;
            this.minimumProbability = minimumProbability;
        }

        @Override
        public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
            if (index <= close.getBarSeries().getBeginIndex() + 1) {
                return false;
            }
            WaveForecast current = forecast.getValue(index);
            WaveForecast previous = forecast.getValue(index - 1);
            boolean downwardTurn = close.getValue(index).isLessThan(close.getValue(index - 1))
                    && close.getValue(index - 1).isGreaterThanOrEqual(close.getValue(index - 2));
            boolean currentPeakReversal = current.isStable() && PEAK_PHASES.contains(current.mostLikelyPhase())
                    && meetsProbability(current, minimumProbability) && downwardTurn;
            boolean transitionedFromPeak = previous.isStable() && PEAK_PHASES.contains(previous.mostLikelyPhase())
                    && meetsProbability(previous, minimumProbability) && current.isStable()
                    && current.mostLikelyPhase() != previous.mostLikelyPhase();
            boolean satisfied = currentPeakReversal || transitionedFromPeak;
            traceIsSatisfied(index, satisfied);
            return satisfied;
        }
    }

    private static boolean meetsProbability(final WaveForecast forecast, final double threshold) {
        Num probability = forecast.probability();
        return !Num.isNaNOrNull(probability)
                && probability.isGreaterThanOrEqual(probability.getNumFactory().numOf(threshold));
    }

    private record PreparedRules(Rule entryRule, Rule exitRule, int unstableBars) {
    }
}
