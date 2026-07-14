/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.Num;

/**
 * Named, representation-bound feature extractors for return-moment models.
 *
 * <p>
 * Values are raw and unscaled. Distance and regression consumers remain
 * responsible for fitting and applying their own training-only standardization.
 *
 * @since 0.23.1
 */
public final class ForecastFeatureExtractors {

    private ForecastFeatureExtractors() {
    }

    /**
     * Extracts {@code [mean, volatility]}.
     *
     * @param representation required return representation
     * @param <S>            state type
     * @return two-feature extractor
     */
    public static <S extends ReturnMomentState> ForecastFeatureExtractor<S> meanVolatility(
            ReturnRepresentation representation) {
        String returnUnit = unit(representation);
        return extractor("return-moments/mean-volatility", representation,
                List.of(new ForecastFeatureSchema.Feature("mean", returnUnit),
                        new ForecastFeatureSchema.Feature("volatility", returnUnit)),
                List.of(ReturnMomentState::mean, ReturnMomentState::volatility));
    }

    /**
     * Extracts {@code [drift, volatility]}.
     *
     * @param representation required return representation
     * @param <S>            state type
     * @return two-feature extractor
     */
    public static <S extends ReturnMomentState> ForecastFeatureExtractor<S> driftVolatility(
            ReturnRepresentation representation) {
        String returnUnit = unit(representation);
        return extractor("return-moments/drift-volatility", representation,
                List.of(new ForecastFeatureSchema.Feature("drift", returnUnit),
                        new ForecastFeatureSchema.Feature("volatility", returnUnit)),
                List.of(ReturnMomentState::drift, ReturnMomentState::volatility));
    }

    /**
     * Extracts {@code [mean, drift, variance]} without duplicating dispersion.
     *
     * @param representation required return representation
     * @param <S>            state type
     * @return three-feature extractor
     */
    public static <S extends ReturnMomentState> ForecastFeatureExtractor<S> meanDriftVariance(
            ReturnRepresentation representation) {
        String returnUnit = unit(representation);
        return extractor("return-moments/mean-drift-variance", representation,
                List.of(new ForecastFeatureSchema.Feature("mean", returnUnit),
                        new ForecastFeatureSchema.Feature("drift", returnUnit),
                        new ForecastFeatureSchema.Feature("variance", returnUnit + "^2")),
                List.of(ReturnMomentState::mean, ReturnMomentState::drift, ReturnMomentState::variance));
    }

    private static <S extends ReturnMomentState> ForecastFeatureExtractor<S> extractor(String id,
            ReturnRepresentation representation, List<ForecastFeatureSchema.Feature> features,
            List<Function<ReturnMomentState, Num>> resolvers) {
        ReturnRepresentation requiredRepresentation = Objects.requireNonNull(representation,
                "representation must not be null");
        ForecastFeatureSchema schema = new ForecastFeatureSchema(id, 1, requiredRepresentation, features);
        List<Function<ReturnMomentState, Num>> valueResolvers = List.copyOf(resolvers);
        return new ForecastFeatureExtractor<>() {
            @Override
            public ForecastFeatureSchema schema() {
                return schema;
            }

            @Override
            public void extractInto(S state, double[] target, int offset) {
                S value = Objects.requireNonNull(state, "state must not be null");
                double[] destination = Objects.requireNonNull(target, "target must not be null");
                if (!value.isStable()) {
                    throw new IllegalArgumentException("state must be stable");
                }
                if (value.representation() != requiredRepresentation) {
                    throw new IllegalArgumentException("state representation must match schema representation");
                }
                if (offset < 0 || offset > destination.length - schema.dimension()) {
                    throw new IndexOutOfBoundsException("target does not have room for the schema at offset");
                }
                for (int i = 0; i < valueResolvers.size(); i++) {
                    destination[offset + i] = finiteDouble(valueResolvers.get(i).apply(value),
                            schema.features().get(i).name());
                }
            }
        };
    }

    private static double finiteDouble(Num value, String fieldName) {
        Num number = Objects.requireNonNull(value, fieldName + " must not be null");
        if (!Num.isFinite(number)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        double primitive = number.doubleValue();
        if (!Double.isFinite(primitive)) {
            throw new IllegalArgumentException(fieldName + " cannot be represented as a finite double");
        }
        if (primitive == 0d && !number.isZero()) {
            throw new IllegalArgumentException(fieldName + " underflows primitive double precision");
        }
        return primitive;
    }

    private static String unit(ReturnRepresentation representation) {
        return switch (Objects.requireNonNull(representation, "representation must not be null")) {
        case LOG -> "log-return";
        case DECIMAL -> "decimal-return";
        case PERCENTAGE -> "percentage-points";
        case MULTIPLICATIVE -> "multiplicative-return";
        };
    }
}
