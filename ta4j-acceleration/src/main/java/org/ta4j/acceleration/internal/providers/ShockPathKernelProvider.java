/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration.internal.providers;

import static org.ta4j.core.indicators.forecast.MonteCarloKernel.INPUT_COUNT;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.INPUT_DRIFTS;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.INPUT_MEANS;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.INPUT_PRICES;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.INPUT_RETURNS;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.INPUT_VARIANCES;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.PARAM_COUNT;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.PARAM_DECAY;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.PARAM_HORIZON;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.PARAM_ITERATIONS;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.PARAM_LOOKBACK;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.PARAM_SHOCK_MODEL;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.PARAM_VOLATILITY_MODE;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.SHOCK_HISTORICAL_BOOTSTRAP;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.SHOCK_NORMAL;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.SHOCK_STANDARDIZED_EMPIRICAL;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.VOLATILITY_CONSTANT;
import static org.ta4j.core.indicators.forecast.MonteCarloKernel.VOLATILITY_EWMA;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.ta4j.core.acceleration.AccelerationRuntime;
import org.ta4j.core.acceleration.AccelerationRuntime.Assessment;
import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.Determinism;
import org.ta4j.core.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;
import org.ta4j.core.acceleration.AccelerationRuntime.KernelResult;
import org.ta4j.core.acceleration.AccelerationRuntime.NumericEncoding;
import org.ta4j.core.acceleration.AccelerationRuntime.Operation;
import org.ta4j.core.acceleration.AccelerationRuntime.Provider;

/**
 * Template for sample-output native providers behind the versioned
 * {@link Operation#MONTE_CARLO_SHOCK_PATHS_V1} kernel contract (see
 * {@link org.ta4j.core.indicators.forecast.MonteCarloKernel}).
 *
 * <p>
 * The core owns indicator recognition, eligibility, snapshotting, validation,
 * scalar fallback, and forecast reconstruction. Providers only answer two
 * questions over immutable {@link KernelRequest} primitives: {@link #assess},
 * which predicts the total offload cost without initializing native code, and
 * {@link #execute}, which returns the raw per-sample cumulative log-returns.
 *
 * <p>
 * Assessment never loads libraries, creates contexts, or allocates request
 * buffers: library presence is answered from configuration and packaged
 * resources, memory rejection runs on arithmetic alone before any input or
 * output materialization, and approximate accuracy is admitted only when
 * {@link ShockPathErrorBound} certifies the requested tolerance for this lane's
 * precision.
 *
 * @since 0.25.1
 */
abstract class ShockPathKernelProvider implements Provider {

    /** Native shock-model codes of the kernel ABI. */
    private static final int NATIVE_BOOTSTRAP = 0;
    private static final int NATIVE_STANDARDIZED = 1;
    private static final int NATIVE_NORMAL = 2;

    /** Per-path device workspace bytes budgeted by the native lanes. */
    private static final long WORKSPACE_BYTES_PER_PATH = 68L;

    private final Backend backend;
    private final String providerId;
    private final String maxMemoryProperty;
    private final long defaultMaxMemoryBytes;
    private final boolean exactCapable;
    private final boolean approximateCapable;
    private final ShockPathErrorBound.Precision precision;
    private final ShockPathQualification qualification;

    private volatile boolean resident;
    private volatile String probedDevice;
    private volatile long probedCeilingBytes;

    ShockPathKernelProvider(Backend backend, String providerId, String maxMemoryProperty, long defaultMaxMemoryBytes,
            boolean exactCapable, boolean approximateCapable, ShockPathErrorBound.Precision precision,
            ShockPathQualification qualification) {
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.maxMemoryProperty = Objects.requireNonNull(maxMemoryProperty, "maxMemoryProperty must not be null");
        this.defaultMaxMemoryBytes = defaultMaxMemoryBytes;
        this.exactCapable = exactCapable;
        this.approximateCapable = approximateCapable;
        this.precision = Objects.requireNonNull(precision, "precision must not be null");
        this.qualification = Objects.requireNonNull(qualification, "qualification must not be null");
    }

    @Override
    public final Assessment assess(KernelRequest request) {
        RequestValidation validation = validateRequest(request);
        if (!validation.supported()) {
            return unsupported(validation.code(), validation.detail());
        }
        Dimensions dimensions = validation.dimensions();
        if (!libraryPresent()) {
            return unsupported(DiagnosticCode.PROVIDER_UNAVAILABLE, libraryDetail());
        }
        long ceiling = memoryCeiling();
        if (ceiling <= 0L) {
            return unsupported(DiagnosticCode.PROVIDER_UNAVAILABLE, maxMemoryProperty + " must be > 0");
        }
        if (dimensions.bytesPerDecision() > ceiling - dimensions.fixedBytes()) {
            return unsupported(DiagnosticCode.PROVIDER_UNAVAILABLE, perDecisionDetail(dimensions, ceiling));
        }
        String family = deviceFamily();
        long predicted = qualification.predictedTotalNanos(backend, request.operation().version(), family,
                dimensions.steps(), dimensions.stagedBytes(), resident);
        long peak = Math.min(dimensions.peakBytes(), ceiling);
        boolean exact = request.determinism() == Determinism.BITWISE_IDENTICAL;
        return Assessment.supported(backend, deviceId(), predicted, peak, exact ? exactCapable : approximateCapable);
    }

