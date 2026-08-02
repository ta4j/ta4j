/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

import org.ta4j.acceleration.internal.adapters.CloseSmaControlAdapter;
import org.ta4j.acceleration.internal.adapters.ForecastBatchAdapter;
import org.ta4j.acceleration.internal.providers.CudaAccelerationProviderFactory;
import org.ta4j.acceleration.internal.providers.MetalAccelerationProviderFactory;
import org.ta4j.acceleration.spi.AdapterMatch;
import org.ta4j.acceleration.spi.IndicatorAccelerationAdapter;
import org.ta4j.acceleration.spi.IndicatorAccelerationProvider;
import org.ta4j.acceleration.spi.IndicatorAccelerationProviderFactory;
import org.ta4j.acceleration.spi.ProviderCapability;
import org.ta4j.core.Indicator;
import org.ta4j.core.acceleration.AccelerationConfig;
import org.ta4j.core.acceleration.AccelerationDiagnostic;
import org.ta4j.core.acceleration.AccelerationDiagnosticCode;
import org.ta4j.core.acceleration.AccelerationDiagnostics;
import org.ta4j.core.acceleration.AccelerationException;
import org.ta4j.core.acceleration.AccelerationMode;
import org.ta4j.core.acceleration.IndicatorBatchEvaluator;
import org.ta4j.core.acceleration.IndicatorBatchRequest;
import org.ta4j.core.acceleration.IndicatorBatchResult;
import org.ta4j.core.acceleration.IndexedIndicatorValue;

/**
 * Optional adapter/provider-aware indicator batch evaluator.
 *
 * <p>
 * This evaluator adds explicit graph-family adapters and lazy provider probing
 * on top of the dependency-free core batch contract. Unsupported or
 * unprofitable requests return canonical CPU results unless the request marks
 * the provider as required.
 *
 * @since 0.23.1
 */
public final class AcceleratedIndicatorBatchEvaluator {

    private final List<IndicatorAccelerationAdapter<?>> adapters;
    private final List<IndicatorAccelerationProviderFactory> providerFactories;

    /**
     * Creates an evaluator with built-in adapters and discovered providers.
     *
     * @since 0.23.1
     */
    public AcceleratedIndicatorBatchEvaluator() {
        this(defaultAdapters(), defaultProviderFactories());
    }

    /**
     * Creates an evaluator with explicit adapters and providers.
     *
     * @param adapters          adapters
     * @param providerFactories provider factories
     * @since 0.23.1
     */
    public AcceleratedIndicatorBatchEvaluator(List<IndicatorAccelerationAdapter<?>> adapters,
            List<IndicatorAccelerationProviderFactory> providerFactories) {
        this.adapters = List.copyOf(Objects.requireNonNull(adapters, "adapters must not be null"));
        this.providerFactories = List
                .copyOf(Objects.requireNonNull(providerFactories, "providerFactories must not be null"));
    }

