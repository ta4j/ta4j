/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.stream.Stream;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.ExcessReturns;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.frequency.Sample;
import org.ta4j.core.analysis.frequency.SampleSummary;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Computes the Sharpe Ratio.
 *
 * <p>
 * <b>Definition.</b> The Sharpe Ratio is defined as {@code SR = μ / σ}, where
 * {@code μ} is the expected value of excess returns and {@code σ} is the
 * standard deviation of excess returns.
 *
 * <p>
 * <b>What this criterion measures.</b> This implementation builds a time series
 * of <em>excess returns</em> from the {@link CashFlow} equity curve. For each
 * sampled pair {@code (previousIndex, currentIndex)}, it compounds per-bar
 * excess growth factors between the two indices (so mixed in/out-of-market bars
 * are handled correctly) and converts the compounded growth into an excess
 * return. It then returns {@code mean(excessReturn) / stdev(excessReturn)}
 * using the sample standard deviation ({@code N - 1}).
 *
 * <p>
 * <b>Sampling (aggregation) of returns.</b> The {@link SamplingFrequency}
 * parameter controls how the return series is formed:
 * <ul>
 * <li>{@link SamplingFrequency#BAR}: one return per bar, using consecutive bar
 * indices.</li>
 * <li>{@link SamplingFrequency#SECOND}/{@link SamplingFrequency#MINUTE}/{@link SamplingFrequency#HOUR}/{@link SamplingFrequency#DAY}/{@link SamplingFrequency#WEEK}/{@link SamplingFrequency#MONTH}:
 * returns are computed between period endpoints detected from bar
 * {@code endTime} after converting it to {@link #groupingZoneId}. Period
 * boundaries follow ISO week semantics for {@code WEEKLY}.</li>
 * <li>{@link SamplingFrequency#TRADE}: one return per position interval
 * (entry-to-exit), with open positions included only when
 * {@link OpenPositionHandling#MARK_TO_MARKET} is effective.</li>
 * </ul>
 * For time-based sampling ({@link SamplingFrequency#BAR},
 * {@link SamplingFrequency#SECOND}, {@link SamplingFrequency#MINUTE},
 * {@link SamplingFrequency#HOUR}, {@link SamplingFrequency#DAY},
 * {@link SamplingFrequency#WEEK}, {@link SamplingFrequency#MONTH}), the first
 * sampled return is anchored at the series begin index, even when evaluating a
 * single {@link Position}. Trade sampling uses each position interval directly.
 * Pre-entry intervals are handled by {@link CashReturnPolicy}:
 * {@link CashReturnPolicy#CASH_EARNS_RISK_FREE} keeps flat pre-entry equity
 * neutral, while {@link CashReturnPolicy#CASH_EARNS_ZERO} treats flat pre-entry
 * equity as underperforming cash.
 *
 * <p>
 * <b>Risk-free rate.</b> {@link #annualRiskFreeRate} is interpreted as an
 * annualized rate (e.g., 0.05 = 5% per year) and converted into a per-bar
 * compounded growth factor using the elapsed time between bar end times. If
 * {@code annualRiskFreeRate} is {@code null}, it is treated as zero.
 *
 * <p>
 * <b>Cash return policy.</b> {@link CashReturnPolicy#CASH_EARNS_RISK_FREE}
 * makes flat equity intervals benchmark-neutral (approx. zero excess), while
 * {@link CashReturnPolicy#CASH_EARNS_ZERO} treats flat equity as
 * underperforming cash and contributes negative excess returns.
 *
 * <p>
 * <b>Annualization.</b> When {@link Annualization#PERIOD}, the returned Sharpe
 * is per sampling period (no scaling). When {@link Annualization#ANNUALIZED},
 * the per-period Sharpe is multiplied by {@code sqrt(periodsPerYear)} where
 * {@code periodsPerYear} is estimated from observed time deltas (count of
 * positive deltas divided by the sum of deltas in years).
 *
 * <p>
 * <b>Trading record vs. position.</b> Sharpe ratio requires a distribution of
 * returns across periods. A single {@link Position} can still provide multiple
 * sampled excess returns when the series spans multiple bars, so
 * {@link #calculate(BarSeries, Position)} now evaluates the position using the
 * same sampling logic as a trading record.
 *
 * <p>
 * <b>Open positions.</b> When {@link #openPositionHandling} is
 * {@link OpenPositionHandling#MARK_TO_MARKET}, the current open position (if
 * any) contributes to both invested intervals and cash-flow accrual. When
 * {@link OpenPositionHandling#IGNORE}, only closed positions are considered for
 * the return series and position count. When {@link #equityCurveMode} is
 * {@link EquityCurveMode#REALIZED}, open positions are ignored regardless of
 * {@link #openPositionHandling}, so interim price movements do not affect
 * returns.
 *
 * @since 0.22.2
 *
 */
public class SharpeRatioCriterion extends AbstractAnalysisCriterion {

    private final SamplingFrequency samplingFrequency;
    private final Annualization annualization;
    private final CashReturnPolicy cashReturnPolicy;
    private final double annualRiskFreeRate;
    private final ZoneId groupingZoneId;
    private final EquityCurveMode equityCurveMode;
    private final OpenPositionHandling openPositionHandling;

    /**
     * Creates a Sharpe ratio criterion using a zero risk-free rate, per-bar
     * sampling, annualized scaling, UTC grouping, and
     * {@link CashReturnPolicy#CASH_EARNS_RISK_FREE}.
     *
     * @since 0.22.2
     */
    public SharpeRatioCriterion() {
        this(0, SamplingFrequency.BAR, Annualization.ANNUALIZED, ZoneOffset.UTC, CashReturnPolicy.CASH_EARNS_RISK_FREE,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Creates a Sharpe ratio criterion with a custom annual risk-free rate, per-bar
     * sampling, annualized scaling, UTC grouping, and
     * {@link CashReturnPolicy#CASH_EARNS_RISK_FREE}.
     *
     * @param annualRiskFreeRate the annual risk-free rate (e.g. 0.05 for 5%)
     * @since 0.22.2
     */
    public SharpeRatioCriterion(double annualRiskFreeRate) {
        this(annualRiskFreeRate, SamplingFrequency.BAR, Annualization.ANNUALIZED, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Creates a Sharpe ratio criterion with explicit sampling, annualization, and
     * grouping timezone.
     *
     * @param annualRiskFreeRate the annual risk-free rate (e.g. 0.05 for 5%)
     * @param samplingFrequency  the sampling granularity
     * @param annualization      the annualization mode
     * @param groupingZoneId     the time zone used to interpret bar end times
     * @since 0.22.2
     */
    public SharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId) {
        this(annualRiskFreeRate, samplingFrequency, annualization, groupingZoneId,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Creates a Sharpe ratio criterion with explicit cash return handling.
     *
     * @param annualRiskFreeRate the annual risk-free rate (e.g. 0.05 for 5%)
     * @param samplingFrequency  the sampling granularity
     * @param annualization      the annualization mode
     * @param groupingZoneId     the time zone used to interpret bar end times
     * @param cashReturnPolicy   the policy for flat equity intervals
     * @since 0.22.2
     */
    public SharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId, CashReturnPolicy cashReturnPolicy) {
        this(annualRiskFreeRate, samplingFrequency, annualization, groupingZoneId, cashReturnPolicy,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Creates a Sharpe ratio criterion with explicit cash return handling.
     *
     * @param annualRiskFreeRate   the annual risk-free rate (e.g. 0.05 for 5%)
     * @param samplingFrequency    the sampling granularity
     * @param annualization        the annualization mode
     * @param groupingZoneId       the time zone used to interpret bar end times
     * @param cashReturnPolicy     the policy for flat equity intervals
     * @param openPositionHandling how open positions should be handled
     * @since 0.22.2
     */
    public SharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId, CashReturnPolicy cashReturnPolicy,
            OpenPositionHandling openPositionHandling) {
        this(annualRiskFreeRate, samplingFrequency, annualization, groupingZoneId, cashReturnPolicy,
                EquityCurveMode.MARK_TO_MARKET, openPositionHandling);
    }

    /**
     * Creates a Sharpe ratio criterion with explicit cash return handling.
     *
     * @param annualRiskFreeRate   the annual risk-free rate (e.g. 0.05 for 5%)
     * @param samplingFrequency    the sampling granularity
     * @param annualization        the annualization mode
     * @param groupingZoneId       the time zone used to interpret bar end times
     * @param cashReturnPolicy     the policy for flat equity intervals
     * @param equityCurveMode      the equity curve calculation mode
     * @param openPositionHandling how open positions should be handled
     * @since 0.22.2
     */
    public SharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId, CashReturnPolicy cashReturnPolicy,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this.annualRiskFreeRate = annualRiskFreeRate;
        this.annualization = Objects.requireNonNull(annualization, "annualization must not be null");
        this.groupingZoneId = Objects.requireNonNull(groupingZoneId, "groupingZoneId must not be null");
        this.cashReturnPolicy = Objects.requireNonNull(cashReturnPolicy, "cashReturnPolicy must not be null");
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode, "equityCurveMode must not be null");
        this.openPositionHandling = Objects.requireNonNull(openPositionHandling,
                "openPositionHandling must not be null");
        this.samplingFrequency = Objects.requireNonNull(samplingFrequency, "samplingFrequency must not be null");
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null) {
            return series.numFactory().zero();
        }

        return calculate(series, new BaseTradingRecord(position));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        NumFactory numFactory = series.numFactory();
        Num zero = numFactory.zero();
        if (tradingRecord == null) {
            return zero;
        }
        OpenPositionHandling effectiveOpenPositionHandling = effectiveOpenPositionHandling();
        Num annualRiskFreeRateNum = numFactory.numOf(annualRiskFreeRate);
        ExcessReturns excessReturns = new ExcessReturns(series, annualRiskFreeRateNum, cashReturnPolicy, tradingRecord,
                equityCurveMode, effectiveOpenPositionHandling);
        Stream<Sample> samples = RatioSampleSupport.samples(series, tradingRecord, samplingFrequency, groupingZoneId,
                excessReturns, effectiveOpenPositionHandling);
        SampleSummary summary = SampleSummary.fromSamples(samples, numFactory);

        if (summary.count() < 2) {
            return zero;
        }

        Num stdev = summary.sampleVariance(numFactory).sqrt();
        if (stdev.isZero()) {
            return zero;
        }

        Num sharpePerPeriod = summary.mean().dividedBy(stdev);

        return annualization.apply(sharpePerPeriod, summary, numFactory);
    }

    private OpenPositionHandling effectiveOpenPositionHandling() {
        return equityCurveMode == EquityCurveMode.REALIZED ? OpenPositionHandling.IGNORE : openPositionHandling;
    }

    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }

}
