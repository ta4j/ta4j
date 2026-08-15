/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.ta4j.core.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.acceleration.AccelerationRuntime.Diagnostic;
import org.ta4j.core.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.acceleration.AccelerationRuntime.Provider;
import org.ta4j.core.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.acceleration.AccelerationRuntime.Result;
import org.ta4j.core.acceleration.AccelerationRuntime.Status;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastSpec;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.num.DoubleNumFactory;

/**
 * Lazy ta4j-cli provider for transparent built-in forecast acceleration.
 *
 * <p>
 * The public class exists solely for {@link java.util.ServiceLoader}. Its
 * constructor performs no platform probe or native loading.
 *
 * @since 0.23.1
 */
public final class CliIndicatorAccelerationService implements Provider {

    static final double MINIMUM_SPEEDUP = 0.10d;
    private static final Map<String, String> QUARANTINED_PROVIDERS = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> QUALIFICATION_PROVIDER = ThreadLocal.withInitial(() -> "");
    private static final ThreadLocal<ForecastAccelerationProvider> TEST_PROVIDER = new ThreadLocal<>();
    private static final ThreadLocal<Supplier<ProviderSelection>> TEST_SELECTION = new ThreadLocal<>();

    /**
     * Creates a lazy provider service.
     *
     * @since 0.23.1
     */
    public CliIndicatorAccelerationService() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Result<T> evaluate(Request<T> request) {
        if (!(request.indicator() instanceof MonteCarloPriceForecastIndicator forecast)) {
            return notExecuted(Status.SKIPPED, Backend.CPU, DiagnosticCode.UNSUPPORTED, "none",
                    "indicator family is not acceleration eligible");
        }
        MonteCarloPriceForecastSpec spec = forecast.accelerationSpec();
        if (request.series().numFactory() != DoubleNumFactory.getInstance()
                || spec.stateIndicator().getReturnRepresentation() != ReturnRepresentation.LOG
                || spec.stateIndicator().getReturnIndicator().getReturnRepresentation() != ReturnRepresentation.LOG) {
            return notExecuted(Status.SKIPPED, Backend.CPU, DiagnosticCode.UNSUPPORTED, "none",
                    "forecast acceleration requires DoubleNum and log-return state");
        }
        if (legacyRngStreamRequested()) {
            // -Dta4j.forecast.rngVersion=0 selects the pre-0.23.1 shared
            // SplittableRandom stream on the scalar lane. Native kernels only
            // implement the versioned per-path stream (RNG version 1), so an
            // accelerated result would silently publish different values than
            // the scalar lane the property promises to restore. Fall back to
            // scalar so the documented legacy values actually materialize.
            return notExecuted(Status.SKIPPED, Backend.CPU, DiagnosticCode.CPU_FASTER, "none",
                    "ta4j.forecast.rngVersion=0 requests the legacy stream, which native lanes cannot reproduce");
        }

        Request<Forecast> forecastRequest = (Request<Forecast>) (Request<?>) request;
        ProviderSelection selection = providerSelection();
        String quarantineReason = QUARANTINED_PROVIDERS.get(selection.providerId());
        if (quarantineReason != null && !qualificationProvider().equals(selection.providerId())) {
            return notExecuted(Status.FAILED, selection.backend(), DiagnosticCode.PROVIDER_FAILURE,
                    selection.providerId(), "provider quarantined after failure: " + quarantineReason);
        }
        ForecastAccelerationProvider provider = selection.provider().get();
        Capability capability = provider.capability();
        if (!capability.available()) {
            return (Result<T>) provider.evaluate(forecastRequest);
        }

        boolean qualificationRun = qualificationProvider().equals(capability.providerId());
        double predictedSpeedup = provider.predictedSpeedup(forecastRequest);
        if (!qualificationRun && (!Double.isFinite(predictedSpeedup) || predictedSpeedup < MINIMUM_SPEEDUP)) {
            String detail = Double.isFinite(predictedSpeedup)
                    ? "predicted %.2f%% speedup is below the %.2f%% automatic threshold"
                            .formatted(predictedSpeedup * 100d, MINIMUM_SPEEDUP * 100d)
                    : "provider returned an invalid speedup prediction";
            return notExecuted(Status.SKIPPED, capability.backend(), DiagnosticCode.CPU_FASTER, capability.providerId(),
                    detail);
        }
        try {
            return (Result<T>) provider.evaluate(forecastRequest);
        } catch (StaleSeriesException exception) {
            return notExecuted(Status.FAILED, capability.backend(), DiagnosticCode.STALE_SERIES,
                    capability.providerId(), exception.getMessage());
        } catch (IllegalArgumentException | ArithmeticException | IllegalStateException exception) {
            return notExecuted(Status.SKIPPED, capability.backend(), DiagnosticCode.PROVIDER_UNAVAILABLE,
                    capability.providerId(), "provider rejected request: " + exception.getMessage());
        } catch (NativeProviderException exception) {
            // Quarantine under the selection id so the early lookup in later
            // evaluations hits even when the composite advertises a member's
            // capability id (for example CUDA) under the "opencl" selection.
            QUARANTINED_PROVIDERS.putIfAbsent(selection.providerId(), exception.getMessage());
            return notExecuted(Status.FAILED, capability.backend(), DiagnosticCode.PROVIDER_FAILURE,
                    capability.providerId(), exception.getMessage());
        }
    }