    @Override
    public final KernelResult execute(KernelRequest request) {
        RequestValidation validation = validateRequest(request);
        if (!validation.supported()) {
            throw new NativeProviderException(backendName(), validation.detail());
        }
        long started = System.nanoTime();
        Dimensions dimensions = validation.dimensions();
        long ceiling = memoryCeiling();
        if (ceiling <= 0L) {
            throw new NativeProviderException(backendName(), maxMemoryProperty + " must be > 0");
        }
        if (dimensions.bytesPerDecision() > ceiling - dimensions.fixedBytes()) {
            throw new NativeProviderException(backendName(), perDecisionDetail(dimensions, ceiling));
        }
        SampleKernel kernel = ensureKernel();
        List<double[]> inputs = request.inputs();
        double[] raw = new double[request.expectedOutputLength()];
        int offset = 0;
        while (offset < dimensions.decisions()) {
            int count = Math.min(decisionsPerChunk(dimensions, memoryCeiling()), dimensions.decisions() - offset);
            NativeForecastRequest nativeRequest = nativeChunk(request, validation, inputs, offset, count);
            SampleKernel.SampleResult chunkResult;
            try {
                chunkResult = kernel.evaluateSamples(nativeRequest);
            } catch (LinkageError | RuntimeException exception) {
                throw new NativeProviderException(backendName(), exception);
            }
            double[] samples = chunkResult.logReturns();
            int expected = Math.multiplyExact(count, dimensions.iterations());
            if (samples.length != expected) {
                throw new NativeProviderException(backendName() + " returned " + samples.length + " samples, expected "
                        + expected + " for " + count + " decisions");
            }
            System.arraycopy(samples, 0, raw, offset * dimensions.iterations(), samples.length);
            offset += count;
        }
        resident = true;
        return new KernelResult(raw, true, System.nanoTime() - started);
    }

