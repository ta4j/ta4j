/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.ta4j.core.criteria.ReturnRepresentation;

/**
 * Stable identity, order, units, and representation for a feature vector.
 *
 * @param id             durable schema identifier
 * @param version        positive schema version
 * @param representation source return representation
 * @param features       ordered feature descriptors
 * @since 0.23.1
 */
public record ForecastFeatureSchema(String id, int version, ReturnRepresentation representation,
        List<Feature> features) {

    /** Creates a validated defensive schema. */
    public ForecastFeatureSchema {
        id = requireNonblank(id, "id");
        if (version <= 0) {
            throw new IllegalArgumentException("version must be > 0");
        }
        representation = Objects.requireNonNull(representation, "representation must not be null");
        features = List.copyOf(Objects.requireNonNull(features, "features must not be null"));
        if (features.isEmpty()) {
            throw new IllegalArgumentException("features must not be empty");
        }
        Set<String> names = new HashSet<>();
        for (Feature feature : features) {
            Feature value = Objects.requireNonNull(feature, "feature must not be null");
            if (!names.add(value.name())) {
                throw new IllegalArgumentException("feature names must be unique");
            }
        }
    }

    /** @return fixed feature-vector dimension */
    public int dimension() {
        return features.size();
    }

    private static String requireNonblank(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    /**
     * One ordered feature descriptor.
     *
     * @param name stable feature name
     * @param unit operator-facing raw unit
     * @since 0.23.1
     */
    public record Feature(String name, String unit) {

        /** Creates a feature descriptor. */
        public Feature {
            name = requireNonblank(name, "name");
            unit = requireNonblank(unit, "unit");
        }
    }
}
