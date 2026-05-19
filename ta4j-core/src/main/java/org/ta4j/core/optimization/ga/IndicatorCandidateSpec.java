/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.optimization.ga;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

/**
 * Searchable indicator family backed by explicit parameter domains.
 *
 * @param <T> indicator value type
 * @since 0.22.7
 */
public final class IndicatorCandidateSpec<T> {

    private final String name;
    private final List<ParameterDomain<?>> domains;
    private final IndicatorCandidateFactory<T> factory;

    /**
     * Creates a searchable indicator family.
     *
     * @param name    stable indicator family name
     * @param domains ordered parameter domains
     * @param factory indicator factory for decoded parameter values
     * @since 0.22.7
     */
    public IndicatorCandidateSpec(String name, List<ParameterDomain<?>> domains, IndicatorCandidateFactory<T> factory) {
        this.name = requireName(name);
        this.domains = validateDomains(domains);
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    /**
     * @return stable indicator family name
     * @since 0.22.7
     */
    public String name() {
        return name;
    }

    /**
     * @return ordered immutable parameter domains
     * @since 0.22.7
     */
    public List<ParameterDomain<?>> domains() {
        return domains;
    }

    /**
     * Creates a candidate codec bound to the supplied series and source indicators.
     *
     * @param series           bar series associated with decoded indicators
     * @param sourceIndicators optional source indicators used by this family
     * @return codec that decodes genes into indicator candidates
     * @since 0.22.7
     */
    public CandidateCodec<IndicatorCandidate<T>> codec(BarSeries series,
            List<? extends Indicator<?>> sourceIndicators) {
        Objects.requireNonNull(series, "series");
        List<Indicator<?>> sources = copySourceIndicators(sourceIndicators);
        return new CandidateCodec<>(domains, parameters -> createCandidate(series, sources, parameters),
                this::candidateId);
    }

    /**
     * Creates one indicator candidate from decoded parameter values.
     *
     * @param series           bar series associated with the indicator
     * @param sourceIndicators optional source indicators used by this family
     * @param parameters       decoded candidate parameters
     * @return decoded indicator candidate
     * @since 0.22.7
     */
    public IndicatorCandidate<T> createCandidate(BarSeries series, List<? extends Indicator<?>> sourceIndicators,
            CandidateCodec.ParameterValues parameters) {
        Objects.requireNonNull(parameters, "parameters");
        return createCandidateFromParameters(series, sourceIndicators, parametersFrom(parameters.asMap()));
    }

    /**
     * Creates one indicator candidate from decoded values keyed by domain name.
     *
     * @param series           bar series associated with the indicator
     * @param sourceIndicators optional source indicators used by this family
     * @param values           decoded values keyed by parameter name
     * @return decoded indicator candidate
     * @since 0.22.7
     */
    public IndicatorCandidate<T> createCandidate(BarSeries series, List<? extends Indicator<?>> sourceIndicators,
            Map<String, ?> values) {
        return createCandidateFromParameters(series, sourceIndicators, parametersFrom(values));
    }

    private IndicatorCandidate<T> createCandidateFromParameters(BarSeries series,
            List<? extends Indicator<?>> sourceIndicators, CandidateCodec.ParameterValues parameters) {
        Objects.requireNonNull(series, "series");
        List<Indicator<?>> sources = copySourceIndicators(sourceIndicators);
        Indicator<T> indicator = Objects.requireNonNull(factory.create(series, sources, parameters),
                "factory must not return null");
        return new IndicatorCandidate<>(candidateId(parameters), indicator, parameters);
    }

    private String candidateId(CandidateCodec.ParameterValues parameters) {
        return name + " [" + parameters.toStableId() + "]";
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name;
    }

    private static List<ParameterDomain<?>> validateDomains(List<ParameterDomain<?>> domains) {
        Objects.requireNonNull(domains, "domains");
        if (domains.isEmpty()) {
            throw new IllegalArgumentException("domains must not be empty");
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        List<ParameterDomain<?>> copies = new ArrayList<>(domains.size());
        for (ParameterDomain<?> domain : domains) {
            ParameterDomain<?> checked = Objects.requireNonNull(domain, "domain");
            if (!names.add(checked.name())) {
                throw new IllegalArgumentException("duplicate domain name: " + checked.name());
            }
            copies.add(checked);
        }
        return List.copyOf(copies);
    }

    private CandidateCodec.ParameterValues parametersFrom(Map<String, ?> values) {
        Objects.requireNonNull(values, "values");
        if (values.size() != domains.size()) {
            throw new IllegalArgumentException(
                    "value count " + values.size() + " does not match domain count " + domains.size());
        }

        LinkedHashMap<String, Object> orderedValues = new LinkedHashMap<>();
        for (ParameterDomain<?> domain : domains) {
            if (!values.containsKey(domain.name())) {
                throw new IllegalArgumentException("missing value for domain: " + domain.name());
            }
            Object value = Objects.requireNonNull(values.get(domain.name()),
                    "value for domain '" + domain.name() + "' must not be null");
            if (!domain.values().contains(value)) {
                throw new IllegalArgumentException("value out of range for domain '" + domain.name() + "': " + value);
            }
            orderedValues.put(domain.name(), value);
        }
        return new CandidateCodec.ParameterValues(orderedValues);
    }

    private static List<Indicator<?>> copySourceIndicators(List<? extends Indicator<?>> sourceIndicators) {
        Objects.requireNonNull(sourceIndicators, "sourceIndicators");
        List<Indicator<?>> copies = new ArrayList<>(sourceIndicators.size());
        for (Indicator<?> sourceIndicator : sourceIndicators) {
            copies.add(Objects.requireNonNull(sourceIndicator, "sourceIndicator"));
        }
        return List.copyOf(copies);
    }
}
