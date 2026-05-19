/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.optimization.ga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;

import org.apache.commons.math3.random.RandomGenerator;

/**
 * Encodes and decodes fixed-length parameter vectors for candidate search.
 *
 * @param <C> decoded candidate context type
 * @since 0.22.7
 */
public final class CandidateCodec<C> {

    private final List<ParameterDomain<?>> domains;
    private final Function<ParameterValues, C> contextFactory;
    private final Function<ParameterValues, String> idFactory;

    /**
     * Creates a codec from ordered parameter domains and a context factory.
     *
     * @param domains        ordered parameter domains
     * @param contextFactory builds the decoded candidate context
     * @since 0.22.7
     */
    public CandidateCodec(List<ParameterDomain<?>> domains, Function<ParameterValues, C> contextFactory) {
        this(domains, contextFactory, ParameterValues::toStableId);
    }

    /**
     * Creates a codec from ordered parameter domains, a context factory, and a
     * stable candidate identifier factory.
     *
     * @param domains        ordered parameter domains
     * @param contextFactory builds the decoded candidate context
     * @param idFactory      builds a stable identifier from decoded parameters
     * @since 0.22.7
     */
    public CandidateCodec(List<ParameterDomain<?>> domains, Function<ParameterValues, C> contextFactory,
            Function<ParameterValues, String> idFactory) {
        Objects.requireNonNull(domains, "domains");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
        this.idFactory = Objects.requireNonNull(idFactory, "idFactory");
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
        this.domains = List.copyOf(copies);
    }

    /**
     * @return ordered immutable parameter domains
     * @since 0.22.7
     */
    public List<ParameterDomain<?>> domains() {
        return domains;
    }

    /**
     * Decodes one chromosome representation into its candidate context and stable
     * parameter map.
     *
     * @param representation allele indexes in domain order
     * @return decoded candidate
     * @since 0.22.7
     */
    public DecodedCandidate<C> decode(List<Integer> representation) {
        LinkedHashMap<String, Object> decodedValues = decodeValues(representation);
        ParameterValues parameters = new ParameterValues(decodedValues);
        C context = Objects.requireNonNull(contextFactory.apply(parameters), "contextFactory must not return null");
        String id = Objects.requireNonNull(idFactory.apply(parameters), "idFactory must not return null");
        return new DecodedCandidate<>(id, context, parameters);
    }

    /**
     * Creates one random valid chromosome representation from the supplied
     * generator.
     *
     * @param random seeded random generator
     * @return immutable valid representation
     * @since 0.22.7
     */
    public List<Integer> randomRepresentation(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        List<Integer> representation = new ArrayList<>(domains.size());
        for (ParameterDomain<?> domain : domains) {
            representation.add(domain.randomIndex(random));
        }
        return List.copyOf(representation);
    }

    void validateRepresentation(List<Integer> representation) {
        decodeValues(representation);
    }

    private LinkedHashMap<String, Object> decodeValues(List<Integer> representation) {
        Objects.requireNonNull(representation, "representation");
        if (representation.size() != domains.size()) {
            throw new IllegalArgumentException(
                    "representation size " + representation.size() + " does not match domain count " + domains.size());
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < domains.size(); index++) {
            Integer allele = Objects.requireNonNull(representation.get(index),
                    "representation allele at index " + index + " must not be null");
            ParameterDomain<?> domain = domains.get(index);
            values.put(domain.name(), domain.decode(allele));
        }
        return values;
    }

    /**
     * Stable decoded parameter values in domain order.
     *
     * @since 0.22.7
     */
    public static final class ParameterValues {

        private final Map<String, Object> values;

        ParameterValues(Map<String, ?> values) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        /**
         * Returns one decoded value with a type check.
         *
         * @param name parameter name
         * @param type expected value type
         * @param <T>  expected value type
         * @return decoded value
         * @since 0.22.7
         */
        public <T> T get(String name, Class<T> type) {
            Objects.requireNonNull(type, "type");
            Object value = get(name);
            if (!type.isInstance(value)) {
                throw new IllegalArgumentException("parameter '" + name + "' is not a " + type.getSimpleName());
            }
            return type.cast(value);
        }

        /**
         * Returns one decoded value without casting.
         *
         * @param name parameter name
         * @return decoded value
         * @since 0.22.7
         */
        public Object get(String name) {
            if (!values.containsKey(name)) {
                throw new IllegalArgumentException("unknown parameter: " + name);
            }
            return values.get(name);
        }

        /**
         * @param name parameter name
         * @return whether the parameter exists
         * @since 0.22.7
         */
        public boolean contains(String name) {
            return values.containsKey(name);
        }

        /**
         * @return immutable ordered value map
         * @since 0.22.7
         */
        public Map<String, Object> asMap() {
            return values;
        }

        /**
         * @return stable key-value identifier in domain order
         * @since 0.22.7
         */
        public String toStableId() {
            StringJoiner joiner = new StringJoiner(", ");
            values.forEach((name, value) -> joiner.add(name + "=" + value));
            return joiner.toString();
        }

        @Override
        public String toString() {
            return toStableId();
        }
    }

    /**
     * One decoded candidate result.
     *
     * @param id         stable candidate identifier
     * @param context    decoded candidate context
     * @param parameters decoded parameter values
     * @param <C>        candidate context type
     * @since 0.22.7
     */
    public record DecodedCandidate<C>(String id, C context, ParameterValues parameters) {

        /**
         * Creates a validated decoded candidate.
         *
         * @since 0.22.7
         */
        public DecodedCandidate {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("id must not be blank");
            }
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(parameters, "parameters");
        }
    }
}