    private RequestValidation validateRequest(KernelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.operation() != Operation.MONTE_CARLO_SHOCK_PATHS_V1) {
            return RequestValidation.unsupported(DiagnosticCode.UNSUPPORTED,
                    providerId + " implements only MONTE_CARLO_SHOCK_PATHS_V1, not " + request.operation());
        }
        if (request.numeric() != NumericEncoding.FLOAT64) {
            return RequestValidation.unsupported(DiagnosticCode.UNSUPPORTED,
                    providerId + " consumes FLOAT64 buffers, not " + request.numeric());
        }
        double[] params = request.params();
        if (params.length != PARAM_COUNT) {
            return RequestValidation.unsupported(DiagnosticCode.UNSUPPORTED,
                    providerId + " expects " + PARAM_COUNT + " kernel params, not " + params.length);
        }
        int shockModel = (int) params[PARAM_SHOCK_MODEL];
        int nativeShockModel = nativeShockModel(shockModel);
        if (shockModel != params[PARAM_SHOCK_MODEL] || nativeShockModel < 0) {
            return RequestValidation.unsupported(DiagnosticCode.UNSUPPORTED,
                    providerId + " supports historical bootstrap, standardized empirical, and normal shocks");
        }
        int volatilityMode = (int) params[PARAM_VOLATILITY_MODE];
        double decay = params[PARAM_DECAY];
        if (volatilityMode != params[PARAM_VOLATILITY_MODE]
                || volatilityMode != VOLATILITY_CONSTANT && volatilityMode != VOLATILITY_EWMA
                || !(decay > 0d && decay < 1d)) {
            return RequestValidation.unsupported(DiagnosticCode.UNSUPPORTED,
                    providerId + " needs a constant or EWMA volatility mode with a decay in (0, 1)");
        }
        boolean exact = request.determinism() == Determinism.BITWISE_IDENTICAL;
        if (exact ? !exactCapable : !approximateCapable) {
            return RequestValidation.unsupported(DiagnosticCode.PROVIDER_UNAVAILABLE, accuracyDetail(request, exact));
        }
        Dimensions dimensions;
        try {
            dimensions = dimensions(request, params);
        } catch (ArithmeticException exception) {
            return RequestValidation.unsupported(DiagnosticCode.UNSUPPORTED,
                    providerId + " request dimensions overflow: " + exception.getMessage());
        }
        if (request.outputsPerIndex() != dimensions.iterations()) {
            return RequestValidation.unsupported(DiagnosticCode.UNSUPPORTED, providerId + " returns "
                    + dimensions.iterations() + " outputs per index, not " + request.outputsPerIndex());
        }
        String shapeProblem = inputShapeProblem(request.inputs(), dimensions);
        if (shapeProblem != null) {
            return RequestValidation.unsupported(DiagnosticCode.UNSUPPORTED, providerId + " " + shapeProblem);
        }
        if (!exact) {
            String reason = ShockPathErrorBound.uncertifiableReason(precision, shockModel, volatilityMode,
                    request.inputs());
            if (reason != null) {
                return RequestValidation.unsupported(DiagnosticCode.UNSUPPORTED,
                        providerId + " cannot certify an approximate tolerance: " + reason + "; scalar path");
            }
            double bound = ShockPathErrorBound.maxRelativePriceError(precision, shockModel, dimensions.horizon(),
                    request.inputs());
            if (!(bound <= request.tolerance())) {
                return RequestValidation.unsupported(DiagnosticCode.UNSUPPORTED,
                        providerId + " cannot certify approximate tolerance " + request.tolerance()
                                + " for these inputs:" + " its " + precision.name().toLowerCase(Locale.ROOT)
                                + " lane may differ from the scalar price by a relative " + bound + "; raise -D"
                                + AccelerationRuntime.APPROXIMATE_TOLERANCE_PROPERTY + " or run the scalar path");
            }
        }
        return RequestValidation.supported(nativeShockModel, volatilityMode, decay, dimensions);
    }

    private static int nativeShockModel(int shockModel) {
        return switch (shockModel) {
        case SHOCK_HISTORICAL_BOOTSTRAP -> NATIVE_BOOTSTRAP;
        case SHOCK_STANDARDIZED_EMPIRICAL -> NATIVE_STANDARDIZED;
        case SHOCK_NORMAL -> NATIVE_NORMAL;
        default -> -1;
        };
    }

    private static String inputShapeProblem(List<double[]> inputs, Dimensions dimensions) {
        if (inputs.size() != INPUT_COUNT) {
            return "expects " + INPUT_COUNT + " input buffers, not " + inputs.size();
        }
        int decisions = dimensions.decisions();
        for (int buffer : new int[] { INPUT_PRICES, INPUT_MEANS, INPUT_DRIFTS, INPUT_VARIANCES }) {
            if (inputs.get(buffer).length != decisions) {
                return "expects " + decisions + " values in input buffer " + buffer + ", not "
                        + inputs.get(buffer).length;
            }
        }
        long returns = (long) decisions + dimensions.lookback() - 1L;
        if (inputs.get(INPUT_RETURNS).length != returns) {
            return "expects a shared returns buffer of " + returns + " values, not " + inputs.get(INPUT_RETURNS).length;
        }
        return null;
    }

    /**
     * Answers library presence without loading native code: an explicit configured
     * path or a packaged classifier resource.
     */
    abstract boolean libraryPresent();

    /** Detail for the unavailable diagnostic when no library is present. */
    abstract String libraryDetail();

    /** Loads the library, probes the device, and returns the sample kernel. */
    abstract SampleKernel ensureKernel();

    /** Device family used for qualification when no probe has run yet. */
    String deviceFamily() {
        return System.getProperty(ShockPathQualification.familyProperty(backend), "generic")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /** Device label reported in assessments. */
    String deviceId() {
        String probed = probedDevice;
        return backendName() + "/" + (probed == null ? deviceFamily() : probed);
    }

    /** Device name from the native probe, or null before the first execution. */
    final String probedDevice() {
        return probedDevice;
    }

    /**
     * Records a successful native probe; later assessments report the device and
     * honor its ceiling.
     */
    final void recordProbe(String device, long ceilingBytes) {
        probedDevice = Objects.requireNonNull(device, "device must not be null");
        probedCeilingBytes = Math.max(1L, ceilingBytes);
    }

    private String backendName() {
        return backend.name().toLowerCase(Locale.ROOT);
    }

    private Assessment unsupported(DiagnosticCode code, String detail) {
        return Assessment.unsupported(backend, deviceId(), code, providerId, detail);
    }

    private String perDecisionDetail(Dimensions dimensions, long ceiling) {
        return providerId + " needs %,d bytes per decision plus %,d shared bytes, above the %,d-byte provider ceiling"
                .formatted(dimensions.bytesPerDecision(), dimensions.fixedBytes(), ceiling);
    }

    /**
     * Explains why a request's accuracy mode is unsupported. Backends whose native
     * lane cannot serve versioned sample output override this.
     */
    String accuracyDetail(KernelRequest request, boolean exact) {
        if (exact) {
            return providerId + " is not exact-capable; exact requests stay scalar"
                    + (approximateCapable
                            ? " unless -D" + AccelerationRuntime.APPROXIMATE_TOLERANCE_PROPERTY
                                    + " opts into an approximate tolerance"
                            : "");
        }
        return providerId + " serves exact requests only and declines approximate tolerance " + request.tolerance();
    }

    private long memoryCeiling() {
        long configured = Long.getLong(maxMemoryProperty, defaultMaxMemoryBytes);
        long probed = probedCeilingBytes;
        return probed <= 0L ? configured : Math.min(configured, probed);
    }

    /**
     * Derives chunking dimensions from request primitives alone. No buffers are
     * allocated here; rejection stays ahead of materialization. Each decision row
     * stages three state doubles plus one new shared return, and the
     * {@code lookback - 1} leading returns are shared by every row of a chunk.
     */
    private Dimensions dimensions(KernelRequest request, double[] params) {
        int horizon = (int) params[PARAM_HORIZON];
        int iterations = (int) params[PARAM_ITERATIONS];
        int lookback = (int) params[PARAM_LOOKBACK];
        if (horizon < 1 || iterations < 1 || lookback < 1) {
            throw new ArithmeticException("kernel params must be positive");
        }
        int decisions = request.size();
        long steps = Math.multiplyExact(Math.multiplyExact((long) decisions, iterations), horizon);
        long perDecisionInputs = 4L * Double.BYTES;
        long fixedBytes = Math.multiplyExact(lookback - 1L, 2L * Double.BYTES);
        long stagedBytes = Math
                .addExact(Math.multiplyExact(Math.multiplyExact((long) decisions, perDecisionInputs), 2L), fixedBytes);
        long outputBytes = Math.multiplyExact(Math.multiplyExact((long) decisions, iterations), (long) Double.BYTES);
        long bytesPerDecision = Math.addExact(perDecisionInputs * 2L,
                Math.multiplyExact((long) iterations, Double.BYTES + WORKSPACE_BYTES_PER_PATH));
        long peakBytes = Math.addExact(Math.addExact(stagedBytes, outputBytes), 256L);
        return new Dimensions(decisions, horizon, iterations, lookback, steps, stagedBytes, fixedBytes,
                bytesPerDecision, peakBytes);
    }

    private int decisionsPerChunk(Dimensions dimensions, long ceiling) {
        long capacity = (ceiling - dimensions.fixedBytes()) / dimensions.bytesPerDecision();
        long nativeCellCapacity = Integer.MAX_VALUE / (long) dimensions.iterations();
        long nativeHistoryCapacity = Integer.MAX_VALUE - (dimensions.lookback() - 1L);
        capacity = Math.min(capacity, Math.min(nativeCellCapacity, nativeHistoryCapacity));
        if (capacity < 1L) {
            throw new NativeProviderException(perDecisionDetail(dimensions, ceiling));
        }
        return (int) Math.min(capacity, Integer.MAX_VALUE);
    }

    private NativeForecastRequest nativeChunk(KernelRequest request, RequestValidation validation,
            List<double[]> inputs, int base, int count) {
        Dimensions dimensions = validation.dimensions();
        double[] returns = inputs.get(INPUT_RETURNS);
        // Row r reads returns[r .. r + lookback - 1], so a chunk of rows
        // [base, base + count) needs the shared slice [base, base + count + lookback -
        // 1).
        double[] chunkReturns = Arrays.copyOfRange(returns, base, base + count + dimensions.lookback() - 1);
        return new NativeForecastRequest(Math.addExact(request.fromInclusive(), base), count, dimensions.horizon(),
                dimensions.iterations(), dimensions.lookback(), request.seed(), validation.nativeShockModel(),
                validation.volatilityMode(), validation.decay(),
                Arrays.copyOfRange(inputs.get(INPUT_MEANS), base, base + count),
                Arrays.copyOfRange(inputs.get(INPUT_DRIFTS), base, base + count),
                Arrays.copyOfRange(inputs.get(INPUT_VARIANCES), base, base + count), chunkReturns);
    }

    private record Dimensions(int decisions, int horizon, int iterations, int lookback, long steps, long stagedBytes,
            long fixedBytes, long bytesPerDecision, long peakBytes) {
    }

    private record RequestValidation(int nativeShockModel, int volatilityMode, double decay, Dimensions dimensions,
            DiagnosticCode code, String detail) {

        static RequestValidation supported(int nativeShockModel, int volatilityMode, double decay,
                Dimensions dimensions) {
            return new RequestValidation(nativeShockModel, volatilityMode, decay, dimensions, null, null);
        }

        static RequestValidation unsupported(DiagnosticCode code, String detail) {
            return new RequestValidation(-1, -1, Double.NaN, null, code, detail);
        }

        boolean supported() {
            return code == null;
        }
    }
}
