/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.forecast;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.montecarlo.MonteCarloContext;
import org.ta4j.core.analysis.montecarlo.MonteCarloMethod;
import org.ta4j.core.analysis.montecarlo.NormalInverseGammaForecastMethod;
import org.ta4j.core.analysis.montecarlo.PosteriorSmoothedResidualMonteCarloMethod;
import org.ta4j.core.analysis.montecarlo.ShockPathMonteCarloMethod;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.ShockModel;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.VolatilityUpdateMode;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Walk-forward calibration bake-off of the four swappable Monte Carlo
 * techniques introduced by the {@code MonteCarloMethod} seam (PR #1616).
 *
 * <p>
 * Each arm is one technique built through the public seam: the four
 * {@link ShockPathMonteCarloMethod} shock/volatility combinations plus the
 * Normal-Inverse-Gamma posterior-predictive method. Every arm shares the same
 * EWMA state, lookback window, horizon, iteration count, and per-origin random
 * seed, so the comparison is paired. The experiment drives the seam directly
 * (each origin builds a {@link MonteCarloContext} mirroring the shared
 * simulation engine's window assembly and deterministic seed derivation) so the
 * raw terminal cumulative log-return samples are available for a genuine
 * sample-based CRPS.
 *
 * <p>
 * Metrics at each origin {@code i} are scored against the realized
 * {@code h}-bar log return:
 * <ul>
 * <li>central-interval coverage: fraction of realized returns inside
 * {@code [q05, q95]} versus the 90% nominal;</li>
 * <li>pinball loss at the {@code q05}, {@code q50}, and {@code q95}
 * quantiles;</li>
 * <li>sample-based CRPS of the terminal cumulative-return distribution;</li>
 * <li>per-arm unstable rate (technique returned no stable result).</li>
 * </ul>
 *
 * <p>
 * Origins are additionally sliced by forward realized-volatility terciles
 * (RMS of the single-bar log returns over the forecast window), the regime
 * where the Normal-Inverse-Gamma iid-Normal likelihood is expected to miss
 * volatility clustering.
 *
 * <p>
 * The experiment is deterministic: results are written incrementally as JSON
 * under {@code temp/walk-forward-calibration/}.
 *
 * @since 0.24.2
 */
public final class WalkForwardCalibrationBakeoffExample {

    /** S&amp;P 500 weekly history, ~4k decisions at h=4. */
    static final String SP500_RESOURCE = "YahooFinance-SP500-PT7D-19500103_20260730.json";

    /** Coinbase ETH-USD daily history, ~9.5y. */
    static final String ETH_RESOURCE = "Coinbase-ETH-USD-PT1D-20160517_20251028.json";

    static final int HORIZON = 4;
    static final int LOOKBACK = 252;
    static final int ITERATION_COUNT = 1000;
    static final long SEED = 42L;
    static final double EWMA_DECAY = 0.94d;
    static final double NOMINAL_COVERAGE = 0.90d;
    static final double[] PINBALL_QUANTILES = new double[] { 0.05d, 0.5d, 0.95d };

    private static final Logger LOG = LogManager.getLogger(WalkForwardCalibrationBakeoffExample.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<DatasetSpec> DATASETS = List.of(
            new DatasetSpec(SP500_RESOURCE, "SP500-weekly", "sp500"),
            new DatasetSpec(ETH_RESOURCE, "ETH-USD-daily", "eth"));

    private WalkForwardCalibrationBakeoffExample() {
    }

    /**
     * Runs the calibration bake-off over the committed ossified datasets.
     *
     * @param args optional first argument is a positive whole-run decision cap to
     *             bound the walk forward (default: all usable origins)
     */
    public static void main(String[] args) {
        int decisionCap = DecisionCap.fromArgs(args);
        for (DatasetSpec dataset : DATASETS) {
            runBakeoff(dataset, decisionCap);
        }
        LOG.info("Bake-off complete for {} datasets; results under temp/walk-forward-calibration/.", DATASETS.size());
    }

    static void runBakeoff(DatasetSpec dataset, int decisionCap) {
        BarSeries series = loadSeries(dataset.resource());
        LogReturnIndicator returns = new LogReturnIndicator(series);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns);
        List<ArmSpec> arms = arms();

        int endIndex = series.getEndIndex();
        int lastDecision = endIndex - HORIZON;
        int firstDecision = Math.max(LOOKBACK, state.getCountOfUnstableBars());
        if (decisionCap > 0) {
            firstDecision = Math.max(firstDecision, lastDecision - decisionCap + 1);
        }
        if (firstDecision > lastDecision) {
            LOG.warn("{}: empty decision range [{}, {}]; nothing to evaluate", dataset.label(), firstDecision,
                    lastDecision);
            return;
        }

        LOG.info("{}: bars={}, decisions=[{}, {}], horizon={}, lookback={}, iterations={}, seed={}",
                dataset.label(), series.getBarCount(), firstDecision, lastDecision, HORIZON, LOOKBACK,
                ITERATION_COUNT, SEED);

        List<Double> forwardVols = new ArrayList<>();
        List<Integer> usableOrigins = new ArrayList<>();
        for (int index = firstDecision; index <= lastDecision; index++) {
            Double forwardVol = forwardVolatilityRMS(returns, index, HORIZON);
            if (forwardVol == null) {
                continue;
            }
            ReturnMoments moments = stableMoments(state, index);
            if (moments == null || !validWindow(returns, index, LOOKBACK)) {
                continue;
            }
            forwardVols.add(forwardVol);
            usableOrigins.add(index);
        }

        if (usableOrigins.isEmpty()) {
            LOG.warn("{}: no usable origins after state/window gating", dataset.label());
            return;
        }
        double[] tercileBounds = tercileBounds(forwardVols);

        List<ArmResult> results = new ArrayList<>();
        for (ArmSpec arm : arms) {
            Path checkpoint = outputDirectory().resolve(dataset.token() + "-" + arm.token() + ".json");
            Accumulator acc = evaluateArm(arm, returns, state, usableOrigins, tercileBounds, checkpoint);
            results.add(acc.toResult(arm.name()));
            LOG.info("{} {}: samples={}, unstable={} ({}%), coverage={}% (nominal {}%), "
                            + "crps={}, pinball q05={} q50={} q95={}",
                    dataset.label(), arm.name(), acc.sampleCount, acc.unstableCount,
                    100d * acc.unstableCount / usableOrigins.size(), 100d * acc.coverageHits / acc.sampleCount,
                    100d * NOMINAL_COVERAGE, String.format("%.5f", acc.crps / acc.sampleCount),
                    String.format("%.5f", acc.pinball05 / acc.sampleCount),
                    String.format("%.5f", acc.pinball50 / acc.sampleCount),
                    String.format("%.5f", acc.pinball95 / acc.sampleCount));
        }

        BakeoffResult aggregate = new BakeoffResult(dataset.label(), dataset.resource(), HORIZON, LOOKBACK,
                ITERATION_COUNT, SEED, usableOrigins.size(), results);
        writeJson(outputDirectory().resolve(dataset.token() + "-bakeoff.json"), aggregate);
        LOG.info("{}: wrote aggregate {}", dataset.label(), outputDirectory().resolve(dataset.token() + "-bakeoff.json"));
    }

    private static List<ArmSpec> arms() {
        return List.of(
                new ArmSpec("standardized-empirical-ewma", "STANDARDIZED_EMPIRICAL+EWMA",
                        new ShockPathMonteCarloMethod(ShockModel.STANDARDIZED_EMPIRICAL, VolatilityUpdateMode.EWMA,
                                EWMA_DECAY)),
                new ArmSpec("normal-constant", "NORMAL+CONSTANT",
                        new ShockPathMonteCarloMethod(ShockModel.NORMAL, VolatilityUpdateMode.CONSTANT, EWMA_DECAY)),
                new ArmSpec("historical-bootstrap-ewma", "HISTORICAL_BOOTSTRAP+EWMA",
                        new ShockPathMonteCarloMethod(ShockModel.HISTORICAL_BOOTSTRAP, VolatilityUpdateMode.EWMA,
                                EWMA_DECAY)),
                new ArmSpec("smoothed-empirical-ewma", "SMOOTHED_EMPIRICAL+EWMA",
                        new ShockPathMonteCarloMethod(ShockModel.SMOOTHED_EMPIRICAL, VolatilityUpdateMode.EWMA,
                                EWMA_DECAY)),
                new ArmSpec("nig-empirical-priors", "NI-GAMMA",
                                                NormalInverseGammaForecastMethod.withEmpiricalPriors()),
                new ArmSpec("posterior-smoothed-empirical", "NIG-COMPOSED",
                        new PosteriorSmoothedResidualMonteCarloMethod(null)),
                new ArmSpec("nig-composed-recentvol", "NIG-COMPOSED+RECENTVOL",
                        new RecentVolatilityWidenMethod(new PosteriorSmoothedResidualMonteCarloMethod(null))),
                new ArmSpec("nig-composed-ttail", "NIG-COMPOSED+TTAIL",
                        new StudentTScaleMixingMethod(new PosteriorSmoothedResidualMonteCarloMethod(null))),
                new ArmSpec("nig-composed-recentvol-ttail", "NIG-COMPOSED+RECENTVOL+TTAIL",
                        new StudentTScaleMixingMethod(
                                new RecentVolatilityWidenMethod(new PosteriorSmoothedResidualMonteCarloMethod(null)))),
                new ArmSpec("nig-composed-recentvol2-ttail", "NIG-COMPOSED+RECENTVOL2+TTAIL",
                        new StudentTScaleMixingMethod(
                                new RecentVolatilityWidenMethod(new PosteriorSmoothedResidualMonteCarloMethod(null),
                                        RecentVolatilityWidenMethod.Amplification.TWO))),
                new ArmSpec("nig-normal-composed-recentvol-ttail", "NIG-NORMAL+RECENTVOL+TTAIL",
                        new StudentTScaleMixingMethod(new RecentVolatilityWidenMethod(
                                new PosteriorSmoothedResidualMonteCarloMethod(
                                        new ShockPathMonteCarloMethod(ShockModel.NORMAL,
                                                VolatilityUpdateMode.CONSTANT, EWMA_DECAY))))),
                new ArmSpec("nig-boot-composed-recentvol-ttail", "NIG-BOOT+RECENTVOL+TTAIL",
                        new StudentTScaleMixingMethod(new RecentVolatilityWidenMethod(
                                new PosteriorSmoothedResidualMonteCarloMethod(
                                        new ShockPathMonteCarloMethod(ShockModel.HISTORICAL_BOOTSTRAP,
                                                VolatilityUpdateMode.EWMA, EWMA_DECAY))))),
                new ArmSpec("ensemble-boot-nig-recentvol-ttail", "ENSEMBLE-BOOT+NIG+RECENTVOL+TTAIL",
                        new StudentTScaleMixingMethod(new RecentVolatilityWidenMethod(
                                new EnsembleMethod(
                                        new ShockPathMonteCarloMethod(ShockModel.HISTORICAL_BOOTSTRAP,
                                                VolatilityUpdateMode.EWMA, EWMA_DECAY),
                                        new PosteriorSmoothedResidualMonteCarloMethod(null))))));
    }

    /**
     * Evaluates one arm over the usable origins, persisting a per-origin dirty
     * write so progress survives interruption.
     */
    private static Accumulator evaluateArm(ArmSpec arm, LogReturnIndicator returns,
            EwmaReturnForecastStateIndicator state, List<Integer> usableOrigins, double[] tercileBounds,
            Path checkpoint) {
        NumFactory numFactory = returns.getBarSeries().numFactory();
        Accumulator acc = new Accumulator();
        List<OriginRow> rows = new ArrayList<>();
        int logged = 0;
        for (int index : usableOrigins) {
            Num realizedReturn = realizedReturn(returns, index, HORIZON);
            MonteCarloContext context = context(index, returns, state, numFactory);
            List<Num> terminalSamples = arm.method().terminalReturns(context);
            if (terminalSamples == null || terminalSamples.size() != ITERATION_COUNT
                    || !allFinite(terminalSamples)) {
                acc.unstableCount++;
                continue;
            }
            double[] samples = toPrimitiveDoubles(terminalSamples);
            java.util.Arrays.sort(samples);
            double q05 = percentile(samples, 0.05d);
            double q50 = percentile(samples, 0.50d);
            double q95 = percentile(samples, 0.95d);
            double realized = realizedReturn.doubleValue();
            double coverageHit = realized >= q05 && realized <= q95 ? 1d : 0d;
            double pinball05 = pinball(0.05d, q05, realized);
            double pinball50 = pinball(0.50d, q50, realized);
            double pinball95 = pinball(0.95d, q95, realized);
            double crps = crps(samples, realized);

            acc.sampleCount++;
            acc.coverageHits += coverageHit;
            acc.pinball05 += pinball05;
            acc.pinball50 += pinball50;
            acc.pinball95 += pinball95;
            acc.crps += crps;
            rows.add(new OriginRow(index, tercileBucket(forwardVolatilityRMS(returns, index, HORIZON).doubleValue(),
                    tercileBounds), coverageHit, pinball05, pinball50, pinball95, crps));

            if (++logged % 250 == 0) {
                LOG.info("  {}: {} origins evaluated, {} unstable", arm.name(), acc.sampleCount + acc.unstableCount,
                        acc.unstableCount);
            }
        }
        acc.rows = rows;
        return acc;
    }

    private static ReturnMoments stableMoments(EwmaReturnForecastStateIndicator state, int index) {
        var value = state.getValue(index);
        if (value == null) {
            return null;
        }
        ReturnMoments moments = value.moments();
        return moments != null && moments.index() == index && moments.isStable() ? moments : null;
    }

    private static boolean validWindow(LogReturnIndicator returns, int index, int lookback) {
        for (int i = index - lookback + 1; i <= index; i++) {
            if (!Num.isFinite(returns.getValue(i))) {
                return false;
            }
        }
        return true;
    }

    private static MonteCarloContext context(int index, LogReturnIndicator returns,
            EwmaReturnForecastStateIndicator state, NumFactory numFactory) {
        List<Num> window = new ArrayList<>(LOOKBACK);
        for (int i = index - LOOKBACK + 1; i <= index; i++) {
            window.add(numFactory.numOf(returns.getValue(i).bigDecimalValue()));
        }
        ReturnMoments moments = Objects.requireNonNull(stableMoments(state, index));
        RandomGenerator random = new SplittableRandom(mixSeed(SEED, index, HORIZON));
        return new MonteCarloContext(index, HORIZON, ITERATION_COUNT, window, moments, random, numFactory);
    }

    /**
     * Mirrors {@code MonteCarloSimulation#mixSeed} so the draws reproduce the
     * production engine's deterministic random stream exactly.
     */
    private static long mixSeed(long seed, int index, int horizon) {
        long value = seed;
        value ^= 0x9E3779B97F4A7C15L + ((long) index << 32) + index;
        value = Long.rotateLeft(value, 27) * 0x3C79AC492BA7B653L;
        value ^= 0x1C69B3F74AC4AE35L + horizon;
        value = Long.rotateLeft(value, 31) * 0x1C69B3F74AC4AE35L;
        return value ^ value >>> 33;
    }

    private static Num realizedReturn(LogReturnIndicator returns, int index, int horizon) {
        Num sum = returns.getBarSeries().numFactory().zero();
        for (int i = index + 1; i <= index + horizon; i++) {
            Num value = returns.getValue(i);
            if (!Num.isFinite(value)) {
                return null;
            }
            sum = sum.plus(value);
        }
        return sum;
    }

    /**
     * Forward realized-volatility proxy: RMS of the single-bar log returns over
     * the forecast window {@code (index, index+horizon]}.
     */
    private static Double forwardVolatilityRMS(LogReturnIndicator returns, int index, int horizon) {
        double sumSquares = 0d;
        for (int i = index + 1; i <= index + horizon; i++) {
            Num value = returns.getValue(i);
            if (!Num.isFinite(value)) {
                return null;
            }
            double r = value.doubleValue();
            sumSquares += r * r;
        }
        return Math.sqrt(sumSquares / horizon);
    }

    private static boolean allFinite(List<Num> samples) {
        for (Num sample : samples) {
            if (!Num.isFinite(sample)) {
                return false;
            }
        }
        return true;
    }

    private static double[] toPrimitiveDoubles(List<Num> samples) {
        double[] out = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            out[i] = samples.get(i).doubleValue();
        }
        return out;
    }

    private static double percentile(double[] sorted, double probability) {
        int n = sorted.length;
        if (n == 1) {
            return sorted[0];
        }
        double rank = probability * (n - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sorted[lo];
        }
        double weight = rank - lo;
        return sorted[lo] * (1d - weight) + sorted[hi] * weight;
    }

    private static double pinball(double probability, double quantile, double realized) {
        double diff = realized - quantile;
        return Math.max(probability * diff, (probability - 1d) * diff);
    }

    /**
     * Sample-based CRPS of the empirical cumulative-return distribution against
     * the realized return, computed in linear time from the sorted samples.
     */
    static double crps(double[] sorted, double realized) {
        int n = sorted.length;
        double meanAbs = 0d;
        double weighted = 0d;
        for (int k = 1; k <= n; k++) {
            double sample = sorted[k - 1];
            meanAbs += Math.abs(sample - realized);
            weighted += (2.0 * k - n - 1) * sample;
        }
        return meanAbs / n - weighted / ((double) n * n);
    }

    private static double[] tercileBounds(List<Double> values) {
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).toArray();
        java.util.Arrays.sort(sorted);
        int low = (int) Math.floor(sorted.length / 3.0);
        int high = (int) Math.floor(2.0 * sorted.length / 3.0);
        return new double[] { sorted[low], sorted[high] };
    }

    private static int tercileBucket(double value, double[] bounds) {
        if (value < bounds[0]) {
            return 0;
        }
        return value <= bounds[1] ? 1 : 2;
    }

    private static BarSeries loadSeries(String resource) {
        return Objects.requireNonNull(JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(resource),
                resource + " resource was not available");
    }

    private static Path outputDirectory() {
        Path dir = Path.of("temp", "walk-forward-calibration");
        try {
            Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Failed to create output directory " + dir, e);
        }
        return dir;
    }

    private static void writeJson(Path path, Object value) {
        try {
            Files.writeString(path, GSON.toJson(value));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Failed to write " + path, e);
        }
    }

    private static final class DecisionCap {
        static int fromArgs(String[] args) {
            if (args.length == 0) {
                return 0;
            }
            int cap = Integer.parseInt(args[0]);
            if (cap < 1) {
                throw new IllegalArgumentException("decision cap must be >= 1");
            }
            return cap;
        }
    }

    /**
     * Experimental decorator: widens the inner technique's centered terminal
     * samples by the ratio of the recent realized volatility to the state
     * volatility. Targets the high-forward-vol under-coverage observed across
     * all baseline arms, where the state EWMA volatility lags the realized
     * forward window. Widening is asymmetric: when the recent realized
     * volatility is below the state estimate the factor is 1, so calm regimes
     * are untouched.
     *
     * <p>
     * All randomness is drawn exclusively from {@code context.random()}; the
     * factor itself is deterministic in the window. Null/wrong-count results
     * propagate as unstable.
     */
    private static final class RecentVolatilityWidenMethod implements MonteCarloMethod {

        /** How aggressively the recent-realized over state volatility ratio is applied. */
        enum Amplification {
            /** factor = max(1, ratio), bounded by {@link #MAX_WIDEN}. */
            STANDARD,
            /** factor = max(1, 1 + 2 * (ratio - 1)), bounded by {@link #MAX_WIDEN}. */
            TWO
        }

        private static final int RECENT_WINDOW = 10;
        private static final double MAX_WIDEN = 4d;

        private final MonteCarloMethod inner;
        private final Amplification amplification;

        RecentVolatilityWidenMethod(MonteCarloMethod inner) {
            this(inner, Amplification.STANDARD);
        }

        RecentVolatilityWidenMethod(MonteCarloMethod inner, Amplification amplification) {
            this.inner = inner;
            this.amplification = amplification;
        }

        @Override
        public List<Num> terminalReturns(MonteCarloContext context) {
            List<Num> samples = inner.terminalReturns(context);
            if (samples == null || samples.size() != context.iterationCount()) {
                return null;
            }
            ReturnMoments moments = context.moments();
            if (moments == null || !moments.isStable()) {
                return null;
            }
            NumFactory numFactory = context.numFactory();
            Num drift = moments.drift();
            Num stateVol = moments.volatility();
            if (!Num.isFinite(drift) || !Num.isFinite(stateVol) || stateVol.isZero()) {
                return null;
            }
            double recentVol = recentVolatilityRms(context.historicalLogReturns(), numFactory);
            if (!Double.isFinite(recentVol)) {
                return null;
            }
            double ratio = recentVol / stateVol.doubleValue();
            double expanded = amplification == Amplification.TWO ? 1d + 2d * (ratio - 1d) : ratio;
            double factor = Math.min(MAX_WIDEN, Math.max(1d, expanded));
            Num driftPath = drift.multipliedBy(numFactory.numOf(context.horizon()));
            List<Num> widened = new ArrayList<>(context.iterationCount());
            for (Num sample : samples) {
                Num centered = sample.minus(driftPath);
                Num scaled = driftPath.plus(centered.multipliedBy(numFactory.numOf(factor)));
                if (!Num.isFinite(scaled)) {
                    return null;
                }
                widened.add(scaled);
            }
            return widened;
        }

        /** RMS of the log returns over the trailing {@value #RECENT_WINDOW} bars. */
        private static double recentVolatilityRms(List<Num> window, NumFactory numFactory) {
            int start = Math.max(0, window.size() - RECENT_WINDOW);
            if (window.size() - start < 2) {
                return Double.NaN;
            }
            double sum = 0d;
            int count = 0;
            for (int i = start; i < window.size(); i++) {
                double value = window.get(i).doubleValue();
                if (!Double.isFinite(value)) {
                    return Double.NaN;
                }
                sum += value * value;
                count++;
            }
            return Math.sqrt(sum / count);
        }
    }

    /**
     * Experimental decorator: fattens the tails of the inner technique's
     * centered terminal samples with a mean-normalized Student-t scale mixing
     * factor drawn from {@code context.random()}. The factor has expectation 1
     * (no central-scale shift) but heavy right tail, so the quantile spread
     * grows where it matters for coverage.
     */
    private static final class StudentTScaleMixingMethod implements MonteCarloMethod {

        private static final int T_DF = 5;
        /** E[sqrt(df / chiSq(df))] normalizer so the mixing factor has mean 1. */
        private static final double T_MEAN = tScaleMean(T_DF);

        private final MonteCarloMethod inner;

        StudentTScaleMixingMethod(MonteCarloMethod inner) {
            this.inner = inner;
        }

        @Override
        public List<Num> terminalReturns(MonteCarloContext context) {
            List<Num> samples = inner.terminalReturns(context);
            if (samples == null || samples.size() != context.iterationCount()) {
                return null;
            }
            ReturnMoments moments = context.moments();
            if (moments == null || !moments.isStable()) {
                return null;
            }
            NumFactory numFactory = context.numFactory();
            Num drift = moments.drift();
            if (!Num.isFinite(drift)) {
                return null;
            }
            Num driftPath = drift.multipliedBy(numFactory.numOf(context.horizon()));
            java.util.random.RandomGenerator random = context.random();
            List<Num> mixed = new ArrayList<>(context.iterationCount());
            for (Num sample : samples) {
                double factor = tScaleDraw(random) / T_MEAN;
                Num centered = sample.minus(driftPath);
                Num scaled = driftPath.plus(centered.multipliedBy(numFactory.numOf(factor)));
                if (!Num.isFinite(scaled)) {
                    return null;
                }
                mixed.add(scaled);
            }
            return mixed;
        }

        /** {@code sqrt(df / chiSq(df))} draw from the Student-t scale distribution. */
        private static double tScaleDraw(java.util.random.RandomGenerator random) {
            double chiSq = 0d;
            for (int i = 0; i < T_DF; i++) {
                double normal = random.nextGaussian();
                chiSq += normal * normal;
            }
            return Math.sqrt(T_DF / chiSq);
        }

        /** Closed-form expectation of {@code sqrt(df / chiSq(df))}. */
        private static double tScaleMean(int df) {
            return Math.sqrt(df / 2d) * gamma((df - 1d) / 2d) / gamma(df / 2d);
        }

        private static double gamma(double x) {
            // Lanczos approximation, sufficient for small half-integer arguments.
            double[] coefficients = { 0.99999999999980993, 676.5203681218851, -1259.1392167224028,
                    771.32342877765313, -176.61502916214059, 12.507343278686905, -0.13857109526572012,
                    9.9843695780195716e-6, 1.5056327351493116e-7 };
            if (x < 0.5) {
                return Math.PI / (Math.sin(Math.PI * x) * gamma(1d - x));
            }
            x -= 1d;
            double a = coefficients[0];
            double t = x + 7.5;
            for (int i = 1; i < coefficients.length; i++) {
                a += coefficients[i] / (x + i);
            }
            return Math.sqrt(2d * Math.PI) * Math.pow(t, x + 0.5) * Math.exp(-t) * a;
        }
    }

    /**
     * Experimental decorator: pools two independent techniques 50/50 into a
     * single sample distribution. Each component runs at half the iteration
     * count with its own {@code SplittableRandom} derived from
     * {@code context.random()} (two {@code nextLong} draws), so the ensemble is
     * deterministic under the seam's single-source rule and each component
     * reproduces its standalone draws at half the count. Null/wrong-count
     * results propagate as unstable.
     */
    private static final class EnsembleMethod implements MonteCarloMethod {

        private final MonteCarloMethod first;
        private final MonteCarloMethod second;

        EnsembleMethod(MonteCarloMethod first, MonteCarloMethod second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public List<Num> terminalReturns(MonteCarloContext context) {
            int half = context.iterationCount() / 2;
            int remainder = context.iterationCount() - half;
            RandomGenerator baseRandom = context.random();
            MonteCarloContext firstContext = subContext(context, half, new SplittableRandom(baseRandom.nextLong()));
            MonteCarloContext secondContext = subContext(context, remainder, new SplittableRandom(baseRandom.nextLong()));
            List<Num> firstSamples = first.terminalReturns(firstContext);
            List<Num> secondSamples = second.terminalReturns(secondContext);
            if (firstSamples == null || secondSamples == null) {
                return null;
            }
            if (firstSamples.size() != half || secondSamples.size() != remainder) {
                return null;
            }
            List<Num> pooled = new ArrayList<>(context.iterationCount());
            pooled.addAll(firstSamples);
            pooled.addAll(secondSamples);
            return pooled;
        }

        private static MonteCarloContext subContext(MonteCarloContext context, int count, RandomGenerator random) {
            return new MonteCarloContext(context.index(), context.horizon(), count, context.historicalLogReturns(),
                    context.moments(), random, context.numFactory());
        }
    }

    private record DatasetSpec(String resource, String label, String token) {
    }

    private record ArmSpec(String token, String name, MonteCarloMethod method) {
    }

    private record OriginRow(int index, int regime, double coverageHit, double pinball05, double pinball50,
            double pinball95, double crps) {
    }

    static final class Accumulator {
        int sampleCount;
        int unstableCount;
        double coverageHits;
        double pinball05;
        double pinball50;
        double pinball95;
        double crps;
        List<OriginRow> rows = List.of();

        double coverageRate() {
            return sampleCount == 0 ? Double.NaN : coverageHits / sampleCount;
        }

        double meanPinball05() {
            return sampleCount == 0 ? Double.NaN : pinball05 / sampleCount;
        }

        double meanPinball50() {
            return sampleCount == 0 ? Double.NaN : pinball50 / sampleCount;
        }

        double meanPinball95() {
            return sampleCount == 0 ? Double.NaN : pinball95 / sampleCount;
        }

        double meanCrps() {
            return sampleCount == 0 ? Double.NaN : crps / sampleCount;
        }

        ArmResult toResult(String armName) {
            double[] overall = new double[] { coverageRate(), meanPinball05(), meanPinball50(), meanPinball95(),
                    meanCrps() };
            return new ArmResult(armName, sampleCount, unstableCount, overall,
                    new RegimeResult[] { regimeResult(0), regimeResult(1), regimeResult(2) });
        }

        private RegimeResult regimeResult(int regime) {
            int count = 0;
            double coverageHits = 0d;
            double pinball05 = 0d;
            double pinball50 = 0d;
            double pinball95 = 0d;
            double crps = 0d;
            for (OriginRow row : rows) {
                if (row.regime() != regime) {
                    continue;
                }
                count++;
                coverageHits += row.coverageHit();
                pinball05 += row.pinball05();
                pinball50 += row.pinball50();
                pinball95 += row.pinball95();
                crps += row.crps();
            }
            return new RegimeResult(regimeName(regime), count,
                    count == 0 ? Double.NaN : coverageHits / count,
                    count == 0 ? Double.NaN : pinball05 / count,
                    count == 0 ? Double.NaN : pinball50 / count,
                    count == 0 ? Double.NaN : pinball95 / count,
                    count == 0 ? Double.NaN : crps / count);
        }

        private static String regimeName(int regime) {
            return switch (regime) {
            case 0 -> "low";
            case 1 -> "mid";
            default -> "high";
            };
        }
    }

    record RegimeResult(String regime, int count, double coverage, double pinball05, double pinball50,
            double pinball95, double crps) {
    }

    /**
     * {@code metrics} is {@code [coverage, pinball05, pinball50, pinball95, crps]}
     * over all stable origins.
     */
    record ArmResult(String name, int stableCount, int unstableCount, double[] metrics, RegimeResult[] regimes) {
    }

    record BakeoffResult(String dataset, String resource, int horizon, int lookback, int iterations, long seed,
            int usableOrigins, List<ArmResult> arms) {
    }
}