    /**
     * Evaluates a batch request with adapter/provider planning.
     *
     * @param indicator     indicator to evaluate
     * @param fromInclusive first index
     * @param toInclusive   last index
     * @param config        acceleration configuration
     * @param <T>           value type
     * @return ordered batch result
     * @since 0.23.1
     */
    public <T> IndicatorBatchResult<T> evaluate(Indicator<T> indicator, int fromInclusive, int toInclusive,
            AccelerationConfig config) {
        IndicatorBatchRequest<T> request = new IndicatorBatchRequest<>(indicator, fromInclusive, toInclusive, config);
        if (config.mode() == AccelerationMode.OFF || config.mode() == AccelerationMode.CPU) {
            return IndicatorBatchEvaluator.evaluate(indicator, fromInclusive, toInclusive, config);
        }
        AdapterMatch<T> match = match(indicator);
        if (!match.supported()) {
            return cpuResult(request, "scalar-cpu",
                    List.of(AccelerationDiagnostic.of(AccelerationDiagnosticCode.UNSUPPORTED_GRAPH,
                            "No acceleration adapter accepted the graph: " + match.rejectionReason())));
        }
        if (!match.deviceEligible()) {
            if (config.required() && config.mode().canUseDevice()) {
                throw new AccelerationException(AccelerationDiagnosticCode.NO_BENEFICIAL_DEVICE_STAGE,
                        "Adapter %s has no beneficial device stage".formatted(match.operationId()));
            }
            return cpuResult(request, match.operationId(),
                    List.of(new AccelerationDiagnostic(AccelerationDiagnosticCode.CPU_FASTER,
                            "Control adapter is intentionally CPU-planned under current evidence", null,
                            match.operationId())));
        }

        List<AccelerationDiagnostic> diagnostics = new ArrayList<>();
        List<IndicatorAccelerationProviderFactory> candidates = providerFactories.stream()
                .filter(factory -> eligible(config.mode(), factory.mode()))
                .sorted(Comparator.comparing(IndicatorAccelerationProviderFactory::providerId))
                .toList();
        for (IndicatorAccelerationProviderFactory factory : candidates) {
            Optional<ProviderSelection> selection = probeProvider(factory, match.operationId(), diagnostics);
            if (selection.isEmpty()) {
                continue;
            }
            IndicatorAccelerationProvider provider = selection.get().provider();
            ProviderCapability capability = selection.get().capability();
            try {
                boolean executeProvider = true;
                if (config.mode() == AccelerationMode.AUTO || config.mode() == AccelerationMode.HYBRID) {
                    double predictedSpeedup = provider.predictedSpeedup(request, match);
                    if (!Double.isFinite(predictedSpeedup) || predictedSpeedup < 0d) {
                        executeProvider = false;
                        diagnostics
                                .add(new AccelerationDiagnostic(AccelerationDiagnosticCode.PROVIDER_UNAVAILABLE,
                                        "Provider %s returned invalid predicted speedup %s"
                                                .formatted(capability.providerId(), predictedSpeedup),
                                        capability.providerId(), match.operationId()));
                    } else if (predictedSpeedup < config.minimumSpeedup()) {
                        executeProvider = false;
                        diagnostics.add(new AccelerationDiagnostic(AccelerationDiagnosticCode.CPU_FASTER,
                                "Provider %s predicted %.2f%% speedup, below the %.2f%% automatic threshold".formatted(
                                        capability.providerId(), predictedSpeedup * 100d,
                                        config.minimumSpeedup() * 100d),
                                capability.providerId(), match.operationId()));
                    }
                }
                if (executeProvider) {
                    Optional<IndicatorBatchResult<T>> result = provider.evaluate(request, match);
                    if (result.isPresent() && providerResultMatchesRequest(request, result.get())) {
                        return result.get();
                    }
                    addRejectedResultDiagnostic(request, match, capability, result, diagnostics);
                }
            } catch (LinkageError | RuntimeException exception) {
                diagnostics.add(new AccelerationDiagnostic(
                        AccelerationDiagnosticCode.PROVIDER_UNAVAILABLE, "Provider %s execution failed: %s"
                                .formatted(capability.providerId(), failureMessage(exception)),
                        capability.providerId(), match.operationId()));
            }
        }

        if (config.required() && config.mode().canUseDevice()) {
            boolean belowThresholdOnly = diagnostics.stream()
                    .anyMatch(diagnostic -> diagnostic.code() == AccelerationDiagnosticCode.CPU_FASTER)
                    && diagnostics.stream()
                            .noneMatch(
                                    diagnostic -> diagnostic.code() == AccelerationDiagnosticCode.PROVIDER_UNAVAILABLE
                                            || diagnostic.code() == AccelerationDiagnosticCode.NOT_IMPLEMENTED);
            if (belowThresholdOnly) {
                throw new AccelerationException(AccelerationDiagnosticCode.NO_BENEFICIAL_DEVICE_STAGE,
                        "No provider met the automatic speedup threshold for operation %s"
                                .formatted(match.operationId()));
            }
            throw new AccelerationException(AccelerationDiagnosticCode.REQUIRED_PROVIDER_UNAVAILABLE,
                    "No required provider could execute operation %s".formatted(match.operationId()));
        }
        if (config.mode() == AccelerationMode.HYBRID && match.partitionSafe()) {
            diagnostics.add(new AccelerationDiagnostic(AccelerationDiagnosticCode.HYBRID_FALLBACK,
                    "HYBRID requested, but no executable GPU partition was available; CPU completed the full range",
                    null, match.operationId()));
        }
        if (diagnostics.stream()
                .noneMatch(diagnostic -> diagnostic.code() == AccelerationDiagnosticCode.CPU_FASTER
                        || diagnostic.code() == AccelerationDiagnosticCode.PROVIDER_UNAVAILABLE
                        || diagnostic.code() == AccelerationDiagnosticCode.NOT_IMPLEMENTED)) {
            diagnostics.add(new AccelerationDiagnostic(AccelerationDiagnosticCode.PROVIDER_UNAVAILABLE,
                    "No eligible provider was available; canonical CPU fallback completed the request", null,
                    match.operationId()));
        }
        return cpuResult(request, match.operationId(), diagnostics);
    }

    @SuppressWarnings("unchecked")
    private <T> AdapterMatch<T> match(Indicator<T> indicator) {
        List<String> rejections = new ArrayList<>();
        for (IndicatorAccelerationAdapter<?> adapter : adapters) {
            AdapterMatch<?> match = adapter.match(indicator);
            if (match.supported()) {
                return (AdapterMatch<T>) match;
            }
            rejections.add(adapter.operationId() + ": " + match.rejectionReason());
        }
        return AdapterMatch.unsupported(String.join("; ", rejections));
    }