    static void quarantineForTests(String providerId, String reason) {
        QUARANTINED_PROVIDERS.put(providerId, reason);
    }

    static void clearQuarantineForTests() {
        QUARANTINED_PROVIDERS.clear();
        QUALIFICATION_PROVIDER.remove();
        TEST_PROVIDER.remove();
        TEST_SELECTION.remove();
    }

    static void useQualificationProviderForTests(String providerId) {
        QUALIFICATION_PROVIDER.set(providerId);
    }

    static void useProviderForTests(ForecastAccelerationProvider provider) {
        TEST_PROVIDER.set(provider);
    }

    static void useProviderSelectionForTests(Supplier<ProviderSelection> selection) {
        TEST_SELECTION.set(selection);
    }

    private static ProviderSelection providerSelection() {
        Supplier<ProviderSelection> testSelection = TEST_SELECTION.get();
        if (testSelection != null) {
            return testSelection.get();
        }
        ForecastAccelerationProvider testProvider = TEST_PROVIDER.get();
        if (testProvider != null) {
            Capability capability = testProvider.capability();
            return new ProviderSelection(capability.providerId(), capability.backend(), () -> testProvider);
        }
        String forced = qualificationProvider();
        if ("metal".equals(forced)) {
            return new ProviderSelection("metal", Backend.METAL, () -> new MetalAccelerationProviderFactory().probe());
        }
        if ("cuda".equals(forced)) {
            return new ProviderSelection("cuda", Backend.CUDA, () -> new CudaAccelerationProviderFactory().probe());
        }
        if ("opencl".equals(forced)) {
            return new ProviderSelection("opencl", Backend.OPENCL,
                    () -> new OpenClAccelerationProviderFactory().probe());
        }
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (operatingSystem.contains("mac")) {
            return new ProviderSelection("metal", Backend.METAL, () -> new MetalAccelerationProviderFactory().probe());
        }
        if (operatingSystem.contains("windows")) {
            return new ProviderSelection("cuda", Backend.CUDA, () -> new CudaAccelerationProviderFactory().probe());
        }
        if (operatingSystem.contains("linux")) {
            return new ProviderSelection("opencl", Backend.OPENCL, () -> new CompositeForecastAccelerationProvider(List
                    .of(new CudaAccelerationProviderFactory()::probe, new OpenClAccelerationProviderFactory()::probe)));
        }
        Capability capability = new Capability("none", Backend.CPU, false, false, "",
                "no Metal, CUDA, or OpenCL provider exists for " + operatingSystem);
        return new ProviderSelection(capability.providerId(), capability.backend(),
                () -> new UnavailableForecastProvider(capability));
    }

    private static String qualificationProvider() {
        return QUALIFICATION_PROVIDER.get().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * True when the pre-0.23.1 shared {@link java.util.SplittableRandom} stream was
     * requested via {@code -Dta4j.forecast.rngVersion=0}. Native lanes cannot
     * reproduce that stream, so acceleration must not engage.
     */
    private static boolean legacyRngStreamRequested() {
        String configured = System.getProperty("ta4j.forecast.rngVersion");
        if (configured == null || configured.isBlank()) {
            return false;
        }
        return "0".equals(configured.trim());
    }

    private static <T> Result<T> notExecuted(Status status, Backend backend, DiagnosticCode code, String providerId,
            String detail) {
        return new Result<>(status, backend, List.of(), false, 0L, new Diagnostic(code, providerId, detail));
    }

    record ProviderSelection(String providerId, Backend backend, Supplier<ForecastAccelerationProvider> provider) {
    }
}
