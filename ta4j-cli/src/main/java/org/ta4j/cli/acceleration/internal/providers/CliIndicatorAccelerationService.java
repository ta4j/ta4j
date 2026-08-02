/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.ta4j.core.internal.acceleration.AccelerationRuntime.Backend;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Diagnostic;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.DiagnosticCode;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Provider;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Request;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Result;
import org.ta4j.core.internal.acceleration.AccelerationRuntime.Status;
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

    static final String QUALIFICATION_PROVIDER_PROPERTY = "ta4j.acceleration.qualification.provider";

    private static final double MINIMUM_SPEEDUP = 0.10d;
    private static final Map<String, String> QUARANTINED_PROVIDERS = new ConcurrentHashMap<>();

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

        Request<Forecast> forecastRequest = (Request<Forecast>) (Request<?>) request;
        String selectedProviderId = selectedProviderId();
        String quarantineReason = QUARANTINED_PROVIDERS.get(selectedProviderId);
        if (quarantineReason != null) {
            Backend backend = "metal".equals(selectedProviderId) ? Backend.METAL : Backend.CUDA;
            return notExecuted(Status.FAILED, backend, DiagnosticCode.PROVIDER_FAILURE, selectedProviderId,
                    "provider quarantined after failure: " + quarantineReason);
        }
        ForecastAccelerationProvider provider = providerForCurrentHost();
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
        } catch (NativeProviderException exception) {
            QUARANTINED_PROVIDERS.putIfAbsent(capability.providerId(), exception.getMessage());
            return notExecuted(Status.FAILED, capability.backend(), DiagnosticCode.PROVIDER_FAILURE,
                    capability.providerId(), exception.getMessage());
        }
    }

    static void quarantineForTests(String providerId, String reason) {
        QUARANTINED_PROVIDERS.put(providerId, reason);
    }

    static void clearQuarantineForTests() {
        QUARANTINED_PROVIDERS.clear();
    }

    private static ForecastAccelerationProvider providerForCurrentHost() {
        String forced = qualificationProvider();
        if ("metal".equals(forced)) {
            return new MetalAccelerationProviderFactory().probe();
        }
        if ("cuda".equals(forced)) {
            return new CudaAccelerationProviderFactory().probe();
        }
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (operatingSystem.contains("mac")) {
            return new MetalAccelerationProviderFactory().probe();
        }
        if (operatingSystem.contains("windows") || operatingSystem.contains("linux")) {
            return new CudaAccelerationProviderFactory().probe();
        }
        Capability capability = new Capability("none", Backend.CPU, false, false, "",
                "no Metal or CUDA provider exists for " + operatingSystem);
        return new UnavailableForecastProvider(capability);
    }

    private static String selectedProviderId() {
        String forced = qualificationProvider();
        if ("metal".equals(forced) || "cuda".equals(forced)) {
            return forced;
        }
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return operatingSystem.contains("mac") ? "metal" : "cuda";
    }

    private static String qualificationProvider() {
        return System.getProperty(QUALIFICATION_PROVIDER_PROPERTY, "").trim().toLowerCase(Locale.ROOT);
    }

    private static <T> Result<T> notExecuted(Status status, Backend backend, DiagnosticCode code, String providerId,
            String detail) {
        return new Result<>(status, backend, List.of(), false, 0L, new Diagnostic(code, providerId, detail));
    }
}