    private Optional<ProviderSelection> probeProvider(IndicatorAccelerationProviderFactory factory, String operationId,
            List<AccelerationDiagnostic> diagnostics) {
        diagnostics.add(new AccelerationDiagnostic(AccelerationDiagnosticCode.LAZY_PROVIDER_DISCOVERED,
                "Discovered provider factory " + factory.providerId(), factory.providerId(), operationId));
        try {
            IndicatorAccelerationProvider provider = factory.probe(List.of(operationId));
            ProviderCapability capability = provider.capability();
            if (capability.nativeInitialized()) {
                diagnostics.add(new AccelerationDiagnostic(AccelerationDiagnosticCode.NATIVE_PROVIDER_INITIALIZED,
                        "Provider initialized native runtime " + capability.providerId(), capability.providerId(),
                        operationId));
            }
            if (capability.available() && capability.supports(operationId)) {
                return Optional.of(new ProviderSelection(provider, capability));
            }
            AccelerationDiagnosticCode code = capability.rejectionReason().contains("NOT_IMPLEMENTED")
                    ? AccelerationDiagnosticCode.NOT_IMPLEMENTED
                    : AccelerationDiagnosticCode.PROVIDER_UNAVAILABLE;
            diagnostics.add(new AccelerationDiagnostic(code, capability.rejectionReason(), capability.providerId(),
                    operationId));
        } catch (LinkageError | RuntimeException exception) {
            diagnostics.add(new AccelerationDiagnostic(AccelerationDiagnosticCode.PROVIDER_UNAVAILABLE,
                    "Provider %s probe failed: %s".formatted(factory.providerId(), failureMessage(exception)),
                    factory.providerId(), operationId));
        }
        return Optional.empty();
    }

    private static <T> void addRejectedResultDiagnostic(IndicatorBatchRequest<T> request, AdapterMatch<T> match,
            ProviderCapability capability, Optional<IndicatorBatchResult<T>> result,
            List<AccelerationDiagnostic> diagnostics) {
        if (result.isPresent()) {
            diagnostics.add(new AccelerationDiagnostic(AccelerationDiagnosticCode.PROVIDER_UNAVAILABLE,
                    "Provider %s returned an invalid result for requested indices [%d, %d]"
                            .formatted(capability.providerId(), request.fromInclusive(), request.toInclusive()),
                    capability.providerId(), match.operationId()));
        } else {
            diagnostics.add(new AccelerationDiagnostic(
                    AccelerationDiagnosticCode.NOT_IMPLEMENTED, "Provider %s did not implement operation %s"
                            .formatted(capability.providerId(), match.operationId()),
                    capability.providerId(), match.operationId()));
        }
    }

    private static String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static boolean eligible(AccelerationMode requested, AccelerationMode providerMode) {
        return requested == AccelerationMode.AUTO || requested == AccelerationMode.HYBRID || requested == providerMode;
    }

    private static boolean providerResultMatchesRequest(IndicatorBatchRequest<?> request,
            IndicatorBatchResult<?> result) {
        int expectedCount = request.toInclusive() - request.fromInclusive() + 1;
        if (result.values().size() != expectedCount) {
            return false;
        }
        for (int offset = 0; offset < expectedCount; offset++) {
            IndexedIndicatorValue<?> value = result.values().get(offset);
            if (value.index() != request.fromInclusive() + offset) {
                return false;
            }
        }
        return true;
    }

    private static <T> IndicatorBatchResult<T> cpuResult(IndicatorBatchRequest<T> request, String operationId,
            Collection<AccelerationDiagnostic> leadingDiagnostics) {
        List<AccelerationDiagnostic> diagnostics = new ArrayList<>(leadingDiagnostics);
        diagnostics.add(AccelerationDiagnostic.of(AccelerationDiagnosticCode.CPU_EVALUATED,
                "Values were produced by Indicator#getValue(int)"));
        boolean nativeInitialized = diagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.code() == AccelerationDiagnosticCode.NATIVE_PROVIDER_INITIALIZED);
        AccelerationDiagnostics execution = new AccelerationDiagnostics(request.config().mode(), AccelerationMode.CPU,
                "cpu", operationId, nativeInitialized, diagnostics);
        IndicatorBatchResult<T> scalar = IndicatorBatchEvaluator.evaluate(request.indicator(), request.fromInclusive(),
                request.toInclusive(), AccelerationConfig.cpu());
        return new IndicatorBatchResult<>(scalar.values(), execution);
    }

    private static List<IndicatorAccelerationAdapter<?>> defaultAdapters() {
        return List.of(new CloseSmaControlAdapter(), new ForecastBatchAdapter());
    }

    private static List<IndicatorAccelerationProviderFactory> defaultProviderFactories() {
        List<IndicatorAccelerationProviderFactory> factories = new ArrayList<>();
        ServiceLoader.load(IndicatorAccelerationProviderFactory.class).forEach(factories::add);
        if (factories.isEmpty()) {
            factories.add(new MetalAccelerationProviderFactory());
            factories.add(new CudaAccelerationProviderFactory());
        }
        return factories;
    }

    private record ProviderSelection(IndicatorAccelerationProvider provider, ProviderCapability capability) {
    }
}
